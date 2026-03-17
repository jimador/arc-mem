package dev.arcmem.simulator.engine;

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
 * @param unitAttributionCount  number of facts with at least one matching injected unit
 * @param strategyEffectiveness   contradiction rate per attack strategy
 * @param degradedConflictCount   conflicts where detection quality was DEGRADED (ACON1);
 *                                non-zero means conflict results are unreliable for this run
 * @param complianceRate          percentage of evaluated turns that were constraint-respecting
 *                                (no contradictions); mirrors driftAbsorptionRate for
 *                                enforcement-strategy A/B comparison
 * @param erosionRate             percentage of repeatedly-attacked facts that eventually eroded
 *                                (contradicted on 2+ turns)
 */
public record ScoringResult(
        double factSurvivalRate,
        int contradictionCount,
        int majorContradictionCount,
        double driftAbsorptionRate,
        double meanTurnsToFirstDrift,
        int unitAttributionCount,
        Map<String, Double> strategyEffectiveness,
        int degradedConflictCount,
        double complianceRate,
        double erosionRate
) {
    public ScoringResult(double factSurvivalRate, int contradictionCount,
                         int majorContradictionCount, double driftAbsorptionRate,
                         double meanTurnsToFirstDrift, int unitAttributionCount,
                         Map<String, Double> strategyEffectiveness) {
        this(factSurvivalRate, contradictionCount, majorContradictionCount,
             driftAbsorptionRate, meanTurnsToFirstDrift, unitAttributionCount,
             strategyEffectiveness, 0, 0.0, 0.0);
    }

    public ScoringResult(double factSurvivalRate, int contradictionCount,
                         int majorContradictionCount, double driftAbsorptionRate,
                         double meanTurnsToFirstDrift, int unitAttributionCount,
                         Map<String, Double> strategyEffectiveness, int degradedConflictCount) {
        this(factSurvivalRate, contradictionCount, majorContradictionCount,
             driftAbsorptionRate, meanTurnsToFirstDrift, unitAttributionCount,
             strategyEffectiveness, degradedConflictCount, 0.0, 0.0);
    }
}
