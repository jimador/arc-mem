package dev.dunnam.diceanchors.sim.report;

import java.util.List;

/**
 * All contradictions for a single ground truth fact across all conditions and runs,
 * ordered by condition, run index, then turn number.
 *
 * @param factId  stable fact identifier from scenario ground truth
 * @param factText human-readable fact text
 * @param details  ordered contradiction events
 */
public record FactContradictionGroup(
        String factId,
        String factText,
        List<ContradictionDetail> details) {
}
