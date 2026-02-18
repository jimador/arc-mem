package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;

import java.util.List;

/**
 * Result of enforcing a prompt token budget for anchor assembly.
 *
 * @param included        anchors retained in the prompt
 * @param excluded        anchors dropped to fit budget constraints
 * @param estimatedTokens estimated total tokens for the final anchor block
 * @param budgetExceeded  true when mandatory content exceeded budget or anchors were dropped
 */
public record BudgetResult(
        List<Anchor> included,
        List<Anchor> excluded,
        int estimatedTokens,
        boolean budgetExceeded
) {
}
