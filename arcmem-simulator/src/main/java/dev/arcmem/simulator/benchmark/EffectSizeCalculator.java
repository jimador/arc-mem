package dev.arcmem.simulator.benchmark;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
            "driftAbsorptionRate", "meanTurnsToFirstDrift", "unitAttributionCount",
            "erosionRate", "complianceRate"
    );

    private final StatisticalTestRunner statisticalTestRunner;

    public EffectSizeCalculator(StatisticalTestRunner statisticalTestRunner) {
        this.statisticalTestRunner = statisticalTestRunner;
    }

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

        return applyHypothesisTests(matrix, cellReports);
    }

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

    public Map<String, Map<String, Double>> computeStrategyDeltas(
            Map<String, BenchmarkReport> cellReports) {

        var allStrategies = new TreeSet<String>();
        for (var report : cellReports.values()) {
            allStrategies.addAll(report.strategyStatistics().keySet());
        }

        var result = new LinkedHashMap<String, Map<String, Double>>();

        for (var strategy : allStrategies) {
            var conditionMeans = new LinkedHashMap<String, Double>();

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

    double computeCohensD(double mean1, double sd1, int n1, double mean2, double sd2, int n2) {
        var pooledVariance = ((n1 - 1) * sd1 * sd1 + (n2 - 1) * sd2 * sd2)
                             / (n1 + n2 - 2);
        var pooledSd = Math.sqrt(pooledVariance);

        if (pooledSd == 0.0) {
            return 0.0;
        }

        return (mean1 - mean2) / pooledSd;
    }

    static double toSampleStddev(double popStddev, int n) {
        if (n < 2 || Double.isNaN(popStddev)) {
            return popStddev;
        }
        return popStddev * Math.sqrt((double) n / (n - 1));
    }

    private Map<String, Map<String, EffectSizeEntry>> applyHypothesisTests(
            Map<String, Map<String, EffectSizeEntry>> matrix,
            Map<String, BenchmarkReport> cellReports) {

        record EntryRef(String pairKey, String metric, EffectSizeEntry entry) {}
        var refs = new ArrayList<EntryRef>();
        var rawPValues = new ArrayList<Double>();

        for (var pairEntry : matrix.entrySet()) {
            var pair = pairEntry.getKey().split(":", 2);
            var condA = pair[0];
            var condB = pair[1];

            for (var metricEntry : pairEntry.getValue().entrySet()) {
                var valuesA = collectRawMetricValues(cellReports, condA, metricEntry.getKey());
                var valuesB = collectRawMetricValues(cellReports, condB, metricEntry.getKey());
                var p = statisticalTestRunner.mannWhitneyU(
                        valuesA.stream().mapToDouble(Double::doubleValue).toArray(),
                        valuesB.stream().mapToDouble(Double::doubleValue).toArray());
                refs.add(new EntryRef(pairEntry.getKey(), metricEntry.getKey(), metricEntry.getValue()));
                rawPValues.add(Double.isNaN(p) ? Double.NaN : p);
            }
        }

        if (refs.isEmpty()) {
            return Map.copyOf(matrix);
        }

        var pArray = rawPValues.stream().mapToDouble(Double::doubleValue).toArray();
        var corrected = statisticalTestRunner.benjaminiHochberg(pArray);

        var result = new LinkedHashMap<String, Map<String, EffectSizeEntry>>();
        for (int i = 0; i < refs.size(); i++) {
            var ref = refs.get(i);
            var correctedP = corrected[i];
            var sig = StatisticalTestRunner.significanceLabel(correctedP);
            result.computeIfAbsent(ref.pairKey(), k -> new LinkedHashMap<>())
                  .put(ref.metric(), new EffectSizeEntry(
                          ref.entry().cohensD(), ref.entry().interpretation(),
                          ref.entry().lowConfidence(), correctedP, sig));
        }

        return Map.copyOf(result.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Map.copyOf(e.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new)));
    }

    private List<Double> collectRawMetricValues(
            Map<String, BenchmarkReport> cellReports, String conditionName, String metric) {
        var values = new ArrayList<Double>();
        for (var entry : cellReports.entrySet()) {
            if (entry.getKey().startsWith(conditionName + ":")) {
                var stats = entry.getValue().metricStatistics().get(metric);
                if (stats != null && !Double.isNaN(stats.mean())) {
                    for (int i = 0; i < stats.sampleCount(); i++) {
                        values.add(stats.mean());
                    }
                }
            }
        }
        return values;
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
