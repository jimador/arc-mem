package dev.dunnam.diceanchors.sim.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import dev.dunnam.diceanchors.sim.engine.AssertionResult;
import dev.dunnam.diceanchors.sim.engine.EvalVerdict;
import dev.dunnam.diceanchors.sim.engine.ScoringResult;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Summary panel showing drift metrics and assertion results after simulation completion.
 * <p>
 * Displays a 3x3 CSS grid with nine metrics when {@link ScoringResult} is available:
 * <ul>
 *   <li>Survival rate (fact survival from ScoringResult or anchor count fallback)</li>
 *   <li>Contradiction count</li>
 *   <li>Major contradictions (from ScoringResult, or consecutive-drift fallback)</li>
 *   <li>Mean first drift turn</li>
 *   <li>Attribution accuracy (from ScoringResult anchor attribution count)</li>
 *   <li>Absorption rate</li>
 * </ul>
 * When adversarial scenarios include per-strategy metrics, a strategy effectiveness
 * breakdown section is rendered below the grid.
 * Below that, assertion results are shown with pass/fail badges.
 */
public class DriftSummaryPanel extends VerticalLayout {

    private final Div metricsGrid;
    private final VerticalLayout strategySection;
    private final VerticalLayout assertionSection;

    private int totalTurns;
    private int seedAnchorCount;
    private final List<EvalVerdict> allVerdicts = new ArrayList<>();
    private final List<Integer> contradictionTurns = new ArrayList<>();
    private int currentAnchorCount;
    private int confirmedCount;

    public DriftSummaryPanel() {
        setVisible(false);
        setPadding(true);
        setSpacing(true);
        setWidthFull();

        var title = new H4("Drift Summary");
        title.getStyle().set("margin", "0 0 8px 0");

        metricsGrid = new Div();
        metricsGrid.getStyle()
                   .set("display", "grid")
                   .set("grid-template-columns", "repeat(3, 1fr)")
                   .set("gap", "12px")
                   .set("width", "100%");

        strategySection = new VerticalLayout();
        strategySection.setPadding(false);
        strategySection.setSpacing(true);

        assertionSection = new VerticalLayout();
        assertionSection.setPadding(false);
        assertionSection.setSpacing(true);

        add(title, metricsGrid, strategySection, assertionSection);
    }

    /**
     * Track verdicts from each turn for aggregate metrics.
     */
    public void recordTurnVerdicts(int turnNumber, List<EvalVerdict> verdicts) {
        if (verdicts == null) {
            return;
        }
        for (var verdict : verdicts) {
            if (verdict == null) {
                continue;
            }
            allVerdicts.add(verdict);
            if (verdict.verdict() == EvalVerdict.Verdict.CONTRADICTED) {
                contradictionTurns.add(turnNumber);
            }
            if (verdict.verdict() == EvalVerdict.Verdict.CONFIRMED) {
                confirmedCount++;
            }
        }
    }

