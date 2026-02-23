package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.IntegerField;
import dev.dunnam.diceanchors.sim.benchmark.BenchmarkProgress;
import dev.dunnam.diceanchors.sim.benchmark.BenchmarkReport;
import dev.dunnam.diceanchors.sim.benchmark.BenchmarkStatistics;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Panel displaying aggregated benchmark statistics across multiple simulation runs.
 * <p>
 * Renders metric cards with mean/stddev/median/p95/sample count, per-strategy
 * effectiveness bars with confidence bounds, baseline comparison badges, and
 * high-variance warnings. Driven by {@link BenchmarkReport} via {@link #showReport}.
 * <p>
 * Does NOT implement {@link SimulationProgressListener} — progress is driven by
 * {@link dev.dunnam.diceanchors.sim.benchmark.BenchmarkRunner} callbacks.
 */
public class BenchmarkPanel extends VerticalLayout {

    // Human-readable labels for metric keys
    private static final Map<String, String> METRIC_LABELS = Map.of(
            "factSurvivalRate", "Survival Rate",
            "contradictionCount", "Contradictions",
            "majorContradictionCount", "Major Contradictions",
            "driftAbsorptionRate", "Absorption Rate",
            "anchorAttributionCount", "Attribution",
            "meanTurnsToFirstDrift", "Mean First Drift"
    );

    // Metrics where higher is better (for health coloring and delta badges)
    private static final Map<String, Boolean> HIGHER_IS_BETTER = Map.of(
            "factSurvivalRate", true,
            "contradictionCount", false,
            "majorContradictionCount", false,
            "driftAbsorptionRate", true,
            "anchorAttributionCount", true,
            "meanTurnsToFirstDrift", true
    );

    // Controls
    private final IntegerField runCountField;
    private final Button runButton;
    private final Button cancelButton;
    private final Button saveBaselineButton;

    // Progress
    private final HorizontalLayout progressLayout;
    private final Span progressLabel;
    private final ProgressBar progressBar;

    // Content sections
    private final Div metricsGrid;
    private final VerticalLayout strategySection;
    private final VerticalLayout baselineSection;

    // Callbacks
    private Consumer<Integer> runBenchmarkCallback;
    private Runnable cancelCallback;
    private Runnable saveBaselineCallback;

    public BenchmarkPanel() {
        setVisible(false);
        setPadding(true);
        setSpacing(true);
        setWidthFull();

        // --- Controls ---
        runCountField = new IntegerField("Run Count");
        runCountField.setValue(5);
        runCountField.setMin(2);
        runCountField.setMax(20);
        runCountField.setStepButtonsVisible(true);
        runCountField.setWidth("140px");

        runButton = new Button("Run Benchmark");
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        runButton.addClickListener(e -> {
            if (runBenchmarkCallback != null) {
                var count = runCountField.getValue() != null ? runCountField.getValue() : 5;
                runBenchmarkCallback.accept(count);
            }
        });

        cancelButton = new Button("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addClickListener(e -> {
            if (cancelCallback != null) {
                cancelCallback.run();
            }
        });

        saveBaselineButton = new Button("Save as Baseline");
        saveBaselineButton.setEnabled(false);
        saveBaselineButton.addClickListener(e -> {
            if (saveBaselineCallback != null) {
                saveBaselineCallback.run();
            }
        });

        var controlsLayout = new HorizontalLayout(runCountField, runButton, cancelButton, saveBaselineButton);
        controlsLayout.setAlignItems(HorizontalLayout.Alignment.BASELINE);
        controlsLayout.setSpacing(true);
        controlsLayout.addClassName("ar-bench-controls");

        // --- Progress ---
        progressLabel = new Span();
        progressLabel.addClassName("ar-bench-progress-label");

        progressBar = new ProgressBar(0, 1, 0);
        progressBar.setWidthFull();

        progressLayout = new HorizontalLayout(progressLabel, progressBar);
        progressLayout.setWidthFull();
        progressLayout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        progressLayout.setSpacing(true);
        progressLayout.setVisible(false);
        progressLayout.addClassName("ar-bench-progress");
        progressLayout.setFlexGrow(1, progressBar);

        // --- Content ---
        var title = new H4("Benchmark Results");
        title.addClassName("ar-section-title");

        metricsGrid = new Div();
        metricsGrid.addClassName("ar-metrics-grid");

        strategySection = new VerticalLayout();
        strategySection.setPadding(false);
        strategySection.setSpacing(true);

        baselineSection = new VerticalLayout();
        baselineSection.setPadding(false);
        baselineSection.setSpacing(true);

        add(controlsLayout, progressLayout, title, metricsGrid, strategySection, baselineSection);
        setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Render a completed benchmark report with metric cards, strategy bars,
     * and baseline comparison badges.
     */
    public void showReport(BenchmarkReport report) {
        metricsGrid.removeAll();
        strategySection.removeAll();
        baselineSection.removeAll();
        saveBaselineButton.setEnabled(true);

        // Metric cards
        for (var entry : report.metricStatistics().entrySet()) {
            var metricName = entry.getKey();
            var stats = entry.getValue();
            var label = METRIC_LABELS.getOrDefault(metricName, metricName);
            var health = determineHealth(metricName, stats);
            var delta = report.baselineDeltas() != null ? report.baselineDeltas().get(metricName) : null;
            metricsGrid.add(benchmarkMetricCard(label, stats, health, delta, metricName));
        }

        // Strategy effectiveness bars with confidence bounds
        if (!report.strategyStatistics().isEmpty()) {
            renderStrategyBreakdown(report.strategyStatistics());
        }

        setVisible(true);
    }

    /**
     * Update the progress indicator during benchmark execution.
     */
    public void showProgress(BenchmarkProgress progress) {
        progressLayout.setVisible(true);
        progressLabel.setText("Run %d/%d".formatted(progress.completedRuns(), progress.totalRuns()));
        progressBar.setValue((double) progress.completedRuns() / progress.totalRuns());
    }

    /**
     * Clear all content and hide the panel.
     */
    public void reset() {
        metricsGrid.removeAll();
        strategySection.removeAll();
        baselineSection.removeAll();
        progressLayout.setVisible(false);
        progressBar.setValue(0);
        progressLabel.setText("");
        saveBaselineButton.setEnabled(false);
    }

    /**
     * Set the callback invoked when the user clicks "Run Benchmark".
     * The callback receives the desired run count.
     */
    public void setRunBenchmarkCallback(Consumer<Integer> callback) {
        this.runBenchmarkCallback = callback;
    }

    /**
     * Set the callback invoked when the user clicks "Cancel".
     */
    public void setCancelCallback(Runnable callback) {
        this.cancelCallback = callback;
    }

    /**
     * Set the callback invoked when the user clicks "Save as Baseline".
     */
    public void setSaveBaselineCallback(Runnable callback) {
        this.saveBaselineCallback = callback;
    }

    /**
     * Toggle control state between running and idle.
     * When running: disables run button and run count field, enables cancel.
     * When not running: enables run button and run count field, disables cancel, hides progress.
     */
    public void setRunning(boolean running) {
        runButton.setEnabled(!running);
        runCountField.setEnabled(!running);
        cancelButton.setEnabled(running);
        if (!running) {
            progressLayout.setVisible(false);
        }
    }

    // -------------------------------------------------------------------------
    // Private rendering helpers
    // -------------------------------------------------------------------------

    /**
     * Build a metric card showing mean +/- stddev, median, p95, sample count,
     * optional high-variance warning, and optional baseline delta badge.
     */
    private Div benchmarkMetricCard(String label, BenchmarkStatistics stats,
                                     String health, Double delta, String metricName) {
        var card = new Div();
        card.addClassName("ar-metric-card");
        card.addClassName("ar-bench-metric-card");
        card.getElement().setAttribute("data-health", health);

        // Mean +/- stddev
        var meanText = Double.isNaN(stats.mean()) ? "N/A" : "%.1f".formatted(stats.mean());
        var stddevText = Double.isNaN(stats.stddev()) ? "" : " \u00B1 %.1f".formatted(stats.stddev());
        var valueSpan = new Span(meanText + stddevText);
        valueSpan.addClassName("ar-metric-value");
        valueSpan.getElement().setAttribute("data-health", health);

        // Label
        var labelSpan = new Span(label);
        labelSpan.addClassName("ar-metric-label");

        // Median and P95
        var medianText = Double.isNaN(stats.median()) ? "N/A" : "%.1f".formatted(stats.median());
        var p95Text = Double.isNaN(stats.p95()) ? "N/A" : "%.1f".formatted(stats.p95());
        var detailSpan = new Span("median: %s | p95: %s".formatted(medianText, p95Text));
        detailSpan.addClassName("ar-bench-metric-detail");

        // Sample count
        var sampleSpan = new Span("n=%d".formatted(stats.sampleCount()));
        sampleSpan.addClassName("ar-bench-sample-count");

        card.add(valueSpan, labelSpan, detailSpan, sampleSpan);

        // High-variance warning
        if (stats.isHighVariance()) {
            var warningBadge = new Span("HIGH VARIANCE");
            warningBadge.addClassName("ar-bench-warning-badge");
            card.add(warningBadge);
        }

        // Baseline delta badge
        if (delta != null) {
            var higherBetter = HIGHER_IS_BETTER.getOrDefault(metricName, true);
            card.add(baselineDeltaBadge(delta, higherBetter));
        }

        return card;
    }

    /**
     * Render baseline comparison badge: IMPROVED, REGRESSED, or UNCHANGED.
     */
    private Span baselineDeltaBadge(double delta, boolean higherIsBetter) {
        String text;
        String badgeType;

        if (Math.abs(delta) < 0.01) {
            text = "UNCHANGED";
            badgeType = "neutral";
        } else {
            var improved = higherIsBetter ? delta > 0 : delta < 0;
            text = improved
                    ? "IMPROVED %+.1f".formatted(delta)
                    : "REGRESSED %+.1f".formatted(delta);
            badgeType = improved ? "good" : "bad";
        }

        var badge = new Span(text);
        badge.addClassName("ar-bench-delta-badge");
        badge.getElement().setAttribute("data-health", badgeType);
        return badge;
    }

    /**
     * Render per-strategy effectiveness bars with confidence bounds (mean +/- 1 stddev).
     */
    private void renderStrategyBreakdown(Map<String, BenchmarkStatistics> strategyStats) {
        var title = new H4("Strategy Effectiveness (Aggregated)");
        title.addClassName("ar-section-title--inner");
        strategySection.add(title);

        for (var entry : strategyStats.entrySet()) {
            var strategy = entry.getKey();
            var stats = entry.getValue();
            var meanPct = stats.mean() * 100.0;
            var stddevPct = stats.stddev() * 100.0;
            var health = meanPct == 0 ? "good" : meanPct <= 30 ? "warn" : "bad";

            var row = new HorizontalLayout();
            row.setWidthFull();
            row.setSpacing(true);
            row.setAlignItems(HorizontalLayout.Alignment.CENTER);

            var nameSpan = new Span(strategy);
            nameSpan.addClassName("ar-strategy-name");

            var barOuter = new Div();
            barOuter.addClassName("ar-bar-outer");

            // Main bar at mean
            var barInner = new Div();
            barInner.addClassName("ar-bar-inner");
            barInner.getElement().setAttribute("data-health", health);
            barInner.getElement().setAttribute("style", "width: %.0f%%".formatted(Math.min(100, meanPct)));
            barOuter.add(barInner);

            // Confidence range indicator (mean +/- 1 stddev)
            var lowerBound = Math.max(0, meanPct - stddevPct);
            var upperBound = Math.min(100, meanPct + stddevPct);
            var confidenceSpan = new Span("[%.0f%% - %.0f%%]".formatted(lowerBound, upperBound));
            confidenceSpan.addClassName("ar-bench-confidence");

            var rateSpan = new Span("%.0f%% \u00B1 %.0f%%".formatted(meanPct, stddevPct));
            rateSpan.addClassName("ar-strategy-rate");
            rateSpan.getElement().setAttribute("data-health", health);

            row.add(nameSpan, barOuter, rateSpan, confidenceSpan);
            strategySection.add(row);

            // High-variance warning for strategy
            if (stats.isHighVariance()) {
                var warningSpan = new Span("high variance (CV=%.2f)".formatted(stats.coefficientOfVariation()));
                warningSpan.addClassName("ar-bench-warning-badge");
                strategySection.add(warningSpan);
            }
        }
    }

    /**
     * Determine health coloring for a metric based on its mean value.
     */
    private String determineHealth(String metricName, BenchmarkStatistics stats) {
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
