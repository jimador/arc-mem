package dev.dunnam.diceanchors.sim.engine.adversary;

import dev.dunnam.diceanchors.anchor.Anchor;

import java.util.List;

/**
 * Contract for adaptive adversary implementations.
 * <p>
 * Implementations receive full anchor state at each turn and the run's
 * {@link AttackHistory}, and produce a non-null {@link AttackPlan}.
 */
public interface AdversaryStrategy {

    /**
     * Select an attack plan for the current turn.
     *
     * @param active     currently active anchors (highest-rank first, from {@code AnchorEngine.inject()})
     * @param conflicted anchors currently in conflict state (from {@code AnchorEngine.detectConflicts()})
     * @param history    outcomes recorded so far in this simulation run
     * @return a non-null {@link AttackPlan} with at least one strategy and at least one target
     */
    AttackPlan selectAttack(List<Anchor> active, List<Anchor> conflicted, AttackHistory history);
}
