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

/**
 * Effect size between two conditions for a single metric.
 *
 * @param conditionA     first condition name
 * @param conditionB     second condition name
 * @param metricKey      the metric being compared
 * @param cohensD        Cohen's d effect size value
 * @param interpretation "negligible", "small", "medium", or "large"
 * @param lowConfidence  true if sample count is low or variance is high
 */
public record EffectSizeSummary(
        String conditionA,
        String conditionB,
        String metricKey,
        double cohensD,
        String interpretation,
        boolean lowConfidence) {
}
