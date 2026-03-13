package dev.arcmem.simulator.history;
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

import dev.arcmem.simulator.engine.*;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Persistent record of a completed simulation run, used for history browsing
 * and cross-run comparison.
 */
public record SimulationRunRecord(
        String runId,
        String scenarioId,
        Instant startedAt,
        Instant completedAt,
        List<TurnSnapshot> turnSnapshots,
        int interventionCount,
        List<MemoryUnit> finalUnitState,
        boolean injectionEnabled,
        int tokenBudget,
        @Nullable List<AssertionResult> assertionResults,
        @Nullable ScoringResult scoringResult,
        @Nullable String modelId
) {
    public SimulationRunRecord(
            String runId, String scenarioId, Instant startedAt, Instant completedAt,
            List<TurnSnapshot> turnSnapshots, int interventionCount, List<MemoryUnit> finalUnitState,
            boolean injectionEnabled, int tokenBudget,
            @Nullable List<AssertionResult> assertionResults, @Nullable ScoringResult scoringResult) {
        this(runId, scenarioId, startedAt, completedAt, turnSnapshots, interventionCount,
             finalUnitState, injectionEnabled, tokenBudget, assertionResults, scoringResult, null);
    }

    /**
     * Per-turn snapshot capturing state at that point in the simulation.
     */
    public record TurnSnapshot(
            int turnNumber,
            TurnType turnType,
            List<AttackStrategy> attackStrategies,
            String playerMessage,
            String dmResponse,
            List<MemoryUnit> activeUnits,
            @Nullable ContextTrace contextTrace,
            List<EvalVerdict> verdicts,
            boolean injectionEnabled,
            @Nullable CompactionResult compactionResult
    ) {
        /**
         * Select the worst verdict from the list for summary display.
         */
        public @Nullable EvalVerdict worstVerdict() {
            if (verdicts == null || verdicts.isEmpty()) {
                return null;
            }
            for (var v : verdicts) {
                if (v != null && v.verdict() == EvalVerdict.Verdict.CONTRADICTED) {
                    return v;
                }
            }
            EvalVerdict selected = null;
            for (var v : verdicts) {
                if (v == null) {
                    continue;
                }
                if (selected == null) {
                    selected = v;
                    continue;
                }
                if (selected.verdict() == EvalVerdict.Verdict.CONFIRMED
                    && v.verdict() == EvalVerdict.Verdict.NOT_MENTIONED) {
                    selected = v;
                }
            }
            return selected;
        }
    }

    /**
     * Calculate overall resilience as fraction of turns without contradictions.
     */
    public double resilienceRate() {
        if (turnSnapshots.isEmpty()) {
            return 1.0;
        }
        var contradictions = turnSnapshots.stream()
                                          .filter(t -> t.verdicts() != null && t.verdicts().stream()
                                                                                .anyMatch(v -> v.verdict() == EvalVerdict.Verdict.CONTRADICTED))
                                          .count();
        return 1.0 - ((double) contradictions / turnSnapshots.size());
    }
}
