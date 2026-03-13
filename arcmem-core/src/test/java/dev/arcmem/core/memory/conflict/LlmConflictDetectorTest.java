package dev.arcmem.core.memory.conflict;
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
import java.util.List;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("LlmConflictDetector")
@ExtendWith(MockitoExtension.class)
class LlmConflictDetectorTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private LlmCallService llmCallService;

    private LlmConflictDetector detector() {
        return new LlmConflictDetector(chatModel, "test-model", llmCallService);
    }

    private MemoryUnit unit(String id, String text) {
        return MemoryUnit.withoutTrust(id, text, 700, Authority.RELIABLE, false, 0.9, 0);
    }

    private void mockLlmResponse(String text) {
        var message = new AssistantMessage(text);
        when(generation.getOutput()).thenReturn(message);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }

    @Nested
    @DisplayName("detect")
    class Detect {

        @Test
        @DisplayName("parses REVISION conflict type and reasoning")
        void parsesRevisionConflictTypeAndReasoning() {
            mockLlmResponse("""
                    {"contradicts": true, "reasoning": "Explicit user correction", "conflictType": "REVISION", "explanation": "change request"}""");

            var conflicts = detector().detect("Actually make him a bard", List.of(unit("a1", "He is a wizard")));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().conflictType()).isEqualTo(ConflictType.REVISION);
            assertThat(conflicts.getFirst().reason()).isEqualTo("Explicit user correction");
            assertThat(conflicts.getFirst().detectionQuality()).isEqualTo(ConflictDetector.DetectionQuality.FULL);
        }

        @Test
        @DisplayName("parses explicit CONTRADICTION conflict type")
        void parsesContradictionConflictType() {
            mockLlmResponse("""
                    {"contradicts": true, "reasoning": "Directly inconsistent", "conflictType": "CONTRADICTION", "explanation": "incompatible"}""");

            var conflicts = detector().detect("The capital is Berlin", List.of(unit("a1", "The capital is Paris")));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().conflictType()).isEqualTo(ConflictType.CONTRADICTION);
        }

        @Test
        @DisplayName("parses explicit WORLD_PROGRESSION conflict type")
        void parsesWorldProgressionConflictType() {
            mockLlmResponse("""
                    {"contradicts": true, "reasoning": "Model classified this as progression", "conflictType": "WORLD_PROGRESSION", "explanation": "progression"}""");

            var conflicts = detector().detect("The king died in the siege", List.of(unit("a1", "The king is alive")));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().conflictType()).isEqualTo(ConflictType.WORLD_PROGRESSION);
        }

        @Test
        @DisplayName("defaults missing conflictType to CONTRADICTION")
        void defaultsMissingConflictTypeToContradiction() {
            mockLlmResponse("""
                    {"contradicts": true, "reasoning": "No type field provided", "explanation": "conflicts"}""");

            var conflicts = detector().detect("The shield is broken", List.of(unit("a1", "The shield is intact")));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().conflictType()).isEqualTo(ConflictType.CONTRADICTION);
        }

        @Test
        @DisplayName("returns empty when contradicts is false")
        void returnsEmptyWhenContradictsFalse() {
            mockLlmResponse("""
                    {"contradicts": false, "reasoning": "Progression", "conflictType": null, "explanation": "compatible"}""");

            var conflicts = detector().detect("The king is now crowned", List.of(unit("a1", "The prince was crowned")));

            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("falls back to keyword parse with CONTRADICTION type on malformed json")
        void fallsBackToKeywordParseOnMalformedJson() {
            mockLlmResponse("not-json true");

            var conflicts = detector().detect("The bridge collapsed", List.of(unit("a1", "The bridge is intact")));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().detectionQuality()).isEqualTo(ConflictDetector.DetectionQuality.FALLBACK);
            assertThat(conflicts.getFirst().conflictType()).isEqualTo(ConflictType.CONTRADICTION);
        }

        @Test
        @DisplayName("marks detection as DEGRADED when malformed json has no true keyword")
        void marksDegradedWhenMalformedJsonWithoutTrueKeyword() {
            mockLlmResponse("I cannot determine this relation.");

            var conflicts = detector().detect("The bridge collapsed", List.of(unit("a1", "The bridge is intact")));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().detectionQuality()).isEqualTo(ConflictDetector.DetectionQuality.DEGRADED);
            assertThat(conflicts.getFirst().conflictType()).isNull();
        }

        @Test
        @DisplayName("strips markdown code fences")
        void stripsMarkdownCodeFences() {
            mockLlmResponse("""
                    ```json
                    {"contradicts": true, "reasoning": "Direct conflict", "conflictType": "CONTRADICTION", "explanation": "direct contradiction"}
                    ```""");

            var conflicts = detector().detect("The door is unlocked", List.of(unit("a1", "The door is locked")));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().conflictType()).isEqualTo(ConflictType.CONTRADICTION);
        }
    }

    @Nested
    @DisplayName("batchDetect")
    class BatchDetect {

        @Test
        @DisplayName("parses mixed conflict types per memory unit match")
        void parsesMixedConflictTypesPerUnitMatch() {
            when(llmCallService.callBatched(anyString(), anyString())).thenReturn("""
                    {
                      "results": [
                        {
                          "candidate": "Actually make Anakin a bard",
                          "contradictingUnits": [
                            {"unitText": "Anakin is a wizard", "conflictType": "REVISION", "reasoning": "Explicit correction"},
                            {"unitText": "Anakin cannot cast bardic magic", "conflictType": "CONTRADICTION", "reasoning": "Incompatible class constraints"}
                          ]
                        },
                        {"candidate": "The tavern is open", "contradictingUnits": []}
                      ]
                    }""");
            var units = List.of(
                    unit("a1", "Anakin is a wizard"),
                    unit("a2", "Anakin cannot cast bardic magic"));

            var result = detector().batchDetect(List.of("Actually make Anakin a bard", "The tavern is open"), units);

            assertThat(result.get("Actually make Anakin a bard")).hasSize(2);
            assertThat(result.get("Actually make Anakin a bard").get(0).conflictType()).isEqualTo(ConflictType.REVISION);
            assertThat(result.get("Actually make Anakin a bard").get(1).conflictType()).isEqualTo(ConflictType.CONTRADICTION);
            assertThat(result.get("The tavern is open")).isEmpty();
        }

        @Test
        @DisplayName("defaults missing batch conflictType to CONTRADICTION")
        void defaultsMissingBatchConflictTypeToContradiction() {
            when(llmCallService.callBatched(anyString(), anyString())).thenReturn("""
                    {
                      "results": [
                        {
                          "candidate": "The gem is fake",
                          "contradictingUnits": [
                            {"unitText": "The gem is authentic", "reasoning": "Direct inconsistency"}
                          ]
                        }
                      ]
                    }""");
            var units = List.of(unit("a1", "The gem is authentic"));

            var result = detector().batchDetect(List.of("The gem is fake"), units);

            assertThat(result.get("The gem is fake")).hasSize(1);
            assertThat(result.get("The gem is fake").getFirst().conflictType()).isEqualTo(ConflictType.CONTRADICTION);
        }

        @Test
        @DisplayName("falls back to per-candidate detection when batch call fails")
        void fallsBackToPerCandidateDetectionWhenBatchCallFails() {
            when(llmCallService.callBatched(anyString(), anyString())).thenThrow(new RuntimeException("timeout"));
            mockLlmResponse("""
                    {"contradicts": true, "reasoning": "Explicit update", "conflictType": "REVISION", "explanation": "revision"}""");
            var units = List.of(unit("a1", "Anakin is a wizard"));

            var result = detector().batchDetect(List.of("Actually make Anakin a bard"), units);

            assertThat(result.get("Actually make Anakin a bard")).hasSize(1);
            assertThat(result.get("Actually make Anakin a bard").getFirst().conflictType()).isEqualTo(ConflictType.REVISION);
        }

        @Test
        @DisplayName("marks candidates as DEGRADED when batch response parsing fails")
        void marksCandidatesDegradedWhenBatchResponseParsingFails() {
            when(llmCallService.callBatched(anyString(), anyString())).thenReturn("not-json");
            var units = List.of(unit("a1", "The gate is closed"));

            var result = detector().batchDetect(List.of("The gate is open"), units);

            assertThat(result.get("The gate is open")).hasSize(1);
            assertThat(result.get("The gate is open").getFirst().detectionQuality())
                    .isEqualTo(ConflictDetector.DetectionQuality.DEGRADED);
        }
    }
}
