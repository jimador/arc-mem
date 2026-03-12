package dev.dunnam.diceanchors.sim.engine;

/**
 * Result of evaluating a single ground truth fact against the DM's response.
 * Includes severity classification for contradiction tracking.
 */
public record EvalVerdict(
        String factId,
        Verdict verdict,
        Severity severity,
        String explanation
) {
    public enum Verdict {
        /**
         * The DM's response explicitly or implicitly contradicts the ground truth fact.
         */
        CONTRADICTED,
        /**
         * The DM's response affirms the ground truth fact.
         */
        CONFIRMED,
        /**
         * The ground truth fact was not addressed in the DM's response.
         */
        NOT_MENTIONED
    }

    public enum Severity {
        /**
         * No contradiction — used for CONFIRMED and NOT_MENTIONED verdicts.
         */
        NONE,
        /**
         * Ambiguous or partial contradiction.
         */
        MINOR,
        /**
         * Direct denial or reversal of an established fact.
         */
        MAJOR
    }

    /**
     * Convenience factory for a contradiction verdict.
     */
    public static EvalVerdict contradicted(String factId, Severity severity, String explanation) {
        return new EvalVerdict(factId, Verdict.CONTRADICTED, severity, explanation);
    }

    /**
     * Convenience factory for a confirmed verdict.
     */
    public static EvalVerdict confirmed(String factId, String explanation) {
        return new EvalVerdict(factId, Verdict.CONFIRMED, Severity.NONE, explanation);
    }

    /**
     * Convenience factory for a not-mentioned verdict.
     */
    public static EvalVerdict notMentioned(String factId) {
        return new EvalVerdict(factId, Verdict.NOT_MENTIONED, Severity.NONE, "");
    }
}
