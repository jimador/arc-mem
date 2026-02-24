package dev.dunnam.diceanchors.sim.report;

import dev.dunnam.diceanchors.sim.engine.AttackStrategy;
import dev.dunnam.diceanchors.sim.engine.EvalVerdict;

import java.util.List;

/**
 * One contradiction event with its full turn context.
 *
 * @param factId           ground truth fact that was contradicted
 * @param condition        experiment condition (e.g., FULL_ANCHORS)
 * @param runIndex         1-based run index within the condition
 * @param turnNumber       turn number where contradiction occurred
 * @param attackStrategies attack strategies active on this turn
 * @param playerMessage    truncated player/attack message
 * @param dmResponse       truncated DM response
 * @param severity         contradiction severity
 * @param explanation      evaluator explanation of the contradiction
 */
public record ContradictionDetail(
        String factId,
        String condition,
        int runIndex,
        int turnNumber,
        List<AttackStrategy> attackStrategies,
        String playerMessage,
        String dmResponse,
        EvalVerdict.Severity severity,
        String explanation) {

    private static final int MESSAGE_MAX_LENGTH = 120;

    /**
     * Create a detail with messages pre-truncated to 120 characters.
     */
    public static ContradictionDetail of(
            String factId, String condition, int runIndex, int turnNumber,
            List<AttackStrategy> attackStrategies,
            String playerMessage, String dmResponse,
            EvalVerdict.Severity severity, String explanation) {
        return new ContradictionDetail(
                factId, condition, runIndex, turnNumber,
                attackStrategies != null ? List.copyOf(attackStrategies) : List.of(),
                truncate(playerMessage, MESSAGE_MAX_LENGTH),
                truncate(dmResponse, MESSAGE_MAX_LENGTH),
                severity,
                explanation != null ? explanation : "");
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
