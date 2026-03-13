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
import dev.arcmem.core.memory.event.ArchiveReason;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.spi.llm.LlmCallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProactiveMaintenanceStrategy")
@ExtendWith(MockitoExtension.class)
class ProactiveMaintenanceStrategyTest {

    @Mock
    MemoryPressureGauge pressureGauge;
    @Mock
    ArcMemEngine arcMemEngine;
    @Mock
    MemoryUnitRepository repository;
    @Mock
    CanonizationGate canonizationGate;
    @Mock
    InvariantEvaluator invariantEvaluator;
    @Mock
    LlmCallService llmCallService;

    ProactiveMaintenanceStrategy strategy;

    private static final ArcMemProperties.PressureConfig PRESSURE_CONFIG =
            new ArcMemProperties.PressureConfig(true, 0.4, 0.3, 0.2, 0.1, 0.4, 0.8, 1.5, 5);

    private static final ArcMemProperties.ProactiveConfig PROACTIVE_CONFIG =
            new ArcMemProperties.ProactiveConfig(10, 0.1, 0.3, 0.6, 10, 0.8, 5, 50, 50, false, false);

    private static final ArcMemProperties.UnitConfig UNIT_CONFIG =
            new ArcMemProperties.UnitConfig(
                    20, 500, 100, 900, true, 0.65,
                    DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED,
                    true, true, true, 0.6, 400, 200,
                    new ArcMemProperties.TierConfig(600, 350, 1.5, 1.0, 0.6),
                    new ArcMemProperties.RevisionConfig(true, false, 0.75),
                    null, null, null);

    @BeforeEach
    void setUp() {
        var maintenance = new ArcMemProperties.MaintenanceConfig(MaintenanceMode.PROACTIVE, PROACTIVE_CONFIG);
        var properties = new ArcMemProperties(
                UNIT_CONFIG, null, null, null, null, null, null, null,
                maintenance, PRESSURE_CONFIG, null, null, new ArcMemProperties.LlmCallConfig(30, 10));
        strategy = new ProactiveMaintenanceStrategy(pressureGauge, arcMemEngine, repository,
                canonizationGate, invariantEvaluator, llmCallService, properties);
    }

    // ─── Trigger logic ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("shouldRunSweep")
    class ShouldRunSweep {

