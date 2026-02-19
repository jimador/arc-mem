package dev.dunnam.diceanchors.sim.engine.adversary;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable log of {@link AttackOutcome} records for a single simulation run.
 * <p>
 * Invariant I3: scoped to one simulation run; discarded when the run ends.
 * Thread-safety: not required — simulation turns execute sequentially.
 */
public class AttackHistory {

    private final List<AttackOutcome> outcomes = new ArrayList<>();

    /** Record the outcome of an adaptive adversary turn. */
    public void recordOutcome(AttackOutcome outcome) {
        outcomes.add(outcome);
    }

    /**
     * Return the most recent {@code n} outcomes, oldest first.
     * If fewer than {@code n} outcomes exist, returns all of them.
     */
    public List<AttackOutcome> lastN(int n) {
        if (n <= 0) {
            return List.of();
        }
        var start = Math.max(0, outcomes.size() - n);
        return List.copyOf(outcomes.subList(start, outcomes.size()));
    }

    /** Total number of outcomes recorded so far. */
    public int size() {
        return outcomes.size();
    }
}
