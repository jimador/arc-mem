package dev.arcmem.simulator.engine;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.arcmem.simulator.benchmark.BenchmarkReport;
import dev.arcmem.simulator.benchmark.BenchmarkStatistics;
import dev.arcmem.simulator.benchmark.ExperimentReport;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Neo4jRunHistoryStore")
class Neo4jRunHistoryStoreTest {

    @Mock private PersistenceManager persistenceManager;
    @Captor private ArgumentCaptor<QuerySpecification> queryCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private Neo4jRunHistoryStore store;

    @BeforeEach
    void setUp() {
        store = new Neo4jRunHistoryStore(persistenceManager, objectMapper);
    }

    private BenchmarkReport benchmarkReport(String id, String scenario) {
        var stats = new BenchmarkStatistics(0.85, 0.05, 0.7, 1.0, 0.86, 0.95, 5);
        return new BenchmarkReport(
                id, scenario, Instant.parse("2026-01-15T10:00:00Z"), 5, 12000L,
                Map.of("factSurvivalRate", stats),
                Map.of("SUBTLE_REFRAME", stats),
                List.of("run-1", "run-2"),
                null, null, "gpt-4o"
        );
    }

    private ExperimentReport experimentReport(String id, String name) {
        var cellReport = benchmarkReport("bench-cell-1", "scenario-a");
        return new ExperimentReport(
                id, name, Instant.parse("2026-01-20T12:00:00Z"),
                List.of("control", "no-units"),
                List.of("scenario-a"),
                3, 45000L,
                Map.of("control:scenario-a", cellReport),
                Map.of(), Map.of(), Map.of(), false
        );
    }

    @Nested
    @DisplayName("benchmark report CRUD")
    class BenchmarkReportCrud {

        @Test
        @DisplayName("save and load round-trips through JSON serialization")
        void saveAndLoadRoundTrip() throws Exception {
            var report = benchmarkReport("bench-1", "scenario-a");
            store.saveBenchmarkReport(report);

            var json = objectMapper.writeValueAsString(report);
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(json));

            var loaded = store.loadBenchmarkReport("bench-1");

            assertThat(loaded).isPresent();
            assertThat(loaded.get().reportId()).isEqualTo("bench-1");
            assertThat(loaded.get().scenarioId()).isEqualTo("scenario-a");
            assertThat(loaded.get().runCount()).isEqualTo(5);
            assertThat(loaded.get().modelId()).isEqualTo("gpt-4o");
            assertThat(loaded.get().metricStatistics()).containsKey("factSurvivalRate");
        }

        @Test
        @DisplayName("list returns reports deserialized from multiple JSON payloads")
        void listReturnsReportsInOrder() throws Exception {
            var report1 = benchmarkReport("bench-1", "scenario-a");
            var report2 = benchmarkReport("bench-2", "scenario-b");
            var json1 = objectMapper.writeValueAsString(report1);
            var json2 = objectMapper.writeValueAsString(report2);

            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(json1, json2));

            var results = store.listBenchmarkReports();

