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

import static dev.dunnam.diceanchors.sim.views.BenchmarkRenderUtils.METRIC_LABELS;

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
            var health = BenchmarkRenderUtils.determineHealth(metricName, stats);
            var delta = report.baselineDeltas() != null ? report.baselineDeltas().get(metricName) : null;
            metricsGrid.add(BenchmarkRenderUtils.metricCard(label, stats, health, delta, metricName));
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
     * Render per-strategy effectiveness bars with confidence bounds (mean +/- 1 stddev).
     */
    private void renderStrategyBreakdown(Map<String, BenchmarkStatistics> strategyStats) {
        var title = new H4("Strategy Effectiveness (Aggregated)");
        title.addClassName("ar-section-title--inner");
        strategySection.add(title);

        for (var entry : strategyStats.entrySet()) {
            var strategy = entry.getKey();
            var stats = entry.getValue();
            strategySection.add(BenchmarkRenderUtils.strategyBar(strategy, stats));

            // High-variance warning for strategy
            if (stats.isHighVariance()) {
                var warningSpan = new Span("high variance (CV=%.2f)".formatted(stats.coefficientOfVariation()));
                warningSpan.addClassName("ar-bench-warning-badge");
                strategySection.add(warningSpan);
            }
        }
    }
}
