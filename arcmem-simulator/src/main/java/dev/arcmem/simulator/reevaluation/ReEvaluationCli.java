package dev.arcmem.simulator.reevaluation;

import dev.arcmem.simulator.evaluation.JudgeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "arc-mem.reevaluate.experiment-id")
public class ReEvaluationCli implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ReEvaluationCli.class);

    private final ReEvaluationRunner runner;
    private final ReEvaluationExporter exporter;
    private final String experimentId;
    private final String outputDir;

    public ReEvaluationCli(
            ReEvaluationRunner runner,
            ReEvaluationExporter exporter,
            @Value("${arc-mem.reevaluate.experiment-id}") String experimentId,
            @Value("${arc-mem.reevaluate.output-dir:reevaluation-output}") String outputDir) {
        this.runner = runner;
        this.exporter = exporter;
        this.experimentId = experimentId;
        this.outputDir = outputDir;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            logger.info("Re-evaluation CLI: starting for experiment {}", experimentId);
            var report = runner.reEvaluate(experimentId, JudgeConfig.hardened());

            var outPath = Path.of(outputDir);
            Files.createDirectories(outPath);

            Files.writeString(outPath.resolve(experimentId + "-reevaluation.csv"),
                    exporter.exportCsv(report));
            Files.writeString(outPath.resolve(experimentId + "-reevaluation-summary.md"),
                    exporter.exportSummaryMarkdown(report));

            logger.info("Re-evaluation CLI: complete — {} paired results written to {}",
                    report.pairedResults().size(), outPath);
            System.exit(0);
        } catch (Exception e) {
            logger.error("Re-evaluation CLI: failed", e);
            System.exit(1);
        }
    }
}
