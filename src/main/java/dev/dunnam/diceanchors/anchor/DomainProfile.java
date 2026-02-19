package dev.dunnam.diceanchors.anchor;

import com.google.common.annotations.VisibleForTesting;

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
     * Creates a {@code DomainProfile} for testing purposes, bypassing weight sum validation.
     * <p>
     * This factory SHOULD only be used in test code. Production code MUST use
     * the canonical constructor, which enforces the weight sum invariant.
     *
     * @param name                 profile identifier
     * @param weights              signal name to weight mapping (validation skipped)
     * @param autoPromoteThreshold score >= this value routes to AUTO_PROMOTE
     * @param reviewThreshold      score >= this value (but below autoPromote) routes to REVIEW
     * @param archiveThreshold     score below this value routes to ARCHIVE
     * @return a DomainProfile with no weight validation
     */
    @VisibleForTesting
    public static DomainProfile forTesting(String name, Map<String, Double> weights,
                                           double autoPromoteThreshold, double reviewThreshold,
                                           double archiveThreshold) {
        // Normalize weights to sum to exactly 1.0 for testing convenience
        var sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum == 0.0) {
            throw new IllegalArgumentException("forTesting: weights must not all be zero");
        }
        var normalized = new java.util.LinkedHashMap<String, Double>();
        for (var entry : weights.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / sum);
        }
        return new DomainProfile(name, Map.copyOf(normalized), autoPromoteThreshold,
                reviewThreshold, archiveThreshold);
    }

    /**
     * Graph-heavy weights, strict thresholds.
     * Favors propositions with strong graph consistency.
     */
    public static final DomainProfile SECURE = new DomainProfile("SECURE",
                                                                 Map.of("sourceAuthority", 0.2, "extractionConfidence", 0.2, "graphConsistency", 0.4, "corroboration", 0.2),
                                                                 0.85, 0.50, 0.30);

    /**
     * Source-heavy weights, permissive thresholds.
     * Trusts DM-sourced propositions more readily.
     */
    public static final DomainProfile NARRATIVE = new DomainProfile("NARRATIVE",
                                                                    Map.of("sourceAuthority", 0.4, "extractionConfidence", 0.3, "graphConsistency", 0.15, "corroboration", 0.15),
                                                                    0.60, 0.35, 0.20);

    /**
     * Equal weights, moderate thresholds.
     * Default profile when no specific domain bias is needed.
     */
    public static final DomainProfile BALANCED = new DomainProfile("BALANCED",
                                                                   Map.of("sourceAuthority", 0.25, "extractionConfidence", 0.25, "graphConsistency", 0.25, "corroboration", 0.25),
                                                                   0.70, 0.40, 0.25);

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
            case "NARRATIVE" -> NARRATIVE;
            case "BALANCED" -> BALANCED;
            default -> BALANCED;
        };
    }
}
