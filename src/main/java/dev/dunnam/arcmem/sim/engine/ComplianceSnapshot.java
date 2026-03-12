package dev.dunnam.diceanchors.sim.engine;

/**
 * Captures post-generation compliance validation results for a single simulation turn.
 * In simulation mode, enforcement always accepts the response but captures violations
 * for observability.
 */
public record ComplianceSnapshot(
        int violationCount,
        String suggestedAction,
        boolean wouldHaveRetried,
        long validationMs
) {
    public static ComplianceSnapshot none() {
        return new ComplianceSnapshot(0, "", false, 0L);
    }
}
