package dev.dunnam.diceanchors.sim.report;

import dev.dunnam.diceanchors.sim.benchmark.BenchmarkStatistics;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import dev.dunnam.diceanchors.sim.engine.AttackStrategy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a {@link ResilienceReport} as a complete Markdown document.
 * <p>
 * Invariant RR2: pure function — no side effects, no I/O, no state mutation.
 */
public final class MarkdownReportRenderer {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private static final int FACT_TEXT_MAX_LENGTH = 60;

    private MarkdownReportRenderer() {
    }

    /**
     * Renders the given resilience report as a Markdown document string.
     */
    public static String render(ResilienceReport report) {
        var sb = new StringBuilder();
        var timestamp = TIMESTAMP_FORMAT.format(report.generatedAt());

        renderHeader(sb, report, timestamp);
        renderResilienceScore(sb, report);
        renderScenarioSections(sb, report.scenarioSections(), report.conditions());
        renderStrategyEffectiveness(sb, report.strategySection(), report.conditions());
        renderPositioning(sb, report.positioning());
        renderFooter(sb, timestamp);

        return sb.toString();
    }

    private static void renderHeader(StringBuilder sb, ResilienceReport report, String timestamp) {
        sb.append("# ").append(report.title()).append("\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| Experiment | ").append(report.experimentName()).append(" |\n");
        sb.append("| Generated | ").append(timestamp).append(" |\n");
        sb.append("| Conditions | ").append(String.join(", ", report.conditions())).append(" |\n");
        sb.append("| Scenarios | ").append(String.join(", ", report.scenarioIds())).append(" |\n");
        sb.append("| Repetitions | ").append(report.repetitionsPerCell()).append(" per cell |\n");
        if (report.modelId() != null) {
            sb.append("| Model | ").append(report.modelId()).append(" |\n");
        }
        sb.append("\n");

        if (report.cancelled()) {
            sb.append("> **Note:** This experiment was cancelled. Results are partial.\n\n");
        }
    }

    private static void renderResilienceScore(StringBuilder sb, ResilienceReport report) {
        sb.append("## Resilience Score\n\n");

        var score = report.overallScore();
        sb.append("**Overall: ").append("%.2f".formatted(score.overall()))
                .append("/100 — ").append(score.interpretation()).append("**\n\n");
        sb.append("| Component | Score |\n");
        sb.append("|-----------|-------|\n");
        sb.append("| Fact Survival (40%) | ").append("%.2f".formatted(score.survivalComponent())).append(" |\n");
        sb.append("| Drift Resistance (25%) | ").append("%.2f".formatted(score.driftResistanceComponent())).append(" |\n");
        sb.append("| Contradiction Penalty (20%) | ").append("%.2f".formatted(score.contradictionPenalty())).append(" |\n");
        sb.append("| Strategy Resistance (15%) | ").append("%.2f".formatted(score.strategyResistanceComponent())).append(" |\n");
        sb.append("\n");

        if (report.conditionScores() != null && report.conditionScores().size() > 1) {
            sb.append("### Per-Condition Scores\n\n");
            sb.append("| Condition | Overall | Survival | Drift | Contradiction | Strategy |\n");
            sb.append("|-----------|---------|----------|-------|---------------|----------|\n");
            for (var entry : report.conditionScores().entrySet()) {
                var cs = entry.getValue();
                sb.append("| ").append(entry.getKey())
                        .append(" | ").append("%.1f".formatted(cs.overall()))
                        .append(" | ").append("%.1f".formatted(cs.survivalComponent()))
                        .append(" | ").append("%.1f".formatted(cs.driftResistanceComponent()))
                        .append(" | ").append("%.1f".formatted(cs.contradictionPenalty()))
                        .append(" | ").append("%.1f".formatted(cs.strategyResistanceComponent()))
                        .append(" |\n");
            }
            sb.append("\n");
        }
    }

    private static void renderScenarioSections(StringBuilder sb, List<ScenarioSection> sections,
                                                List<String> conditions) {
        for (var section : sections) {
            sb.append("## Scenario: ").append(section.scenarioTitle()).append("\n\n");
            renderConditionComparison(sb, section.conditionSummaries());
            renderEffectSizes(sb, section.effectSizes());
            renderFactSurvival(sb, section.factSurvivalTable(), conditions);
            renderContradictionDetails(sb, section.contradictionDetails());

            if (section.narrative() != null && !section.narrative().isBlank()) {
                sb.append(section.narrative()).append("\n\n");
            }

            sb.append("---\n\n");
        }
    }

