package dev.dunnam.diceanchors.extract;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.sim.engine.LlmCallService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DuplicateDetector batch operations")
class DuplicateDetectorBatchTest {

    private static final String CONTEXT_ID = "test-ctx";

    @Mock private ChatModel chatModel;
    @Mock private AnchorEngine engine;
    @Mock private LlmCallService llmCallService;

    private final NormalizedStringDuplicateDetector fastDetector = new NormalizedStringDuplicateDetector();

    private DuplicateDetector detectorWithStrategy(String strategy) {
        var anchorConfig = new DiceAnchorsProperties.AnchorConfig(
                20, 500, 100, 900, true, 0.65, strategy, "TIERED", true, true, true, 0.6, 400, 200);
        var properties = new DiceAnchorsProperties(
                anchorConfig, null, null, null, null, null, null,
                new DiceAnchorsProperties.AssemblyConfig(0));
        return new DuplicateDetector(chatModel, engine, fastDetector, properties, llmCallService);
    }

    private List<Anchor> singleAnchor(String text) {
        return List.of(Anchor.withoutTrust("a1", text, 500, Authority.PROVISIONAL, false, 0.9, 0));
    }

    @Nested
    @DisplayName("batchIsDuplicate")
    class BatchIsDuplicate {

        @Test
        @DisplayName("empty list returns empty map, no LLM call")
        void batchIsDuplicateEmptyListReturnsEmptyMap() {
            var detector = detectorWithStrategy("FAST_THEN_LLM");

            var result = detector.batchIsDuplicate(CONTEXT_ID, List.of());

            assertThat(result).isEmpty();
            verify(llmCallService, never()).callBatched(anyString(), anyString());
            verify(engine, never()).inject(anyString());
        }

        @Test
        @DisplayName("fast-path matches resolve without LLM call")
        void batchIsDuplicateAllFastPathNoLlmCall() {
            var detector = detectorWithStrategy("FAST_THEN_LLM");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));

            var result = detector.batchIsDuplicate(CONTEXT_ID, List.of("the king is dead"));

            assertThat(result).containsEntry("the king is dead", true);
            verify(llmCallService, never()).callBatched(anyString(), anyString());
        }

        @Test
        @DisplayName("non-fast-path candidates are sent to LLM in one batched call")
        void batchIsDuplicateMixedFastAndLlm() {
            var detector = detectorWithStrategy("FAST_THEN_LLM");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));
            var batchResponse = """
                    {"results": [{"candidate": "The monarch has perished", "isDuplicate": true}]}""";
            when(llmCallService.callBatched(anyString(), anyString())).thenReturn(batchResponse);

            var result = detector.batchIsDuplicate(CONTEXT_ID,
                    List.of("the king is dead", "The monarch has perished"));

            assertThat(result).containsEntry("the king is dead", true);
            assertThat(result).containsEntry("The monarch has perished", true);
            verify(llmCallService).callBatched(anyString(), anyString());
        }

        @Test
        @DisplayName("LLM failure falls back to individual isDuplicate calls")
        void batchIsDuplicateLlmFailureFallsBackToIndividualCalls() {
            var detector = detectorWithStrategy("LLM_ONLY");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));
            when(llmCallService.callBatched(anyString(), anyString()))
                    .thenThrow(new RuntimeException("LLM timeout"));
            // Individual fallback will use chatModel
            var generation = org.mockito.Mockito.mock(org.springframework.ai.chat.model.Generation.class);
            var message = new org.springframework.ai.chat.messages.AssistantMessage("UNIQUE");
            when(generation.getOutput()).thenReturn(message);
            var chatResponse = org.mockito.Mockito.mock(org.springframework.ai.chat.model.ChatResponse.class);
            when(chatResponse.getResult()).thenReturn(generation);
            when(chatModel.call(org.mockito.ArgumentMatchers.any(
                    org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponse);

            var result = detector.batchIsDuplicate(CONTEXT_ID, List.of("Novel statement"));

            // Should have fallen back and gotten a result for the candidate
            assertThat(result).containsKey("Novel statement");
        }

        @Test
        @DisplayName("no anchors in context returns all false without LLM call")
        void batchIsDuplicateNoAnchorsReturnsFalseForAll() {
            var detector = detectorWithStrategy("FAST_THEN_LLM");
            when(engine.inject(CONTEXT_ID)).thenReturn(List.of());

            var result = detector.batchIsDuplicate(CONTEXT_ID, List.of("fact one", "fact two"));

            assertThat(result).containsEntry("fact one", false);
            assertThat(result).containsEntry("fact two", false);
            verify(llmCallService, never()).callBatched(anyString(), anyString());
        }

        @Test
        @DisplayName("FAST_ONLY strategy never calls LLM for non-matches")
        void batchIsDuplicateFastOnlyNeverCallsLlm() {
            var detector = detectorWithStrategy("FAST_ONLY");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));

            var result = detector.batchIsDuplicate(CONTEXT_ID,
                    List.of("Something completely different", "Another new fact"));

            assertThat(result).containsEntry("Something completely different", false);
            assertThat(result).containsEntry("Another new fact", false);
            verify(llmCallService, never()).callBatched(anyString(), anyString());
        }

        @Test
        @DisplayName("malformed LLM JSON response results in all-unique fallback")
        void batchIsDuplicateMalformedJsonFallsBackToAllUnique() {
            var detector = detectorWithStrategy("LLM_ONLY");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("fact"));
            when(llmCallService.callBatched(anyString(), anyString()))
                    .thenReturn("this is not json at all");

            var result = detector.batchIsDuplicate(CONTEXT_ID, List.of("candidate"));

            assertThat(result).containsEntry("candidate", false);
        }
    }
}
