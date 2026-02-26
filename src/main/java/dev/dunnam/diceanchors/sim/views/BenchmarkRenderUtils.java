package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import dev.dunnam.diceanchors.sim.benchmark.BenchmarkStatistics;

import java.util.Map;

class BenchmarkRenderUtils {

    static final Map<String, String> METRIC_LABELS = Map.of(
            "factSurvivalRate", "Survival Rate",
            "contradictionCount", "Contradictions",
            "majorContradictionCount", "Major Contradictions",
            "driftAbsorptionRate", "Absorption Rate",
            "anchorAttributionCount", "Attribution",
            "meanTurnsToFirstDrift", "Mean First Drift"
    );

    static final Map<String, String> METRIC_DESCRIPTIONS = Map.of(
            "factSurvivalRate", "Percentage of ground truth facts that were confirmed by the DM and never contradicted. Facts the DM never mentioned are not counted as survived.",
            "contradictionCount", "Total number of individual contradiction verdicts across all evaluated turns.",
            "majorContradictionCount", "Number of contradiction verdicts classified as major severity \u2014 direct, unambiguous reversals of established facts.",
            "driftAbsorptionRate",
            "Percentage of engaged turns (where the DM confirmed or contradicted at least one fact) that had zero contradictions. Turns where no facts were mentioned are "
            + "excluded.",
            "anchorAttributionCount", "Number of distinct ground truth facts that received at least one CONFIRMED verdict, indicating the DM actively referenced them.",
            "meanTurnsToFirstDrift",
            "Average turn number at which each contradicted fact was first contradicted. Higher means facts held longer before drifting. N/A if no contradictions occurred."
    );

    static final Map<String, Boolean> HIGHER_IS_BETTER = Map.of(
            "factSurvivalRate", true,
            "contradictionCount", false,
            "majorContradictionCount", false,
            "driftAbsorptionRate", true,
            "anchorAttributionCount", true,
            "meanTurnsToFirstDrift", true
    );

    private BenchmarkRenderUtils() {
    }

    static Div metricCard(String label, BenchmarkStatistics stats, String health) {
        return metricCard(label, stats, health, null);
    }

    static Div metricCard(String label, BenchmarkStatistics stats, String health,
                          String metricKey) {
        var card = new Div();
        card.addClassName("ar-metric-card");
        card.addClassName("ar-bench-metric-card");
        card.getElement().setAttribute("data-health", health);

        var description = metricKey != null ? METRIC_DESCRIPTIONS.get(metricKey) : null;
        if (description != null) {
            card.getElement().setAttribute("title", description);
        }

        var meanText = Double.isNaN(stats.mean()) ? "N/A" : "%.2f".formatted(stats.mean());
        var stddevText = Double.isNaN(stats.stddev()) ? "" : " \u00B1 %.2f".formatted(stats.stddev());
        var valueSpan = new Span(meanText + stddevText);
        valueSpan.addClassName("ar-metric-value");
        valueSpan.getElement().setAttribute("data-health", health);

        var labelSpan = new Span(label);
        labelSpan.addClassName("ar-metric-label");

        var medianText = Double.isNaN(stats.median()) ? "N/A" : "%.2f".formatted(stats.median());
        var p95Text = Double.isNaN(stats.p95()) ? "N/A" : "%.2f".formatted(stats.p95());
        var detailSpan = new Span("median: %s | p95: %s".formatted(medianText, p95Text));
        detailSpan.addClassName("ar-bench-metric-detail");

        var sampleSpan = new Span("n=%d".formatted(stats.sampleCount()));
        sampleSpan.addClassName("ar-bench-sample-count");

        card.add(valueSpan, labelSpan, detailSpan, sampleSpan);

        if (stats.isHighVariance()) {
            var warningBadge = new Span("HIGH VARIANCE");
            warningBadge.addClassName("ar-bench-warning-badge");
            card.add(warningBadge);
        }

        return card;
    }

    static Div metricCard(String label, BenchmarkStatistics stats, String health,
                          Double delta, String metricName) {
        var card = metricCard(label, stats, health, metricName);

        if (delta != null) {
            var higherBetter = HIGHER_IS_BETTER.getOrDefault(metricName, true);
            card.add(deltaBadge(delta, higherBetter));
        }

        return card;
    }

    static Span deltaBadge(double delta, boolean higherIsBetter) {
        String text;
        String badgeType;

        if (Math.abs(delta) < 0.01) {
            text = "UNCHANGED";
            badgeType = "neutral";
        } else {
            var improved = higherIsBetter ? delta > 0 : delta < 0;
            text = improved
                    ? "IMPROVED %+.2f".formatted(delta)
                    : "REGRESSED %+.2f".formatted(delta);
            badgeType = improved ? "good" : "bad";
        }

        var badge = new Span(text);
        badge.addClassName("ar-bench-delta-badge");
        badge.getElement().setAttribute("data-health", badgeType);
        return badge;
    }

    static HorizontalLayout strategyBar(String name, BenchmarkStatistics stats) {
        var meanPct = stats.mean() * 100.0;
        var stddevPct = stats.stddev() * 100.0;
        var health = meanPct == 0 ? "good" : meanPct <= 30 ? "warn" : "bad";

        var row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(true);
        row.setAlignItems(HorizontalLayout.Alignment.CENTER);

        var nameSpan = new Span(name);
        nameSpan.addClassName("ar-strategy-name");

        var barOuter = new Div();
        barOuter.addClassName("ar-bar-outer");

        var barInner = new Div();
        barInner.addClassName("ar-bar-inner");
        barInner.getElement().setAttribute("data-health", health);
        barInner.getElement().setAttribute("style", "width: %.0f%%".formatted(Math.min(100, meanPct)));
        barOuter.add(barInner);

        var lowerBound = Math.max(0, meanPct - stddevPct);
        var upperBound = Math.min(100, meanPct + stddevPct);
        var confidenceSpan = new Span("[%.0f%% - %.0f%%]".formatted(lowerBound, upperBound));
        confidenceSpan.addClassName("ar-bench-confidence");

        var rateSpan = new Span("%.0f%% \u00B1 %.0f%%".formatted(meanPct, stddevPct));
        rateSpan.addClassName("ar-strategy-rate");
        rateSpan.getElement().setAttribute("data-health", health);

        row.add(nameSpan, barOuter, rateSpan, confidenceSpan);
        return row;
    }

    static String determineHealth(String metricName, BenchmarkStatistics stats) {
        if (Double.isNaN(stats.mean())) {
            return "good";
        }
        return switch (metricName) {
            case "factSurvivalRate" -> stats.mean() >= 80 ? "good" : stats.mean() >= 50 ? "warn" : "bad";
            case "contradictionCount" -> stats.mean() == 0 ? "good" : stats.mean() <= 2 ? "warn" : "bad";
            case "majorContradictionCount" -> stats.mean() == 0 ? "good" : "bad";
            case "driftAbsorptionRate" -> stats.mean() >= 80 ? "good" : stats.mean() >= 50 ? "warn" : "bad";
            case "anchorAttributionCount" -> stats.mean() > 0 ? "good" : "warn";
            case "meanTurnsToFirstDrift" -> "warn";
            default -> "good";
        };
    }
}
