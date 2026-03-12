package dev.dunnam.diceanchors.anchor;

import java.util.List;

/**
 * Policy interface for anchor budget enforcement.
 * <p>
 * Two implementations are provided: {@link CountBasedBudgetStrategy} reproduces the
 * current count-only behavior; {@link InterferenceDensityBudgetStrategy} reduces the
 * effective budget when semantic overlap is high, applying the phase-transition insight
 * from Guo et al. (2025) "Sleeping LLMs" to prompt-injected working memory.
 */
public sealed interface BudgetStrategy
        permits CountBasedBudgetStrategy, InterferenceDensityBudgetStrategy {

    /**
     * Computes the effective budget for the current anchor set.
     *
     * @param activeAnchors anchors currently active in the context
     * @param rawBudget     the configured maximum from properties
     * @return the effective budget to enforce; always in [1, rawBudget]
     */
    int computeEffectiveBudget(List<Anchor> activeAnchors, int rawBudget);

    /**
     * Selects anchors to evict when active count exceeds the effective budget.
     * <p>
     * Implementations MUST NOT include pinned or CANON anchors in the result.
     * The engine may further filter candidates through invariant evaluation before
     * executing eviction.
     *
     * @param activeAnchors anchors currently active in the context
     * @param excess        number of anchors to evict ({@code activeCount - effectiveBudget})
     * @return ordered list of eviction candidates; may be shorter than {@code excess}
     *         if insufficient evictable anchors exist
     */
    List<Anchor> selectForEviction(List<Anchor> activeAnchors, int excess);
}
