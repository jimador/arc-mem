package dev.dunnam.diceanchors.sim.report;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Self-contained evaluation report capturing experiment configuration,
 * statistical results, per-fact survival, and positioning narrative.
 * <p>
 * Invariant RR1: fully constructible from an ExperimentReport without
 * additional user input.
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
        @Nullable String positioning,
        @Nullable String modelId) {

    public ResilienceReport(
            String title, Instant generatedAt, String experimentName,
            List<String> conditions, List<String> scenarioIds, int repetitionsPerCell,
            boolean cancelled, ResilienceScore overallScore,
            Map<String, ResilienceScore> conditionScores, List<ScenarioSection> scenarioSections,
            StrategySection strategySection, @Nullable String positioning) {
        this(title, generatedAt, experimentName, conditions, scenarioIds, repetitionsPerCell,
                cancelled, overallScore, conditionScores, scenarioSections, strategySection,
                positioning, null);
    }
}
