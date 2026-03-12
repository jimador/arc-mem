package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentReport;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScenarioLoader;
import dev.dunnam.diceanchors.sim.report.FactSurvivalLoader;
import dev.dunnam.diceanchors.sim.report.FactSurvivalRow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drill-down renderer providing per-metric, per-scenario fact survival tables.
 * <p>
 * Not a standalone visual panel. Instead, {@link ConditionComparisonPanel} delegates
 * to {@link #renderDrillDownFor(String, String, VerticalLayout)} to lazily populate
 * inline drill-down content within each metric's {@link com.vaadin.flow.component.details.Details}.
 * <p>
 * Data loading is delegated to {@link FactSurvivalLoader}. This class caches
 * {@link FactSurvivalRow} lists per scenario and handles only UI rendering.
 */
public class FactDrillDownPanel {

    private final RunHistoryStore runHistoryStore;
    private final ScenarioLoader scenarioLoader;

    /**
     * Cached survival rows per scenario, populated on first expand.
     */
    private final Map<String, List<FactSurvivalRow>> loadedRows = new HashMap<>();

    private ExperimentReport currentReport;

    public FactDrillDownPanel(RunHistoryStore runHistoryStore, ScenarioLoader scenarioLoader) {
        this.runHistoryStore = runHistoryStore;
        this.scenarioLoader = scenarioLoader;
    }

    /**
     * Store the report reference and reset any previously-loaded state.
     * Drill-down content is rendered lazily via {@link #renderDrillDownFor}.
     *
     * @param report the completed (or cancelled) experiment report to display
     */
    public void showReport(ExperimentReport report) {
        this.currentReport = report;
        this.loadedRows.clear();
    }

    /**
     * Render the per-fact survival table for a given metric and scenario into the
     * provided content area. Called lazily by {@link ConditionComparisonPanel} when
     * a metric's {@link com.vaadin.flow.component.details.Details} is expanded.
     *
     * @param metricKey   the metric key (currently unused -- all metrics share the same
     *                    per-fact survival view, but passed for future extensibility)
     * @param scenarioId  the scenario to drill into
     * @param contentArea the container to populate with drill-down content
     */
    public void renderDrillDownFor(String metricKey, String scenarioId, VerticalLayout contentArea) {
        if (currentReport == null) {
            contentArea.add(new Span("No report loaded."));
            return;
        }

        var rows = loadedRows.computeIfAbsent(scenarioId,
                                              k -> FactSurvivalLoader.loadFactSurvival(currentReport, runHistoryStore, scenarioLoader)
                                                                     .getOrDefault(k, List.of()));

        if (rows.isEmpty()) {
            contentArea.add(new Span("Per-fact drill-down requires ground truth data."));
            return;
        }

        contentArea.add(buildSurvivalTable(rows, currentReport.conditions()));
    }

    /**
     * Build a CSS-grid table showing survived/total counts and first-drift turn
     * per fact per condition. Each condition gets two columns: survival count and
     * first-drift turn.
     * <p>
     * Survival cells are color-coded:
     * <ul>
     *   <li>{@code data-health="good"} -- all runs survived</li>
     *   <li>{@code data-health="warn"} -- partial survival</li>
     *   <li>{@code data-health="bad"}  -- no runs survived</li>
     * </ul>
     */
    private Div buildSurvivalTable(List<FactSurvivalRow> rows, List<String> conditions) {
        var table = new Div();
        table.addClassName("ar-survival-table");
        table.getElement().setAttribute(
                "style",
                "display: grid; grid-template-columns: 2fr repeat(%d, 1fr auto)"
                        .formatted(conditions.size()));

        var factHeader = new Div();
        factHeader.addClassName("ar-survival-header");
        factHeader.setText("Fact");
        table.add(factHeader);

        for (var condition : conditions) {
            var survivalHeader = new Div();
            survivalHeader.addClassName("ar-survival-header");
            survivalHeader.setText(condition);
            table.add(survivalHeader);

            var driftHeader = new Div();
            driftHeader.addClassName("ar-survival-header");
            driftHeader.addClassName("ar-drift-header");
            driftHeader.setText("1st Drift");
            table.add(driftHeader);
        }

        for (var row : rows) {
            var factCell = new Div();
            factCell.addClassName("ar-survival-fact");
            var displayText = row.factText().length() > 80
                    ? row.factText().substring(0, 77) + "..."
                    : row.factText();
            factCell.setText(displayText);
            factCell.getElement().setAttribute("title", row.factText());
            table.add(factCell);

            for (var condition : conditions) {
                var result = row.conditionResults().get(condition);

                String survivalText;
                String health;

                if (result == null || result.total() == 0) {
                    survivalText = "\u2014";
                    health = "good";
                } else {
                    survivalText = "%d/%d".formatted(result.survived(), result.total());
                    health = result.survived() == result.total() ? "good"
                            : result.survived() == 0 ? "bad" : "warn";
                }

                var survivalCell = new Div();
                survivalCell.addClassName("ar-survival-cell");
                survivalCell.getElement().setAttribute("data-health", health);
                survivalCell.setText(survivalText);
                table.add(survivalCell);

                var driftCell = new Div();
                driftCell.addClassName("ar-survival-cell");
                driftCell.addClassName("ar-drift-cell");

                if (result != null && result.firstDriftTurn().isPresent()) {
                    driftCell.setText("turn %d".formatted(result.firstDriftTurn().getAsInt()));
                    driftCell.getElement().setAttribute("data-health", "bad");
                } else {
                    driftCell.setText("no drift");
                    driftCell.getElement().setAttribute("data-health", "good");
                }

                table.add(driftCell);
            }
        }

        return table;
    }
}
