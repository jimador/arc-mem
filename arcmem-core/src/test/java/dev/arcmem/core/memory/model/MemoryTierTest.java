package dev.arcmem.core.memory.model;
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

@DisplayName("Working Memory Tiering")
class MemoryTierTest {

    @Nested
    @DisplayName("MemoryTier enum")
    class MemoryTierEnum {

        @Test
        @DisplayName("ordinal ordering: COLD < WARM < HOT")
        void ordinalOrderingColdLessThanWarmLessThanHot() {
            assertThat(MemoryTier.COLD.ordinal()).isLessThan(MemoryTier.WARM.ordinal());
            assertThat(MemoryTier.WARM.ordinal()).isLessThan(MemoryTier.HOT.ordinal());
        }

        @Nested
        @DisplayName("fromRank with hotThreshold=600, warmThreshold=350")
        class FromRank {

            private static final int HOT_THRESHOLD = 600;
            private static final int WARM_THRESHOLD = 350;

            @Test
            @DisplayName("rank 100 returns COLD")
            void rank100ReturnsCold() {
                assertThat(MemoryTier.fromRank(100, HOT_THRESHOLD, WARM_THRESHOLD))
                        .isEqualTo(MemoryTier.COLD);
            }

            @Test
            @DisplayName("rank 349 returns COLD")
            void rank349ReturnsCold() {
                assertThat(MemoryTier.fromRank(349, HOT_THRESHOLD, WARM_THRESHOLD))
                        .isEqualTo(MemoryTier.COLD);
            }

            @Test
            @DisplayName("rank 350 (exact warm boundary) returns WARM")
            void rank350ReturnsWarm() {
                assertThat(MemoryTier.fromRank(350, HOT_THRESHOLD, WARM_THRESHOLD))
                        .isEqualTo(MemoryTier.WARM);
            }

            @Test
            @DisplayName("rank 599 returns WARM")
            void rank599ReturnsWarm() {
                assertThat(MemoryTier.fromRank(599, HOT_THRESHOLD, WARM_THRESHOLD))
                        .isEqualTo(MemoryTier.WARM);
            }

            @Test
            @DisplayName("rank 600 (exact hot boundary) returns HOT")
            void rank600ReturnsHot() {
                assertThat(MemoryTier.fromRank(600, HOT_THRESHOLD, WARM_THRESHOLD))
                        .isEqualTo(MemoryTier.HOT);
            }

            @Test
            @DisplayName("rank 900 returns HOT")
            void rank900ReturnsHot() {
                assertThat(MemoryTier.fromRank(900, HOT_THRESHOLD, WARM_THRESHOLD))
                        .isEqualTo(MemoryTier.HOT);
            }
        }
    }

    @Nested
    @DisplayName("MemoryUnit record with tier")
    class UnitWithTier {

        @Test
        @DisplayName("memory unit constructed with HOT tier has correct memoryTier")
        void unitConstructedWithHotTierHasCorrectMemoryTier() {
            var unit = new MemoryUnit("1", "test", 700, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.5, 1.0, MemoryTier.HOT, null);
            assertThat(unit.memoryTier()).isEqualTo(MemoryTier.HOT);
        }

        @Test
        @DisplayName("withoutTrust defaults to WARM tier")
        void withoutTrustDefaultsToWarmTier() {
            var unit = MemoryUnit.withoutTrust("1", "test", 500, Authority.RELIABLE, false, 0.9, 0);
            assertThat(unit.memoryTier()).isEqualTo(MemoryTier.WARM);
        }
    }

    @Nested
    @DisplayName("Tier-aware decay")
    class TierAwareDecay {

        private final DecayPolicy policy = DecayPolicy.exponential(24.0);

        @Test
        @DisplayName("HOT tier (multiplier 1.5) decays slower than WARM (1.0)")
        void hotTierDecaysSlowerThanWarm() {
            var unit = new MemoryUnit("1", "test", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.0, 1.0, MemoryTier.HOT, null);

            var hotDecayed = policy.applyDecay(unit, 24, 1.5);
            var warmDecayed = policy.applyDecay(unit, 24, 1.0);

            assertThat(hotDecayed).isGreaterThan(warmDecayed);
        }

        @Test
        @DisplayName("COLD tier (multiplier 0.6) decays faster than WARM (1.0)")
        void coldTierDecaysFasterThanWarm() {
            var unit = new MemoryUnit("1", "test", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.0, 1.0, MemoryTier.COLD, null);

            var coldDecayed = policy.applyDecay(unit, 24, 0.6);
            var warmDecayed = policy.applyDecay(unit, 24, 1.0);

            assertThat(coldDecayed).isLessThan(warmDecayed);
        }

