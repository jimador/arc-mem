package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dunnam.diceanchors.sim.benchmark.BenchmarkReport;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentReport;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Neo4j-backed implementation of {@link RunHistoryStore}.
 * Stores {@link SimulationRunRecord} as {@code SimulationRun} nodes with
 * indexed {@code runId} and {@code scenarioId} properties. The full record
 * is serialized to JSON in the {@code payload} property.
 */
public class Neo4jRunHistoryStore implements RunHistoryStore {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jRunHistoryStore.class);

    private static final String SAVE = """
            MERGE (r:SimulationRun {runId: $runId})
            SET r.scenarioId = $scenarioId,
                r.startedAt = $startedAt,
                r.completedAt = $completedAt,
                r.injectionEnabled = $injectionEnabled,
                r.payload = $payload
            """;

    private static final String LOAD = """
            MATCH (r:SimulationRun {runId: $runId})
            RETURN r.payload
            """;

    private static final String LIST_ALL = """
            MATCH (r:SimulationRun)
            RETURN r.payload
            ORDER BY r.completedAt DESC
            """;

    private static final String LIST_BY_SCENARIO = """
            MATCH (r:SimulationRun {scenarioId: $scenarioId})
            RETURN r.payload
            ORDER BY r.completedAt DESC
            """;

    private static final String DELETE = """
            MATCH (r:SimulationRun {runId: $runId})
            DELETE r
            """;

    private final PersistenceManager persistenceManager;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, BenchmarkReport> benchmarkReports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> scenarioBaselines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExperimentReport> experimentReports = new ConcurrentHashMap<>();

    public Neo4jRunHistoryStore(PersistenceManager persistenceManager, ObjectMapper objectMapper) {
        this.persistenceManager = persistenceManager;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(SimulationRunRecord record) {
        try {
            var payload = objectMapper.writeValueAsString(record);
            var params = Map.of(
                    "runId", record.runId(),
                    "scenarioId", record.scenarioId(),
                    "startedAt", record.startedAt().toString(),
                    "completedAt", record.completedAt().toString(),
                    "injectionEnabled", record.injectionEnabled(),
                    "payload", payload
            );
            persistenceManager.execute(QuerySpecification.withStatement(SAVE).bind(params));
            logger.debug("Saved simulation run {} to Neo4j", record.runId());
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize simulation run {}: {}", record.runId(), e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to save simulation run {} to Neo4j: {}", record.runId(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SimulationRunRecord> load(String runId) {
        try {
            var results = persistenceManager.query(
                    QuerySpecification
                            .withStatement(LOAD)
                            .bind(Map.of("runId", runId))
                            .transform(String.class)
            );
            if (results.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(results.getFirst(), SimulationRunRecord.class));
        } catch (Exception e) {
            logger.error("Failed to load simulation run {}: {}", runId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulationRunRecord> list() {
        try {
            var payloads = persistenceManager.query(
                    QuerySpecification
                            .withStatement(LIST_ALL)
                            .transform(String.class)
            );
            return payloads.stream()
                           .map(this::deserialize)
                           .filter(Optional::isPresent)
                           .map(Optional::get)
                           .toList();
        } catch (Exception e) {
            logger.error("Failed to list simulation runs: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulationRunRecord> listByScenario(String scenarioId) {
        try {
            var payloads = persistenceManager.query(
                    QuerySpecification
                            .withStatement(LIST_BY_SCENARIO)
                            .bind(Map.of("scenarioId", scenarioId))
                            .transform(String.class)
            );
            return payloads.stream()
                           .map(this::deserialize)
                           .filter(Optional::isPresent)
                           .map(Optional::get)
                           .toList();
        } catch (Exception e) {
            logger.error("Failed to list simulation runs for scenario {}: {}", scenarioId, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional
    public void delete(String runId) {
        try {
            persistenceManager.execute(
                    QuerySpecification.withStatement(DELETE).bind(Map.of("runId", runId))
            );
            logger.debug("Deleted simulation run {} from Neo4j", runId);
        } catch (Exception e) {
            logger.error("Failed to delete simulation run {}: {}", runId, e.getMessage(), e);
        }
    }

    private Optional<SimulationRunRecord> deserialize(String payload) {
        try {
            return Optional.of(objectMapper.readValue(payload, SimulationRunRecord.class));
        } catch (JsonProcessingException e) {
            logger.warn("Failed to deserialize simulation run payload: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void saveBenchmarkReport(BenchmarkReport report) {
        benchmarkReports.put(report.reportId(), report);
        logger.debug("Saved benchmark report {}", report.reportId());
    }

    @Override
    public Optional<BenchmarkReport> loadBenchmarkReport(String reportId) {
        return Optional.ofNullable(benchmarkReports.get(reportId));
    }

    @Override
    public List<BenchmarkReport> listBenchmarkReports() {
        return benchmarkReports.values().stream()
                               .sorted(Comparator.comparing(BenchmarkReport::createdAt).reversed())
                               .toList();
    }

    @Override
    public List<BenchmarkReport> listBenchmarkReportsByScenario(String scenarioId) {
        return benchmarkReports.values().stream()
                               .filter(r -> r.scenarioId().equals(scenarioId))
                               .sorted(Comparator.comparing(BenchmarkReport::createdAt).reversed())
                               .toList();
    }

    @Override
    public void saveAsBaseline(String reportId, String scenarioId) {
        if (!benchmarkReports.containsKey(reportId)) {
            logger.warn("Cannot set baseline: benchmark report {} not found", reportId);
            return;
        }
        scenarioBaselines.put(scenarioId, reportId);
        logger.debug("Set baseline for scenario {} to report {}", scenarioId, reportId);
    }

    @Override
    public Optional<BenchmarkReport> loadBaseline(String scenarioId) {
        var reportId = scenarioBaselines.get(scenarioId);
        if (reportId == null) {
            return Optional.empty();
        }
        return loadBenchmarkReport(reportId);
    }

    @Override
    public void deleteBenchmarkReport(String reportId) {
        benchmarkReports.remove(reportId);
        scenarioBaselines.values().removeIf(id -> id.equals(reportId));
        logger.debug("Deleted benchmark report {}", reportId);
    }

    @Override
    public void saveExperimentReport(ExperimentReport report) {
        experimentReports.put(report.reportId(), report);
        logger.debug("Saved experiment report {}", report.reportId());
    }

    @Override
    public Optional<ExperimentReport> loadExperimentReport(String reportId) {
        return Optional.ofNullable(experimentReports.get(reportId));
    }

    @Override
    public List<ExperimentReport> listExperimentReports() {
        return experimentReports.values().stream()
                                .sorted(Comparator.comparing(ExperimentReport::createdAt).reversed())
                                .toList();
    }

    @Override
    public void deleteExperimentReport(String reportId) {
        experimentReports.remove(reportId);
        logger.debug("Deleted experiment report {}", reportId);
    }
}
