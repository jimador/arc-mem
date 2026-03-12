package dev.dunnam.diceanchors.sim.report;

import dev.dunnam.diceanchors.sim.benchmark.BenchmarkStatistics;

import java.util.Map;

/**
 * Per-condition metric summary for a single scenario in a resilience report.
 *
 * @param conditionName the ablation condition name (e.g., "FULL_ANCHORS")
 * @param metrics       metric key to statistics (from the cell's BenchmarkReport)
 * @param runCount      number of runs aggregated for this condition-scenario pair
 */
public record ConditionSummary(
        String conditionName,
        Map<String, BenchmarkStatistics> metrics,
        int runCount) {
}
