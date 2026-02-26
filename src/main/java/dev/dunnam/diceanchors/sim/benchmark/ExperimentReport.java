package dev.dunnam.diceanchors.sim.benchmark;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Full experiment report containing cell-level results, cross-condition effect sizes,
 * per-strategy effectiveness deltas, and experiment metadata.
 *
 * @param reportId            unique identifier (exp-{uuid})
 * @param experimentName      human-readable experiment name
 * @param createdAt           when the experiment completed
 * @param conditions          names of all conditions tested
 * @param scenarioIds         IDs of all scenarios tested
 * @param repetitionsPerCell  runs per condition-scenario cell
 * @param totalDurationMs     wall-clock duration of the entire experiment
 * @param cellReports         per-cell BenchmarkReport keyed by "conditionName:scenarioId"
 * @param effectSizeMatrix    Cohen's d per metric between condition pairs, keyed by "condA:condB" (alphabetical)
 * @param confidenceIntervals 95% CIs per metric per cell, keyed by cell key then metric name
 * @param strategyDeltas      per-strategy effectiveness means keyed by strategy name then condition name
 * @param cancelled           true if the experiment was cancelled before completion
 */
public record ExperimentReport(
        String reportId,
        String experimentName,
        Instant createdAt,
        List<String> conditions,
        List<String> scenarioIds,
        int repetitionsPerCell,
        long totalDurationMs,
        Map<String, BenchmarkReport> cellReports,
        Map<String, Map<String, EffectSizeEntry>> effectSizeMatrix,
        Map<String, Map<String, ConfidenceInterval>> confidenceIntervals,
        Map<String, Map<String, Double>> strategyDeltas,
        boolean cancelled
) {
    public ExperimentReport {
        conditions = List.copyOf(conditions);
        scenarioIds = List.copyOf(scenarioIds);
        cellReports = Map.copyOf(cellReports);
        effectSizeMatrix = Map.copyOf(effectSizeMatrix);
        confidenceIntervals = Map.copyOf(confidenceIntervals);
        strategyDeltas = Map.copyOf(strategyDeltas);
    }
}
