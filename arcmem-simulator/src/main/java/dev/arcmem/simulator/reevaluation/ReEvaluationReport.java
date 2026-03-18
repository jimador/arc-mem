package dev.arcmem.simulator.reevaluation;

import dev.arcmem.simulator.evaluation.JudgeConfig;

import java.time.Instant;
import java.util.List;

public record ReEvaluationReport(
        String experimentReportId,
        JudgeConfig judgeConfig,
        Instant createdAt,
        List<PairedRunResult> pairedResults
) {}
