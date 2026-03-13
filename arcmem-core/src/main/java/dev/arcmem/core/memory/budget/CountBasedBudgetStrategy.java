package dev.arcmem.core.memory.budget;

import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryUnit;

import java.util.Comparator;
import java.util.List;

/**
 * Budget strategy that enforces a hard count limit, evicting the lowest-ranked
 * non-pinned, non-CANON units when the active set exceeds the budget.
 * <p>
 * This is a mechanical extraction of the inline logic previously in
 * {@code ArcMemEngine.promote()}, preserving identical behavior.
 */
public final class CountBasedBudgetStrategy implements BudgetStrategy {

    @Override
    public int computeEffectiveBudget(List<MemoryUnit> activeUnits, int rawBudget) {
        return rawBudget;
    }

    @Override
    public List<MemoryUnit> selectForEviction(List<MemoryUnit> activeUnits, int excess) {
        return activeUnits.stream()
                          .filter(a -> !a.pinned() && a.authority() != Authority.CANON)
                          .sorted(Comparator.comparingInt(MemoryUnit::rank))
                          .limit(excess)
                          .toList();
    }
}
