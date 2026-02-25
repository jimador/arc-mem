package dev.dunnam.diceanchors.sim.report;

import dev.dunnam.diceanchors.sim.benchmark.BenchmarkReport;
import dev.dunnam.diceanchors.sim.benchmark.BenchmarkStatistics;
import dev.dunnam.diceanchors.sim.benchmark.EffectSizeEntry;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentReport;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScenarioLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link ResilienceReport} from a completed {@link ExperimentReport},
 * combining per-condition statistics, cross-condition effect sizes, per-fact
 * survival data, and narrative interpretation.
 * <p>
 * Invariant RR1: fully constructible from an ExperimentReport without
 * additional user input.
 */
@Service
public class ResilienceReportBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ResilienceReportBuilder.class);

    private final RunHistoryStore runHistoryStore;
    private final ScenarioLoader scenarioLoader;

    public ResilienceReportBuilder(RunHistoryStore runHistoryStore, ScenarioLoader scenarioLoader) {
        this.runHistoryStore = runHistoryStore;
        this.scenarioLoader = scenarioLoader;
    }

    /**
     * Build a complete resilience report from an experiment report.
     */
    public ResilienceReport build(ExperimentReport report) {
        logger.info("Building resilience report for experiment '{}'", report.experimentName());

        var scoresByCondition = ResilienceScoreCalculator.computeComparative(report);
        var overallScore = computeOverallScore(scoresByCondition);

        var factSurvivalByScenario = FactSurvivalLoader.loadFactSurvival(
                report, runHistoryStore, scenarioLoader);

        var contradictionsByScenario = ContradictionDetailLoader.loadContradictionDetails(
                report, runHistoryStore, scenarioLoader);

        var effectSizes = buildEffectSizes(report);

        var scenarioSections = new ArrayList<ScenarioSection>();
        for (var scenarioId : report.scenarioIds()) {
            var conditionSummaries = buildConditionSummaries(report, scenarioId);
            var factSurvivalRows = factSurvivalByScenario.getOrDefault(scenarioId, List.of());
            var contradictionDetails = contradictionsByScenario.getOrDefault(scenarioId, List.of());
            var scenarioTitle = resolveScenarioTitle(scenarioId);
            var narrative = generateNarrative(conditionSummaries, effectSizes, factSurvivalRows);

            scenarioSections.add(new ScenarioSection(
                    scenarioId,
                    scenarioTitle,
                    conditionSummaries,
                    effectSizes,
                    factSurvivalRows,
                    contradictionDetails,
                    narrative));
        }

        var strategySection = new StrategySection(report.strategyDeltas());

        var modelId = report.cellReports().values().stream()
                .map(BenchmarkReport::modelId)
                .filter(id -> id != null && !"default".equals(id))
                .findFirst()
                .orElse(null);

        return new ResilienceReport(
                "DICE Anchors Resilience Report",
                Instant.now(),
                report.experimentName(),
                report.conditions(),
                report.scenarioIds(),
                report.repetitionsPerCell(),
                report.cancelled(),
                overallScore,
                scoresByCondition,
                List.copyOf(scenarioSections),
                strategySection,
                null,
                modelId);
    }

    private ResilienceScore computeOverallScore(Map<String, ResilienceScore> scoresByCondition) {
        if (scoresByCondition.isEmpty()) {
            return new ResilienceScore(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        // Use FULL_ANCHORS as the primary intervention condition; fall back to best score
        var fullAnchorsScore = scoresByCondition.get("FULL_ANCHORS");
        if (fullAnchorsScore != null) {
            return fullAnchorsScore;
        }
        return scoresByCondition.values().stream()
                .max(java.util.Comparator.comparingDouble(ResilienceScore::overall))
                .orElse(new ResilienceScore(0.0, 0.0, 0.0, 0.0, 0.0));
    }

    private List<ConditionSummary> buildConditionSummaries(ExperimentReport report, String scenarioId) {
        var summaries = new ArrayList<ConditionSummary>();
        for (var condition : report.conditions()) {
            var cellKey = condition + ":" + scenarioId;
            var cellReport = report.cellReports().get(cellKey);
            if (cellReport != null) {
                summaries.add(new ConditionSummary(
                        condition,
                        cellReport.metricStatistics(),
                        cellReport.runCount()));
            } else {
                logger.debug("No cell report for key {}, skipping condition summary", cellKey);
            }
        }
        return List.copyOf(summaries);
    }

    private List<EffectSizeSummary> buildEffectSizes(ExperimentReport report) {
        var summaries = new ArrayList<EffectSizeSummary>();
        for (var pairEntry : report.effectSizeMatrix().entrySet()) {
            var pairKey = pairEntry.getKey();
            var colonIndex = pairKey.indexOf(':');
            if (colonIndex < 0) {
                logger.warn("Unexpected effect size matrix key format: {}", pairKey);
                continue;
            }
            var conditionA = pairKey.substring(0, colonIndex);
            var conditionB = pairKey.substring(colonIndex + 1);

            for (var metricEntry : pairEntry.getValue().entrySet()) {
                var metricKey = metricEntry.getKey();
                var entry = metricEntry.getValue();
                summaries.add(new EffectSizeSummary(
                        conditionA,
                        conditionB,
                        metricKey,
                        entry.cohensD(),
                        entry.interpretation(),
                        entry.lowConfidence()));
            }
        }
        return List.copyOf(summaries);
    }

    private String resolveScenarioTitle(String scenarioId) {
        try {
            var scenario = scenarioLoader.load(scenarioId);
            return scenario.title() != null ? scenario.title() : scenarioId;
        } catch (Exception e) {
            logger.debug("Could not resolve title for scenario {}: {}", scenarioId, e.getMessage());
            return scenarioId;
        }
    }

    String generateNarrative(
            List<ConditionSummary> conditionSummaries,
            List<EffectSizeSummary> effectSizes,
            List<FactSurvivalRow> factSurvivalRows) {

        var sb = new StringBuilder();

        if (conditionSummaries.size() < 2) {
            sb.append("**Provisional:** Single-condition run without ablation controls. ")
              .append("Comparative claims cannot be made.\n\n");
        }

        var totalDegradedRuns = countDegradedRuns(conditionSummaries);
        if (totalDegradedRuns > 0) {
            sb.append("**Provisional:** %d degraded conflict detection(s) occurred. ".formatted(totalDegradedRuns))
              .append("Contradiction counts may be unreliable.\n\n");
        }

        String bestCondition = null;
        var bestSurvival = -1.0;
        for (var summary : conditionSummaries) {
            var survivalStat = summary.metrics().get("factSurvivalRate");
            if (survivalStat != null && survivalStat.mean() > bestSurvival) {
                bestSurvival = survivalStat.mean();
                bestCondition = summary.conditionName();
            }
        }

        if (bestCondition != null) {
            sb.append("The '%s' condition achieved the highest fact survival rate (%.1f%%).".formatted(
                    bestCondition, bestSurvival));
        }

        for (var es : effectSizes) {
            if ("large".equals(es.interpretation()) || "medium".equals(es.interpretation())) {
                sb.append(" A %s effect size (d=%.2f) was observed between '%s' and '%s' on %s.".formatted(
                        es.interpretation(), es.cohensD(),
                        es.conditionA(), es.conditionB(), es.metricKey()));
                break;
            }
        }

        var contradictedFacts = new ArrayList<String>();
        for (var row : factSurvivalRows) {
            var universallyContradicted = true;
            for (var result : row.conditionResults().values()) {
                if (result.total() == 0 || result.survived() > 0) {
                    universallyContradicted = false;
                    break;
                }
            }
            if (universallyContradicted) {
                contradictedFacts.add(row.factId());
            }
        }
        if (!contradictedFacts.isEmpty()) {
            sb.append(" %d fact(s) were universally contradicted across all conditions: %s.".formatted(
                    contradictedFacts.size(), String.join(", ", contradictedFacts)));
        }

        for (var summary : conditionSummaries) {
            for (var metricEntry : summary.metrics().entrySet()) {
                if (metricEntry.getValue().isHighVariance()) {
                    sb.append(" Warning: high variance detected in '%s' for metric '%s' (CV > 0.5).".formatted(
                            summary.conditionName(), metricEntry.getKey()));
                    break;
                }
            }
        }

        if (sb.isEmpty()) {
            return "Insufficient data to generate a narrative for this scenario.";
        }

        return sb.toString();
    }

    private int countDegradedRuns(List<ConditionSummary> conditionSummaries) {
        var total = 0;
        for (var summary : conditionSummaries) {
            var stat = summary.metrics().get("degradedConflictCount");
            if (stat != null && stat.mean() > 0) {
                total += (int) Math.round(stat.mean() * stat.sampleCount());
            }
        }
        return total;
    }
}
