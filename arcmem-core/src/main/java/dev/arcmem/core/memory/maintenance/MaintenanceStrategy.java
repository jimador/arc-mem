package dev.arcmem.core.memory.maintenance;

import dev.arcmem.core.memory.mutation.ReinforcementPolicy;

/**
 * Unified coordination interface for unit memory maintenance.
 *
 * <p>This interface supersedes the split between {@link DecayPolicy} and
 * {@link ReinforcementPolicy} as the top-level scheduling contract. It does NOT replace
 * those interfaces — it composes them and controls when they are applied.
 *
 * <h2>Design</h2>
 * Three execution modes are expressed through two orthogonal method pairs:
 * <ul>
 *   <li><strong>Reactive</strong> ({@code REACTIVE} / {@code HYBRID}):
 *       {@link #onTurnComplete} fires after every turn, inline with the conversation loop.</li>
 *   <li><strong>Proactive</strong> ({@code PROACTIVE} / {@code HYBRID}):
 *       {@link #shouldRunSweep} + {@link #executeSweep} implement periodic consolidation cycles
 *       inspired by the Sleeping LLM paper (Guo et al., 2025), which demonstrated recovery of
 *       degraded knowledge from 40% to 100% recall within 4 sleep cycles. The 5-step sweep
 *       contract (audit, refresh, consolidate, prune, validate) maps to applicable steps from
 *       the paper's 8-step sleep architecture.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * All implementations MUST be thread-safe. Multiple simulation runs may share a single
 * strategy instance and invoke its methods concurrently from different threads.
 *
 * <h2>Error contract</h2>
 * Implementations MUST NOT throw from any method. On failure they MUST log the error and
 * return a safe default: no-op for {@link #onTurnComplete}, {@code false} for
 * {@link #shouldRunSweep}, {@link SweepResult#empty()} for {@link #executeSweep}.
 *
 * <h2>Known implementations</h2>
 * <ul>
 *   <li>{@link ReactiveMaintenanceStrategy} — per-turn hook; no sweep</li>
 *   <li>{@link ProactiveMaintenanceStrategy} — stub for F07; sweeps not yet active</li>
 * </ul>
 */
public sealed interface MaintenanceStrategy
        permits ReactiveMaintenanceStrategy, ProactiveMaintenanceStrategy, HybridMaintenanceStrategy {

    /**
     * Reactive hook called after each conversation or simulation turn completes.
     * <p>
     * Implementations in REACTIVE or HYBRID mode use this to perform lightweight
     * per-turn bookkeeping. PROACTIVE-only implementations treat this as a no-op.
     *
     * @param context snapshot of the current runtime context; never null
     */
    void onTurnComplete(MaintenanceContext context);

    /**
     * Returns {@code true} when a proactive sweep should be triggered for the given context.
     * <p>
     * Called by the engine after {@link #onTurnComplete} to decide whether a full sweep
     * cycle is warranted. REACTIVE-only implementations always return {@code false}.
     *
     * @param context snapshot of the current runtime context; never null
     *
     * @return true if {@link #executeSweep} should be called immediately
     */
    boolean shouldRunSweep(MaintenanceContext context);

    /**
     * Execute a proactive maintenance sweep over the active unit set.
     * <p>
     * The sweep contract follows the 5-step pattern derived from Sleeping LLM:
     * audit all units, refresh rank/authority, consolidate overlaps, prune candidates,
     * validate surviving units. The {@link SweepResult} records counters for each step.
     * <p>
     * REACTIVE-only implementations return {@link SweepResult#empty()}.
     *
     * @param context snapshot of the current runtime context; never null
     *
     * @return a non-null result describing what the sweep did
     */
    SweepResult executeSweep(MaintenanceContext context);
}
