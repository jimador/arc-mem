package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CompactionValidator — Configurable Threshold (Section 8.5)")
class CompactionValidatorThresholdTest {

    @Nested
    @DisplayName("configurable minMatchRatio")
    class ConfigurableThreshold {

        @Test
        @DisplayName("default threshold (0.5) rejects summary with ratio 0.3")
        void defaultThresholdRejectsSummaryWithLowRatio() {
            // "dragon" (4 chars, not stop word) + "guards" + "golden" + "ancient" + "treasure" = 5 significant
            // Summary contains only "dragon" + "treasure" = 2/5 = 0.4 < 0.5
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "The dragon guards the golden ancient treasure",
                            500, Authority.RELIABLE, false, 0.8, 2)
            );
            var summary = "A dragon found the treasure.";

            var losses = CompactionValidator.validate(summary, anchors, 0.5);

            assertThat(losses).hasSize(1);
        }

        @Test
        @DisplayName("custom threshold (0.2) accepts summary with ratio 0.3")
        void customThresholdAcceptsSummaryWithModerateRatio() {
            // Significant words: "dragon", "guards", "golden", "ancient", "treasure" = 5
            // Summary has "dragon" + "treasure" = 2/5 = 0.4 >= 0.2
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "The dragon guards the golden ancient treasure",
                            500, Authority.RELIABLE, false, 0.8, 2)
            );
            var summary = "A dragon found the treasure.";

            var losses = CompactionValidator.validate(summary, anchors, 0.2);

            assertThat(losses).isEmpty();
        }

        @Test
        @DisplayName("zero threshold (0.0) accepts summary with NO matching words")
        void zeroThresholdAcceptsSummaryWithNoMatches() {
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "The dragon guards the golden treasure",
                            500, Authority.RELIABLE, false, 0.8, 2)
            );
            var summary = "Completely unrelated text about taverns and inns.";

            var losses = CompactionValidator.validate(summary, anchors, 0.0);

            assertThat(losses).isEmpty();
        }

        @Test
        @DisplayName("threshold 1.0 requires all significant words present")
        void fullThresholdRequiresAllWords() {
            // Significant words: "dragon", "guards", "golden", "treasure" = 4
            // Summary has "dragon", "golden", "treasure" = 3/4 = 0.75 < 1.0
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "The dragon guards the golden treasure",
                            500, Authority.RELIABLE, false, 0.8, 2)
            );
            var summary = "The dragon found the golden treasure.";

            var losses = CompactionValidator.validate(summary, anchors, 1.0);

            assertThat(losses).hasSize(1);
        }

        @Test
        @DisplayName("threshold comparison is strict less-than: ratio == threshold passes")
        void exactRatioMatchPasses() {
            // Significant words: "dragon", "sleeps" = 2
            // Summary contains "dragon" = 1/2 = 0.5, threshold is 0.5
            // 0.5 < 0.5 is false, so it should pass (no loss)
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "The dragon sleeps",
                            500, Authority.RELIABLE, false, 0.8, 2)
            );
            var summary = "The dragon was here.";

            var losses = CompactionValidator.validate(summary, anchors, 0.5);

            assertThat(losses).isEmpty();
        }
    }
}
