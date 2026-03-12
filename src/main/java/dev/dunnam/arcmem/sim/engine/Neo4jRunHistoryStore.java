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

import jakarta.annotation.PostConstruct;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Neo4jRunHistoryStore implements RunHistoryStore {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jRunHistoryStore.class);

    private static final String INDEX_RUN_ID = """
            CREATE INDEX sim_run_id_idx IF NOT EXISTS
            FOR (r:SimulationRun) ON (r.runId)
            """;

    private static final String INDEX_BENCHMARK_REPORT_ID = """
            CREATE INDEX benchmark_report_id_idx IF NOT EXISTS
            FOR (r:BenchmarkReport) ON (r.reportId)
            """;

    private static final String INDEX_BENCHMARK_SCENARIO_ID = """
            CREATE INDEX benchmark_scenario_id_idx IF NOT EXISTS
            FOR (r:BenchmarkReport) ON (r.scenarioId)
            """;

    private static final String INDEX_EXPERIMENT_REPORT_ID = """
            CREATE INDEX experiment_report_id_idx IF NOT EXISTS
            FOR (r:ExperimentReport) ON (r.reportId)
            """;

    private static final String INDEX_BASELINE_SCENARIO_ID = """
            CREATE INDEX baseline_scenario_id_idx IF NOT EXISTS
            FOR (b:Baseline) ON (b.scenarioId)
            """;

    // SimulationRun queries
    private static final String SAVE_RUN = """
            MERGE (r:SimulationRun {runId: $runId})
            SET r.scenarioId = $scenarioId,
                r.startedAt = $startedAt,
                r.completedAt = $completedAt,
                r.injectionEnabled = $injectionEnabled,
                r.payload = $payload
            """;

    private static final String LOAD_RUN = """
            MATCH (r:SimulationRun {runId: $runId})
            RETURN r.payload
            """;

    private static final String LIST_RUNS = """
            MATCH (r:SimulationRun)
            RETURN r.payload
            ORDER BY r.completedAt DESC
            """;

    private static final String LIST_RUNS_BY_SCENARIO = """
            MATCH (r:SimulationRun {scenarioId: $scenarioId})
            RETURN r.payload
            ORDER BY r.completedAt DESC
            """;

    private static final String DELETE_RUN = """
            MATCH (r:SimulationRun {runId: $runId})
            DETACH DELETE r
            """;

    // BenchmarkReport queries
    private static final String SAVE_BENCHMARK = """
            MERGE (r:BenchmarkReport {reportId: $reportId})
            SET r.scenarioId = $scenarioId,
                r.createdAt = $createdAt,
                r.modelId = $modelId,
                r.payload = $payload
            """;

    private static final String LOAD_BENCHMARK = """
            MATCH (r:BenchmarkReport {reportId: $reportId})
            RETURN r.payload
            """;

    private static final String LIST_BENCHMARKS = """
            MATCH (r:BenchmarkReport)
            RETURN r.payload
            ORDER BY r.createdAt DESC
            """;

    private static final String LIST_BENCHMARKS_BY_SCENARIO = """
            MATCH (r:BenchmarkReport {scenarioId: $scenarioId})
            RETURN r.payload
            ORDER BY r.createdAt DESC
            """;

    private static final String DELETE_BENCHMARK = """
            MATCH (r:BenchmarkReport {reportId: $reportId})
            DETACH DELETE r
            """;

    private static final String BENCHMARK_EXISTS = """
            MATCH (r:BenchmarkReport {reportId: $reportId})
            RETURN count(r) > 0
            """;

    // ExperimentReport queries
    private static final String SAVE_EXPERIMENT = """
            MERGE (r:ExperimentReport {reportId: $reportId})
            SET r.createdAt = $createdAt,
                r.payload = $payload
            """;

    private static final String LOAD_EXPERIMENT = """
            MATCH (r:ExperimentReport {reportId: $reportId})
            RETURN r.payload
            """;

    private static final String LIST_EXPERIMENTS = """
            MATCH (r:ExperimentReport)
            RETURN r.payload
            ORDER BY r.createdAt DESC
            """;

    private static final String DELETE_EXPERIMENT = """
            MATCH (r:ExperimentReport {reportId: $reportId})
            DETACH DELETE r
            """;

    // Baseline queries
    private static final String SAVE_BASELINE = """
            MATCH (report:BenchmarkReport {reportId: $reportId})
            MERGE (b:Baseline {scenarioId: $scenarioId})
            WITH b, report
            OPTIONAL MATCH (b)-[old:BASELINE_FOR]->()
            DELETE old
            CREATE (b)-[:BASELINE_FOR]->(report)
            """;

    private static final String LOAD_BASELINE = """
            MATCH (b:Baseline {scenarioId: $scenarioId})-[:BASELINE_FOR]->(r:BenchmarkReport)
            RETURN r.payload
            """;

    private final PersistenceManager persistenceManager;
    private final ObjectMapper objectMapper;

    public Neo4jRunHistoryStore(PersistenceManager persistenceManager, ObjectMapper objectMapper) {
        this.persistenceManager = persistenceManager;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void provision() {
        try {
            persistenceManager.execute(QuerySpecification.withStatement(INDEX_RUN_ID));
            persistenceManager.execute(QuerySpecification.withStatement(INDEX_BENCHMARK_REPORT_ID));
            persistenceManager.execute(QuerySpecification.withStatement(INDEX_BENCHMARK_SCENARIO_ID));
            persistenceManager.execute(QuerySpecification.withStatement(INDEX_EXPERIMENT_REPORT_ID));
            persistenceManager.execute(QuerySpecification.withStatement(INDEX_BASELINE_SCENARIO_ID));
            logger.info("Provisioned run history indexes");
        } catch (Exception e) {
            logger.warn("Failed to provision run history indexes: {}", e.getMessage());
        }
    }

    // --- SimulationRunRecord ---

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
            persistenceManager.execute(QuerySpecification.withStatement(SAVE_RUN).bind(params));
            logger.debug("Saved simulation run {} to Neo4j", record.runId());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize simulation run " + record.runId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist simulation run " + record.runId(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SimulationRunRecord> load(String runId) {
        return queryPayload(LOAD_RUN, Map.of("runId", runId), SimulationRunRecord.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulationRunRecord> list() {
        return queryPayloads(LIST_RUNS, Map.of(), SimulationRunRecord.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulationRunRecord> listByScenario(String scenarioId) {
        return queryPayloads(LIST_RUNS_BY_SCENARIO, Map.of("scenarioId", scenarioId), SimulationRunRecord.class);
    }

    @Override
    @Transactional
    public void delete(String runId) {
        execute(DELETE_RUN, Map.of("runId", runId));
        logger.debug("Deleted simulation run {} from Neo4j", runId);
    }

    // --- BenchmarkReport ---

    @Override
    @Transactional
    public void saveBenchmarkReport(BenchmarkReport report) {
        try {
            var payload = objectMapper.writeValueAsString(report);
            var params = Map.of(
                    "reportId", report.reportId(),
                    "scenarioId", report.scenarioId(),
                    "createdAt", report.createdAt().toString(),
                    "modelId", report.modelId() != null ? report.modelId() : "",
                    "payload", payload
            );
            persistenceManager.execute(QuerySpecification.withStatement(SAVE_BENCHMARK).bind(params));
            logger.debug("Saved benchmark report {} to Neo4j", report.reportId());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize benchmark report " + report.reportId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist benchmark report " + report.reportId(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BenchmarkReport> loadBenchmarkReport(String reportId) {
        return queryPayload(LOAD_BENCHMARK, Map.of("reportId", reportId), BenchmarkReport.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BenchmarkReport> listBenchmarkReports() {
        return queryPayloads(LIST_BENCHMARKS, Map.of(), BenchmarkReport.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BenchmarkReport> listBenchmarkReportsByScenario(String scenarioId) {
        return queryPayloads(LIST_BENCHMARKS_BY_SCENARIO, Map.of("scenarioId", scenarioId), BenchmarkReport.class);
    }

    @Override
    @Transactional
    public void deleteBenchmarkReport(String reportId) {
        execute(DELETE_BENCHMARK, Map.of("reportId", reportId));
        logger.debug("Deleted benchmark report {} from Neo4j", reportId);
    }

    // --- Baseline ---

    @Override
    @Transactional
    public void saveAsBaseline(String reportId, String scenarioId) {
        var exists = persistenceManager.query(
                QuerySpecification.withStatement(BENCHMARK_EXISTS)
                        .bind(Map.of("reportId", reportId))
                        .transform(Boolean.class)
        );
        if (exists.isEmpty() || !exists.getFirst()) {
            throw new IllegalArgumentException("Benchmark report not found: " + reportId);
        }
        execute(SAVE_BASELINE, Map.of("reportId", reportId, "scenarioId", scenarioId));
        logger.debug("Set baseline for scenario {} to report {}", scenarioId, reportId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BenchmarkReport> loadBaseline(String scenarioId) {
        return queryPayload(LOAD_BASELINE, Map.of("scenarioId", scenarioId), BenchmarkReport.class);
    }

    // --- ExperimentReport ---

    @Override
    @Transactional
    public void saveExperimentReport(ExperimentReport report) {
        try {
            var payload = objectMapper.writeValueAsString(report);
            var params = Map.of(
                    "reportId", report.reportId(),
                    "createdAt", report.createdAt().toString(),
                    "payload", payload
            );
            persistenceManager.execute(QuerySpecification.withStatement(SAVE_EXPERIMENT).bind(params));
            logger.debug("Saved experiment report {} to Neo4j", report.reportId());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize experiment report " + report.reportId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist experiment report " + report.reportId(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExperimentReport> loadExperimentReport(String reportId) {
        return queryPayload(LOAD_EXPERIMENT, Map.of("reportId", reportId), ExperimentReport.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExperimentReport> listExperimentReports() {
        return queryPayloads(LIST_EXPERIMENTS, Map.of(), ExperimentReport.class);
    }

    @Override
    @Transactional
    public void deleteExperimentReport(String reportId) {
        execute(DELETE_EXPERIMENT, Map.of("reportId", reportId));
        logger.debug("Deleted experiment report {} from Neo4j", reportId);
    }

    // --- Shared helpers ---

    private <T> Optional<T> queryPayload(String cypher, Map<String, Object> params, Class<T> type) {
        try {
            var results = persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(String.class)
            );
            if (results.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(results.getFirst(), type));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to deserialize " + type.getSimpleName(), e);
        }
    }

    private <T> List<T> queryPayloads(String cypher, Map<String, Object> params, Class<T> type) {
        var payloads = persistenceManager.query(
                QuerySpecification.withStatement(cypher).bind(params).transform(String.class)
        );
        return payloads.stream()
                .map(p -> deserialize(p, type))
                .toList();
    }

    private <T> T deserialize(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to deserialize " + type.getSimpleName() + " payload", e);
        }
    }

    private void execute(String cypher, Map<String, Object> params) {
        persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
    }
}
