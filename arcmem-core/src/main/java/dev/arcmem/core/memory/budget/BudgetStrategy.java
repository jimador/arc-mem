package dev.arcmem.core.memory.budget;

import dev.arcmem.core.memory.model.MemoryUnit;

import java.util.List;

/**
 * Policy interface for unit budget enforcement.
 * <p>
 * {@link CountBasedBudgetStrategy} is the default implementation, enforcing a simple
 * count-based maximum on active units.
 */
public sealed interface BudgetStrategy
        permits CountBasedBudgetStrategy {

    /**
     * Computes the effective budget for the current unit set.
     *
     * @param activeUnits units currently active in the context
     * @param rawBudget   the configured maximum from properties
     *
     * @return the effective budget to enforce; always in [1, rawBudget]
     */
    int computeEffectiveBudget(List<MemoryUnit> activeUnits, int rawBudget);

    /**
     * Selects units to evict when active count exceeds the effective budget.
     * <p>
     * Implementations MUST NOT include pinned or CANON units in the result.
     * The engine may further filter candidates through invariant evaluation before
     * executing eviction.
     *
     * @param activeUnits units currently active in the context
     * @param excess      number of units to evict ({@code activeCount - effectiveBudget})
     *
     * @return ordered list of eviction candidates; may be shorter than {@code excess}
     * if insufficient evictable units exist
     */
    List<MemoryUnit> selectForEviction(List<MemoryUnit> activeUnits, int excess);
}
