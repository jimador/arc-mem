package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers and loads YAML scenario files from {@code classpath:simulations/}.
 * Files ending in {@code .yml} or {@code .yaml} are eligible.
 */
@Service
public class ScenarioLoader {

    private static final Logger logger = LoggerFactory.getLogger(ScenarioLoader.class);

    private final ObjectMapper yamlMapper;

    public ScenarioLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Scan the classpath for all simulation YAML files and return parsed scenarios.
     * Files that fail to parse are logged as warnings and skipped.
     */
    public List<SimulationScenario> listScenarios() {
        var scenarios = new ArrayList<SimulationScenario>();
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            var resources = resolver.getResources("classpath:simulations/*.y*ml");
            for (var resource : resources) {
                try {
                    var scenario = yamlMapper.readValue(resource.getInputStream(), SimulationScenario.class);
                    if (scenario.id() == null || scenario.id().isBlank()) {
                        logger.debug("Skipping non-scenario file: {}", resource.getFilename());
                    } else {
                        scenarios.add(scenario);
                        logger.debug("Loaded scenario: {}", scenario.id());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to load scenario {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to scan simulation directory: {}", e.getMessage());
        }
        return scenarios;
    }

    /**
     * Filter scenarios by category.
     *
     * @param category the category to filter by (e.g., "adversarial", "trust", "baseline")
     *
     * @return scenarios matching the given category; empty list if none match
     */
    public List<SimulationScenario> listByCategory(String category) {
        return listScenarios().stream()
                              .filter(s -> category.equals(s.category()))
                              .toList();
    }

    /**
     * Load a single scenario by ID.
     *
     * @param id the scenario ID as declared in the YAML file
     *
     * @throws IllegalArgumentException if no scenario with that ID is found
     */
    public SimulationScenario load(String id) {
        return listScenarios().stream()
                              .filter(s -> s.id().equals(id))
                              .findFirst()
                              .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));
    }
}
