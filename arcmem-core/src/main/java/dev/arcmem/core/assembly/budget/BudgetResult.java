package dev.arcmem.core.assembly.budget;

import dev.arcmem.core.memory.model.MemoryUnit;

import java.util.List;

/**
 * Result of enforcing a prompt token budget for unit assembly.
 *
 * @param included        units retained in the prompt
 * @param excluded        units dropped to fit budget constraints
 * @param estimatedTokens estimated total tokens for the final unit block
 * @param budgetExceeded  true when mandatory content exceeded budget or units were dropped
 */
public record BudgetResult(
        List<MemoryUnit> included,
        List<MemoryUnit> excluded,
        int estimatedTokens,
        boolean budgetExceeded
) {
}
