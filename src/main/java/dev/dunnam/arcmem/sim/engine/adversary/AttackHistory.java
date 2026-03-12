package dev.dunnam.diceanchors.sim.engine.adversary;

import dev.dunnam.diceanchors.sim.engine.AttackStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mutable log of {@link AttackOutcome} records for a single simulation run.
 * <p>
 * Invariant I3: scoped to one simulation run; discarded when the run ends.
 * Thread-safety: not required — simulation turns execute sequentially.
 */
public class AttackHistory {

    private final List<AttackOutcome> outcomes = new ArrayList<>();

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

    /**
     * Turn number of the most recent attack, or 0 if none recorded.
     */
    public int lastAttackTurn() {
        var recent = lastN(1);
        return recent.isEmpty() ? 0 : recent.get(0).turn();
    }

    /**
     * Strategy IDs used in the most recent {@code n} attacks.
     */
    public Set<String> recentStrategyIds(int n) {
        return lastN(n).stream()
                       .flatMap(o -> o.plan().strategies().stream())
                       .map(AttackStrategy::name)
                       .collect(Collectors.toUnmodifiableSet());
    }

    public int size() {
        return outcomes.size();
    }
}
