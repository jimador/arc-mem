package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.DiceAnchorsProperties.RetrievalConfig;
import dev.dunnam.diceanchors.DiceAnchorsProperties.ScoringConfig;
import dev.dunnam.diceanchors.assembly.RetrievalMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Retrieval Config Validation")
class RetrievalConfigValidationTest {

    private static DiceAnchorsProperties.AnchorConfig validAnchorConfig() {
        return new DiceAnchorsProperties.AnchorConfig(
                20, 500, 100, 900, true, 0.65,
                "FAST_THEN_LLM", "TIERED",
                true, true, true,
                0.6, 400, 200, null);
    }

    private AnchorConfiguration configurationWithRetrieval(RetrievalConfig retrievalConfig) {
        var properties = new DiceAnchorsProperties(
                validAnchorConfig(),
                new DiceAnchorsProperties.ChatConfig("dm", 200, null),
                new DiceAnchorsProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new DiceAnchorsProperties.PersistenceConfig(false),
                new DiceAnchorsProperties.SimConfig("gpt-4.1-mini", 30, 30, 10, true),
                new DiceAnchorsProperties.ConflictDetectionConfig("llm", "gpt-4o-nano"),
                new DiceAnchorsProperties.RunHistoryConfig("memory"),
                new DiceAnchorsProperties.AssemblyConfig(0),
                null,
                retrievalConfig);
        return new AnchorConfiguration(properties);
    }

    @Nested
    @DisplayName("Retrieval Config Validation")
    class RetrievalConfigValidation {

        @Test
        @DisplayName("valid retrieval config passes validation")
        void validRetrievalConfigPasses() {
            var config = new RetrievalConfig(RetrievalMode.HYBRID, 0.3, 5, 5,
                    new ScoringConfig(0.4, 0.3, 0.3));
            var anchorConfiguration = configurationWithRetrieval(config);

            assertThatNoException().isThrownBy(anchorConfiguration::validateConfiguration);
        }

        @Test
        @DisplayName("invalid minRelevance rejected")
        void invalidMinRelevanceRejected() {
            var configNegative = new RetrievalConfig(RetrievalMode.HYBRID, -0.1, 5, 5,
                    new ScoringConfig(0.4, 0.3, 0.3));
            var anchorConfigNeg = configurationWithRetrieval(configNegative);

            assertThatThrownBy(anchorConfigNeg::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("min-relevance");

            var configOver = new RetrievalConfig(RetrievalMode.HYBRID, 1.5, 5, 5,
                    new ScoringConfig(0.4, 0.3, 0.3));
            var anchorConfigOver = configurationWithRetrieval(configOver);

            assertThatThrownBy(anchorConfigOver::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("min-relevance");
        }

        @Test
        @DisplayName("invalid baselineTopK rejected")
        void invalidBaselineTopKRejected() {
            var config = new RetrievalConfig(RetrievalMode.HYBRID, 0.3, 0, 5,
                    new ScoringConfig(0.4, 0.3, 0.3));
            var anchorConfiguration = configurationWithRetrieval(config);

            assertThatThrownBy(anchorConfiguration::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("baseline-top-k");
        }

        @Test
        @DisplayName("invalid scoring weight sum rejected")
        void invalidScoringWeightSumRejected() {
            var config = new RetrievalConfig(RetrievalMode.HYBRID, 0.3, 5, 5,
                    new ScoringConfig(0.5, 0.5, 0.5));
            var anchorConfiguration = configurationWithRetrieval(config);

            assertThatThrownBy(anchorConfiguration::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("weights must sum to 1.0");
        }

        @Test
        @DisplayName("null retrieval config preserves backward compatibility")
        void defaultsAreBackwardCompatible() {
            var anchorConfiguration = configurationWithRetrieval(null);

            assertThatNoException().isThrownBy(anchorConfiguration::validateConfiguration);
        }
    }
}
