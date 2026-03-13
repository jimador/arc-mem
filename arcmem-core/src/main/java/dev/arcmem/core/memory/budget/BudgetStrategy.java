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

import java.util.List;

/**
 * Policy interface for unit budget enforcement.
 * <p>
 * Two implementations are provided: {@link CountBasedBudgetStrategy} reproduces the
 * current count-only behavior; {@link InterferenceDensityBudgetStrategy} reduces the
 * effective budget when semantic overlap is high, applying the phase-transition insight
 * from Guo et al. (2025) "Sleeping LLMs" to prompt-injected working memory.
 */
public sealed interface BudgetStrategy
        permits CountBasedBudgetStrategy, InterferenceDensityBudgetStrategy {

    /**
     * Computes the effective budget for the current unit set.
     *
     * @param activeUnits units currently active in the context
     * @param rawBudget     the configured maximum from properties
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
     * @param excess        number of units to evict ({@code activeCount - effectiveBudget})
     * @return ordered list of eviction candidates; may be shorter than {@code excess}
     *         if insufficient evictable units exist
     */
    List<MemoryUnit> selectForEviction(List<MemoryUnit> activeUnits, int excess);
}
