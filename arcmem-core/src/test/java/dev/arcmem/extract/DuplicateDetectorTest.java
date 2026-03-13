package dev.arcmem.core.extraction;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import dev.arcmem.core.spi.llm.LlmCallService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmDuplicateDetector")
class DuplicateDetectorTest {

    @Mock private ChatModel chatModel;
    @Mock private LlmCallService llmCallService;

    private List<MemoryUnit> singleUnit(String text) {
        return List.of(MemoryUnit.withoutTrust("a1", text, 500, Authority.PROVISIONAL, false, 0.9, 0));
    }

    private void mockLlmResponse(String response) {
        var generation = new Generation(new AssistantMessage(response));
        var chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Nested
    @DisplayName("isDuplicate")
    class IsDuplicate {

        @Test
        @DisplayName("returns true when LLM says DUPLICATE")
        void llmDuplicate() {
            var detector = new LlmDuplicateDetector(chatModel, llmCallService);
            mockLlmResponse("DUPLICATE");

            assertThat(detector.isDuplicate("The monarch has perished", singleUnit("The king is dead"))).isTrue();
            verify(chatModel).call(any(Prompt.class));
        }

        @Test
        @DisplayName("returns false when LLM says UNIQUE")
        void llmUnique() {
            var detector = new LlmDuplicateDetector(chatModel, llmCallService);
            mockLlmResponse("UNIQUE");

            assertThat(detector.isDuplicate("Something different", singleUnit("The king is dead"))).isFalse();
        }

        @Test
        @DisplayName("returns false for empty memory unit list without LLM call")
        void emptyUnitsReturnsFalse() {
            var detector = new LlmDuplicateDetector(chatModel, llmCallService);

            assertThat(detector.isDuplicate("anything", List.of())).isFalse();
            verify(chatModel, never()).call(any(Prompt.class));
        }

        @Test
        @DisplayName("fail-open: LLM exception returns false (assume unique)")
        void llmFailureReturnsUnique() {
            var detector = new LlmDuplicateDetector(chatModel, llmCallService);
            when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM timeout"));

            assertThat(detector.isDuplicate("candidate", singleUnit("unit"))).isFalse();
        }
    }

    @Nested
    @DisplayName("batchIsDuplicate")
    class BatchIsDuplicate {

        @Test
        @DisplayName("fail-open: parse failure returns all false (assume unique)")
        void batchParseFailureReturnsAllUnique() {
            var detector = new LlmDuplicateDetector(chatModel, llmCallService);
            when(llmCallService.callBatched(any(), any())).thenReturn("not-json");

            var result = detector.batchIsDuplicate(List.of("A", "B"), singleUnit("The king is dead"));

            assertThat(result).isEqualTo(Map.of("A", false, "B", false));
        }

        @Test
        @DisplayName("fail-open: omitted candidates assumed unique")
        void batchOmissionsAssumedUnique() {
            var detector = new LlmDuplicateDetector(chatModel, llmCallService);
            when(llmCallService.callBatched(any(), any())).thenReturn("""
                    {"results":[{"candidate":"A","isDuplicate":false}]}
                    """);

            var result = detector.batchIsDuplicate(List.of("A", "B"), singleUnit("The king is dead"));

            assertThat(result).isEqualTo(Map.of("A", false, "B", false));
        }
    }

    @Nested
    @DisplayName("CompositeDuplicateDetector")
    class Composite {

        @Test
        @DisplayName("fast-path match short-circuits without LLM call")
        void fastPathMatchSkipsLlm() {
            var fast = new NormalizedStringDuplicateDetector();
            var llm = new LlmDuplicateDetector(chatModel, llmCallService);
            var detector = new CompositeDuplicateDetector(fast, llm);

            assertThat(detector.isDuplicate("the king is dead", singleUnit("The king is dead"))).isTrue();
            verify(chatModel, never()).call(any(Prompt.class));
        }

        @Test
        @DisplayName("falls back to LLM when fast-path misses")
        void fallsBackToLlm() {
            var fast = new NormalizedStringDuplicateDetector();
            var llm = new LlmDuplicateDetector(chatModel, llmCallService);
            var detector = new CompositeDuplicateDetector(fast, llm);
            mockLlmResponse("DUPLICATE");

            assertThat(detector.isDuplicate("The monarch has perished", singleUnit("The king is dead"))).isTrue();
            verify(chatModel).call(any(Prompt.class));
        }

        @Test
        @DisplayName("returns false when both paths say unique")
        void bothUnique() {
            var fast = new NormalizedStringDuplicateDetector();
            var llm = new LlmDuplicateDetector(chatModel, llmCallService);
            var detector = new CompositeDuplicateDetector(fast, llm);
            mockLlmResponse("UNIQUE");

            assertThat(detector.isDuplicate("The tavern is warm", singleUnit("The king is dead"))).isFalse();
        }
    }
}
