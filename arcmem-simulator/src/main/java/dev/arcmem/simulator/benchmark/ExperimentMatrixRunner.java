package dev.arcmem.simulator.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.arcmem.simulator.report.ExperimentExporter;
import dev.arcmem.simulator.report.MarkdownReportRenderer;
import dev.arcmem.simulator.report.ResilienceReportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ExperimentMatrixRunner {

    private static final Logger logger = LoggerFactory.getLogger(ExperimentMatrixRunner.class);

    private final ExperimentRunner experimentRunner;
    private final ExperimentExporter exporter;
    private final ResilienceReportBuilder reportBuilder;
    private final ObjectMapper objectMapper;

    public ExperimentMatrixRunner(ExperimentRunner experimentRunner,
                                  ExperimentExporter exporter,
                                  ResilienceReportBuilder reportBuilder,
                                  ObjectMapper objectMapper) {
        this.experimentRunner = experimentRunner;
        this.exporter = exporter;
        this.reportBuilder = reportBuilder;
        this.objectMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path runMatrix(ExperimentMatrixConfig config) throws IOException {
        var outputDir = Path.of(config.outputDir());
        Files.createDirectories(outputDir);

        var conditions = config.resolveConditions();
        var definition = new ExperimentDefinition(
                config.name(), conditions, config.scenarios(),
                config.repetitions(), Optional.ofNullable(config.model()));

        logger.info("Starting experiment matrix: {} conditions × {} scenarios × {} reps = {} total runs",
                conditions.size(), config.scenarios().size(), config.repetitions(), definition.totalRuns());

        var startedAt = Instant.now();
        var report = experimentRunner.runExperiment(definition, () -> true, () -> 4000, p -> {
            if (p.currentCell() > 0 && p.currentCell() % 5 == 0) {
                logger.info("Progress: {}/{} cells complete", p.currentCell(), definition.totalCells());
            }
        });
        var completedAt = Instant.now();
        var wallClockMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();

        logger.info("Experiment complete in {}ms. Exporting results to {}", wallClockMs, outputDir);

        var baseName = safeFileName(config.name());

        var jsonContent = exporter.exportJson(report);
        Files.writeString(outputDir.resolve(baseName + ".json"), jsonContent, StandardCharsets.UTF_8);

        var csvContent = exporter.exportCsv(report);
        Files.writeString(outputDir.resolve(baseName + ".csv"), csvContent, StandardCharsets.UTF_8);

        try {
            var resilienceReport = reportBuilder.build(report);
            var markdownContent = MarkdownReportRenderer.render(resilienceReport);
            Files.writeString(outputDir.resolve(baseName + ".md"), markdownContent, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Markdown report generation failed: {}", e.getMessage());
        }

        var manifest = new RunManifest(
                computeConfigHash(config),
                detectGitCommit(),
                startedAt, completedAt, wallClockMs,
                System.getProperty("java.version"),
                config.conditions(), config.scenarios(),
                config.repetitions(), definition.totalCells(),
                definition.totalRuns(), report.cancelled());
        Files.writeString(outputDir.resolve("manifest.json"),
                objectMapper.writeValueAsString(manifest), StandardCharsets.UTF_8);

        logger.info("All exports written to {}", outputDir);
        return outputDir;
    }

    private String computeConfigHash(ExperimentMatrixConfig config) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var configJson = objectMapper.writeValueAsString(config);
            var hash = digest.digest(configJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String detectGitCommit() {
        try {
            var process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 ? output.substring(0, Math.min(output.length(), 12)) : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String safeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