            assertThat(results).hasSize(2);
            assertThat(results).extracting(BenchmarkReport::reportId)
                    .containsExactly("bench-1", "bench-2");
        }

        @Test
        @DisplayName("list by scenario passes scenario filter parameter")
        void listByScenarioFilters() throws Exception {
            var report = benchmarkReport("bench-1", "scenario-a");
            var json = objectMapper.writeValueAsString(report);
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(json));

            var results = store.listBenchmarkReportsByScenario("scenario-a");

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().scenarioId()).isEqualTo("scenario-a");
            verify(persistenceManager).query(queryCaptor.capture());
            assertThat(queryCaptor.getValue().toString()).contains("scenarioId");
        }

        @Test
        @DisplayName("delete executes DETACH DELETE query")
        void deleteRemovesReport() {
            store.deleteBenchmarkReport("bench-1");

            verify(persistenceManager).execute(queryCaptor.capture());
            assertThat(queryCaptor.getValue().toString()).contains("DETACH DELETE");
        }
    }

    @Nested
    @DisplayName("experiment report CRUD")
    class ExperimentReportCrud {

        @Test
        @DisplayName("save and load round-trips through JSON serialization")
        void saveAndLoadRoundTrip() throws Exception {
            var report = experimentReport("exp-1", "ablation-test");
            store.saveExperimentReport(report);

            var json = objectMapper.writeValueAsString(report);
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(json));

            var loaded = store.loadExperimentReport("exp-1");

            assertThat(loaded).isPresent();
            assertThat(loaded.get().reportId()).isEqualTo("exp-1");
            assertThat(loaded.get().experimentName()).isEqualTo("ablation-test");
            assertThat(loaded.get().conditions()).containsExactly("control", "no-units");
            assertThat(loaded.get().cellReports()).containsKey("control:scenario-a");
        }

        @Test
        @DisplayName("list returns all experiment reports")
        void listReturnsReports() throws Exception {
            var report = experimentReport("exp-1", "ablation-test");
            var json = objectMapper.writeValueAsString(report);
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(json));

            var results = store.listExperimentReports();

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().reportId()).isEqualTo("exp-1");
        }

        @Test
        @DisplayName("delete executes DETACH DELETE query")
        void deleteRemovesReport() {
            store.deleteExperimentReport("exp-1");

            verify(persistenceManager).execute(queryCaptor.capture());
            assertThat(queryCaptor.getValue().toString()).contains("DETACH DELETE");
        }
    }

    @Nested
    @DisplayName("baseline management")
    class BaselineManagement {

        @Test
        @DisplayName("save and load baseline round-trips through relationship query")
        void saveAndLoadBaseline() throws Exception {
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(true));

            store.saveAsBaseline("bench-1", "scenario-a");

            var report = benchmarkReport("bench-1", "scenario-a");
            var json = objectMapper.writeValueAsString(report);
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(json));

            var loaded = store.loadBaseline("scenario-a");

            assertThat(loaded).isPresent();
            assertThat(loaded.get().reportId()).isEqualTo("bench-1");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when benchmark report does not exist")
        void saveBaselineThrowsForNonexistentReport() {
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(false));

            assertThatThrownBy(() -> store.saveAsBaseline("no-such-report", "scenario-a"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no-such-report");
        }

        @Test
        @DisplayName("overwriting baseline replaces old relationship")
        void overwriteBaselineReplacesOldRelationship() throws Exception {
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(true));

            store.saveAsBaseline("bench-1", "scenario-a");
            store.saveAsBaseline("bench-2", "scenario-a");

            var report2 = benchmarkReport("bench-2", "scenario-a");
            var json2 = objectMapper.writeValueAsString(report2);
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(json2));

            var loaded = store.loadBaseline("scenario-a");

            assertThat(loaded).isPresent();
            assertThat(loaded.get().reportId()).isEqualTo("bench-2");
        }

        @Test
        @DisplayName("deleting report cascades baseline relationship removal")
        void deleteReportCascadesBaselineRelationship() throws Exception {
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of(true));

            store.saveAsBaseline("bench-1", "scenario-a");
            store.deleteBenchmarkReport("bench-1");

            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of());

            var loaded = store.loadBaseline("scenario-a");

            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("report type isolation")
    class ReportTypeIsolation {

        @Test
        @DisplayName("benchmark and experiment queries use distinct node labels")
        void benchmarkListExcludesExperiments() {
            when(persistenceManager.query(any(QuerySpecification.class)))
                    .thenReturn(List.of());

            store.listBenchmarkReports();
            store.listExperimentReports();

            verify(persistenceManager, org.mockito.Mockito.times(2)).query(queryCaptor.capture());
            var captured = queryCaptor.getAllValues();
            assertThat(captured.get(0).toString()).contains("BenchmarkReport");
            assertThat(captured.get(1).toString()).contains("ExperimentReport");
        }
    }
}
