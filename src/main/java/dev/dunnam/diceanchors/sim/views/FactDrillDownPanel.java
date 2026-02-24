package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentReport;
import dev.dunnam.diceanchors.sim.engine.EvalVerdict;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScenarioLoader;
import dev.dunnam.diceanchors.sim.engine.SimulationRunRecord;
import dev.dunnam.diceanchors.sim.engine.SimulationRunRecord.TurnSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drill-down renderer providing per-metric, per-scenario fact survival tables.
 * <p>
 * Not a standalone visual panel. Instead, {@link ConditionComparisonPanel} delegates
 * to {@link #renderDrillDownFor(String, String, VerticalLayout)} to lazily populate
 * inline drill-down content within each metric's {@link com.vaadin.flow.component.details.Details}.
 * <p>
 * Per-fact survival uses {@link EvalVerdict#factId()} as the stable cross-condition
 * join key. This ID originates from {@link dev.dunnam.diceanchors.sim.engine.SimulationScenario.GroundTruth#id()} and
 * is consistent across all ablation conditions (including NO_ANCHORS). Display text
 * is resolved from the scenario's ground truth list via {@link ScenarioLoader}.
 * <p>
 * First-drift turn is the earliest turn number at which a fact was contradicted
 * across all runs for a given condition. Displayed as "T3" (turn 3) or "—" if never.
 */
public class FactDrillDownPanel {

    private static final Logger logger = LoggerFactory.getLogger(FactDrillDownPanel.class);

    private final RunHistoryStore runHistoryStore;
    private final ScenarioLoader scenarioLoader;

    /** Cached run records per cell key (condition:scenarioId), populated on first expand. */
    private final Map<String, List<SimulationRunRecord>> loadedRecords = new HashMap<>();

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
        this.loadedRecords.clear();
    }

    // -------------------------------------------------------------------------
    // Public API: drill-down rendering
    // -------------------------------------------------------------------------

    /**
     * Render the per-fact survival table for a given metric and scenario into the
     * provided content area. Called lazily by {@link ConditionComparisonPanel} when
     * a metric's {@link com.vaadin.flow.component.details.Details} is expanded.
     *
     * @param metricKey   the metric key (currently unused — all metrics share the same
     *                    per-fact survival view, but passed for future extensibility)
     * @param scenarioId  the scenario to drill into
     * @param contentArea the container to populate with drill-down content
     */
    public void renderDrillDownFor(String metricKey, String scenarioId, VerticalLayout contentArea) {
        if (currentReport == null) {
            contentArea.add(new Span("No report loaded."));
            return;
        }

        // Build factId -> display text index from the scenario's ground truth list.
        var factIdToText = buildGroundTruthIndex(scenarioId);

        // factId -> condition -> FactStats
        var factStatsByCondition = new HashMap<String, Map<String, FactStats>>();

        for (var condition : currentReport.conditions()) {
            var cellKey = condition + ":" + scenarioId;
            var records = loadCellRecords(cellKey);

            var factStatsMap = new HashMap<String, FactStats>();
            factStatsByCondition.put(condition, factStatsMap);

            for (var record : records) {
                var firstDriftTurns = collectFirstDriftTurns(record);

                for (var factId : factIdToText.keySet()) {
                    var current = factStatsMap.computeIfAbsent(factId,
                            k -> new FactStats(0, 0, Integer.MAX_VALUE));

                    var driftTurn = firstDriftTurns.getOrDefault(factId, Integer.MAX_VALUE);
                    var contradicted = firstDriftTurns.containsKey(factId);
                    factStatsMap.put(factId, current.withRun(contradicted, driftTurn));
                }
            }
        }

        var orderedFactIds = new ArrayList<>(factIdToText.keySet());

        if (orderedFactIds.isEmpty()) {
            var totalLoaded = loadedRecords.values().stream().mapToInt(List::size).sum();
            contentArea.add(new Span(
                    "Per-fact drill-down requires ground truth data. Run records: %d loaded."
                            .formatted(totalLoaded)));
            return;
        }

        contentArea.add(buildSurvivalTable(
                orderedFactIds, factIdToText, currentReport.conditions(), factStatsByCondition));
    }

    // -------------------------------------------------------------------------
    // Private: data types
    // -------------------------------------------------------------------------

    /**
     * Per-fact aggregation: survival count and earliest drift turn per condition.
     *
     * @param survived  number of runs where this fact was not contradicted
     * @param total     total runs examined
     * @param firstDrift earliest turn number where contradiction occurred (Integer.MAX_VALUE if never)
     */
    private record FactStats(int survived, int total, int firstDrift) {
        FactStats withRun(boolean contradicted, int driftTurn) {
            var newTotal = total + 1;
            var newSurvived = contradicted ? survived : survived + 1;
            var newDrift = contradicted ? Math.min(firstDrift, driftTurn) : firstDrift;
            return new FactStats(newSurvived, newTotal, newDrift);
        }
    }

    // -------------------------------------------------------------------------
    // Private: data loading
    // -------------------------------------------------------------------------

    /**
     * Build a factId -> display text index from the scenario's ground truth list.
     * Uses {@link ScenarioLoader} to load the scenario. Returns an ordered map
     * preserving the scenario's ground truth definition order.
     */
    private Map<String, String> buildGroundTruthIndex(String scenarioId) {
        var index = new LinkedHashMap<String, String>();
        try {
            var scenario = scenarioLoader.load(scenarioId);
            if (scenario.groundTruth() != null) {
                for (var fact : scenario.groundTruth()) {
                    if (fact.id() != null && fact.text() != null) {
                        index.put(fact.id(), fact.text());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load scenario {} for ground truth index: {}", scenarioId, e.getMessage());
        }
        return index;
    }

    /**
     * Load run records for a given cell key, using the in-memory cache if already populated.
     */
    private List<SimulationRunRecord> loadCellRecords(String cellKey) {
        return loadedRecords.computeIfAbsent(cellKey, k -> {
            try {
                var benchReport = currentReport.cellReports().get(k);
                if (benchReport == null) {
                    return List.of();
                }
                var records = new ArrayList<SimulationRunRecord>();
                for (var runId : benchReport.runIds()) {
                    runHistoryStore.load(runId).ifPresent(records::add);
                }
                return records;
            } catch (Exception e) {
                logger.warn("Failed to load run records for cell {}: {}", k, e.getMessage());
                return List.of();
            }
        });
    }

    /**
     * Collect the earliest turn number at which each factId received a
     * {@link EvalVerdict.Verdict#CONTRADICTED} verdict.
     *
     * @return map of factId to first-drift turn number; absent means never contradicted
     */
    private Map<String, Integer> collectFirstDriftTurns(SimulationRunRecord record) {
        var firstDrift = new HashMap<String, Integer>();
        for (TurnSnapshot snapshot : record.turnSnapshots()) {
            if (snapshot.verdicts() == null) {
                continue;
            }
            for (var verdict : snapshot.verdicts()) {
                if (verdict != null
                        && verdict.verdict() == EvalVerdict.Verdict.CONTRADICTED
                        && verdict.factId() != null) {
                    firstDrift.merge(verdict.factId(), snapshot.turnNumber(), Math::min);
                }
            }
        }
        return firstDrift;
    }

    // -------------------------------------------------------------------------
    // Private: survival table rendering
    // -------------------------------------------------------------------------

    /**
     * Build a CSS-grid table showing survived/total counts and first-drift turn
     * per fact per condition. Each condition gets two columns: survival count and
     * first-drift turn.
     * <p>
     * Survival cells are color-coded:
     * <ul>
     *   <li>{@code data-health="good"} — all runs survived</li>
     *   <li>{@code data-health="warn"} — partial survival</li>
     *   <li>{@code data-health="bad"}  — no runs survived</li>
     * </ul>
     */
    private Div buildSurvivalTable(
            List<String> factIds,
            Map<String, String> factIdToText,
            List<String> conditions,
            Map<String, Map<String, FactStats>> factStatsByCondition) {

        var table = new Div();
        table.addClassName("ar-survival-table");
        table.getElement().setAttribute(
                "style",
                "display: grid; grid-template-columns: 2fr repeat(%d, 1fr auto)"
                        .formatted(conditions.size()));

        // Header row
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

        // Data rows
        for (var factId : factIds) {
            var fullText = factIdToText.getOrDefault(factId, factId);
            var factCell = new Div();
            factCell.addClassName("ar-survival-fact");
            var displayText = fullText.length() > 80 ? fullText.substring(0, 77) + "..." : fullText;
            factCell.setText(displayText);
            factCell.getElement().setAttribute("title", fullText);
            table.add(factCell);

            for (var condition : conditions) {
                var condMap = factStatsByCondition.get(condition);
                var stats = condMap != null ? condMap.get(factId) : null;

                String survivalText;
                String health;

                if (stats == null || stats.total() == 0) {
                    survivalText = "—";
                    health = "good";
                } else {
                    survivalText = "%d/%d".formatted(stats.survived(), stats.total());
                    health = stats.survived() == stats.total() ? "good"
                            : stats.survived() == 0 ? "bad" : "warn";
                }

                var survivalCell = new Div();
                survivalCell.addClassName("ar-survival-cell");
                survivalCell.getElement().setAttribute("data-health", health);
                survivalCell.setText(survivalText);
                table.add(survivalCell);

                var driftCell = new Div();
                driftCell.addClassName("ar-survival-cell");
                driftCell.addClassName("ar-drift-cell");

                if (stats != null && stats.firstDrift() < Integer.MAX_VALUE) {
                    driftCell.setText("turn %d".formatted(stats.firstDrift()));
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
