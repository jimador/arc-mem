package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.assembly.CompactionResult;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Snapshot of simulation state delivered to the UI after each turn.
 * <p>
 * Consumed by the Vaadin view via {@code ui.access()} for thread-safe UI updates.
 */
public record SimulationProgress(
        SimulationPhase phase,
        @Nullable TurnType turnType,
        int turnNumber,
        int totalTurns,
        String lastPlayerMessage,
        String lastDmResponse,
        List<Anchor> activeAnchors,
        ContextTrace contextTrace,
        List<EvalVerdict> verdicts,
        boolean complete,
        String statusMessage,
        boolean injectionState,
        @Nullable List<AssertionResult> assertionResults,
        @Nullable CompactionResult compactionResult,
        List<SimulationTurn.AnchorEvent> anchorEvents,
        @Nullable ScoringResult scoringResult
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