        @Test
        @DisplayName("tier multiplier composes with diceDecay: diceDecay=2.0 + HOT still decays slower than COLD + diceDecay=2.0")
        void tierMultiplierComposesWithDiceDecay() {
            var hotUnit = new MemoryUnit("1", "test", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.0, 2.0, MemoryTier.HOT, null);
            var coldUnit = new MemoryUnit("2", "test", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.0, 2.0, MemoryTier.COLD, null);

            var hotDecayed = policy.applyDecay(hotUnit, 24, 1.5);
            var coldDecayed = policy.applyDecay(coldUnit, 24, 0.6);

            // Both decay (rank < 500), but HOT retains more rank
            assertThat(hotDecayed).isLessThan(500);
            assertThat(coldDecayed).isLessThan(500);
            assertThat(hotDecayed).isGreaterThan(coldDecayed);
        }

        @Test
        @DisplayName("tierMultiplier=1.0 produces same result as 2-arg applyDecay (backward compatibility)")
        void tierMultiplierOneMatchesTwoArgDecay() {
            var unit = new MemoryUnit("1", "test", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.0, 1.0, MemoryTier.WARM, null);

            var twoArgResult = policy.applyDecay(unit, 48);
            var threeArgResult = policy.applyDecay(unit, 48, 1.0);

            assertThat(threeArgResult).isEqualTo(twoArgResult);
        }
    }

    @Nested
    @DisplayName("Tier-aware assembly (PromptBudgetEnforcer)")
    class TierAwareAssembly {

        private final PromptBudgetEnforcer enforcer = new PromptBudgetEnforcer();
        private final TokenCounter counter = text -> {
            if (text == null || text.isBlank()) return 0;
            if (text.startsWith("[Established Facts")) return 10;
            if (text.startsWith(" (rank:")) return 1;
            return 10;
        };

        @Test
        @DisplayName("within same authority band, COLD is dropped before WARM before HOT")
        void coldDroppedBeforeWarmBeforeHotInSameAuthority() {
            var cold = new MemoryUnit("cold", "cold text", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.5, 1.0, MemoryTier.COLD, null);
            var warm = new MemoryUnit("warm", "warm text", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.5, 1.0, MemoryTier.WARM, null);
            var hot = new MemoryUnit("hot", "hot text", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.5, 1.0, MemoryTier.HOT, null);

            // Budget tight enough to force dropping one unit:
            // overhead=10, each unit=10+1=11, total=10+33=43
            // budget=32 forces dropping at least one
            var result = enforcer.enforce(
                    List.of(hot, warm, cold),
                    32,
                    counter,
                    CompliancePolicy.flat());

            // COLD should be dropped first
            assertThat(result.excluded()).isNotEmpty();
            assertThat(result.excluded().get(0).id()).isEqualTo("cold");

            // If more are dropped, WARM should come before HOT
            if (result.excluded().size() > 1) {
                assertThat(result.excluded().get(1).id()).isEqualTo("warm");
            }
        }

        @Test
        @DisplayName("tier takes precedence over diceImportance in drop order")
        void tierTakesPrecedenceOverDiceImportance() {
            // COLD with high importance vs WARM with low importance -- COLD is dropped first
            var coldHighImportance = new MemoryUnit("cold-hi", "cold-hi text", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.9, 1.0, MemoryTier.COLD, null);
            var warmLowImportance = new MemoryUnit("warm-lo", "warm-lo text", 500, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.1, 1.0, MemoryTier.WARM, null);

            // Budget tight enough to force one drop: overhead=10, 2 units=22, total=32
            // budget=21 forces dropping one
            var result = enforcer.enforce(
                    List.of(warmLowImportance, coldHighImportance),
                    21,
                    counter,
                    CompliancePolicy.flat());

            assertThat(result.excluded()).hasSize(1);
            assertThat(result.excluded().get(0).id()).isEqualTo("cold-hi");
            assertThat(result.included()).hasSize(1);
            assertThat(result.included().get(0).id()).isEqualTo("warm-lo");
        }

        @Test
        @DisplayName("CANON memory units never dropped regardless of tier")
        void canonNeverDroppedRegardlessOfTier() {
            var canonCold = new MemoryUnit("canon-cold", "canon-cold text", 200, Authority.CANON, false, 0.5, 0,
                    null, 0.0, 1.0, MemoryTier.COLD, null);
            var reliableHot = new MemoryUnit("reliable-hot", "reliable-hot text", 800, Authority.RELIABLE, false, 0.9, 0,
                    null, 0.9, 1.0, MemoryTier.HOT, null);

            // Budget forces one drop: overhead=10, 2 units=22, total=32
            // budget=21 forces dropping one
            var result = enforcer.enforce(
                    List.of(canonCold, reliableHot),
                    21,
                    counter,
                    CompliancePolicy.flat());

            // CANON is always included even though it's COLD with low rank
            assertThat(result.included()).extracting(MemoryUnit::id).contains("canon-cold");
            // RELIABLE is the one that gets dropped
            assertThat(result.excluded()).extracting(MemoryUnit::id).contains("reliable-hot");
        }
    }
}
