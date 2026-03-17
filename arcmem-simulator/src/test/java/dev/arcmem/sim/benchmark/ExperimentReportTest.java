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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExperimentReport")
class ExperimentReportTest {

    private static ExperimentReport sampleReport() {
        var cellReport = new BenchmarkReport("bench-1", "s1", Instant.now(), 5, 1000L,
                Map.of("factSurvivalRate", new BenchmarkStatistics(0.85, 0.05, 0.8, 0.9, 0.85, 0.89, 5)),
                Map.of(), List.of(), null, null);
        return new ExperimentReport(
                "exp-001", "test-exp", Instant.now(),
                List.of("FULL_AWMU", "NO_AWMU"), List.of("s1"), 5, 5000L,
                Map.of("FULL_AWMU:s1", cellReport),
                Map.of("FULL_AWMU:NO_AWMU", Map.of("factSurvivalRate", new EffectSizeEntry(1.5, "large", false))),
                Map.of("FULL_AWMU:s1", Map.of("factSurvivalRate", new ConfidenceInterval(0.82, 0.88))),
                Map.of("SUBTLE_REFRAME", Map.of("FULL_AWMU", 0.15, "NO_AWMU", 0.60)),
                false);
    }

    @Nested
    @DisplayName("immutable collections")
    class ImmutableCollections {

        @Test
        @DisplayName("conditions list is unmodifiable")
        void conditionsUnmodifiable() {
            var report = sampleReport();
            assertThatThrownBy(() -> report.conditions().add("NEW"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("scenarioIds list is unmodifiable")
        void scenarioIdsUnmodifiable() {
            var report = sampleReport();
            assertThatThrownBy(() -> report.scenarioIds().add("NEW"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("cellReports map is unmodifiable")
        void cellReportsUnmodifiable() {
            var report = sampleReport();
            assertThatThrownBy(() -> report.cellReports().put("key", null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("JSON serialization")
    class JsonSerialization {

        @Test
        @DisplayName("round-trip via Jackson preserves all fields")
        void jsonRoundTrip() throws Exception {
            var mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            var original = sampleReport();
            var json = mapper.writeValueAsString(original);
            var deserialized = mapper.readValue(json, ExperimentReport.class);

            assertThat(deserialized.reportId()).isEqualTo(original.reportId());
            assertThat(deserialized.experimentName()).isEqualTo(original.experimentName());
            assertThat(deserialized.createdAt()).isEqualTo(original.createdAt());
            assertThat(deserialized.conditions()).isEqualTo(original.conditions());
            assertThat(deserialized.scenarioIds()).isEqualTo(original.scenarioIds());
            assertThat(deserialized.repetitionsPerCell()).isEqualTo(original.repetitionsPerCell());
            assertThat(deserialized.totalDurationMs()).isEqualTo(original.totalDurationMs());
            assertThat(deserialized.cancelled()).isEqualTo(original.cancelled());
            assertThat(deserialized.cellReports()).hasSize(original.cellReports().size());
            assertThat(deserialized.effectSizeMatrix()).hasSize(original.effectSizeMatrix().size());
            assertThat(deserialized.confidenceIntervals()).hasSize(original.confidenceIntervals().size());
            assertThat(deserialized.strategyDeltas()).hasSize(original.strategyDeltas().size());
        }
    }
}
