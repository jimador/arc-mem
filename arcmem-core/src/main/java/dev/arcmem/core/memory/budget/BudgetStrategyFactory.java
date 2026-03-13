package dev.arcmem.core.memory.budget;

/**
 * Factory for creating {@link BudgetStrategy} instances from a {@link BudgetStrategyType}.
 * Used by the simulation layer to apply per-scenario budget strategy overrides.
 */
@FunctionalInterface
public interface BudgetStrategyFactory {

    BudgetStrategy create(BudgetStrategyType strategyType);
}
