package dev.arcmem.simulator.reevaluation;

import dev.arcmem.simulator.engine.ScoringResult;
import dev.arcmem.simulator.evaluation.JudgeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReEvaluationExporter")
class ReEvaluationExporterTest {

    @Test
    @DisplayName("exportCsvProducesLongFormatWithBothJudgeModes")
    void exportCsvProducesLongFormatWithBothJudgeModes() {
        var original = new ScoringResult(86.0, 3, 2, 89.0, 3.0, 2, Map.of(), 0, 85.0, 15.0);
        var reEvaluated = new ScoringResult(92.0, 1, 1, 95.0, 5.0, 2, Map.of(), 0, 95.0, 0.0);
        var paired = new PairedRunResult("run-1", "FULL_AWMU", "adversarial-contradictory", original, reEvaluated);
        var report = new ReEvaluationReport("exp-1", JudgeConfig.hardened(), Instant.now(), List.of(paired));

        var csv = new ReEvaluationExporter().exportCsv(report);
        var lines = csv.split("\n");

        assertThat(lines[0]).startsWith("runId,condition,scenario,judge_mode,");
        assertThat(lines).hasSize(3); // header + 2 rows (original + hardened)
        assertThat(lines[1]).startsWith("run-1,FULL_AWMU,adversarial-contradictory,original,");
        assertThat(lines[2]).startsWith("run-1,FULL_AWMU,adversarial-contradictory,hardened,");
        assertThat(lines[1]).contains("86.0");
        assertThat(lines[2]).contains("92.0");
    }
}
