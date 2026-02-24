package dev.dunnam.diceanchors.sim.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Self-contained evaluation report capturing experiment configuration,
 * statistical results, per-fact survival, and positioning narrative.
 * <p>
 * Invariant RR1: fully constructible from an ExperimentReport without
 * additional user input.
 *
 * @param title              report title
 * @param generatedAt        generation timestamp
 * @param experimentName     source experiment name
 * @param conditions         condition names in the experiment
 * @param scenarioIds        scenario IDs in the experiment
 * @param repetitionsPerCell repetitions per cell
 * @param cancelled          whether the source experiment was cancelled
 * @param overallScore       composite resilience score (FULL_ANCHORS or best condition)
 * @param conditionScores    per-condition resilience scores
 * @param scenarioSections   per-scenario result sections
 * @param strategySection    strategy effectiveness breakdown
 * @param positioning        positioning statement relative to related systems
 */
public record ResilienceReport(
        String title,
        Instant generatedAt,
        String experimentName,
        List<String> conditions,
        List<String> scenarioIds,
        int repetitionsPerCell,
        boolean cancelled,
        ResilienceScore overallScore,
        Map<String, ResilienceScore> conditionScores,
        List<ScenarioSection> scenarioSections,
        StrategySection strategySection,
        String positioning) {
}
