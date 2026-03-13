package dev.arcmem.core.memory.budget;
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
