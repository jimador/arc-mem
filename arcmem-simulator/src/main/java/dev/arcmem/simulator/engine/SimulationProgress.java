package dev.arcmem.simulator.engine;
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
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Snapshot of simulation state delivered to the UI after each turn.
 * <p>
 * Captured after turn execution and delivered to the UI callback via {@code ui.access()} for thread-safe updates.
 * Includes the current phase, turn number, player/DM messages, active units, drift verdicts, assertion results,
 * unit lifecycle events, and scoring results. The {@code injectionState} field indicates whether unit injection was active for this turn.
 */
public record SimulationProgress(
        SimulationPhase phase,
        @Nullable TurnType turnType,
        List<AttackStrategy> attackStrategies,
        int turnNumber,
        int totalTurns,
        String lastPlayerMessage,
        String lastDmResponse,
        List<MemoryUnit> activeUnits,
        ContextTrace contextTrace,
        List<EvalVerdict> verdicts,
        boolean complete,
        String statusMessage,
        boolean injectionState,
        @Nullable List<AssertionResult> assertionResults,
        @Nullable CompactionResult compactionResult,
        List<SimulationTurn.MemoryUnitEvent> unitEvents,
        @Nullable ScoringResult scoringResult,
        long turnDurationMs,
        @Nullable List<InvariantRule> activeInvariantRules,
        @Nullable List<InvariantViolationData> invariantViolations,
        @Nullable String runId
) {
    /**
     * Lifecycle phases for the simulation state machine.
     */
    public enum SimulationPhase {
        SETUP,
        WARM_UP,
        ESTABLISH,
        ATTACK,
        EVALUATE,
        COMPLETE,
        CANCELLED
    }

    /**
     * Select the worst verdict from the list for summary display.
     * <p>
     * Priority: CONTRADICTED (actual drift) > CONFIRMED > NOT_MENTIONED.
     * NOT_MENTIONED means the fact wasn't addressed — that's normal, not drift.
     * Returns null when no verdicts indicate drift (i.e., no CONTRADICTED).
     */
    public @Nullable EvalVerdict worstVerdict() {
        if (verdicts == null || verdicts.isEmpty()) {
            return null;
        }
        EvalVerdict contradicted = null;
        EvalVerdict confirmed = null;
        for (var v : verdicts) {
            if (v == null) {
                continue;
            }
            if (v.verdict() == EvalVerdict.Verdict.CONTRADICTED) {
                // Prefer MAJOR over MINOR severity
                if (contradicted == null || v.severity().ordinal() > contradicted.severity().ordinal()) {
                    contradicted = v;
                }
            } else if (v.verdict() == EvalVerdict.Verdict.CONFIRMED && confirmed == null) {
                confirmed = v;
            }
        }
        // Only return a verdict if there's a contradiction (actual drift)
        // or at least one confirmation (positive signal). NOT_MENTIONED alone
        // is not worth surfacing — the DM simply didn't address that fact.
        if (contradicted != null) {
            return contradicted;
        }
        return confirmed;
    }
}
