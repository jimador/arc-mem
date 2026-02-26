package dev.dunnam.diceanchors.sim.engine;

import java.util.Map;

/**
 * Aggregate scoring metrics for a completed simulation run.
 * All rate fields are percentages in [0, 100].
 *
 * @param factSurvivalRate        percentage of ground truth facts never contradicted
 * @param contradictionCount      total CONTRADICTED verdicts across all turns
 * @param majorContradictionCount total MAJOR severity contradictions
 * @param driftAbsorptionRate     percentage of evaluated turns with zero contradictions
 * @param meanTurnsToFirstDrift   average turn number of first contradiction per fact (NaN if none)
 * @param anchorAttributionCount  number of facts with at least one matching injected anchor
 * @param strategyEffectiveness   contradiction rate per attack strategy
 * @param degradedConflictCount   conflicts where detection quality was DEGRADED (ACON1);
 *                                non-zero means conflict results are unreliable for this run
 */
public record ScoringResult(
        double factSurvivalRate,
        int contradictionCount,
        int majorContradictionCount,
        double driftAbsorptionRate,
        double meanTurnsToFirstDrift,
        int anchorAttributionCount,
        Map<String, Double> strategyEffectiveness,
        int degradedConflictCount
) {
    /**
     * Backward-compatible constructor that defaults degradedConflictCount to 0.
     */
    public ScoringResult(double factSurvivalRate, int contradictionCount,
                         int majorContradictionCount, double driftAbsorptionRate,
                         double meanTurnsToFirstDrift, int anchorAttributionCount,
                         Map<String, Double> strategyEffectiveness) {
        this(factSurvivalRate, contradictionCount, majorContradictionCount,
             driftAbsorptionRate, meanTurnsToFirstDrift, anchorAttributionCount,
             strategyEffectiveness, 0);
    }
}
