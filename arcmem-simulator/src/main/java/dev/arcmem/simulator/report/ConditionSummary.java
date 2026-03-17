package dev.arcmem.simulator.report;
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

import dev.arcmem.simulator.benchmark.BenchmarkStatistics;

import java.util.Map;

/**
 * Per-condition metric summary for a single scenario in a resilience report.
 *
 * @param conditionName the ablation condition name (e.g., "FULL_AWMU")
 * @param metrics       metric key to statistics (from the cell's BenchmarkReport)
 * @param runCount      number of runs aggregated for this condition-scenario pair
 */
public record ConditionSummary(
        String conditionName,
        Map<String, BenchmarkStatistics> metrics,
        int runCount) {
}
