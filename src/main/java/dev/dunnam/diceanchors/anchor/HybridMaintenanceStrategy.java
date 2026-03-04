package dev.dunnam.diceanchors.anchor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composes reactive and proactive maintenance into a single {@link MaintenanceStrategy}.
 *
 * <p>The hybrid model mirrors the wake/sleep cycle from Sleeping LLM (Guo et al., 2025):
 * lightweight per-turn bookkeeping (reactive, analogous to the "wake" phase) runs on every
 * turn, while periodic consolidation sweeps (proactive, analogous to the "sleep" phase) fire
 * when the proactive trigger condition is met. The two mechanisms are sequential — the reactive
 * hook completes before the sweep decision is evaluated.
 *
 * <h2>Delegation semantics</h2>
 * <ul>
 *   <li>{@link #onTurnComplete} — delegates to reactive only.</li>
 *   <li>{@link #shouldRunSweep} — delegates to proactive only.</li>
 *   <li>{@link #executeSweep} — delegates to proactive only.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * Thread-safe. All fields are final and each delegate is responsible for its own thread safety.
 */
public non-sealed class HybridMaintenanceStrategy implements MaintenanceStrategy {

    private static final Logger logger = LoggerFactory.getLogger(HybridMaintenanceStrategy.class);

    private final ReactiveMaintenanceStrategy reactive;
    private final ProactiveMaintenanceStrategy proactive;

    public HybridMaintenanceStrategy(ReactiveMaintenanceStrategy reactive,
                                     ProactiveMaintenanceStrategy proactive) {
        this.reactive = reactive;
        this.proactive = proactive;
    }

    @Override
    public void onTurnComplete(MaintenanceContext context) {
        reactive.onTurnComplete(context);
    }

    @Override
    public boolean shouldRunSweep(MaintenanceContext context) {
        return proactive.shouldRunSweep(context);
    }

    @Override
    public SweepResult executeSweep(MaintenanceContext context) {
        var result = proactive.executeSweep(context);
        logger.debug("Hybrid sweep completed for context={} audited={} pruned={}",
                context.contextId(), result.anchorsAudited(), result.anchorsPruned());
        return result;
    }
}
