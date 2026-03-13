package dev.arcmem.simulator.benchmark;
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

import dev.arcmem.simulator.history.SimulationRunStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("ExperimentPersistence (SimulationRunStore)")
class ExperimentPersistenceTest {

    private SimulationRunStore store;

    @BeforeEach
    void setUp() {
        store = new SimulationRunStore();
    }

    private static ExperimentReport experimentReport(String reportId, Instant createdAt) {
        var cellReport = new BenchmarkReport("bench-1", "s1", createdAt, 5, 1000L,
                Map.of("factSurvivalRate", new BenchmarkStatistics(0.85, 0.05, 0.8, 0.9, 0.85, 0.89, 5)),
                Map.of(), List.of(), null, null);
        return new ExperimentReport(
                reportId, "test-exp", createdAt,
                List.of("FULL_UNITS"), List.of("s1"), 5, 5000L,
                Map.of("FULL_UNITS:s1", cellReport),
                Map.of(), Map.of(), Map.of(), false);
    }

    private static BenchmarkReport benchmarkReport(String reportId) {
        return new BenchmarkReport(
                reportId, "s1", Instant.now(), 5, 1000L,
                Map.of(), Map.of(), List.of(), null, null);
    }

    @Nested
    @DisplayName("save and load")
    class SaveAndLoad {

        @Test
        @DisplayName("round-trips an experiment report by ID")
        void saveLoadRoundTrip() {
            var report = experimentReport("exp-001", Instant.now());
            store.saveExperimentReport(report);

            var loaded = store.loadExperimentReport("exp-001");
            assertThat(loaded).isPresent();
            assertThat(loaded.get().reportId()).isEqualTo("exp-001");
            assertThat(loaded.get().experimentName()).isEqualTo("test-exp");
            assertThat(loaded.get().conditions()).isEqualTo(report.conditions());
            assertThat(loaded.get().scenarioIds()).isEqualTo(report.scenarioIds());
            assertThat(loaded.get().repetitionsPerCell()).isEqualTo(report.repetitionsPerCell());
            assertThat(loaded.get().totalDurationMs()).isEqualTo(report.totalDurationMs());
        }

        @Test
        @DisplayName("load nonexistent returns Optional.empty()")
        void loadNonexistentReturnsEmpty() {
            assertThat(store.loadExperimentReport("no-such-report")).isEmpty();
        }
    }

    @Nested
    @DisplayName("list ordering")
    class ListOrdering {

        @Test
        @DisplayName("list returns newest first by createdAt")
        void listReturnsNewestFirst() {
            var oldest = Instant.parse("2026-01-01T00:00:00Z");
            var middle = Instant.parse("2026-01-02T00:00:00Z");
            var newest = Instant.parse("2026-01-03T00:00:00Z");

            store.saveExperimentReport(experimentReport("exp-oldest", oldest));
            store.saveExperimentReport(experimentReport("exp-middle", middle));
            store.saveExperimentReport(experimentReport("exp-newest", newest));

            var reports = store.listExperimentReports();
            assertThat(reports).hasSize(3);
            assertThat(reports.get(0).reportId()).isEqualTo("exp-newest");
            assertThat(reports.get(1).reportId()).isEqualTo("exp-middle");
            assertThat(reports.get(2).reportId()).isEqualTo("exp-oldest");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("delete removes the report so load returns empty")
        void deleteRemovesReport() {
            var report = experimentReport("exp-001", Instant.now());
            store.saveExperimentReport(report);

            store.deleteExperimentReport("exp-001");

            assertThat(store.loadExperimentReport("exp-001")).isEmpty();
            assertThat(store.listExperimentReports()).isEmpty();
        }

        @Test
        @DisplayName("delete nonexistent report is a no-op")
        void deleteNonexistentIsNoOp() {
            store.saveExperimentReport(experimentReport("exp-001", Instant.now()));

            assertThatNoException().isThrownBy(() -> store.deleteExperimentReport("no-such-report"));
            assertThat(store.listExperimentReports()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("type isolation")
    class TypeIsolation {

        @Test
        @DisplayName("experiment reports do not appear in listBenchmarkReports")
        void experimentReportsDoNotAppearInBenchmarkList() {
            store.saveExperimentReport(experimentReport("exp-001", Instant.now()));
            store.saveBenchmarkReport(benchmarkReport("bench-001"));

            var benchmarkReports = store.listBenchmarkReports();
            var experimentReports = store.listExperimentReports();

            assertThat(benchmarkReports).hasSize(1);
            assertThat(benchmarkReports.get(0).reportId()).isEqualTo("bench-001");

            assertThat(experimentReports).hasSize(1);
            assertThat(experimentReports.get(0).reportId()).isEqualTo("exp-001");
        }
    }
}