    /**
     * Update with final progress data and display metrics.
     * Uses {@link ScoringResult} when available on the progress; falls back
     * to inline computation from accumulated verdicts when null.
     */
    public void showResults(SimulationProgress progress) {
        this.totalTurns = progress.totalTurns();
        this.currentAnchorCount = progress.activeAnchors() != null ? progress.activeAnchors().size() : 0;

        metricsGrid.removeAll();
        strategySection.removeAll();
        assertionSection.removeAll();

        var scoring = progress.scoringResult();

        // Compute metrics — prefer ScoringResult, fall back to inline
        var survivalRate = scoring != null
                ? scoring.factSurvivalRate()
                : (seedAnchorCount > 0
                ? (double) currentAnchorCount / seedAnchorCount * 100.0
                : 100.0);

        var contradictionCount = scoring != null
                ? scoring.contradictionCount()
                : contradictionTurns.size();

        var majorContradictionCount = scoring != null
                ? scoring.majorContradictionCount()
                : countMajorDrifts();

        var meanFirstDrift = scoring != null
                ? (Double.isNaN(scoring.meanTurnsToFirstDrift())
                ? "N/A"
                : "%.1f".formatted(scoring.meanTurnsToFirstDrift()))
                : (contradictionTurns.isEmpty()
                ? "N/A"
                : "%.1f".formatted(contradictionTurns.stream().mapToInt(i -> i).average().orElse(0)));

        var attributionAccuracy = scoring != null
                ? scoring.anchorAttributionCount()
                : (allVerdicts.isEmpty()
                ? 0
                : confirmedCount);

        var attributionLabel = scoring != null
                ? "%d facts".formatted(attributionAccuracy)
                : "%.0f%%".formatted(allVerdicts.isEmpty()
                                             ? 100.0
                                             : (double) confirmedCount / allVerdicts.size() * 100.0);

        var absorptionRate = scoring != null
                ? scoring.driftAbsorptionRate()
                : (totalTurns > 0
                ? (double) (totalTurns - contradictionTurns.size()) / totalTurns * 100.0
                : 100.0);

        // Render metrics grid
        metricsGrid.add(
                metricCard("Survival Rate", "%.0f%%".formatted(survivalRate),
                           survivalRate >= 80 ? "#4caf50" : survivalRate >= 50 ? "#ff9800" : "#f44336"),
                metricCard("Contradictions", String.valueOf(contradictionCount),
                           contradictionCount == 0 ? "#4caf50" : contradictionCount <= 2 ? "#ff9800" : "#f44336"),
                metricCard("Major Contradictions", String.valueOf(majorContradictionCount),
                           majorContradictionCount == 0 ? "#4caf50" : "#f44336"),
                metricCard("Mean First Drift", meanFirstDrift,
                           "N/A".equals(meanFirstDrift) ? "#4caf50" : "#ff9800"),
                metricCard("Attribution", attributionLabel,
                           scoring != null
                                   ? (scoring.anchorAttributionCount() > 0 ? "#4caf50" : "#ff9800")
                                   : (allVerdicts.isEmpty() || confirmedCount > allVerdicts.size() / 2
                                   ? "#4caf50" : "#ff9800")),
                metricCard("Absorption Rate", "%.0f%%".formatted(absorptionRate),
                           absorptionRate >= 80 ? "#4caf50" : absorptionRate >= 50 ? "#ff9800" : "#f44336")
        );

        // Strategy effectiveness breakdown (8.3)
        if (scoring != null && scoring.strategyEffectiveness() != null
            && !scoring.strategyEffectiveness().isEmpty()) {
            renderStrategyBreakdown(scoring.strategyEffectiveness());
        }

        // Render assertion results
        if (progress.assertionResults() != null && !progress.assertionResults().isEmpty()) {
            var assertionTitle = new H4("Assertion Results");
            assertionTitle.getStyle().set("margin", "12px 0 4px 0");
            assertionSection.add(assertionTitle);

            for (var result : progress.assertionResults()) {
                assertionSection.add(assertionResultCard(result));
            }
        }

        setVisible(true);
    }

    /**
     * Reset the panel to its hidden initial state.
     */
    public void reset() {
        setVisible(false);
        metricsGrid.removeAll();
        strategySection.removeAll();
        assertionSection.removeAll();
        allVerdicts.clear();
        contradictionTurns.clear();
        confirmedCount = 0;
        seedAnchorCount = 0;
        totalTurns = 0;
        currentAnchorCount = 0;
    }

    /**
     * Set the initial anchor count for survival rate calculation.
     */
    public void setSeedAnchorCount(int count) {
        this.seedAnchorCount = count;
    }

    // -------------------------------------------------------------------------
    // Private rendering helpers
    // -------------------------------------------------------------------------

