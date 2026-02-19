package dev.dunnam.diceanchors.sim.engine.adversary;

import dev.dunnam.diceanchors.sim.engine.AttackStrategy;
import dev.dunnam.diceanchors.sim.engine.StrategyTier;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Immutable plan produced by an {@link AdversaryStrategy} for a single simulation turn.
 * <p>
 * Invariants:
 * <ul>
 *   <li>I1: {@code strategies} is never empty</li>
 *   <li>I2: {@code tier} matches the tier of every strategy in {@code strategies} per the catalog</li>
 * </ul>
 *
 * @param targetFacts       anchor texts the adversary intends to challenge or undermine
 * @param strategies        attack techniques to apply this turn
 * @param tier              difficulty tier of the chosen strategies
 * @param rationale         brief explanation of why this attack was selected (for debugging)
 * @param sequence          non-null when this turn is part of a multi-turn SETUP→BUILD→PAYOFF chain
 */
public record AttackPlan(
        List<String> targetFacts,
        List<AttackStrategy> strategies,
        StrategyTier tier,
        String rationale,
        @Nullable AttackSequence sequence
) {
    public AttackPlan {
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalArgumentException("AttackPlan.strategies must not be empty (I1)");
        }
        targetFacts = targetFacts != null ? List.copyOf(targetFacts) : List.of();
        strategies = List.copyOf(strategies);
    }
}
