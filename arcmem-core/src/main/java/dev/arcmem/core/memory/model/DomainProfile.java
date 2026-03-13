package dev.arcmem.core.memory.model;

import java.util.Map;

/**
 * Named weight configuration for trust signal evaluation.
 * Defines per-signal weights (must sum to 1.0) and threshold boundaries
 * for promotion zone routing.
 *
 * @param name                 profile identifier
 * @param weights              signal name to weight mapping (MUST sum to 1.0 ± 0.001)
 * @param autoPromoteThreshold score >= this value routes to AUTO_PROMOTE
 * @param reviewThreshold      score >= this value (but below autoPromote) routes to REVIEW
 * @param archiveThreshold     score below this value routes to ARCHIVE
 */
public record DomainProfile(
        String name,
        Map<String, Double> weights,
        double autoPromoteThreshold,
        double reviewThreshold,
        double archiveThreshold
) {

    private static final double WEIGHT_SUM_TOLERANCE = 0.001;

    /**
     * Compact constructor that validates the signal weights sum to 1.0 (within ±0.001).
     *
     * @throws IllegalArgumentException if weights are null or do not sum to 1.0 ± 0.001
     */
    public DomainProfile {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("DomainProfile weights must not be null or empty");
        }
        var sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > WEIGHT_SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "DomainProfile signal weights must sum to 1.0 (±" + WEIGHT_SUM_TOLERANCE + "), got: " + sum);
        }
    }

    /**
     * Graph-heavy weights, strict thresholds.
     * Favors propositions with strong graph consistency.
     * Quality signals (novelty, importance) are tie-breakers at 0.05 each;
     * their weight redistributes to present signals when disabled.
     */
    public static final DomainProfile SECURE = new DomainProfile("SECURE",
                                                                 Map.of("sourceAuthority", 0.18, "extractionConfidence", 0.18, "graphConsistency", 0.36, "corroboration", 0.18, "novelty", 0.05, "importance", 0.05),
                                                                 0.85, 0.50, 0.30);

    /**
     * Equal weights, moderate thresholds.
     * Default profile when no specific domain bias is needed.
     * Quality signals (novelty, importance) at 0.06 each;
     * their weight redistributes to present signals when disabled.
     */
    public static final DomainProfile BALANCED = new DomainProfile("BALANCED",
                                                                   Map.of("sourceAuthority", 0.22, "extractionConfidence", 0.22, "graphConsistency", 0.22, "corroboration", 0.22, "novelty", 0.06, "importance", 0.06),
                                                                   0.65, 0.40, 0.25);

    /**
     * Look up a profile by name (case-insensitive). Returns BALANCED if the
     * name is not recognized.
     */
    public static DomainProfile byName(String name) {
        if (name == null) {
            return BALANCED;
        }
        return switch (name.toUpperCase()) {
            case "SECURE" -> SECURE;
            case "BALANCED" -> BALANCED;
            default -> BALANCED;
        };
    }
}