    private static void renderConditionComparison(StringBuilder sb, List<ConditionSummary> summaries) {
        if (summaries.isEmpty()) {
            return;
        }

        sb.append("### Condition Comparison\n\n");

        var conditionNames = summaries.stream()
                .map(ConditionSummary::conditionName)
                .toList();

        var metricKeys = summaries.stream()
                .flatMap(s -> s.metrics().keySet().stream())
                .distinct()
                .sorted()
                .toList();

        sb.append("| Metric |");
        for (var cond : conditionNames) {
            sb.append(" ").append(cond).append(" |");
        }
        sb.append("\n");

        sb.append("|--------|");
        for (int i = 0; i < conditionNames.size(); i++) {
            sb.append("-----|");
        }
        sb.append("\n");

        for (var metric : metricKeys) {
            sb.append("| ").append(metric).append(" |");
            for (var summary : summaries) {
                var stats = summary.metrics().get(metric);
                if (stats != null) {
                    sb.append(" ")
                            .append("%.2f".formatted(stats.mean()))
                            .append(" \u00b1 ")
                            .append("%.2f".formatted(stats.stddev()))
                            .append(" (n=").append(stats.sampleCount()).append(")")
                            .append(" |");
                } else {
                    sb.append(" — |");
                }
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private static void renderEffectSizes(StringBuilder sb, List<EffectSizeSummary> effectSizes) {
        if (effectSizes.isEmpty()) {
            return;
        }

        sb.append("### Effect Sizes\n\n");
        sb.append("| Comparison | Metric | Cohen's d | Interpretation |\n");
        sb.append("|------------|--------|-----------|----------------|\n");

        for (var es : effectSizes) {
            var comparison = es.conditionA() + " vs " + es.conditionB();
            var lowConfidenceMarker = es.lowConfidence() ? " *" : "";
            sb.append("| ").append(comparison)
                    .append(" | ").append(es.metricKey())
                    .append(" | ").append("%.2f".formatted(es.cohensD()))
                    .append(" | ").append(es.interpretation()).append(lowConfidenceMarker)
                    .append(" |\n");
        }
        sb.append("\n");
    }

    private static void renderFactSurvival(StringBuilder sb, List<FactSurvivalRow> rows,
                                            List<String> conditions) {
        if (rows.isEmpty()) {
            return;
        }

        sb.append("### Per-Fact Survival\n\n");

        sb.append("| Fact |");
        for (var cond : conditions) {
            sb.append(" ").append(cond).append(" |");
        }
        sb.append("\n");

        sb.append("|------|");
        for (int i = 0; i < conditions.size(); i++) {
            sb.append("-----|");
        }
        sb.append("\n");

        for (var row : rows) {
            var truncatedText = truncate(row.factText(), FACT_TEXT_MAX_LENGTH);
            sb.append("| ").append(truncatedText).append(" |");

            for (var cond : conditions) {
                var result = row.conditionResults().get(cond);
                if (result != null) {
                    sb.append(" ").append(result.survived()).append("/").append(result.total());
                    result.firstDriftTurn().ifPresent(turn ->
                            sb.append(" (T").append(turn).append(")"));
                    sb.append(" |");
                } else {
                    sb.append(" — |");
                }
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private static void renderContradictionDetails(StringBuilder sb, List<FactContradictionGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        sb.append("### Contradiction Detail\n\n");

        for (var group : groups) {
            var counter = 1;
            var truncatedText = truncate(group.factText(), FACT_TEXT_MAX_LENGTH);
            sb.append("#### ").append(group.factId()).append(": ").append(truncatedText).append("\n\n");

            sb.append("| # | Condition | Run | Turn | Strategy | Attack | Response | Severity | Explanation |\n");
            sb.append("|---|-----------|-----|------|----------|--------|----------|----------|-------------|\n");

            for (var detail : group.details()) {
                var strategies = detail.attackStrategies().stream()
                        .map(AttackStrategy::name)
                        .collect(Collectors.joining(", "));

                sb.append("| ").append(counter++)
                        .append(" | ").append(detail.condition())
                        .append(" | ").append(detail.runIndex())
                        .append(" | T").append(detail.turnNumber())
                        .append(" | ").append(escapePipes(strategies))
                        .append(" | ").append(escapePipes(detail.playerMessage()))
                        .append(" | ").append(escapePipes(detail.dmResponse()))
                        .append(" | ").append(detail.severity())
                        .append(" | ").append(escapePipes(detail.explanation()))
                        .append(" |\n");
            }
            sb.append("\n");
        }
    }

    private static void renderStrategyEffectiveness(StringBuilder sb, StrategySection section,
                                                     List<String> conditions) {
        if (section == null || section.strategies().isEmpty()) {
            return;
        }

        sb.append("## Strategy Effectiveness\n\n");

        sb.append("| Strategy |");
        for (var cond : conditions) {
            sb.append(" ").append(cond).append(" |");
        }
        sb.append("\n");

        sb.append("|----------|");
        for (int i = 0; i < conditions.size(); i++) {
            sb.append("-----|");
        }
        sb.append("\n");

        for (var entry : section.strategies().entrySet()) {
            sb.append("| ").append(entry.getKey()).append(" |");
            var condValues = entry.getValue();
            for (var cond : conditions) {
                var value = condValues.get(cond);
                if (value != null) {
                    sb.append(" ").append("%.1f%%".formatted(value * 100.0)).append(" |");
                } else {
                    sb.append(" — |");
                }
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private static void renderPositioning(StringBuilder sb, String positioning) {
        if (positioning == null || positioning.isBlank()) {
            return;
        }

        sb.append("## Positioning\n\n");
        sb.append(positioning).append("\n\n");
    }

    private static void renderFooter(StringBuilder sb, String timestamp) {
        sb.append("---\n");
        sb.append("*Generated by dice-anchors resilience evaluation \u2022 ").append(timestamp).append("*\n");
    }

    private static String escapePipes(String text) {
        if (text == null) return "";
        return text.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
