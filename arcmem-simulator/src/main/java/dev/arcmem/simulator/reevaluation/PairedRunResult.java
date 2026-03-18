package dev.arcmem.simulator.reevaluation;

import dev.arcmem.simulator.engine.ScoringResult;

public record PairedRunResult(
        String runId,
        String condition,
        String scenarioId,
        ScoringResult originalMetrics,
        ScoringResult reEvaluatedMetrics
) {}
