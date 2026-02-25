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
public class DriftSummaryPanel extends VerticalLayout implements SimulationProgressListener {

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
        title.addClassName("ar-section-title");

        metricsGrid = new Div();
        metricsGrid.addClassName("ar-metrics-grid");

        strategySection = new VerticalLayout();
        strategySection.setPadding(false);
        strategySection.setSpacing(true);

        assertionSection = new VerticalLayout();
        assertionSection.setPadding(false);
        assertionSection.setSpacing(true);

        add(title, metricsGrid, strategySection, assertionSection);
    }

    @Override
    public void onTurnCompleted(SimulationProgress progress) {
        if (progress.verdicts() != null && !progress.verdicts().isEmpty()) {
            recordTurnVerdicts(progress.turnNumber(), progress.verdicts());
        }
    }

    @Override
    public void onSimulationCompleted(SimulationProgress progress) {
        if (progress.phase() != SimulationProgress.SimulationPhase.CANCELLED) {
            showResults(progress);
        }
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

        metricsGrid.add(
                metricCard("Survival Rate", "%.0f%%".formatted(survivalRate),
                           survivalRate >= 80 ? "good" : survivalRate >= 50 ? "warn" : "bad"),
                metricCard("Contradictions", String.valueOf(contradictionCount),
                           contradictionCount == 0 ? "good" : contradictionCount <= 2 ? "warn" : "bad"),
                metricCard("Major Contradictions", String.valueOf(majorContradictionCount),
                           majorContradictionCount == 0 ? "good" : "bad"),
                metricCard("Mean First Drift", meanFirstDrift,
                           "N/A".equals(meanFirstDrift) ? "good" : "warn"),
                metricCard("Attribution", attributionLabel,
                           scoring != null
                                   ? (scoring.anchorAttributionCount() > 0 ? "good" : "warn")
                                   : (allVerdicts.isEmpty() || confirmedCount > allVerdicts.size() / 2
                                   ? "good" : "warn")),
                metricCard("Absorption Rate", "%.0f%%".formatted(absorptionRate),
                           absorptionRate >= 80 ? "good" : absorptionRate >= 50 ? "warn" : "bad")
        );

        if (scoring != null && scoring.strategyEffectiveness() != null
            && !scoring.strategyEffectiveness().isEmpty()) {
            renderStrategyBreakdown(scoring.strategyEffectiveness());
        }

        if (progress.assertionResults() != null && !progress.assertionResults().isEmpty()) {
            var assertionTitle = new H4("Assertion Results");
            assertionTitle.addClassName("ar-section-title--inner");
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

    /**
     * Render per-strategy contradiction rate breakdown for adversarial scenarios.
     * Each strategy shows its name and contradiction rate as a colored bar.
     */
    private void renderStrategyBreakdown(Map<String, Double> strategyEffectiveness) {
        var title = new H4("Strategy Effectiveness");
        title.addClassName("ar-section-title--inner");
        strategySection.add(title);

        for (var entry : strategyEffectiveness.entrySet()) {
            var strategy = entry.getKey();
            var rate = entry.getValue();
            var pct = rate * 100.0;
            var health = pct == 0 ? "good" : pct <= 30 ? "warn" : "bad";

            var row = new HorizontalLayout();
            row.setWidthFull();
            row.setSpacing(true);
            row.setAlignItems(HorizontalLayout.Alignment.CENTER);

            var nameSpan = new Span(strategy);
            nameSpan.addClassName("ar-strategy-name");

            var barOuter = new Div();
            barOuter.addClassName("ar-bar-outer");

            var barInner = new Div();
            barInner.addClassName("ar-bar-inner");
            barInner.getElement().setAttribute("data-health", health);
            barInner.getElement().setAttribute("style", "width: %.0f%%".formatted(Math.min(100, pct)));
            barOuter.add(barInner);

            var rateSpan = new Span("%.0f%%".formatted(pct));
            rateSpan.addClassName("ar-strategy-rate");
            rateSpan.getElement().setAttribute("data-health", health);

            row.add(nameSpan, barOuter, rateSpan);
            strategySection.add(row);
        }
    }

    private Div metricCard(String label, String value, String health) {
        var card = new Div();
        card.addClassName("ar-metric-card");
        card.getElement().setAttribute("data-health", health);

        var description = METRIC_DESCRIPTIONS.get(label);
        if (description != null) {
            card.getElement().setAttribute("title", description);
        }

        var valueSpan = new Span(value);
        valueSpan.addClassName("ar-metric-value");
        valueSpan.getElement().setAttribute("data-health", health);

        var labelSpan = new Span(label);
        labelSpan.addClassName("ar-metric-label");

        card.add(valueSpan, labelSpan);
        return card;
    }

    private static final Map<String, String> METRIC_DESCRIPTIONS = Map.of(
            "Survival Rate", "Percentage of ground truth facts that were confirmed by the DM and never contradicted. Facts the DM never mentioned are not counted as survived.",
            "Contradictions", "Total number of individual contradiction verdicts across all evaluated turns.",
            "Major Contradictions", "Number of contradiction verdicts classified as major severity \u2014 direct, unambiguous reversals of established facts.",
            "Absorption Rate", "Percentage of engaged turns (where the DM confirmed or contradicted at least one fact) that had zero contradictions. Turns where no facts were mentioned are excluded.",
            "Attribution", "Number of distinct ground truth facts that received at least one CONFIRMED verdict, indicating the DM actively referenced them.",
            "Mean First Drift", "Average turn number at which each contradicted fact was first contradicted. Higher means facts held longer before drifting. N/A if no contradictions occurred."
    );

    private Div assertionResultCard(AssertionResult result) {
        var assertion = result.passed() ? "pass" : "fail";

        var card = new Div();
        card.addClassName("ar-assertion-card");
        card.getElement().setAttribute("data-assertion", assertion);

        var badge = new Span(result.passed() ? "PASS" : "FAIL");
        badge.addClassName("ar-badge");
        badge.getElement().setAttribute("data-assertion", assertion);
        badge.getElement().setAttribute("style", "margin-right: 8px");

        var name = new Span(result.name());
        name.addClassName("ar-assertion-name");

        var details = new Paragraph(result.details());
        details.addClassName("ar-assertion-details");

        card.add(badge, name, details);
        return card;
    }

    private int countMajorDrifts() {
        // Counts consecutive contradictions (2+ in a row) as "major"
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
