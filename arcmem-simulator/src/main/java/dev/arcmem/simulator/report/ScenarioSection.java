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

import java.util.List;

/**
 * Per-scenario section of a resilience report containing condition summaries,
 * effect sizes, per-fact survival, contradiction details, and a narrative interpretation.
 *
 * @param scenarioId           scenario identifier
 * @param scenarioTitle        human-readable scenario title
 * @param conditionSummaries   per-condition metric summaries
 * @param effectSizes          pairwise effect size entries for this scenario
 * @param factSurvivalTable    per-fact survival across conditions
 * @param contradictionDetails per-fact contradiction events with turn context
 * @param narrative            plain-English interpretation of results
 */
public record ScenarioSection(
        String scenarioId,
        String scenarioTitle,
        List<ConditionSummary> conditionSummaries,
        List<EffectSizeSummary> effectSizes,
        List<FactSurvivalRow> factSurvivalTable,
        List<FactContradictionGroup> contradictionDetails,
        String narrative) {
}
