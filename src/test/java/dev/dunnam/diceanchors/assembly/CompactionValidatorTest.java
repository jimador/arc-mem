package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CompactionValidator")
class CompactionValidatorTest {

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("returns empty list when all facts found in summary")
        void allFactsFoundReturnsEmptyList() {
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "The dragon sleeps in the mountain cave", 700, Authority.RELIABLE, false, 0.9, 3)
            );
            var summary = "The dragon sleeps in a deep mountain cave near the village.";

            var losses = CompactionValidator.validate(summary, anchors, 0.5);

            assertThat(losses).isEmpty();
        }

        @Test
        @DisplayName("reports loss when fact is missing from summary")
        void factMissingFromSummaryReportsLoss() {
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "The cursed blade corrupts its wielder", 600, Authority.RELIABLE, true, 0.85, 2)
            );
            var summary = "The party traveled through the forest and found a tavern.";

            var losses = CompactionValidator.validate(summary, anchors, 0.5);

            assertThat(losses).hasSize(1);
            assertThat(losses.getFirst().anchorId()).isEqualTo("a-1");
            assertThat(losses.getFirst().anchorText()).isEqualTo("The cursed blade corrupts its wielder");
            assertThat(losses.getFirst().authority()).isEqualTo(Authority.RELIABLE);
            assertThat(losses.getFirst().rank()).isEqualTo(600);
        }

        @Test
        @DisplayName("partial keyword match sufficient when majority present")
        void partialKeywordMatchSufficientWhenMajorityPresent() {
            // Significant words: "dragon", "guards", "golden", "treasure"
            // Summary contains "dragon", "golden", "treasure" (3/4 = 75% > 50%)
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "The dragon guards the golden treasure", 500, Authority.PROVISIONAL, false, 0.7, 0)
            );
            var summary = "A golden treasure was found near the dragon lair.";

            var losses = CompactionValidator.validate(summary, anchors, 0.5);

            assertThat(losses).isEmpty();
        }

        @Test
        @DisplayName("multiple anchors with some found and some missing")
        void multipleAnchorsSomeFoundSomeMissing() {
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "The king rules from Waterdeep", 800, Authority.CANON, true, 1.0, 10),
                    Anchor.withoutTrust("a-2", "Elminster lives in Shadowdale tower", 600, Authority.RELIABLE, false, 0.8, 5),
                    Anchor.withoutTrust("a-3", "The goblin army marches south", 400, Authority.PROVISIONAL, false, 0.6, 1)
            );
            var summary = "The king continues to rule from Waterdeep. The goblin army marches towards the southern border.";

            var losses = CompactionValidator.validate(summary, anchors, 0.5);

            assertThat(losses).hasSize(1);
            assertThat(losses.getFirst().anchorId()).isEqualTo("a-2");
        }

        @Test
        @DisplayName("empty summary reports all anchors as lost")
        void emptySummaryReportsAllLost() {
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "The sword is enchanted with fire", 700, Authority.RELIABLE, false, 0.9, 3),
                    Anchor.withoutTrust("a-2", "Dark elves patrol the underdark", 500, Authority.UNRELIABLE, false, 0.7, 1)
            );

            var losses = CompactionValidator.validate("", anchors, 0.5);

            assertThat(losses).hasSize(2);
            assertThat(losses).extracting(CompactionLossEvent::anchorId)
                    .containsExactlyInAnyOrder("a-1", "a-2");
        }

        @Test
        @DisplayName("null summary reports all anchors as lost")
        void nullSummaryReportsAllLost() {
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "Ancient temple holds secrets", 500, Authority.PROVISIONAL, false, 0.6, 0)
            );

            var losses = CompactionValidator.validate(null, anchors, 0.5);

            assertThat(losses).hasSize(1);
        }

        @Test
        @DisplayName("empty anchor list returns empty losses")
        void emptyAnchorListReturnsEmpty() {
            var losses = CompactionValidator.validate("Some summary text here.", List.of(), 0.5);

            assertThat(losses).isEmpty();
        }

        @Test
        @DisplayName("anchor with only stop words is skipped")
        void anchorWithOnlyStopWordsIsSkipped() {
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "the is a an", 500, Authority.PROVISIONAL, false, 0.5, 0)
            );

            var losses = CompactionValidator.validate("Unrelated summary.", anchors, 0.5);

            assertThat(losses).isEmpty();
        }
    }
}
