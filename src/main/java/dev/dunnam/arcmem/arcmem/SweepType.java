package dev.dunnam.diceanchors.anchor;

/**
 * Classification of a proactive maintenance sweep based on memory pressure level.
 *
 * <p>Sweep type determines audit depth: {@code FULL} sweeps invoke the optional LLM
 * audit pass for borderline anchors; {@code LIGHT} sweeps use heuristic scoring only.
 */
public enum SweepType {
    /** Pressure below light-sweep threshold — no sweep warranted. */
    NONE,
    /** Moderate pressure (>= light threshold, < full threshold) — heuristic audit only. */
    LIGHT,
    /** High pressure (>= full threshold) — heuristic audit plus optional LLM refinement. */
    FULL
}
