package dev.dunnam.diceanchors.anchor;

import java.util.Comparator;
import java.util.List;

/**
 * Budget strategy that enforces a hard count limit, evicting the lowest-ranked
 * non-pinned, non-CANON anchors when the active set exceeds the budget.
 * <p>
 * This is a mechanical extraction of the inline logic previously in
 * {@code AnchorEngine.promote()}, preserving identical behavior.
 */
public final class CountBasedBudgetStrategy implements BudgetStrategy {

    @Override
    public int computeEffectiveBudget(List<Anchor> activeAnchors, int rawBudget) {
        return rawBudget;
    }

    @Override
    public List<Anchor> selectForEviction(List<Anchor> activeAnchors, int excess) {
        return activeAnchors.stream()
                .filter(a -> !a.pinned() && a.authority() != Authority.CANON)
                .sorted(Comparator.comparingInt(Anchor::rank))
                .limit(excess)
                .toList();
    }
}
