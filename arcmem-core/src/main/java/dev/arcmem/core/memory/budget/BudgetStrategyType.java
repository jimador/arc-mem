package dev.arcmem.core.memory.budget;

/**
 * Selects the active {@link BudgetStrategy} implementation from configuration.
 */
public enum BudgetStrategyType {

    /**
     * Count-only enforcement. Reproduces the original inline behavior.
     */
    COUNT
}
