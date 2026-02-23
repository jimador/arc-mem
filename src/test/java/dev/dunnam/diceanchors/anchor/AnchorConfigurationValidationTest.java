package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnchorConfiguration validation")
class AnchorConfigurationValidationTest {

    private AnchorConfiguration configuration(DiceAnchorsProperties.AnchorConfig anchorConfig) {
        return configurationWithConflict(anchorConfig, null);
    }

    private AnchorConfiguration configurationWithConflict(DiceAnchorsProperties.AnchorConfig anchorConfig,
                                                          DiceAnchorsProperties.ConflictConfig conflictConfig) {
        var properties = new DiceAnchorsProperties(
                anchorConfig,
                new DiceAnchorsProperties.ChatConfig("dm", 200, null),
                new DiceAnchorsProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new DiceAnchorsProperties.PersistenceConfig(false),
                new DiceAnchorsProperties.SimConfig("gpt-4.1-mini", 30, 30, 10, true),
                new DiceAnchorsProperties.ConflictDetectionConfig("llm", "gpt-4o-nano"),
                new DiceAnchorsProperties.RunHistoryConfig("memory"),
                new DiceAnchorsProperties.AssemblyConfig(0),
                conflictConfig, null);
        return new AnchorConfiguration(properties);
    }

    private static DiceAnchorsProperties.AnchorConfig validConfig() {
        return new DiceAnchorsProperties.AnchorConfig(
                20, 500, 100, 900, true, 0.65,
                "FAST_THEN_LLM", "TIERED",
                true, true, true,
                0.6, 400, 200, null);
    }

    @Nested
    @DisplayName("budget validation")
    class BudgetValidation {

        @Test
        @DisplayName("budget=0 fails with IllegalStateException containing 'budget'")
        void budgetZeroFails() {
            var config = new DiceAnchorsProperties.AnchorConfig(
                    0, 500, 100, 900, true, 0.65,
                    "FAST_THEN_LLM", "TIERED",
                    true, true, true,
                    0.6, 400, 200, null);
            var anchorConfiguration = configuration(config);

            assertThatThrownBy(anchorConfiguration::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("budget");
        }
    }

    @Nested
    @DisplayName("rank range validation")
    class RankRangeValidation {

        @Test
        @DisplayName("minRank >= maxRank fails with IllegalStateException containing 'minRank'")
        void minRankGreaterThanMaxRankFails() {
            var config = new DiceAnchorsProperties.AnchorConfig(
                    20, 500, 900, 100, true, 0.65,
                    "FAST_THEN_LLM", "TIERED",
                    true, true, true,
                    0.6, 400, 200, null);
            var anchorConfiguration = configuration(config);

            assertThatThrownBy(anchorConfiguration::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("minRank");
        }
    }

    @Nested
    @DisplayName("threshold validation")
    class ThresholdValidation {

        @Test
        @DisplayName("autoActivateThreshold=1.5 fails with IllegalStateException containing 'autoActivateThreshold'")
        void thresholdOutOfRangeFails() {
            var config = new DiceAnchorsProperties.AnchorConfig(
                    20, 500, 100, 900, true, 1.5,
                    "FAST_THEN_LLM", "TIERED",
                    true, true, true,
                    0.6, 400, 200, null);
            var anchorConfiguration = configuration(config);

            assertThatThrownBy(anchorConfiguration::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("autoActivateThreshold");
        }
    }

    @Nested
    @DisplayName("valid configuration")
    class ValidConfiguration {

        @Test
        @DisplayName("valid configuration passes without exception")
        void validConfigPasses() {
            var anchorConfiguration = configuration(validConfig());

            assertThatNoException().isThrownBy(anchorConfiguration::validateConfiguration);
        }
    }

    @Nested
    @DisplayName("conflict config validation")
    class ConflictConfigValidation {

        @Test
        @DisplayName("negation overlap threshold of 0.0 rejects startup")
        void invalidNegationThresholdRejectsStartup() {
            var conflictConfig = new DiceAnchorsProperties.ConflictConfig(0.0, 0.9, 0.8, 0.6, null);
            var anchorConfiguration = configurationWithConflict(validConfig(), conflictConfig);

            assertThatThrownBy(anchorConfiguration::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("negation-overlap-threshold");
        }

        @Test
        @DisplayName("replace threshold <= demote threshold rejects startup")
        void invertedReplaceAndDemoteThresholdsRejected() {
            var conflictConfig = new DiceAnchorsProperties.ConflictConfig(0.5, 0.9, 0.5, 0.7, null);
            var anchorConfiguration = configurationWithConflict(validConfig(), conflictConfig);

            assertThatThrownBy(anchorConfiguration::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("replace-threshold");
        }

        @Test
        @DisplayName("tier modifier outside [-0.5, 0.5] rejects startup")
        void invalidTierModifierRejected() {
            var tierMod = new DiceAnchorsProperties.TierModifierConfig(0.6, 0.0, -0.1);
            var conflictConfig = new DiceAnchorsProperties.ConflictConfig(0.5, 0.9, 0.8, 0.6, tierMod);
            var anchorConfiguration = configurationWithConflict(validConfig(), conflictConfig);

            assertThatThrownBy(anchorConfiguration::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("hot-defense-modifier");
        }

        @Test
        @DisplayName("valid conflict config passes validation")
        void validConflictConfigPassesValidation() {
            var tierMod = new DiceAnchorsProperties.TierModifierConfig(0.1, 0.0, -0.1);
            var conflictConfig = new DiceAnchorsProperties.ConflictConfig(0.5, 0.9, 0.8, 0.6, tierMod);
            var anchorConfiguration = configurationWithConflict(validConfig(), conflictConfig);

            assertThatNoException().isThrownBy(anchorConfiguration::validateConfiguration);
        }
    }
}
