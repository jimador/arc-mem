package dev.arcmem.simulator.ui.panels;

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
import dev.arcmem.simulator.benchmark.BenchmarkReport;
import dev.arcmem.simulator.benchmark.BenchmarkStatistics;
import dev.arcmem.simulator.benchmark.ExperimentReport;
import dev.arcmem.simulator.report.ExperimentExporter;
import dev.arcmem.simulator.report.MarkdownReportRenderer;
import dev.arcmem.simulator.report.ResilienceReportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static dev.arcmem.simulator.ui.panels.BenchmarkRenderUtils.HIGHER_IS_BETTER;
import static dev.arcmem.simulator.ui.panels.BenchmarkRenderUtils.METRIC_LABELS;

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
    private ExperimentExporter exporter;

    public ConditionComparisonPanel() {
        setPadding(true);
        setSpacing(true);
        setWidthFull();
        addClassName("ar-condition-comparison-panel");
    }

    public void setDrillDownPanel(FactDrillDownPanel drillDownPanel) {
        this.drillDownPanel = drillDownPanel;
    }

    public void setReportBuilder(ResilienceReportBuilder reportBuilder) {
        this.reportBuilder = reportBuilder;
    }

    public void setExporter(ExperimentExporter exporter) {
        this.exporter = exporter;
    }

    public void showReport(ExperimentReport report) {
        removeAll();

        if (report.cancelled()) {
            var banner = new Div();
            banner.addClassName("ar-cancelled-banner");
            banner.setText("Cancelled — partial results");
            add(banner);
        }

        var byScenario = groupByScenario(report);

        for (var scenarioEntry : byScenario.entrySet()) {
            var scenarioId = scenarioEntry.getKey();
            var cellsByCondition = scenarioEntry.getValue();

            var scenarioHeader = new H4(scenarioId);
            scenarioHeader.addClassName("ar-scenario-header");
            add(scenarioHeader);

            for (var details : buildMetricRows(report, scenarioId, cellsByCondition)) {
                add(details);
            }

            add(buildHeatmap(report.conditions(), cellsByCondition));
        }

        if (!report.strategyDeltas().isEmpty()) {
            add(buildStrategyTable(report));
        }

        if (reportBuilder != null) {
            add(buildGenerateReportButton(report));
        }
        if (exporter != null) {
            add(buildExportButtons(report));
        }
    }

    private HorizontalLayout buildGenerateReportButton(ExperimentReport report) {
        var generateBtn = new Button("Generate Report");
        generateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        generateBtn.addClassName("ar-generate-report-btn");

        var downloadLink = new Anchor();
        downloadLink.getElement().setAttribute("download", true);
        downloadLink.getStyle().set("display", "none");

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

                downloadLink.setHref(resource);
                downloadLink.getElement().callJsFunction("click");

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

        var row = new HorizontalLayout(generateBtn, downloadLink);
        row.setSpacing(true);
        row.addClassName("ar-generate-report-row");
        return row;
    }

    private HorizontalLayout buildExportButtons(ExperimentReport report) {
        var layout = new HorizontalLayout();
        layout.setSpacing(true);
        layout.addClassName("ar-export-buttons");
        layout.add(buildDownloadButton("Export JSON", report, "application/json", ".json",
                exporter::exportJson));
        layout.add(buildDownloadButton("Export CSV", report, "text/csv", ".csv",
                exporter::exportCsv));
        return layout;
    }

    private Div buildDownloadButton(
            String label, ExperimentReport report, String contentType, String extension,
            Function<ExperimentReport, String> exportFn) {
        var btn = new Button(label);
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        var downloadLink = new Anchor();
        downloadLink.getElement().setAttribute("download", true);
        downloadLink.getStyle().set("display", "none");

        btn.addClickListener(event -> {
            var content = exportFn.apply(report);
            var fileName = "%s%s".formatted(
                    report.experimentName().replaceAll("[^a-zA-Z0-9_-]", "_"), extension);
            var resource = new StreamResource(fileName,
                    () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            resource.setContentType(contentType);
            downloadLink.setHref(resource);
            downloadLink.getElement().callJsFunction("click");
        });

        return new Div(btn, downloadLink);
    }

    private List<Details> buildMetricRows(
            ExperimentReport report,
            String scenarioId,
            Map<String, BenchmarkReport> cellsByCondition) {

        var conditions = report.conditions();
        var metricKeys = new ArrayList<>(METRIC_LABELS.keySet());
        var rows = new ArrayList<Details>();

        for (var metricKey : metricKeys) {
            var label = METRIC_LABELS.getOrDefault(metricKey, metricKey);

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

                if (i < conditions.size() - 1) {
                    var nextCondName = conditions.get(i + 1);
                    var nextCell = cellsByCondition.get(nextCondName);
                    summaryRow.add(buildSingleMetricDelta(
                            report, metricKey, conditionName, cell, nextCondName, nextCell));
                }
            }

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

    private static BenchmarkStatistics emptyStats() {
        return new BenchmarkStatistics(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0);
    }
}
