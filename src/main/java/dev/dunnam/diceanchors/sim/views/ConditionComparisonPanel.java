package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.StreamResource;
import dev.dunnam.diceanchors.sim.benchmark.BenchmarkReport;
import dev.dunnam.diceanchors.sim.benchmark.BenchmarkStatistics;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentReport;
import dev.dunnam.diceanchors.sim.report.MarkdownReportRenderer;
import dev.dunnam.diceanchors.sim.report.ResilienceReportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static dev.dunnam.diceanchors.sim.views.BenchmarkRenderUtils.HIGHER_IS_BETTER;
import static dev.dunnam.diceanchors.sim.views.BenchmarkRenderUtils.METRIC_LABELS;

/**
 * Panel displaying a side-by-side comparison of conditions across scenarios,
 * including metric cards, delta badges, Cohen's d effect sizes, a heatmap,
 * and a strategy effectiveness table.
 * <p>
 * Each metric is rendered as a collapsible {@link Details} row. The summary
 * shows condition cards and deltas for that metric; the content area provides
 * a per-fact drill-down table rendered lazily by {@link FactDrillDownPanel}.
 * <p>
 * Driven by {@link ExperimentReport} via {@link #showReport}.
 */
public class ConditionComparisonPanel extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(ConditionComparisonPanel.class);

    private FactDrillDownPanel drillDownPanel;
    private ResilienceReportBuilder reportBuilder;

    public ConditionComparisonPanel() {
        setPadding(true);
        setSpacing(true);
        setWidthFull();
        addClassName("ar-condition-comparison-panel");
    }

    /**
     * Set the drill-down renderer used to populate per-fact survival tables
     * inside each metric's {@link Details} content area.
     */
    public void setDrillDownPanel(FactDrillDownPanel drillDownPanel) {
        this.drillDownPanel = drillDownPanel;
    }

    /**
     * Set the report builder used to generate resilience evaluation reports.
     */
    public void setReportBuilder(ResilienceReportBuilder reportBuilder) {
        this.reportBuilder = reportBuilder;
    }

    /**
     * Render the full experiment report. Clears all existing children before rendering.
     *
     * @param report the completed (or cancelled) experiment report to display
     */
    public void showReport(ExperimentReport report) {
        removeAll();

        if (report.cancelled()) {
            var banner = new Div();
            banner.addClassName("ar-cancelled-banner");
            banner.setText("Cancelled — partial results");
            add(banner);
        }

        // Group cell reports by scenarioId preserving encounter order
        var byScenario = groupByScenario(report);

        for (var scenarioEntry : byScenario.entrySet()) {
            var scenarioId = scenarioEntry.getKey();
            var cellsByCondition = scenarioEntry.getValue();

            var scenarioHeader = new H4(scenarioId);
            scenarioHeader.addClassName("ar-scenario-header");
            add(scenarioHeader);

            // Per-metric Details rows with inline drill-down
            for (var details : buildMetricRows(report, scenarioId, cellsByCondition)) {
                add(details);
            }

            // Heatmap
            add(buildHeatmap(report.conditions(), cellsByCondition));
        }

        // Strategy effectiveness table
        if (!report.strategyDeltas().isEmpty()) {
            add(buildStrategyTable(report));
        }

        // Generate Report button
        if (reportBuilder != null) {
            add(buildGenerateReportButton(report));
        }
    }

    // -------------------------------------------------------------------------
    // Private: report generation
    // -------------------------------------------------------------------------

    /**
     * Build a "Generate Report" button that triggers async report generation
     * and Markdown download via {@link StreamResource}.
     */
    private HorizontalLayout buildGenerateReportButton(ExperimentReport report) {
        var generateBtn = new Button("Generate Report");
        generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        generateBtn.addClassName("ar-generate-report-btn");

        // Hidden download anchor — programmatically clicked after report generation
        var downloadAnchor = new Anchor();
        downloadAnchor.getElement().setAttribute("download", true);
        downloadAnchor.getStyle().set("display", "none");

        generateBtn.addClickListener(event -> {
            generateBtn.setEnabled(false);
            generateBtn.setText("Generating...");

            var ui = UI.getCurrent();

            CompletableFuture.supplyAsync(() -> {
                var resilienceReport = reportBuilder.build(report);
                return MarkdownReportRenderer.render(resilienceReport);
            }).thenAccept(markdown -> ui.access(() -> {
                var fileName = "resilience-report-%s.md".formatted(
                        report.experimentName().replaceAll("[^a-zA-Z0-9_-]", "_"));
                var resource = new StreamResource(fileName,
                        () -> new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));
                resource.setContentType("text/markdown");

                downloadAnchor.setHref(resource);
                downloadAnchor.getElement().callJsFunction("click");

                generateBtn.setEnabled(true);
                generateBtn.setText("Generate Report");
            })).exceptionally(ex -> {
                logger.error("Report generation failed", ex);
                ui.access(() -> {
                    generateBtn.setEnabled(true);
                    generateBtn.setText("Generate Report");
                });
                return null;
            });
        });

        var row = new HorizontalLayout(generateBtn, downloadAnchor);
        row.setSpacing(true);
        row.addClassName("ar-generate-report-row");
        return row;
    }

    // -------------------------------------------------------------------------
    // Private: per-metric Details rows
    // -------------------------------------------------------------------------

    /**
     * Build one {@link Details} component per metric. Each Details summary shows
     * the horizontal card row (condition cards + delta badges) for a single metric.
     * The content area is lazily populated with a per-fact drill-down table on expand.
     */
    private List<Details> buildMetricRows(
            ExperimentReport report,
            String scenarioId,
            Map<String, BenchmarkReport> cellsByCondition) {

        var conditions = report.conditions();
        var metricKeys = new ArrayList<>(METRIC_LABELS.keySet());
        var rows = new ArrayList<Details>();

        for (var metricKey : metricKeys) {
            var label = METRIC_LABELS.getOrDefault(metricKey, metricKey);

            // Summary row: condition cards + deltas for this single metric
            var summaryRow = new HorizontalLayout();
            summaryRow.setSpacing(false);
            summaryRow.setWidthFull();
            summaryRow.addClassName("ar-condition-card-row");

            for (int i = 0; i < conditions.size(); i++) {
                var conditionName = conditions.get(i);
                var cell = cellsByCondition.get(conditionName);

                var stats = cell != null
                        ? cell.metricStatistics().getOrDefault(metricKey, emptyStats())
                        : emptyStats();
                var health = BenchmarkRenderUtils.determineHealth(metricKey, stats);

                var condWrapper = new VerticalLayout();
                condWrapper.addClassName("ar-condition-column");
                condWrapper.setPadding(true);
                condWrapper.setSpacing(false);

                var condLabel = new Span(conditionName);
                condLabel.addClassName("ar-condition-name");
                condWrapper.add(condLabel);
                condWrapper.add(BenchmarkRenderUtils.metricCard(label, stats, health, metricKey));

                summaryRow.add(condWrapper);

                // Delta between this condition and the next
                if (i < conditions.size() - 1) {
                    var nextCondName = conditions.get(i + 1);
                    var nextCell = cellsByCondition.get(nextCondName);
                    summaryRow.add(buildSingleMetricDelta(
                            report, metricKey, conditionName, cell, nextCondName, nextCell));
                }
            }

            // Drill-down content area (lazily populated on expand)
            var contentArea = new VerticalLayout();
            contentArea.setPadding(false);
            contentArea.setSpacing(true);
            contentArea.addClassName("ar-drilldown-content");

            var details = new Details(summaryRow, contentArea);
            details.setOpened(false);
            details.addClassName("ar-drilldown-details");

            final var mKey = metricKey;
            final var sId = scenarioId;
            details.addOpenedChangeListener(event -> {
                if (event.isOpened() && contentArea.getComponentCount() == 0 && drillDownPanel != null) {
                    drillDownPanel.renderDrillDownFor(mKey, sId, contentArea);
                }
            });

            rows.add(details);
        }

        return rows;
    }

    /**
     * Build the delta badge and effect-size annotation for a single metric
     * between two adjacent conditions.
     */
    private VerticalLayout buildSingleMetricDelta(
            ExperimentReport report,
            String metricKey,
            String condA,
            BenchmarkReport cellA,
            String condB,
            BenchmarkReport cellB) {

        var deltaColumn = new VerticalLayout();
        deltaColumn.addClassName("ar-delta-column");
        deltaColumn.setPadding(false);
        deltaColumn.setSpacing(true);

        var statsA = cellA != null
                ? cellA.metricStatistics().getOrDefault(metricKey, emptyStats())
                : emptyStats();
        var statsB = cellB != null
                ? cellB.metricStatistics().getOrDefault(metricKey, emptyStats())
                : emptyStats();

        var delta = statsB.mean() - statsA.mean();
        var higherIsBetter = HIGHER_IS_BETTER.getOrDefault(metricKey, true);

        var metricDeltaWrapper = new Div();
        metricDeltaWrapper.addClassName("ar-delta-metric-wrapper");
        metricDeltaWrapper.add(BenchmarkRenderUtils.deltaBadge(delta, higherIsBetter));

        // Effect size
        var effectKey = condA.compareTo(condB) <= 0
                ? condA + ":" + condB
                : condB + ":" + condA;
        var effectsForPair = report.effectSizeMatrix().getOrDefault(effectKey, Map.of());
        var effectEntry = effectsForPair.get(metricKey);
        if (effectEntry != null) {
            var effectSpan = new Span("d=%.2f %s".formatted(effectEntry.cohensD(), effectEntry.interpretation()));
            effectSpan.addClassName("ar-effect-size");
            metricDeltaWrapper.add(effectSpan);

            if (effectEntry.lowConfidence()) {
                var warningBadge = new Span("LOW CONF");
                warningBadge.addClassName("ar-bench-warning-badge");
                metricDeltaWrapper.add(warningBadge);
            }
        }

        deltaColumn.add(metricDeltaWrapper);
        return deltaColumn;
    }

    // -------------------------------------------------------------------------
    // Private: heatmap
    // -------------------------------------------------------------------------

    /**
     * Build a CSS grid heatmap: rows = conditions, columns = metrics.
     */
    private Div buildHeatmap(List<String> conditions, Map<String, BenchmarkReport> cellsByCondition) {
        var metricKeys = new ArrayList<>(METRIC_LABELS.keySet());
        var numMetrics = metricKeys.size();

        var heatmap = new Div();
        heatmap.addClassName("ar-heatmap-grid");
        heatmap.getElement().setAttribute(
                "style",
                "grid-template-columns: auto repeat(%d, 1fr)".formatted(numMetrics));

        var cornerCell = new Span();
        cornerCell.addClassName("ar-heatmap-header");
        heatmap.add(cornerCell);

        for (var metricKey : metricKeys) {
            var header = new Span(METRIC_LABELS.getOrDefault(metricKey, metricKey));
            header.addClassName("ar-heatmap-header");
            var desc = BenchmarkRenderUtils.METRIC_DESCRIPTIONS.get(metricKey);
            if (desc != null) {
                header.getElement().setAttribute("title", desc);
            }
            heatmap.add(header);
        }

        for (var conditionName : conditions) {
            var condHeader = new Span(conditionName);
            condHeader.addClassName("ar-heatmap-condition-label");
            heatmap.add(condHeader);

            var cell = cellsByCondition.get(conditionName);

            for (var metricKey : metricKeys) {
                var stats = cell != null
                        ? cell.metricStatistics().getOrDefault(metricKey, emptyStats())
                        : emptyStats();
                var health = BenchmarkRenderUtils.determineHealth(metricKey, stats);
                var meanText = Double.isNaN(stats.mean()) ? "N/A" : "%.1f".formatted(stats.mean());

                var heatCell = new Div();
                heatCell.addClassName("ar-heatmap-cell");
                heatCell.getElement().setAttribute("data-health", health);
                heatCell.setText(meanText);
                var desc = BenchmarkRenderUtils.METRIC_DESCRIPTIONS.get(metricKey);
                if (desc != null) {
                    heatCell.getElement().setAttribute("title", desc);
                }
                heatmap.add(heatCell);
            }
        }

        return heatmap;
    }

    // -------------------------------------------------------------------------
    // Private: strategy effectiveness table
    // -------------------------------------------------------------------------

    /**
     * Build the strategy effectiveness table showing each strategy's mean effectiveness
     * per condition.
     */
    private VerticalLayout buildStrategyTable(ExperimentReport report) {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.addClassName("ar-strategy-table");

        var title = new H4("Strategy Effectiveness by Condition");
        title.addClassName("ar-section-title--inner");
        section.add(title);

        var headerRow = new HorizontalLayout();
        headerRow.setSpacing(true);
        headerRow.setWidthFull();
        headerRow.addClassName("ar-strategy-table-header");

        var strategyHeader = new Span("Strategy");
        strategyHeader.addClassName("ar-strategy-name");
        headerRow.add(strategyHeader);

        for (var condition : report.conditions()) {
            var condHeader = new Span(condition);
            condHeader.addClassName("ar-strategy-condition-header");
            headerRow.add(condHeader);
        }
        section.add(headerRow);

        for (var strategyEntry : report.strategyDeltas().entrySet()) {
            var strategyName = strategyEntry.getKey();
            var conditionMeans = strategyEntry.getValue();

            var row = new HorizontalLayout();
            row.setSpacing(true);
            row.setWidthFull();
            row.addClassName("ar-strategy-table-row");

            var nameSpan = new Span(strategyName);
            nameSpan.addClassName("ar-strategy-name");
            row.add(nameSpan);

            for (var condition : report.conditions()) {
                var mean = conditionMeans.getOrDefault(condition, Double.NaN);
                if (Double.isNaN(mean)) {
                    var naSpan = new Span("N/A");
                    naSpan.addClassName("ar-strategy-rate");
                    row.add(naSpan);
                } else {
                    var syntheticStats = new BenchmarkStatistics(mean, 0.0, mean, mean, mean, mean, 1);
                    row.add(BenchmarkRenderUtils.strategyBar(condition, syntheticStats));
                }
            }

            section.add(row);
        }

        return section;
    }

    // -------------------------------------------------------------------------
    // Private: helpers
    // -------------------------------------------------------------------------

    /**
     * Group cell reports by scenario ID, preserving encounter order via LinkedHashMap.
     * The map value is keyed by condition name.
     */
    private Map<String, Map<String, BenchmarkReport>> groupByScenario(ExperimentReport report) {
        Map<String, Map<String, BenchmarkReport>> result = new LinkedHashMap<>();

        for (var scenarioId : report.scenarioIds()) {
            Map<String, BenchmarkReport> byCond = new LinkedHashMap<>();
            for (var condition : report.conditions()) {
                var key = condition + ":" + scenarioId;
                var cellReport = report.cellReports().get(key);
                if (cellReport != null) {
                    byCond.put(condition, cellReport);
                }
            }
            result.put(scenarioId, byCond);
        }

        return result;
    }

    /**
     * Return a zero-value statistics instance used when a cell is missing.
     */
    private static BenchmarkStatistics emptyStats() {
        return new BenchmarkStatistics(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
    }
}
