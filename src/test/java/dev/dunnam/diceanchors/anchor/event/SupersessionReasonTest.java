package dev.dunnam.diceanchors.anchor.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SupersessionReason")
class SupersessionReasonTest {

    @Nested
    @DisplayName("Enum values")
    class EnumValues {

        @Test
        @DisplayName("has exactly 4 values")
        void hasExactlyFourValues() {
            assertThat(SupersessionReason.values()).hasSize(4);
        }

        @Test
        @DisplayName("all expected values exist")
        void allExpectedValuesExist() {
            assertThat(SupersessionReason.values()).containsExactlyInAnyOrder(
                    SupersessionReason.CONFLICT_REPLACEMENT,
                    SupersessionReason.BUDGET_EVICTION,
                    SupersessionReason.DECAY_DEMOTION,
                    SupersessionReason.MANUAL
            );
        }
    }

    @Nested
    @DisplayName("fromArchiveReason mapping")
    class FromArchiveReason {

        @Test
        @DisplayName("CONFLICT_REPLACEMENT maps to CONFLICT_REPLACEMENT")
        void conflictReplacementMapsCorrectly() {
            assertThat(SupersessionReason.fromArchiveReason(ArchiveReason.CONFLICT_REPLACEMENT))
                    .isEqualTo(SupersessionReason.CONFLICT_REPLACEMENT);
        }

        @Test
        @DisplayName("BUDGET_EVICTION maps to BUDGET_EVICTION")
        void budgetEvictionMapsCorrectly() {
            assertThat(SupersessionReason.fromArchiveReason(ArchiveReason.BUDGET_EVICTION))
                    .isEqualTo(SupersessionReason.BUDGET_EVICTION);
        }

        @Test
        @DisplayName("DORMANCY_DECAY maps to DECAY_DEMOTION")
        void dormancyDecayMapsToDecayDemotion() {
            assertThat(SupersessionReason.fromArchiveReason(ArchiveReason.DORMANCY_DECAY))
                    .isEqualTo(SupersessionReason.DECAY_DEMOTION);
        }

        @Test
        @DisplayName("MANUAL maps to MANUAL")
        void manualMapsCorrectly() {
            assertThat(SupersessionReason.fromArchiveReason(ArchiveReason.MANUAL))
                    .isEqualTo(SupersessionReason.MANUAL);
        }

        @ParameterizedTest
        @EnumSource(ArchiveReason.class)
        @DisplayName("every ArchiveReason maps to a non-null SupersessionReason")
        void everyArchiveReasonMapsToNonNull(ArchiveReason reason) {
            assertThat(SupersessionReason.fromArchiveReason(reason)).isNotNull();
        }
    }
}
