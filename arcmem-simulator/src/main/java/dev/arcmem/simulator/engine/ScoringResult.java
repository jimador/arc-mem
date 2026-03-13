package dev.arcmem.simulator.engine;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

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
        double complianceRate
) {
    /**
     * Backward-compatible constructor that defaults degradedConflictCount to 0 and complianceRate to 0.0.
     */
    public ScoringResult(double factSurvivalRate, int contradictionCount,
                         int majorContradictionCount, double driftAbsorptionRate,
                         double meanTurnsToFirstDrift, int unitAttributionCount,
                         Map<String, Double> strategyEffectiveness) {
        this(factSurvivalRate, contradictionCount, majorContradictionCount,
             driftAbsorptionRate, meanTurnsToFirstDrift, unitAttributionCount,
             strategyEffectiveness, 0, 0.0);
    }

    /**
     * Backward-compatible constructor that defaults complianceRate to 0.0.
     */
    public ScoringResult(double factSurvivalRate, int contradictionCount,
                         int majorContradictionCount, double driftAbsorptionRate,
                         double meanTurnsToFirstDrift, int unitAttributionCount,
                         Map<String, Double> strategyEffectiveness, int degradedConflictCount) {
        this(factSurvivalRate, contradictionCount, majorContradictionCount,
             driftAbsorptionRate, meanTurnsToFirstDrift, unitAttributionCount,
             strategyEffectiveness, degradedConflictCount, 0.0);
    }
}
