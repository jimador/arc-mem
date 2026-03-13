package dev.arcmem.core.memory.maintenance;

import dev.arcmem.core.memory.mutation.ReinforcementPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-turn maintenance strategy that preserves the existing decay/reinforcement behavior.
 *
 * <p>This is a coordination layer, not a replacement for {@link DecayPolicy} and
 * {@link ReinforcementPolicy}. Those policies are still invoked directly by
 * {@code ArcMemEngine#reinforce} and {@code ArcMemEngine#applyDecay}. This strategy
 * holds references to the policies so that future enhancements (F07) can reorganize
 * scheduling without changing the engine's internal logic.
 *
 * <h2>Behavioral contract</h2>
 * <ul>
 *   <li>{@link #onTurnComplete} — logs completion; actual decay/reinforcement is handled
 *       by the engine. This hook point is where F07 will add cross-unit analysis.</li>
 *   <li>{@link #shouldRunSweep} — always {@code false}; reactive mode never triggers sweeps.</li>
 *   <li>{@link #executeSweep} — always returns {@link SweepResult#empty()}.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * Thread-safe. All fields are final and injected via constructor. No mutable state.
 */
public final class ReactiveMaintenanceStrategy implements MaintenanceStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveMaintenanceStrategy.class);

    private final DecayPolicy decayPolicy;
    private final ReinforcementPolicy reinforcementPolicy;

    public ReactiveMaintenanceStrategy(DecayPolicy decayPolicy, ReinforcementPolicy reinforcementPolicy) {
        this.decayPolicy = decayPolicy;
        this.reinforcementPolicy = reinforcementPolicy;
    }

    @Override
    public void onTurnComplete(MaintenanceContext context) {
        logger.debug("Reactive maintenance completed for context={} turn={} units={}",
                     context.contextId(), context.turnNumber(), context.activeUnits().size());
    }

    @Override
    public boolean shouldRunSweep(MaintenanceContext context) {
        return false;
    }

    @Override
    public SweepResult executeSweep(MaintenanceContext context) {
        return SweepResult.empty();
    }

    public DecayPolicy decayPolicy() {
        return decayPolicy;
    }

    public ReinforcementPolicy reinforcementPolicy() {
        return reinforcementPolicy;
    }
}
