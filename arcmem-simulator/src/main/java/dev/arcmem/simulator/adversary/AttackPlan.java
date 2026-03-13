package dev.arcmem.simulator.adversary;
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

import dev.arcmem.simulator.engine.AttackStrategy;
import dev.arcmem.simulator.engine.StrategyTier;
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
 * @param targetFacts unit texts the adversary intends to challenge or undermine
 * @param strategies  attack techniques to apply this turn
 * @param tier        difficulty tier of the chosen strategies
 * @param rationale   brief explanation of why this attack was selected (for debugging)
 * @param sequence    non-null when this turn is part of a multi-turn SETUP→BUILD→PAYOFF chain
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
