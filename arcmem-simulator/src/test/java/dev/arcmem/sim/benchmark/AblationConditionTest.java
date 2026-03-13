package dev.arcmem.simulator.benchmark;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import dev.arcmem.simulator.scenario.SimulationScenario.SeedUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AblationCondition")
class AblationConditionTest {

    @Nested
    @DisplayName("built-in conditions")
    class BuiltInConditions {

        @Test
        @DisplayName("FULL_UNITS has all subsystems enabled with no overrides")
        void fullUnitsAllSubsystemsEnabled() {
            var c = AblationCondition.FULL_UNITS;
            assertThat(c.name()).isEqualTo("FULL_UNITS");
            assertThat(c.injectionEnabled()).isTrue();
            assertThat(c.authorityOverride()).isNull();
            assertThat(c.rankOverride()).isNull();
            assertThat(c.rankMutationEnabled()).isTrue();
            assertThat(c.authorityPromotionEnabled()).isTrue();
        }

        @Test
        @DisplayName("NO_UNITS has injection disabled")
        void noUnitsInjectionDisabled() {
            var c = AblationCondition.NO_UNITS;
            assertThat(c.name()).isEqualTo("NO_UNITS");
            assertThat(c.injectionEnabled()).isFalse();
        }

        @Test
        @DisplayName("FLAT_AUTHORITY overrides to RELIABLE with promotion disabled")
        void flatAuthorityReliableOverride() {
            var c = AblationCondition.FLAT_AUTHORITY;
            assertThat(c.name()).isEqualTo("FLAT_AUTHORITY");
            assertThat(c.injectionEnabled()).isTrue();
            assertThat(c.authorityOverride()).isEqualTo(Authority.RELIABLE);
            assertThat(c.rankOverride()).isNull();
            assertThat(c.rankMutationEnabled()).isTrue();
            assertThat(c.authorityPromotionEnabled()).isFalse();
        }

        @Test
        @DisplayName("NO_RANK_DIFFERENTIATION sets rank 500 with mutation disabled")
        void noRankDifferentiationRank500() {
            var c = AblationCondition.NO_RANK_DIFFERENTIATION;
            assertThat(c.name()).isEqualTo("NO_RANK_DIFFERENTIATION");
            assertThat(c.injectionEnabled()).isTrue();
            assertThat(c.authorityOverride()).isNull();
            assertThat(c.rankOverride()).isEqualTo(500);
            assertThat(c.rankMutationEnabled()).isFalse();
            assertThat(c.authorityPromotionEnabled()).isTrue();
        }

        @Test
        @DisplayName("each built-in condition has a unique name")
        void builtInConditionsHaveUniqueNames() {
            var names = Set.of(
                    AblationCondition.FULL_UNITS.name(),
                    AblationCondition.NO_UNITS.name(),
                    AblationCondition.FLAT_AUTHORITY.name(),
                    AblationCondition.NO_RANK_DIFFERENTIATION.name()
            );
            assertThat(names).hasSize(4);
        }
    }

    @Nested
    @DisplayName("applySeedMemoryUnits")
    class ApplySeedUnits {

        @Test
        @DisplayName("FLAT_AUTHORITY overrides authority to RELIABLE but preserves rank")
        void flatAuthorityOverridesAuthorityPreservesRank() {
            var seeds = List.of(
                    new SeedUnit("fact-one", "PROVISIONAL", 700),
                    new SeedUnit("fact-two", "CANON", 300)
            );

            var result = AblationCondition.FLAT_AUTHORITY.applySeedUnits(seeds);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).authority()).isEqualTo("RELIABLE");
            assertThat(result.get(0).rank()).isEqualTo(700);
            assertThat(result.get(1).authority()).isEqualTo("RELIABLE");
            assertThat(result.get(1).rank()).isEqualTo(300);
        }

        @Test
        @DisplayName("NO_RANK_DIFFERENTIATION sets rank to 500 but preserves authority")
        void noRankDifferentiationSetsRankPreservesAuthority() {
            var seeds = List.of(
                    new SeedUnit("fact-one", "CANON", 800),
                    new SeedUnit("fact-two", "PROVISIONAL", 200)
            );

            var result = AblationCondition.NO_RANK_DIFFERENTIATION.applySeedUnits(seeds);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).authority()).isEqualTo("CANON");
            assertThat(result.get(0).rank()).isEqualTo(500);
            assertThat(result.get(1).authority()).isEqualTo("PROVISIONAL");
            assertThat(result.get(1).rank()).isEqualTo(500);
        }

        @Test
        @DisplayName("NO_UNITS still transforms seed memory units without error")
        void noUnitsTransformsSeedUnitsWithoutError() {
            var seeds = List.of(new SeedUnit("fact-one", "RELIABLE", 500));

            var result = AblationCondition.NO_UNITS.applySeedUnits(seeds);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).text()).isEqualTo("fact-one");
        }

        @Test
        @DisplayName("empty list returns empty list")
        void emptyListReturnsEmptyList() {
            var result = AblationCondition.FULL_UNITS.applySeedUnits(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null list returns empty list")
        void nullListReturnsEmptyList() {
            var result = AblationCondition.FULL_UNITS.applySeedUnits(null);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("rank clamping")
    class RankClamping {

        @Test
        @DisplayName("rankOverride below 100 is clamped to 100")
        void rankOverrideBelowMinClampedTo100() {
            var condition = new AblationCondition("LOW_RANK", true, null, 50, true, true);

            assertThat(condition.rankOverride()).isEqualTo(MemoryUnit.MIN_RANK);
        }

        @Test
        @DisplayName("rankOverride above 900 is clamped to 900")
        void rankOverrideAboveMaxClampedTo900() {
            var condition = new AblationCondition("HIGH_RANK", true, null, 1000, true, true);

            assertThat(condition.rankOverride()).isEqualTo(MemoryUnit.MAX_RANK);
        }
    }

    @Nested
    @DisplayName("authority override direction")
    class AuthorityOverrideDirection {

        @Test
        @DisplayName("authorityOverride applies even when downgrading")
        void authorityOverrideAppliesWhenDowngrading() {
            var condition = new AblationCondition(
                    "DOWNGRADE", true, Authority.PROVISIONAL, null, true, true);
            var seeds = List.of(new SeedUnit("high-trust", "RELIABLE", 600));

            var result = condition.applySeedUnits(seeds);

            assertThat(result.get(0).authority()).isEqualTo("PROVISIONAL");
            assertThat(result.get(0).rank()).isEqualTo(600);
        }
    }

    @Nested
    @DisplayName("idempotency (AC2)")
    class Idempotency {

        @Test
        @DisplayName("applying same condition twice produces identical result")
        void applyTwiceProducesSameResult() {
            var seeds = List.of(
                    new SeedUnit("fact-one", "PROVISIONAL", 700),
                    new SeedUnit("fact-two", "CANON", 300)
            );

            var firstApplication = AblationCondition.FLAT_AUTHORITY.applySeedUnits(seeds);
            var secondApplication = AblationCondition.FLAT_AUTHORITY.applySeedUnits(firstApplication);

            assertThat(secondApplication).isEqualTo(firstApplication);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("null name throws NullPointerException")
        void nullNameThrowsNpe() {
            assertThatThrownBy(() -> new AblationCondition(null, true, null, null, true, true))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("blank name throws IllegalArgumentException")
        void blankNameThrowsIae() {
            assertThatThrownBy(() -> new AblationCondition("   ", true, null, null, true, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