    /**
     * Render per-strategy contradiction rate breakdown for adversarial scenarios.
     * Each strategy shows its name and contradiction rate as a colored bar.
     */
    private void renderStrategyBreakdown(Map<String, Double> strategyEffectiveness) {
        var title = new H4("Strategy Effectiveness");
        title.getStyle().set("margin", "12px 0 4px 0");
        strategySection.add(title);

        for (var entry : strategyEffectiveness.entrySet()) {
            var strategy = entry.getKey();
            var rate = entry.getValue();
            var pct = rate * 100.0;

            var row = new HorizontalLayout();
            row.setWidthFull();
            row.setSpacing(true);
            row.setAlignItems(HorizontalLayout.Alignment.CENTER);

            var nameSpan = new Span(strategy);
            nameSpan.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("min-width", "180px");

            var barOuter = new Div();
            barOuter.getStyle()
                    .set("flex-grow", "1")
                    .set("height", "8px")
                    .set("background", "var(--lumo-contrast-10pct)")
                    .set("border-radius", "4px")
                    .set("overflow", "hidden");

            var barColor = pct == 0 ? "#4caf50" : pct <= 30 ? "#ff9800" : "#f44336";
            var barInner = new Div();
            barInner.getStyle()
                    .set("width", "%.0f%%".formatted(Math.min(100, pct)))
                    .set("height", "100%")
                    .set("background", barColor)
                    .set("border-radius", "4px");
            barOuter.add(barInner);

            var rateSpan = new Span("%.0f%%".formatted(pct));
            rateSpan.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("font-weight", "bold")
                    .set("color", barColor)
                    .set("min-width", "40px")
                    .set("text-align", "right");

            row.add(nameSpan, barOuter, rateSpan);
            strategySection.add(row);
        }
    }

    private Div metricCard(String label, String value, String accentColor) {
        var card = new Div();
        card.getStyle()
            .set("border", "1px solid var(--lumo-contrast-20pct)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("padding", "12px")
            .set("text-align", "center")
            .set("background", "var(--lumo-base-color)")
            .set("border-top", "3px solid " + accentColor);

        var valueSpan = new Span(value);
        valueSpan.getStyle()
                 .set("font-size", "var(--lumo-font-size-xl)")
                 .set("font-weight", "bold")
                 .set("color", accentColor)
                 .set("display", "block");

        var labelSpan = new Span(label);
        labelSpan.getStyle()
                 .set("font-size", "var(--lumo-font-size-xs)")
                 .set("color", "var(--lumo-secondary-text-color)")
                 .set("display", "block")
                 .set("margin-top", "4px");

        card.add(valueSpan, labelSpan);
        return card;
    }

    private Div assertionResultCard(AssertionResult result) {
        var card = new Div();
        var color = result.passed() ? "#4caf50" : "#f44336";
        card.getStyle()
            .set("border-left", "3px solid " + color)
            .set("padding", "6px 12px")
            .set("margin-bottom", "4px")
            .set("border-radius", "var(--lumo-border-radius-s)")
            .set("background", result.passed()
                    ? "var(--lumo-success-color-10pct)"
                    : "var(--lumo-error-color-10pct)");

        var badge = new Span(result.passed() ? "PASS" : "FAIL");
        badge.getStyle()
             .set("font-size", "var(--lumo-font-size-xxs)")
             .set("font-weight", "bold")
             .set("padding", "1px 5px")
             .set("border-radius", "var(--lumo-border-radius-s)")
             .set("color", "white")
             .set("background", color)
             .set("margin-right", "8px");

        var name = new Span(result.name());
        name.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("font-weight", "bold");

        var details = new Paragraph(result.details());
        details.getStyle()
               .set("font-size", "var(--lumo-font-size-xs)")
               .set("color", "var(--lumo-secondary-text-color)")
               .set("margin", "2px 0 0 0");

        card.add(badge, name, details);
        return card;
    }

    private int countMajorDrifts() {
        // Major drift = consecutive contradictions (2+ in a row)
        int major = 0;
        int consecutive = 0;
        for (var verdict : allVerdicts) {
            if (verdict.verdict() == EvalVerdict.Verdict.CONTRADICTED) {
                consecutive++;
                if (consecutive >= 2) {
                    major++;
                }
            } else {
                consecutive = 0;
            }
        }
        return major;
    }
}
