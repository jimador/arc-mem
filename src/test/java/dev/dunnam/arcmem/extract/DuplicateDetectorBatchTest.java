package dev.dunnam.diceanchors.extract;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.sim.engine.LlmCallService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DuplicateDetector batch operations")
class DuplicateDetectorBatchTest {

    @Mock private ChatModel chatModel;
    @Mock private LlmCallService llmCallService;

    private final NormalizedStringDuplicateDetector fastDetector = new NormalizedStringDuplicateDetector();

    private List<Anchor> singleAnchor(String text) {
        return List.of(Anchor.withoutTrust("a1", text, 500, Authority.PROVISIONAL, false, 0.9, 0));
    }

    @Nested
    @DisplayName("CompositeDuplicateDetector batch")
    class CompositeBatch {

        @Test
        @DisplayName("empty list returns empty map, no LLM call")
        void emptyListReturnsEmptyMap() {
            var llm = new LlmDuplicateDetector(chatModel, llmCallService);
            var detector = new CompositeDuplicateDetector(fastDetector, llm);

            var result = detector.batchIsDuplicate(List.of(), singleAnchor("fact"));

            assertThat(result).isEmpty();
            verify(llmCallService, never()).callBatched(anyString(), anyString());
        }

        @Test
        @DisplayName("fast-path matches resolve without LLM call")
        void allFastPathNoLlmCall() {
            var llm = new LlmDuplicateDetector(chatModel, llmCallService);
            var detector = new CompositeDuplicateDetector(fastDetector, llm);

            var result = detector.batchIsDuplicate(List.of("the king is dead"), singleAnchor("The king is dead"));

            assertThat(result).containsEntry("the king is dead", true);
            verify(llmCallService, never()).callBatched(anyString(), anyString());
        }

        @Test
        @DisplayName("non-fast-path candidates are sent to LLM in one batched call")
        void mixedFastAndLlm() {
            var llm = new LlmDuplicateDetector(chatModel, llmCallService);
            var detector = new CompositeDuplicateDetector(fastDetector, llm);
            when(llmCallService.callBatched(anyString(), anyString())).thenReturn(
                    """
                    {"results": [{"candidate": "The monarch has perished", "isDuplicate": true}]}""");

            var result = detector.batchIsDuplicate(
                    List.of("the king is dead", "The monarch has perished"),
                    singleAnchor("The king is dead"));

            assertThat(result).containsEntry("the king is dead", true);
            assertThat(result).containsEntry("The monarch has perished", true);
            verify(llmCallService).callBatched(anyString(), anyString());
        }

        @Test
        @DisplayName("no anchors returns all false without LLM call")
        void noAnchorsReturnsFalseForAll() {
            var llm = new LlmDuplicateDetector(chatModel, llmCallService);
            var detector = new CompositeDuplicateDetector(fastDetector, llm);

            var result = detector.batchIsDuplicate(List.of("fact one", "fact two"), List.of());

            assertThat(result).containsEntry("fact one", false);
            assertThat(result).containsEntry("fact two", false);
            verify(llmCallService, never()).callBatched(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("LlmDuplicateDetector batch")
    class LlmBatch {

        @Test
        @DisplayName("LLM failure falls back to individual isDuplicate calls")
        void llmFailureFallsBackToIndividualCalls() {
            var detector = new LlmDuplicateDetector(chatModel, llmCallService);
            when(llmCallService.callBatched(anyString(), anyString()))
                    .thenThrow(new RuntimeException("LLM timeout"));
            var generation = mock(Generation.class);
            var message = new AssistantMessage("UNIQUE");
            when(generation.getOutput()).thenReturn(message);
            var chatResponse = mock(ChatResponse.class);
            when(chatResponse.getResult()).thenReturn(generation);
            when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

            var result = detector.batchIsDuplicate(List.of("Novel statement"), singleAnchor("The king is dead"));

            assertThat(result).containsKey("Novel statement");
            assertThat(result.get("Novel statement")).isFalse();
        }

        @Test
        @DisplayName("malformed LLM JSON response results in all-unique (fail-open)")
        void malformedJsonAllUnique() {
            var detector = new LlmDuplicateDetector(chatModel, llmCallService);
            when(llmCallService.callBatched(anyString(), anyString())).thenReturn("this is not json at all");

            var result = detector.batchIsDuplicate(List.of("candidate"), singleAnchor("fact"));

            assertThat(result).containsEntry("candidate", false);
        }

        @Test
        @DisplayName("no anchors returns all false without LLM call")
        void noAnchorsReturnsFalse() {
            var detector = new LlmDuplicateDetector(chatModel, llmCallService);

            var result = detector.batchIsDuplicate(List.of("fact one", "fact two"), List.of());

            assertThat(result).containsEntry("fact one", false);
            assertThat(result).containsEntry("fact two", false);
            verify(llmCallService, never()).callBatched(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("NormalizedStringDuplicateDetector as DuplicateDetector")
    class FastOnly {

        @Test
        @DisplayName("never calls LLM for non-matches")
        void fastOnlyNeverCallsLlm() {
            DuplicateDetector detector = fastDetector;

            var result = detector.batchIsDuplicate(
                    List.of("Something completely different", "Another new fact"),
                    singleAnchor("The king is dead"));

            assertThat(result).containsEntry("Something completely different", false);
            assertThat(result).containsEntry("Another new fact", false);
            verify(llmCallService, never()).callBatched(anyString(), anyString());
        }
    }
}
