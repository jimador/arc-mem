package dev.arcmem.simulator.reevaluation;

import dev.arcmem.simulator.engine.ScoringResult;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class ReEvaluationExporter {

    private static final String CSV_HEADER =
            "runId,condition,scenario,judge_mode,factSurvivalRate,contradictionCount,majorContradictionCount,driftAbsorptionRate,erosionRate,complianceRate";

    public String exportCsv(ReEvaluationReport report) {
        var sb = new StringBuilder();
        sb.append(CSV_HEADER).append("\n");

        for (var paired : report.pairedResults()) {
            appendRow(sb, paired.runId(), paired.condition(), paired.scenarioId(), "original", paired.originalMetrics());
            appendRow(sb, paired.runId(), paired.condition(), paired.scenarioId(), report.judgeConfig().label(), paired.reEvaluatedMetrics());
        }

        return sb.toString().stripTrailing();
    }

    public String exportSummaryMarkdown(ReEvaluationReport report) {
        var sb = new StringBuilder();
        sb.append("# Re-Evaluation Summary\n\n");
        sb.append("| Condition | Mean Delta factSurvivalRate | Mean Delta erosionRate |\n");
        sb.append("|-----------|----------------------------|------------------------|\n");

        report.pairedResults().stream()
                .collect(Collectors.groupingBy(PairedRunResult::condition))
                .forEach((condition, pairs) -> {
                    double meanFsrDelta = pairs.stream()
                            .mapToDouble(p -> p.reEvaluatedMetrics().factSurvivalRate() - p.originalMetrics().factSurvivalRate())
                            .average()
                            .orElse(0.0);
                    double meanErosionDelta = pairs.stream()
                            .mapToDouble(p -> p.reEvaluatedMetrics().erosionRate() - p.originalMetrics().erosionRate())
                            .average()
                            .orElse(0.0);
                    sb.append("| ").append(condition)
                            .append(" | ").append(String.format("%.2f", meanFsrDelta))
                            .append(" | ").append(String.format("%.2f", meanErosionDelta))
                            .append(" |\n");
                });

        return sb.toString().stripTrailing();
    }

    private void appendRow(StringBuilder sb, String runId, String condition, String scenario, String judgeMode, ScoringResult metrics) {
        sb.append(escapeCsv(runId)).append(",")
                .append(escapeCsv(condition)).append(",")
                .append(escapeCsv(scenario)).append(",")
                .append(judgeMode).append(",")
                .append(metrics.factSurvivalRate()).append(",")
                .append(metrics.contradictionCount()).append(",")
                .append(metrics.majorContradictionCount()).append(",")
                .append(metrics.driftAbsorptionRate()).append(",")
                .append(metrics.erosionRate()).append(",")
                .append(metrics.complianceRate()).append("\n");
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
