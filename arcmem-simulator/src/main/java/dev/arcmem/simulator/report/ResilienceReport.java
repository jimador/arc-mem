package dev.arcmem.simulator.report;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

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
