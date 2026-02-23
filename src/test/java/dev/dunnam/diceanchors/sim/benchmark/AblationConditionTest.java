package dev.dunnam.diceanchors.sim.benchmark;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario.SeedAnchor;
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
        @DisplayName("FULL_ANCHORS has all subsystems enabled with no overrides")
        void fullAnchorsAllSubsystemsEnabled() {
            var c = AblationCondition.FULL_ANCHORS;
            assertThat(c.name()).isEqualTo("FULL_ANCHORS");
            assertThat(c.injectionEnabled()).isTrue();
            assertThat(c.authorityOverride()).isNull();
            assertThat(c.rankOverride()).isNull();
            assertThat(c.rankMutationEnabled()).isTrue();
            assertThat(c.authorityPromotionEnabled()).isTrue();
        }

        @Test
        @DisplayName("NO_ANCHORS has injection disabled")
        void noAnchorsInjectionDisabled() {
            var c = AblationCondition.NO_ANCHORS;
            assertThat(c.name()).isEqualTo("NO_ANCHORS");
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
                    AblationCondition.FULL_ANCHORS.name(),
                    AblationCondition.NO_ANCHORS.name(),
                    AblationCondition.FLAT_AUTHORITY.name(),
                    AblationCondition.NO_RANK_DIFFERENTIATION.name()
            );
            assertThat(names).hasSize(4);
        }
    }

    @Nested
    @DisplayName("applySeedAnchors")
    class ApplySeedAnchors {

        @Test
        @DisplayName("FLAT_AUTHORITY overrides authority to RELIABLE but preserves rank")
        void flatAuthorityOverridesAuthorityPreservesRank() {
            var seeds = List.of(
                    new SeedAnchor("fact-one", "PROVISIONAL", 700),
                    new SeedAnchor("fact-two", "CANON", 300)
            );

            var result = AblationCondition.FLAT_AUTHORITY.applySeedAnchors(seeds);

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
                    new SeedAnchor("fact-one", "CANON", 800),
                    new SeedAnchor("fact-two", "PROVISIONAL", 200)
            );

            var result = AblationCondition.NO_RANK_DIFFERENTIATION.applySeedAnchors(seeds);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).authority()).isEqualTo("CANON");
            assertThat(result.get(0).rank()).isEqualTo(500);
            assertThat(result.get(1).authority()).isEqualTo("PROVISIONAL");
            assertThat(result.get(1).rank()).isEqualTo(500);
        }

        @Test
        @DisplayName("NO_ANCHORS still transforms seed anchors without error")
        void noAnchorsTransformsSeedAnchorsWithoutError() {
            var seeds = List.of(new SeedAnchor("fact-one", "RELIABLE", 500));

            var result = AblationCondition.NO_ANCHORS.applySeedAnchors(seeds);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).text()).isEqualTo("fact-one");
        }

        @Test
        @DisplayName("empty list returns empty list")
        void emptyListReturnsEmptyList() {
            var result = AblationCondition.FULL_ANCHORS.applySeedAnchors(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null list returns empty list")
        void nullListReturnsEmptyList() {
            var result = AblationCondition.FULL_ANCHORS.applySeedAnchors(null);

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

            assertThat(condition.rankOverride()).isEqualTo(Anchor.MIN_RANK);
        }

        @Test
        @DisplayName("rankOverride above 900 is clamped to 900")
        void rankOverrideAboveMaxClampedTo900() {
            var condition = new AblationCondition("HIGH_RANK", true, null, 1000, true, true);

            assertThat(condition.rankOverride()).isEqualTo(Anchor.MAX_RANK);
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
            var seeds = List.of(new SeedAnchor("high-trust", "RELIABLE", 600));

            var result = condition.applySeedAnchors(seeds);

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
                    new SeedAnchor("fact-one", "PROVISIONAL", 700),
                    new SeedAnchor("fact-two", "CANON", 300)
            );

            var firstApplication = AblationCondition.FLAT_AUTHORITY.applySeedAnchors(seeds);
            var secondApplication = AblationCondition.FLAT_AUTHORITY.applySeedAnchors(firstApplication);

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
