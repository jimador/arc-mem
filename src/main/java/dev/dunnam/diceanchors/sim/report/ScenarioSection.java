package dev.dunnam.diceanchors.sim.report;

import java.util.List;

/**
 * Per-scenario section of a resilience report containing condition summaries,
 * effect sizes, per-fact survival, contradiction details, and a narrative interpretation.
 *
 * @param scenarioId             scenario identifier
 * @param scenarioTitle          human-readable scenario title
 * @param conditionSummaries     per-condition metric summaries
 * @param effectSizes            pairwise effect size entries for this scenario
 * @param factSurvivalTable      per-fact survival across conditions
 * @param contradictionDetails   per-fact contradiction events with turn context
 * @param narrative              plain-English interpretation of results
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
