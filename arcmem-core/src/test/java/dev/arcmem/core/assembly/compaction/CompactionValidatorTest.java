package dev.arcmem.core.assembly.compaction;
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
            var units = List.of(
                    MemoryUnit.withoutTrust("a-1", "The dragon sleeps in the mountain cave", 700, Authority.RELIABLE, false, 0.9, 3)
            );
            var summary = "The dragon sleeps in a deep mountain cave near the village.";

            var losses = CompactionValidator.validate(summary, units, 0.5);

            assertThat(losses).isEmpty();
        }

        @Test
        @DisplayName("reports loss when fact is missing from summary")
        void factMissingFromSummaryReportsLoss() {
            var units = List.of(
                    MemoryUnit.withoutTrust("a-1", "The cursed blade corrupts its wielder", 600, Authority.RELIABLE, true, 0.85, 2)
            );
            var summary = "The party traveled through the forest and found a tavern.";

            var losses = CompactionValidator.validate(summary, units, 0.5);

            assertThat(losses).hasSize(1);
            assertThat(losses.getFirst().unitId()).isEqualTo("a-1");
            assertThat(losses.getFirst().unitText()).isEqualTo("The cursed blade corrupts its wielder");
            assertThat(losses.getFirst().authority()).isEqualTo(Authority.RELIABLE);
            assertThat(losses.getFirst().rank()).isEqualTo(600);
        }

        @Test
        @DisplayName("partial keyword match sufficient when majority present")
        void partialKeywordMatchSufficientWhenMajorityPresent() {
            // Significant words: "dragon", "guards", "golden", "treasure"
            // Summary contains "dragon", "golden", "treasure" (3/4 = 75% > 50%)
            var units = List.of(
                    MemoryUnit.withoutTrust("a-1", "The dragon guards the golden treasure", 500, Authority.PROVISIONAL, false, 0.7, 0)
            );
            var summary = "A golden treasure was found near the dragon lair.";

            var losses = CompactionValidator.validate(summary, units, 0.5);

            assertThat(losses).isEmpty();
        }

        @Test
        @DisplayName("multiple memory units with some found and some missing")
        void multipleUnitsSomeFoundSomeMissing() {
            var units = List.of(
                    MemoryUnit.withoutTrust("a-1", "The king rules from Waterdeep", 800, Authority.CANON, true, 1.0, 10),
                    MemoryUnit.withoutTrust("a-2", "Elminster lives in Shadowdale tower", 600, Authority.RELIABLE, false, 0.8, 5),
                    MemoryUnit.withoutTrust("a-3", "The goblin army marches south", 400, Authority.PROVISIONAL, false, 0.6, 1)
            );
            var summary = "The king continues to rule from Waterdeep. The goblin army marches towards the southern border.";

            var losses = CompactionValidator.validate(summary, units, 0.5);

            assertThat(losses).hasSize(1);
            assertThat(losses.getFirst().unitId()).isEqualTo("a-2");
        }

        @Test
        @DisplayName("empty summary reports all memory units as lost")
        void emptySummaryReportsAllLost() {
            var units = List.of(
                    MemoryUnit.withoutTrust("a-1", "The sword is enchanted with fire", 700, Authority.RELIABLE, false, 0.9, 3),
                    MemoryUnit.withoutTrust("a-2", "Dark elves patrol the underdark", 500, Authority.UNRELIABLE, false, 0.7, 1)
            );

            var losses = CompactionValidator.validate("", units, 0.5);

            assertThat(losses).hasSize(2);
            assertThat(losses).extracting(CompactionLossEvent::unitId)
                    .containsExactlyInAnyOrder("a-1", "a-2");
        }

        @Test
        @DisplayName("null summary reports all memory units as lost")
        void nullSummaryReportsAllLost() {
            var units = List.of(
                    MemoryUnit.withoutTrust("a-1", "Ancient temple holds secrets", 500, Authority.PROVISIONAL, false, 0.6, 0)
            );

            var losses = CompactionValidator.validate(null, units, 0.5);

            assertThat(losses).hasSize(1);
        }

        @Test
        @DisplayName("empty memory unit list returns empty losses")
        void emptyUnitListReturnsEmpty() {
            var losses = CompactionValidator.validate("Some summary text here.", List.of(), 0.5);

            assertThat(losses).isEmpty();
        }

        @Test
        @DisplayName("memory unit with only stop words is skipped")
        void unitWithOnlyStopWordsIsSkipped() {
            var units = List.of(
                    MemoryUnit.withoutTrust("a-1", "the is a an", 500, Authority.PROVISIONAL, false, 0.5, 0)
            );

            var losses = CompactionValidator.validate("Unrelated summary.", units, 0.5);

            assertThat(losses).isEmpty();
        }
    }
}
