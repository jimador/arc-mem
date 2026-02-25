package dev.dunnam.diceanchors.sim.report;

import dev.dunnam.diceanchors.sim.benchmark.BenchmarkReport;
import dev.dunnam.diceanchors.sim.benchmark.BenchmarkStatistics;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computes composite {@link ResilienceScore} from experiment results.
 * <p>
 * Component weights:
 * <ul>
 *   <li>survival: 0.40</li>
 *   <li>drift resistance: 0.25</li>
 *   <li>contradiction penalty: 0.20</li>
 *   <li>strategy resistance: 0.15</li>
 * </ul>
 */
public final class ResilienceScoreCalculator {

    private static final Logger logger = LoggerFactory.getLogger(ResilienceScoreCalculator.class);

    private static final double WEIGHT_SURVIVAL = 0.40;
    private static final double WEIGHT_DRIFT = 0.25;
    private static final double WEIGHT_CONTRADICTION = 0.20;
    private static final double WEIGHT_STRATEGY = 0.15;

    private ResilienceScoreCalculator() {
        // utility class
    }

    /**
     * Compute a single {@link ResilienceScore} for a given condition across all scenarios.
     */
    public static ResilienceScore compute(ExperimentReport report, String referenceCondition) {
        var survivalValues = new ArrayList<Double>();
        var driftValues = new ArrayList<Double>();
        var contradictionCounts = new ArrayList<Double>();

        for (var scenarioId : report.scenarioIds()) {
            var cellKey = referenceCondition + ":" + scenarioId;
            var cell = report.cellReports().get(cellKey);
            if (cell == null) {
                logger.debug("No cell report for key {}, skipping", cellKey);
                continue;
            }

            var survivalStat = cell.metricStatistics().get("factSurvivalRate");
            survivalValues.add(survivalStat != null ? survivalStat.mean() : 0.0);

            var driftStat = cell.metricStatistics().get("driftAbsorptionRate");
            driftValues.add(driftStat != null ? driftStat.mean() : 0.0);

            var contradictionStat = cell.metricStatistics().get("contradictionCount");
            contradictionCounts.add(contradictionStat != null ? contradictionStat.mean() : 0.0);
        }

        var survivalComponent = clamp(mean(survivalValues, 0.0));
        var driftResistanceComponent = clamp(mean(driftValues, 0.0));

        var meanContradictions = mean(contradictionCounts, 0.0);
        var contradictionPenalty = clamp(Math.max(0.0, 100.0 - (meanContradictions * 20.0)));

        var strategyResistanceComponent = computeStrategyResistance(report, referenceCondition);

        var overall = clamp(
                survivalComponent * WEIGHT_SURVIVAL
                + driftResistanceComponent * WEIGHT_DRIFT
                + contradictionPenalty * WEIGHT_CONTRADICTION
                + strategyResistanceComponent * WEIGHT_STRATEGY
        );

        logger.debug("Resilience score for condition '{}': overall={}, survival={}, drift={}, contradiction={}, strategy={}",
                referenceCondition, overall, survivalComponent, driftResistanceComponent,
                contradictionPenalty, strategyResistanceComponent);

        return new ResilienceScore(
                overall,
                survivalComponent,
                driftResistanceComponent,
                contradictionPenalty,
                strategyResistanceComponent
        );
    }

    /**
     * Compute resilience scores for all conditions in the experiment.
     */
    public static Map<String, ResilienceScore> computeComparative(ExperimentReport report) {
        var results = new LinkedHashMap<String, ResilienceScore>();
        for (var condition : report.conditions()) {
            results.put(condition, compute(report, condition));
        }
        return Map.copyOf(results);
    }

    private static double computeStrategyResistance(ExperimentReport report, String condition) {
        var strategyDeltas = report.strategyDeltas();
        if (strategyDeltas.isEmpty()) {
            return clamp(100.0);
        }

        var effectivenessValues = new ArrayList<Double>();
        for (var entry : strategyDeltas.entrySet()) {
            var conditionEffectiveness = entry.getValue().get(condition);
            if (conditionEffectiveness != null) {
                effectivenessValues.add(conditionEffectiveness);
            }
        }

        var meanEffectiveness = mean(effectivenessValues, 0.0);
        return clamp(Math.max(0.0, 100.0 - (meanEffectiveness * 100.0)));
    }

    private static double mean(ArrayList<Double> values, double defaultValue) {
        if (values.isEmpty()) {
            return defaultValue;
        }
        var sum = 0.0;
        for (var v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
