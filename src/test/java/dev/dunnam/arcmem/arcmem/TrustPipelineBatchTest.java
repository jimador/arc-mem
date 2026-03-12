package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrustPipeline batch operations")
class TrustPipelineBatchTest {

    private static final String CONTEXT_ID = "test-ctx";

    private TrustSignal fixedSignal(String name, double value) {
        return new TrustSignal() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
                return OptionalDouble.of(value);
            }
        };
    }

    private TrustSignal absentSignal(String name) {
        return new TrustSignal() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
                return OptionalDouble.empty();
            }
        };
    }

    private PropositionNode propositionNode(String text, double confidence) {
        return new PropositionNode(text, confidence);
    }

    @Nested
    @DisplayName("batchEvaluate")
    class BatchEvaluate {

        @Test
        @DisplayName("each TrustContext gets a TrustScore keyed by proposition text")
        void batchEvaluateEachContextGetsTrustScore() {
            var signals = List.<TrustSignal>of(fixedSignal("sourceAuthority", 0.9));
            var pipeline = new TrustPipeline(signals);

            var prop1 = propositionNode("The dragon is red", 0.9);
            var prop2 = propositionNode("The castle stands tall", 0.8);

            var contexts = List.of(
                    new TrustContext(prop1, CONTEXT_ID),
                    new TrustContext(prop2, CONTEXT_ID)
            );

            var result = pipeline.batchEvaluate(contexts);

            assertThat(result).hasSize(2);
            assertThat(result).containsKey("The dragon is red");
            assertThat(result).containsKey("The castle stands tall");
            assertThat(result.get("The dragon is red").score()).isGreaterThan(0.0);
            assertThat(result.get("The castle stands tall").score()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("empty list returns empty map")
        void batchEvaluateEmptyListReturnsEmptyMap() {
            var pipeline = new TrustPipeline(List.of());

            var result = pipeline.batchEvaluate(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("each proposition uses the same active profile")
        void batchEvaluateUsesActiveProfile() {
            // Quality signals absent — redistribution restores NARRATIVE proportions; all at 0.65 -> score = 0.65
            // NARRATIVE autoPromoteThreshold = 0.60, so 0.65 -> AUTO_PROMOTE
            var signals = List.<TrustSignal>of(
                    fixedSignal("sourceAuthority", 0.65),
                    fixedSignal("extractionConfidence", 0.65),
                    fixedSignal("graphConsistency", 0.65),
                    fixedSignal("corroboration", 0.65),
                    absentSignal("novelty"),
                    absentSignal("importance")
            );
            var pipeline = new TrustPipeline(signals);

            var contexts = List.of(
                    new TrustContext(propositionNode("Fact A", 0.65), CONTEXT_ID),
                    new TrustContext(propositionNode("Fact B", 0.65), CONTEXT_ID)
            );

            var result = pipeline.batchEvaluate(contexts);

            assertThat(result.get("Fact A").promotionZone()).isEqualTo(PromotionZone.AUTO_PROMOTE);
            assertThat(result.get("Fact B").promotionZone()).isEqualTo(PromotionZone.AUTO_PROMOTE);
        }

        @Test
        @DisplayName("single context returns map with one entry")
        void batchEvaluateSingleContextReturnsSingleEntry() {
            var signals = List.<TrustSignal>of(fixedSignal("sourceAuthority", 0.8));
            var pipeline = new TrustPipeline(signals);
            var prop = propositionNode("Single proposition", 0.8);

            var result = pipeline.batchEvaluate(List.of(new TrustContext(prop, CONTEXT_ID)));

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("Single proposition");
        }
    }
}
