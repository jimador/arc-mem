package dev.arcmem.simulator.benchmark;
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

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Computes cross-condition statistical comparisons for ablation experiments.
 * <p>
 * Stateless service computing:
 * <ul>
 *   <li>Cohen's d effect sizes between condition pairs using sample stddev (N-1, Bessel's correction)</li>
 *   <li>95% confidence intervals per metric per cell</li>
 *   <li>Per-strategy effectiveness deltas across conditions</li>
 * </ul>
 * <p>
 * Note: {@link BenchmarkStatistics#stddev()} uses population stddev (N denominator).
 * This calculator derives sample stddev via: {@code sampleStddev = popStddev × √(n/(n-1))}.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>CS1</strong>: Effect size is symmetric in magnitude: |d(A,B)| == |d(B,A)|</li>
 *   <li><strong>CS2</strong>: Matrix contains exactly N*(N-1)/2 entries for N conditions</li>
 *   <li><strong>CS3</strong>: All statistics use sample stddev (N-1 denominator)</li>
 * </ul>
 */
@Service
public class EffectSizeCalculator {

    private static final List<String> METRICS = List.of(
            "factSurvivalRate", "contradictionCount", "majorContradictionCount",
            "driftAbsorptionRate", "meanTurnsToFirstDrift", "unitAttributionCount"
    );

    /**
     * Computes Cohen's d effect size between every pair of conditions for each scoring metric.
     * <p>
     * Cell reports are keyed by "conditionName:scenarioId". For each condition pair,
     * metrics are aggregated across all scenarios that both conditions share.
     *
     * @param cellReports cell-level BenchmarkReports keyed by "conditionName:scenarioId"
     * @param conditions  the ablation conditions tested
     *
     * @return effect size matrix keyed by "condA:condB" (alphabetical order) → metric name → entry
     */
    public Map<String, Map<String, EffectSizeEntry>> computeEffectSizes(
            Map<String, BenchmarkReport> cellReports,
            List<AblationCondition> conditions) {

        var conditionNames = conditions.stream().map(AblationCondition::name).sorted().toList();
        var matrix = new LinkedHashMap<String, Map<String, EffectSizeEntry>>();

        for (int i = 0; i < conditionNames.size(); i++) {
            for (int j = i + 1; j < conditionNames.size(); j++) {
                var condA = conditionNames.get(i);
                var condB = conditionNames.get(j);
                var pairKey = condA + ":" + condB;

                var metricEntries = new LinkedHashMap<String, EffectSizeEntry>();
                for (var metric : METRICS) {
                    var statsA = collectMetricStats(cellReports, condA, metric);
                    var statsB = collectMetricStats(cellReports, condB, metric);

                    if (statsA.isEmpty() || statsB.isEmpty()) {
                        continue;
                    }

                    var meanA = weightedMean(statsA);
                    var meanB = weightedMean(statsB);
                    var sampleSdA = weightedSampleStddev(statsA);
                    var sampleSdB = weightedSampleStddev(statsB);
                    var nA = totalSampleCount(statsA);
                    var nB = totalSampleCount(statsB);

                    if (Double.isNaN(meanA) || Double.isNaN(meanB)) {
                        continue;
                    }

                    var d = computeCohensD(meanA, sampleSdA, nA, meanB, sampleSdB, nB);
                    var interpretation = EffectSizeEntry.interpret(d);
                    var lowConfidence = nA < 10 || nB < 10
                                        || statsA.stream().anyMatch(BenchmarkStatistics::isHighVariance)
                                        || statsB.stream().anyMatch(BenchmarkStatistics::isHighVariance);

                    metricEntries.put(metric, new EffectSizeEntry(d, interpretation, lowConfidence));
                }

                matrix.put(pairKey, Map.copyOf(metricEntries));
            }
        }

        return Map.copyOf(matrix);
    }

    /**
     * Computes 95% confidence intervals for each metric in each cell.
     *
     * @param cellReports cell-level BenchmarkReports keyed by "conditionName:scenarioId"
     *
     * @return map of cell key → metric name → ConfidenceInterval
     */
    public Map<String, Map<String, ConfidenceInterval>> computeConfidenceIntervals(
            Map<String, BenchmarkReport> cellReports) {

        var result = new LinkedHashMap<String, Map<String, ConfidenceInterval>>();

        for (var entry : cellReports.entrySet()) {
            var cellKey = entry.getKey();
            var report = entry.getValue();
            var intervals = new LinkedHashMap<String, ConfidenceInterval>();

            for (var metric : METRICS) {
                var stats = report.metricStatistics().get(metric);
                if (stats != null && !Double.isNaN(stats.mean())) {
                    var sampleSd = toSampleStddev(stats.stddev(), stats.sampleCount());
                    intervals.put(metric, ConfidenceInterval.of(stats.mean(), sampleSd, stats.sampleCount()));
                }
            }

            result.put(cellKey, Map.copyOf(intervals));
        }

        return Map.copyOf(result);
    }

    /**
     * Computes per-strategy effectiveness deltas across conditions.
     * For each strategy, extracts the mean effectiveness from each cell's strategyStatistics.
     *
     * @param cellReports cell-level BenchmarkReports keyed by "conditionName:scenarioId"
     *
     * @return map of strategy name → condition name → mean effectiveness
     */
    public Map<String, Map<String, Double>> computeStrategyDeltas(
            Map<String, BenchmarkReport> cellReports) {

        var allStrategies = new TreeSet<String>();
        for (var report : cellReports.values()) {
            allStrategies.addAll(report.strategyStatistics().keySet());
        }

        var result = new LinkedHashMap<String, Map<String, Double>>();

        for (var strategy : allStrategies) {
            var conditionMeans = new LinkedHashMap<String, Double>();

            // Group cells by condition (extract condition name from cell key "condition:scenario")
            var conditionValues = new LinkedHashMap<String, List<Double>>();
            for (var entry : cellReports.entrySet()) {
                var conditionName = entry.getKey().split(":")[0];
                var stats = entry.getValue().strategyStatistics().get(strategy);
                if (stats != null && !Double.isNaN(stats.mean())) {
                    conditionValues.computeIfAbsent(conditionName, k -> new ArrayList<>())
                                   .add(stats.mean());
                }
            }

            for (var condEntry : conditionValues.entrySet()) {
                var values = condEntry.getValue();
                var mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                conditionMeans.put(condEntry.getKey(), mean);
            }

            result.put(strategy, Map.copyOf(conditionMeans));
        }

        return Map.copyOf(result);
    }

    /**
     * Computes Cohen's d between two groups.
     * Uses pooled standard deviation with Bessel's correction (N-1).
     * Returns 0.0 when pooled SD is 0 (avoids Infinity/NaN).
     */
    double computeCohensD(double mean1, double sd1, int n1, double mean2, double sd2, int n2) {
        var pooledVariance = ((n1 - 1) * sd1 * sd1 + (n2 - 1) * sd2 * sd2)
                             / (n1 + n2 - 2);
        var pooledSd = Math.sqrt(pooledVariance);

        if (pooledSd == 0.0) {
            return 0.0;
        }

        return (mean1 - mean2) / pooledSd;
    }

    /**
     * Converts population stddev (N denominator) to sample stddev (N-1 denominator).
     * Formula: sampleStddev = popStddev × √(n / (n-1))
     */
    static double toSampleStddev(double popStddev, int n) {
        if (n < 2 || Double.isNaN(popStddev)) {
            return popStddev;
        }
        return popStddev * Math.sqrt((double) n / (n - 1));
    }

    private List<BenchmarkStatistics> collectMetricStats(
            Map<String, BenchmarkReport> cellReports, String conditionName, String metric) {
        var stats = new ArrayList<BenchmarkStatistics>();
        for (var entry : cellReports.entrySet()) {
            if (entry.getKey().startsWith(conditionName + ":")) {
                var metricStats = entry.getValue().metricStatistics().get(metric);
                if (metricStats != null && metricStats.sampleCount() > 0 && !Double.isNaN(metricStats.mean())) {
                    stats.add(metricStats);
                }
            }
        }
        return stats;
    }

    private double weightedMean(List<BenchmarkStatistics> stats) {
        var totalWeight = stats.stream().mapToInt(BenchmarkStatistics::sampleCount).sum();
        if (totalWeight == 0) {
            return Double.NaN;
        }
        return stats.stream()
                    .mapToDouble(s -> s.mean() * s.sampleCount())
                    .sum() / totalWeight;
    }

    private double weightedSampleStddev(List<BenchmarkStatistics> stats) {
        if (stats.size() == 1) {
            return toSampleStddev(stats.getFirst().stddev(), stats.getFirst().sampleCount());
        }
        // Pool across multiple cells: convert each to sample variance, pool
        var totalN = totalSampleCount(stats);
        if (totalN < 2) {
            return 0.0;
        }
        var pooledVariance = stats.stream()
                                  .mapToDouble(s -> {
                                      var sampleSd = toSampleStddev(s.stddev(), s.sampleCount());
                                      return (s.sampleCount() - 1) * sampleSd * sampleSd;
                                  })
                                  .sum() / (totalN - stats.size());
        return Math.sqrt(Math.max(0, pooledVariance));
    }

    private int totalSampleCount(List<BenchmarkStatistics> stats) {
        return stats.stream().mapToInt(BenchmarkStatistics::sampleCount).sum();
    }
}
