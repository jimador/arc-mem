package dev.arcmem.simulator.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * CLI entry point for running experiment matrices from YAML config files.
 * Activated when {@code arc-mem.experiment.config} property is set.
 * <p>
 * Usage: {@code ./mvnw spring-boot:run -Darc-mem.experiment.config=paper-matrix.yml}
 */
@Component
@ConditionalOnProperty(name = "arc-mem.experiment.config")
public class ExperimentMatrixCli implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ExperimentMatrixCli.class);

    private final ExperimentMatrixRunner matrixRunner;
    private final String configPath;

    public ExperimentMatrixCli(ExperimentMatrixRunner matrixRunner,
                                @Value("${arc-mem.experiment.config}") String configPath) {
        this.matrixRunner = matrixRunner;
        this.configPath = configPath;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            logger.info("Experiment CLI: loading config from {}", configPath);
            var yamlMapper = new ObjectMapper(new YAMLFactory());
            var config = yamlMapper.readValue(Path.of(configPath).toFile(), ExperimentMatrixConfig.class);
            logger.info("Experiment CLI: {} conditions × {} scenarios × {} reps",
                    config.conditions().size(), config.scenarios().size(), config.repetitions());
            var outputDir = matrixRunner.runMatrix(config);
            logger.info("Experiment CLI: results written to {}", outputDir);
            System.exit(0);
        } catch (Exception e) {
            logger.error("Experiment CLI: failed", e);
            System.exit(1);
        }
    }
}
