package dev.dunnam.diceanchors.anchor;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private dev.dunnam.diceanchors.sim.engine.LlmCallService llmCallService;

    private LlmConflictDetector detector() {
        return new LlmConflictDetector(chatModel, "test-model", llmCallService);
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
        @DisplayName("returns conflict for contradictory statements")
        void returnsConflictForContradictoryStatements() {
            mockLlmResponse("""
                    {"contradicts": true, "explanation": "One says alive, the other says dead"}""");
            var anchors = List.of(
                    Anchor.withoutTrust("1", "The king is alive", 700, Authority.RELIABLE, false, 0.9, 0)
            );
            var conflicts = detector().detect("The king is dead", anchors);
            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().existing().id()).isEqualTo("1");
            assertThat(conflicts.getFirst().confidence()).isEqualTo(0.9);
            assertThat(conflicts.getFirst().reason()).contains("alive");
        }

        @Test
        @DisplayName("returns empty for non-contradictory statements")
        void returnsEmptyForNonContradictoryStatements() {
            mockLlmResponse("""
                    {"contradicts": false, "explanation": "These statements are compatible"}""");
            var anchors = List.of(
                    Anchor.withoutTrust("1", "The king is wise", 600, Authority.RELIABLE, false, 0.9, 0)
            );
            var conflicts = detector().detect("The king is generous", anchors);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("handles JSON parse failure with fallback to keyword check")
        void handlesJsonParseFailureWithFallback() {
            mockLlmResponse("Yes, these statements are contradictory. The answer is true.");
            var anchors = List.of(
                    Anchor.withoutTrust("1", "The bridge is intact", 500, Authority.PROVISIONAL, false, 0.8, 0)
            );
            var conflicts = detector().detect("The bridge has collapsed", anchors);
            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().reason()).contains("fallback");
        }

        @Test
        @DisplayName("handles JSON parse failure returning empty when no true keyword")
        void handlesJsonParseFailureNoTrueKeyword() {
            mockLlmResponse("I cannot determine the relationship between these statements.");
            var anchors = List.of(
                    Anchor.withoutTrust("1", "The bridge is intact", 500, Authority.PROVISIONAL, false, 0.8, 0)
            );
            var conflicts = detector().detect("The bridge has collapsed", anchors);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("returns empty for null anchor list")
        void returnsEmptyForNullAnchorList() {
            var conflicts = detector().detect("Any statement", null);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("returns empty for empty anchor list")
        void returnsEmptyForEmptyAnchorList() {
            var conflicts = detector().detect("Any statement", List.of());
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("strips markdown code fences from LLM response")
        void stripsMarkdownCodeFences() {
            mockLlmResponse("```json\n{\"contradicts\": true, \"explanation\": \"Direct contradiction\"}\n```");
            var anchors = List.of(
                    Anchor.withoutTrust("1", "The door is locked", 500, Authority.PROVISIONAL, false, 0.8, 0)
            );
            var conflicts = detector().detect("The door is unlocked", anchors);
            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().reason()).isEqualTo("Direct contradiction");
        }
    }
}
