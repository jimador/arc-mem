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

import dev.arcmem.simulator.engine.ScoringResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Aggregates multiple {@link ScoringResult} instances into a {@link BenchmarkReport}
 * with descriptive statistics per metric and per strategy.
 */
@Service
public class BenchmarkAggregator {

    /**
     * Aggregates scoring results from multiple simulation runs into a single benchmark report.
     *
     * @param results    scoring results from each run (must not be empty)
     * @param scenarioId scenario that was benchmarked
     * @param runIds     identifiers for each individual run
     * @param durationMs wall-clock duration of the entire benchmark
     *
     * @return aggregated benchmark report with per-metric and per-strategy statistics
     *
     * @throws IllegalArgumentException if results is empty
     */
    public BenchmarkReport aggregate(List<ScoringResult> results, String scenarioId,
                                     List<String> runIds, long durationMs) {
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Cannot aggregate empty results list");
        }

        var metricStats = new LinkedHashMap<String, BenchmarkStatistics>();
        metricStats.put("factSurvivalRate",
                        computeStats(results.stream().mapToDouble(ScoringResult::factSurvivalRate).toArray()));
        metricStats.put("contradictionCount",
                        computeStats(results.stream().mapToDouble(ScoringResult::contradictionCount).toArray()));
        metricStats.put("majorContradictionCount",
                        computeStats(results.stream().mapToDouble(ScoringResult::majorContradictionCount).toArray()));
        metricStats.put("driftAbsorptionRate",
                        computeStats(results.stream().mapToDouble(ScoringResult::driftAbsorptionRate).toArray()));
        metricStats.put("unitAttributionCount",
                        computeStats(results.stream().mapToDouble(ScoringResult::unitAttributionCount).toArray()));
        metricStats.put("degradedConflictCount",
                        computeStats(results.stream().mapToDouble(ScoringResult::degradedConflictCount).toArray()));

        // meanTurnsToFirstDrift: filter NaN values
        var mtfd = results.stream()
                          .mapToDouble(ScoringResult::meanTurnsToFirstDrift)
                          .filter(d -> !Double.isNaN(d))
                          .toArray();
        metricStats.put("meanTurnsToFirstDrift", mtfd.length > 0
                ? computeStats(mtfd)
                : new BenchmarkStatistics(
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, 0));

        var allStrategies = new TreeSet<String>();
        for (var r : results) {
            allStrategies.addAll(r.strategyEffectiveness().keySet());
        }
        var strategyStats = new LinkedHashMap<String, BenchmarkStatistics>();
        for (var strategy : allStrategies) {
            var values = results.stream()
                                .mapToDouble(r -> r.strategyEffectiveness().getOrDefault(strategy, 0.0))
                                .toArray();
            strategyStats.put(strategy, computeStats(values));
        }

        var reportId = "bench-" + UUID.randomUUID().toString().substring(0, 8);
        return new BenchmarkReport(
                reportId, scenarioId, Instant.now(), results.size(), durationMs,
                Map.copyOf(metricStats), Map.copyOf(strategyStats),
                List.copyOf(runIds), null, null);
    }

    /**
     * Computes per-metric deltas between two benchmark reports (current - baseline).
     *
     * @param current  the report to compare
     * @param baseline the reference report
     *
     * @return map of metric name to delta (current mean - baseline mean)
     */
    public Map<String, Double> computeDeltas(BenchmarkReport current, BenchmarkReport baseline) {
        var deltas = new LinkedHashMap<String, Double>();
        for (var entry : current.metricStatistics().entrySet()) {
            var baselineStats = baseline.metricStatistics().get(entry.getKey());
            if (baselineStats != null) {
                deltas.put(entry.getKey(), entry.getValue().mean() - baselineStats.mean());
            }
        }
        return Map.copyOf(deltas);
    }

    /**
     * Computes descriptive statistics for a sorted copy of the given values.
     * Uses population standard deviation (N, not N-1).
     * P95 uses linear interpolation at index 0.95 * (N-1).
     */
    BenchmarkStatistics computeStats(double[] values) {
        var sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        var n = sorted.length;

        var mean = Arrays.stream(sorted).average().orElse(0.0);
        var variance = Arrays.stream(sorted).map(v -> (v - mean) * (v - mean)).sum() / n;
        var stddev = Math.sqrt(variance);
        var min = sorted[0];
        var max = sorted[n - 1];

        double median;
        if (n % 2 == 0) {
            median = (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        } else {
            median = sorted[n / 2];
        }

        // P95 with linear interpolation
        var index = 0.95 * (n - 1);
        var lower = (int) Math.floor(index);
        var upper = Math.min(lower + 1, n - 1);
        var fraction = index - lower;
        var p95 = sorted[lower] + fraction * (sorted[upper] - sorted[lower]);

        return new BenchmarkStatistics(mean, stddev, min, max, median, p95, n);
    }
}
