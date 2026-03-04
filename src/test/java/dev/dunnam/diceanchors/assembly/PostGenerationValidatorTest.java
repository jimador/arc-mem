package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PostGenerationValidator")
class PostGenerationValidatorTest {

    private static Anchor anchor(String id, String text, Authority authority) {
        return new Anchor(id, text, 500, authority, false, 0.9, 0, null, 0.0, 0.0, MemoryTier.WARM);
    }

    private static ChatModel mockModel(String response) {
        var model = mock(ChatModel.class);
        var generation = new Generation(new org.springframework.ai.chat.messages.AssistantMessage(response));
        var chatResponse = new ChatResponse(List.of(generation));
        when(model.call(any(Prompt.class))).thenReturn(chatResponse);
        return model;
    }

    @Nested
    @DisplayName("enforce")
    class Enforce {

        @Test
        @DisplayName("returns compliant when no anchors match policy")
        void compliantWhenNoAnchorsMatchPolicy() {
            var model = mockModel("{\"violations\": []}");
            var validator = new PostGenerationValidator(model);

            var anchor = anchor("a1", "The sky is blue", Authority.PROVISIONAL);
            var policy = ComplianceContext.CompliancePolicy.canonOnly();
            var context = new ComplianceContext("The sky is blue.", List.of(anchor), policy);

            var result = validator.enforce(context);
            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
        }

        @Test
        @DisplayName("returns compliant when LLM finds no violations")
        void compliantWhenNoViolations() {
            var model = mockModel("{\"violations\": []}");
            var validator = new PostGenerationValidator(model);

            var anchor = anchor("a1", "The dragon is red", Authority.CANON);
            var policy = ComplianceContext.CompliancePolicy.canonOnly();
            var context = new ComplianceContext("The red dragon breathes fire.", List.of(anchor), policy);

            var result = validator.enforce(context);
            assertThat(result.compliant()).isTrue();
            assertThat(result.violations()).isEmpty();
        }

        @Test
        @DisplayName("detects violation and suggests REJECT for CANON anchor")
        void rejectsCanonViolation() {
            var response = """
                    {"violations": [{"anchorId": "a1", "description": "Response says dragon is blue", "confidence": 0.95}]}
                    """;
            var model = mockModel(response);
            var validator = new PostGenerationValidator(model);

            var anchor = anchor("a1", "The dragon is red", Authority.CANON);
            var policy = ComplianceContext.CompliancePolicy.canonOnly();
            var context = new ComplianceContext("The blue dragon...", List.of(anchor), policy);

            var result = validator.enforce(context);
            assertThat(result.compliant()).isFalse();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.REJECT);
            assertThat(result.violations()).hasSize(1);
            assertThat(result.violations().getFirst().anchorId()).isEqualTo("a1");
        }

        @Test
        @DisplayName("suggests RETRY for non-CANON violation")
        void retriesReliableViolation() {
            var response = """
                    {"violations": [{"anchorId": "a1", "description": "Contradicts anchor", "confidence": 0.8}]}
                    """;
            var model = mockModel(response);
            var validator = new PostGenerationValidator(model);

            var anchor = anchor("a1", "The tavern is crowded", Authority.RELIABLE);
            var policy = ComplianceContext.CompliancePolicy.tiered();
            var context = new ComplianceContext("The empty tavern...", List.of(anchor), policy);

            var result = validator.enforce(context);
            assertThat(result.compliant()).isFalse();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.RETRY);
        }

        @Test
        @DisplayName("handles malformed LLM response gracefully")
        void handlesMalformedResponse() {
            var model = mockModel("I cannot produce JSON right now.");
            var validator = new PostGenerationValidator(model);

            var anchor = anchor("a1", "Fact", Authority.CANON);
            var policy = ComplianceContext.CompliancePolicy.canonOnly();
            var context = new ComplianceContext("Response text", List.of(anchor), policy);

            var result = validator.enforce(context);
            assertThat(result.compliant()).isTrue();
            assertThat(result.violations()).isEmpty();
        }
    }
}
