package dev.arcmem.core.memory.maintenance;
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

import dev.arcmem.core.config.ArcMemProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExponentialDecayPolicy")
class ExponentialDecayPolicyTest {

    private final DecayPolicy policy = DecayPolicy.exponential(168.0); // 1 week half-life

    @Test
    @DisplayName("no decay at time zero")
    void noDecayAtZero() {
        var unit = MemoryUnit.withoutTrust("1", "test", 500, Authority.RELIABLE, false, 0.9, 0);
        assertThat(policy.applyDecay(unit, 0)).isEqualTo(500);
    }

    @Test
    @DisplayName("halves rank at half-life")
    void halvesAtHalfLife() {
        var unit = MemoryUnit.withoutTrust("1", "test", 500, Authority.RELIABLE, false, 0.9, 0);
        var decayed = policy.applyDecay(unit, 168);
        assertThat(decayed).isEqualTo(250); // 500 * 0.5 = 250
    }

    @Test
    @DisplayName("pinned memory units immune to decay")
    void pinnedImmuneToDecay() {
        var unit = MemoryUnit.withoutTrust("1", "test", 800, Authority.CANON, true, 0.99, 5);
        assertThat(policy.applyDecay(unit, 1000)).isEqualTo(800);
    }

    @Test
    @DisplayName("decayed rank clamped to minimum")
    void decayedRankClamped() {
        var unit = MemoryUnit.withoutTrust("1", "test", 200, Authority.PROVISIONAL, false, 0.5, 0);
        var decayed = policy.applyDecay(unit, 10000); // extreme decay
        assertThat(decayed).isGreaterThanOrEqualTo(MemoryUnit.MIN_RANK);
    }

    @Nested
    @DisplayName("DICE decay modulation (diceDecay field)")
    class DiceDecayModulation {

        @Test
        @DisplayName("diceDecay=0.0 (permanent) results in much less decay than diceDecay=1.0 standard at same elapsed time")
        void permanentDecayNegligible() {
            // diceDecay=0.0 treated as 0.01, so effectiveHalfLife = 24 / 0.01 = 2400h
            // diceDecay=1.0 standard: effectiveHalfLife = 24h
            // At 48h: standard loses 75%, permanent loses a tiny fraction
            var permanentUnit = new MemoryUnit("1", "test", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.0, 0.0, MemoryTier.WARM, null);
            var standardUnit = new MemoryUnit("2", "test", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.0, 1.0, MemoryTier.WARM, null);
            var standardPolicy = DecayPolicy.exponential(24.0);
            var permanentDecayed = standardPolicy.applyDecay(permanentUnit, 48);
            var standardDecayed = standardPolicy.applyDecay(standardUnit, 48);
            // Permanent unit decays much less than standard unit at same time
            assertThat(permanentDecayed).isGreaterThan(standardDecayed);
            // Standard unit at 48h = 500 * 0.5^2 = 125; permanent is significantly higher
            assertThat(permanentDecayed).isGreaterThan(490);
        }

        @Test
        @DisplayName("diceDecay=1.0 (standard) applies normal half-life at 24h")
        void standardDecayHalfLifeAt24h() {
            var standardUnit = new MemoryUnit("1", "test", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.0, 1.0, MemoryTier.WARM, null);
            var standardPolicy = DecayPolicy.exponential(24.0);
            var decayed = standardPolicy.applyDecay(standardUnit, 24);
            // 500 * 0.5^(24/24) = 500 * 0.5 = 250
            assertThat(decayed).isEqualTo(250);
        }

        @Test
        @DisplayName("diceDecay=2.0 (ephemeral) decays faster than diceDecay=1.0 in same elapsed time")
        void ephemeralDecayFasterThanStandard() {
            var standardUnit = new MemoryUnit("1", "test", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.0, 1.0, MemoryTier.WARM, null);
            var ephemeralUnit = new MemoryUnit("2", "test", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.0, 2.0, MemoryTier.WARM, null);
            var standardPolicy = DecayPolicy.exponential(24.0);

            var standardDecayed = standardPolicy.applyDecay(standardUnit, 12);
            var ephemeralDecayed = standardPolicy.applyDecay(ephemeralUnit, 12);

            assertThat(ephemeralDecayed).isLessThan(standardDecayed);
        }
    }

    @Nested
    @DisplayName("shouldDemoteAuthority")
    class ShouldDemoteAuthority {

        private final ArcMemProperties.UnitConfig unitConfig =
                new ArcMemProperties.UnitConfig(
                        20, 500, 100, 900, true, 0.65,
                        DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED,
                        true, true, true,
                        0.6, 400, 200, null, null, null, null, null);

        private final DecayPolicy policyWithThresholds = DecayPolicy.exponential(24.0, unitConfig);

        @Test
        @DisplayName("RELIABLE rank below 400 threshold triggers demotion")
        void reliableRankBelowThresholdTriggersDemotion() {
            var unit = MemoryUnit.withoutTrust("1", "test", 500, Authority.RELIABLE, false, 0.9, 0);
            assertThat(policyWithThresholds.shouldDemoteAuthority(unit, 380)).isTrue();
        }

        @Test
        @DisplayName("RELIABLE rank above 400 threshold does not trigger demotion")
        void reliableRankAboveThresholdNoDemotion() {
            var unit = MemoryUnit.withoutTrust("1", "test", 500, Authority.RELIABLE, false, 0.9, 0);
            assertThat(policyWithThresholds.shouldDemoteAuthority(unit, 450)).isFalse();
        }

        @Test
        @DisplayName("CANON is never demoted regardless of rank")
        void canonNeverDemoted() {
            var unit = MemoryUnit.withoutTrust("1", "test", 500, Authority.CANON, false, 0.9, 0);
            assertThat(policyWithThresholds.shouldDemoteAuthority(unit, 300)).isFalse();
        }

        @Test
        @DisplayName("PROVISIONAL is never demoted regardless of rank")
        void provisionalNeverDemoted() {
            var unit = MemoryUnit.withoutTrust("1", "test", 500, Authority.PROVISIONAL, false, 0.5, 0);
            assertThat(policyWithThresholds.shouldDemoteAuthority(unit, 150)).isFalse();
        }

        @Test
        @DisplayName("UNRELIABLE rank below 200 threshold triggers demotion")
        void unreliableRankBelowThresholdTriggersDemotion() {
            var unit = MemoryUnit.withoutTrust("1", "test", 300, Authority.UNRELIABLE, false, 0.5, 0);
            assertThat(policyWithThresholds.shouldDemoteAuthority(unit, 150)).isTrue();
        }
    }
}