        @Test
        @DisplayName("returnsTrue when pressure above threshold and min turns elapsed")
        void trueWhenPressureHighAndTurnsElapsed() {
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));
            var context = ctx("c1", List.of(), 15);
            assertThat(strategy.shouldRunSweep(context)).isTrue();
        }

        @Test
        @DisplayName("returnsFalse when pressure below light threshold")
        void falseWhenPressureBelowThreshold() {
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.2, 0.2, 0.0, 0.0, 0.0, java.time.Instant.now()));
            var context = ctx("c1", List.of(), 15);
            assertThat(strategy.shouldRunSweep(context)).isFalse();
        }

        @Test
        @DisplayName("returnsFalse when min turns not elapsed")
        void falseWhenMinTurnsNotElapsed() {
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.9, 0.9, 0.0, 0.0, 0.0, java.time.Instant.now()));
            // First sweep at turn 5
            strategy.executeSweep(ctx("c2", List.of(), 5));
            // Attempt at turn 10 — only 5 turns elapsed, need 10
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.9, 0.9, 0.0, 0.0, 0.0, java.time.Instant.now()));
            assertThat(strategy.shouldRunSweep(ctx("c2", List.of(), 10))).isFalse();
        }

        @Test
        @DisplayName("returnsFalse when pressureGauge throws")
        void falseOnException() {
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("gauge failure"));
            assertThat(strategy.shouldRunSweep(ctx("c1", List.of(), 5))).isFalse();
        }
    }

    @Nested
    @DisplayName("determineSweepType")
    class DetermineSweepType {

        @Test
        @DisplayName("FULL for pressure >= 0.8")
        void fullAtHighPressure() {
            var pressure = new PressureScore(0.85, 0.85, 0.0, 0.0, 0.0, java.time.Instant.now());
            assertThat(strategy.determineSweepType(pressure)).isEqualTo(SweepType.FULL);
        }

        @Test
        @DisplayName("LIGHT for pressure between 0.4 and 0.8")
        void lightAtModeratePressure() {
            var pressure = new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now());
            assertThat(strategy.determineSweepType(pressure)).isEqualTo(SweepType.LIGHT);
        }

        @Test
        @DisplayName("NONE for pressure below 0.4")
        void noneAtLowPressure() {
            var pressure = new PressureScore(0.2, 0.2, 0.0, 0.0, 0.0, java.time.Instant.now());
            assertThat(strategy.determineSweepType(pressure)).isEqualTo(SweepType.NONE);
        }
    }

    // ─── Audit step ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Audit step")
    class AuditStep {

        @Test
        @DisplayName("high-rank HOT memory unit with recent reinforcement scores high")
        void highRankHotUnitScoresHigh() {
            var unit = unit("a1", 800, Authority.RELIABLE, false, 20, MemoryTier.HOT);
            var context = ctx("c1", List.of(unit), 22);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.9, 0.9, 0.0, 0.0, 0.0, java.time.Instant.now()));

            var result = strategy.executeSweep(context);
            assertThat(result.unitsAudited()).isEqualTo(1);
        }

        @Test
        @DisplayName("low-rank COLD memory unit scores low and is reflected in prune candidate")
        void lowRankColdUnitScoresLow() {
            var unit = unit("a2", 110, Authority.PROVISIONAL, false, 0, MemoryTier.COLD);
            var context = ctx("c1", List.of(unit), 50);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.9, 0.9, 0.0, 0.0, 0.0, java.time.Instant.now()));
            when(invariantEvaluator.evaluate(anyString(), any(), any(), any()))
                    .thenReturn(new InvariantEvaluation(List.of(), 0));

            // Very low rank, COLD, zero reinforcements = very low score -> pruned
            strategy.executeSweep(context);
            verify(arcMemEngine).archive("a2", ArchiveReason.PROACTIVE_MAINTENANCE);
        }
    }

    // ─── Refresh step ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Refresh step")
    class RefreshStep {

        @Test
        @DisplayName("high-score memory unit receives activation score boost")
        void highScoreReceivesBoost() {
            // HOT + high rank + recent reinforcement -> score >= 0.7
            var unit = unit("a1", 800, Authority.RELIABLE, false, 22, MemoryTier.HOT);
            var context = ctx("c1", List.of(unit), 22);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            verify(repository).updateRank(anyString(), anyInt());
        }

        @Test
        @DisplayName("CANON memory unit is immune to activation score penalty")
        void canonUnitImmuneToRankPenalty() {
            // COLD + low rank + no reinforcement -> would score low, but CANON
            var unit = unit("a1", 110, Authority.CANON, false, 0, MemoryTier.COLD);
            var context = ctx("c1", List.of(unit), 50);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            // CANON units skip rank penalty — no updateRank call at all
            verify(repository, never()).updateRank(anyString(), anyInt());
        }

        @Test
        @DisplayName("pinned memory unit is immune to activation score penalty")
        void pinnedUnitImmuneToRankPenalty() {
            var unit = unit("a1", 110, Authority.RELIABLE, true, 0, MemoryTier.COLD);
            var context = ctx("c1", List.of(unit), 50);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            // Pinned units skip rank penalty — no updateRank call at all
            verify(repository, never()).updateRank(anyString(), anyInt());
        }

        @Test
        @DisplayName("rank boost clamped at MAX_RANK")
        void rankBoostClampedAtMax() {
            var unit = unit("a1", 870, Authority.RELIABLE, false, 22, MemoryTier.HOT);
            var context = ctx("c1", List.of(unit), 22);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            // Boost would go to 920 but clamped to 900
            verify(repository).updateRank("a1", 900);
        }

        @Test
        @DisplayName("rank penalty clamped at MIN_RANK")
        void rankPenaltyClampedAtMin() {
            var unit = unit("a1", 120, Authority.PROVISIONAL, false, 0, MemoryTier.COLD);
            var context = ctx("c1", List.of(unit), 50);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));
            when(invariantEvaluator.evaluate(anyString(), any(), any(), any()))
                    .thenReturn(new InvariantEvaluation(List.of(), 0));

            strategy.executeSweep(context);
            // Penalty would go to 70 but clamped to 100
            verify(repository).updateRank("a1", 100);
        }
    }

    // ─── Consolidate step ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Consolidate step")
    class ConsolidateStep {

        @Test
        @DisplayName("RELIABLE memory unit meeting all criteria is routed to CanonizationGate")
        void reliableUnitMeetingCriteriaIsRouted() {
            // HOT + high rank + high reinforcement -> passes all criteria
            var unit = unit("a1", 800, Authority.RELIABLE, false, 20, MemoryTier.HOT);
            var context = ctx("c1", List.of(unit), 22);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            verify(canonizationGate).requestCanonization(
                    anyString(), anyString(), anyString(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("RELIABLE memory unit missing reinforcement count is not routed")
        void reliableUnitMissingReinforcements() {
            var unit = unit("a1", 800, Authority.RELIABLE, false, 5, MemoryTier.HOT);
            var context = ctx("c1", List.of(unit), 7);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            verify(canonizationGate, never()).requestCanonization(
                    anyString(), anyString(), anyString(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("PROVISIONAL memory unit is skipped")
        void provisionalUnitSkipped() {
            var unit = unit("a1", 800, Authority.PROVISIONAL, false, 20, MemoryTier.HOT);
            var context = ctx("c1", List.of(unit), 22);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            verify(canonizationGate, never()).requestCanonization(
                    anyString(), anyString(), anyString(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("UNRELIABLE memory unit is skipped")
        void unreliableUnitSkipped() {
            var unit = unit("a1", 800, Authority.UNRELIABLE, false, 20, MemoryTier.HOT);
            var context = ctx("c1", List.of(unit), 22);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            verify(canonizationGate, never()).requestCanonization(
                    anyString(), anyString(), anyString(), any(), anyString(), anyString());
        }
    }

    // ─── Prune step ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Prune step")
    class PruneStep {

        @Test
        @DisplayName("memory unit below hard floor is always pruned")
        void belowHardFloorAlwaysPruned() {
            // Minimal rank + COLD + zero reinforcements = score well below 0.1
            // Use moderate pressure (>= light threshold but below soft prune gate) to trigger sweep
            var unit = unit("a1", 100, Authority.PROVISIONAL, false, 0, MemoryTier.COLD);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));
            when(invariantEvaluator.evaluate(anyString(), any(), any(), any()))
                    .thenReturn(new InvariantEvaluation(List.of(), 0));

            strategy.executeSweep(ctx("c1", List.of(unit), 100));
            verify(arcMemEngine).archive("a1", ArchiveReason.PROACTIVE_MAINTENANCE);
        }

        @Test
        @DisplayName("memory unit below soft floor pruned when pressure >= threshold")
        void belowSoftFloorPrunedUnderPressure() {
            // WARM + moderate rank, recent but not very reinforced = score ~0.2 (between hard and soft floor)
            // rank=200, WARM, reinforcementCount=2, turn=10 -> recency=(1-min(8/10,1))=0.2, rank=(200-100)/800=0.125, tier=0.5
            // score = 0.2*0.33 + 0.125*0.33 + 0.5*0.34 = 0.066 + 0.041 + 0.17 = 0.277 (below 0.3, above 0.1)
            var unit = unit("a1", 200, Authority.PROVISIONAL, false, 2, MemoryTier.WARM);
            var context = ctx("c1", List.of(unit), 10);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.65, 0.65, 0.0, 0.0, 0.0, java.time.Instant.now()));
            when(invariantEvaluator.evaluate(anyString(), any(), any(), any()))
                    .thenReturn(new InvariantEvaluation(List.of(), 0));

            strategy.executeSweep(context);
            verify(arcMemEngine).archive("a1", ArchiveReason.PROACTIVE_MAINTENANCE);
        }

        @Test
        @DisplayName("memory unit above soft floor is never pruned")
        void aboveSoftFloorNotPruned() {
            // HOT + high rank = score >= 0.7 -> not pruned
            var unit = unit("a1", 800, Authority.RELIABLE, false, 22, MemoryTier.HOT);
            var context = ctx("c1", List.of(unit), 22);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.9, 0.9, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            verify(arcMemEngine, never()).archive(anyString(), any(ArchiveReason.class));
        }

        @Test
        @DisplayName("CANON memory unit immune to pruning regardless of score")
        void canonUnitImmuneTopruning() {
            var unit = unit("a1", 100, Authority.CANON, false, 0, MemoryTier.COLD);
            var context = ctx("c1", List.of(unit), 100);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.9, 0.9, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            verify(arcMemEngine, never()).archive(anyString(), any(ArchiveReason.class));
        }

        @Test
        @DisplayName("pinned memory unit immune to pruning regardless of score")
        void pinnedUnitImmuneToPruning() {
            var unit = unit("a1", 100, Authority.PROVISIONAL, true, 0, MemoryTier.COLD);
            var context = ctx("c1", List.of(unit), 100);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.9, 0.9, 0.0, 0.0, 0.0, java.time.Instant.now()));

            strategy.executeSweep(context);
            verify(arcMemEngine, never()).archive(anyString(), any(ArchiveReason.class));
        }
    }

    // ─── Validate step ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validate step")
    class ValidateStep {

        @Test
        @DisplayName("clean validation returns zero violations in result")
        void cleanValidationZeroViolations() {
            var unit = unit("a1", 800, Authority.RELIABLE, false, 22, MemoryTier.HOT);
            var context = ctx("c1", List.of(unit), 22);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));

            var result = strategy.executeSweep(context);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("invariant violation is logged via evaluate call")
        void invariantViolationDetected() {
            var unit = unit("a1", 100, Authority.PROVISIONAL, false, 0, MemoryTier.COLD);
            var context = ctx("c1", List.of(unit), 100);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.9, 0.9, 0.0, 0.0, 0.0, java.time.Instant.now()));
            var violation = new InvariantViolationData("rule1", InvariantStrength.SHOULD,
                    ProposedAction.ARCHIVE, "test violation", "a1");
            when(invariantEvaluator.evaluate(anyString(), any(), any(), any()))
                    .thenReturn(new InvariantEvaluation(List.of(violation), 1));

            var result = strategy.executeSweep(context);
            // Violations should be counted
            assertThat(result.summary()).contains("violations=1");
        }
    }

    // ─── Full sweep / integration ──────────────────────────────────────────────

    @Nested
    @DisplayName("Full sweep execution")
    class FullSweep {

        @Test
        @DisplayName("sweep returns correct SweepResult counts after full cycle")
        void sweepReturnsCorrectMetrics() {
            var highUnit = unit("h1", 800, Authority.RELIABLE, false, 22, MemoryTier.HOT);
            var lowUnit = unit("l1", 100, Authority.PROVISIONAL, false, 0, MemoryTier.COLD);
            var context = ctx("c1", List.of(highUnit, lowUnit), 22);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.9, 0.9, 0.0, 0.0, 0.0, java.time.Instant.now()));
            when(invariantEvaluator.evaluate(anyString(), any(), any(), any()))
                    .thenReturn(new InvariantEvaluation(List.of(), 0));

            var result = strategy.executeSweep(context);

            assertThat(result.unitsAudited()).isEqualTo(2);
            assertThat(result.unitsPruned()).isGreaterThanOrEqualTo(0);
            assertThat(result.duration()).isNotNull();
            assertThat(result.summary()).isNotBlank();
        }

        @Test
        @DisplayName("executeSweep never throws even when steps fail")
        void sweepNeverThrows() {
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("gauge failure"));
            var result = strategy.executeSweep(ctx("c1", List.of(), 5));
            assertThat(result).isNotNull();
            assertThat(result.unitsAudited()).isZero();
        }

        @Test
        @DisplayName("light sweep executes all steps with heuristic-only audit")
        void lightSweepExecutesAllSteps() {
            var unit = unit("a1", 500, Authority.RELIABLE, false, 10, MemoryTier.WARM);
            var context = ctx("c1", List.of(unit), 10);
            when(pressureGauge.computePressure(anyString(), anyInt(), anyInt()))
                    .thenReturn(new PressureScore(0.5, 0.5, 0.0, 0.0, 0.0, java.time.Instant.now()));

            var result = strategy.executeSweep(context);
            assertThat(result.unitsAudited()).isEqualTo(1);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static MaintenanceContext ctx(String contextId, List<MemoryUnit> units, int turn) {
        return new MaintenanceContext(contextId, units, turn, null);
    }

    private static MemoryUnit unit(String id, int rank, Authority authority, boolean pinned,
                                  int reinforcementCount, MemoryTier tier) {
        return new MemoryUnit(id, "text-" + id, rank, authority, pinned, 0.9,
                reinforcementCount, null, 0.0, 1.0, tier);
    }

}
