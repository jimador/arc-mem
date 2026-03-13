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

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregated benchmark report capturing statistical results across multiple simulation runs.
 * <p>
 * {@code metricStatistics} is keyed by metric name (e.g., "factSurvivalRate", "driftAbsorptionRate").
 * {@code strategyStatistics} is keyed by strategy name (e.g., "SUBTLE_REFRAME").
 * {@code baselineDeltas} shows the difference between this report's means and the baseline's means.
 *
 * @param reportId           unique identifier (bench-{uuid})
 * @param scenarioId         scenario that was benchmarked
 * @param createdAt          when the benchmark was executed
 * @param runCount           number of simulation runs in the benchmark
 * @param totalDurationMs    wall-clock duration of the entire benchmark
 * @param metricStatistics   per-metric descriptive statistics
 * @param strategyStatistics per-strategy effectiveness statistics
 * @param runIds             references to individual simulation run records
 * @param baselineReportId   ID of the baseline report used for comparison; null if none
 * @param baselineDeltas     metric-name-to-delta map vs baseline; null if no baseline
 */
public record BenchmarkReport(
        String reportId,
        String scenarioId,
        Instant createdAt,
        int runCount,
        long totalDurationMs,
        Map<String, BenchmarkStatistics> metricStatistics,
        Map<String, BenchmarkStatistics> strategyStatistics,
        List<String> runIds,
        @Nullable String baselineReportId,
        @Nullable Map<String, Double> baselineDeltas,
        @Nullable String modelId
) {
    public BenchmarkReport(
            String reportId, String scenarioId, Instant createdAt, int runCount,
            long totalDurationMs, Map<String, BenchmarkStatistics> metricStatistics,
            Map<String, BenchmarkStatistics> strategyStatistics, List<String> runIds,
            @Nullable String baselineReportId, @Nullable Map<String, Double> baselineDeltas) {
        this(reportId, scenarioId, createdAt, runCount, totalDurationMs, metricStatistics,
             strategyStatistics, runIds, baselineReportId, baselineDeltas, null);
    }
}
