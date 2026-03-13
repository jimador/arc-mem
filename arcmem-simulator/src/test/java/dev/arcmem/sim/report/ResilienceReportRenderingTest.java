package dev.arcmem.simulator.report;
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

import dev.arcmem.simulator.benchmark.BenchmarkStatistics;
import dev.arcmem.simulator.history.RunHistoryStore;
import dev.arcmem.simulator.scenario.ScenarioLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Resilience report rendering")
@ExtendWith(MockitoExtension.class)
class ResilienceReportRenderingTest {

    private static final ResilienceScore SAMPLE_SCORE =
            new ResilienceScore(85.0, 90.0, 80.0, 75.0, 95.0);

    @Mock RunHistoryStore runHistoryStore;
    @Mock ScenarioLoader scenarioLoader;

    @Nested
    @DisplayName("model identifier")
    class ModelIdentifier {

        @Test
        @DisplayName("renders model ID in header when present")
        void rendersModelIdWhenPresent() {
            var report = reportWithModelId("gpt-4.1-mini");
            var markdown = MarkdownReportRenderer.render(report);
            assertThat(markdown).contains("| Model | gpt-4.1-mini |");
        }

        @Test
        @DisplayName("omits model row when model ID is null")
        void omitsModelRowWhenNull() {
            var report = reportWithModelId(null);
            var markdown = MarkdownReportRenderer.render(report);
            assertThat(markdown).doesNotContain("| Model |");
        }
    }

    @Nested
    @DisplayName("provisional language")
    class ProvisionalLanguage {

        @Test
        @DisplayName("single-condition narrative includes provisional warning")
        void singleConditionProvisionalWarning() {
            var builder = new ResilienceReportBuilder(runHistoryStore, scenarioLoader);
            var metrics = Map.of("factSurvivalRate",
                    new BenchmarkStatistics(90.0, 5.0, 85.0, 95.0, 90.0, 94.0, 3));
            var summaries = List.of(new ConditionSummary("FULL_UNITS", metrics, 3));

            var narrative = builder.generateNarrative(summaries, List.of(), List.of());
            assertThat(narrative).contains("Provisional");
            assertThat(narrative).contains("Single-condition");
        }

        @Test
        @DisplayName("degraded conflict counts trigger provisional warning")
        void degradedCountsProvisionalWarning() {
            var builder = new ResilienceReportBuilder(runHistoryStore, scenarioLoader);
            var metrics = Map.of(
                    "factSurvivalRate", new BenchmarkStatistics(90.0, 5.0, 85.0, 95.0, 90.0, 94.0, 3),
                    "degradedConflictCount", new BenchmarkStatistics(2.0, 1.0, 1.0, 3.0, 2.0, 3.0, 3));
            var summaries = List.of(
                    new ConditionSummary("FULL_UNITS", metrics, 3),
                    new ConditionSummary("NO_UNITS", metrics, 3));

            var narrative = builder.generateNarrative(summaries, List.of(), List.of());
            assertThat(narrative).contains("degraded conflict detection");
        }

        @Test
        @DisplayName("no provisional warning when multi-condition and no degraded runs")
        void noProvisionalWhenClean() {
            var builder = new ResilienceReportBuilder(runHistoryStore, scenarioLoader);
            var metrics = Map.of("factSurvivalRate",
                    new BenchmarkStatistics(90.0, 5.0, 85.0, 95.0, 90.0, 94.0, 3));
            var summaries = List.of(
                    new ConditionSummary("FULL_UNITS", metrics, 3),
                    new ConditionSummary("NO_UNITS", metrics, 3));

            var narrative = builder.generateNarrative(summaries, List.of(), List.of());
            assertThat(narrative).doesNotContain("Provisional");
        }
    }

    @Nested
    @DisplayName("positioning removal")
    class PositioningRemoval {

        @Test
        @DisplayName("no positioning section when positioning is null")
        void noPositioningSectionWhenNull() {
            var report = reportWithModelId(null);
            var markdown = MarkdownReportRenderer.render(report);
            assertThat(markdown).doesNotContain("## Positioning");
        }
    }

    @Nested
    @DisplayName("percentage formatting")
    class PercentageFormatting {

        @Test
        @DisplayName("narrative formats 100% survival without double percentage")
        void noDoublePercentage() {
            var builder = new ResilienceReportBuilder(runHistoryStore, scenarioLoader);
            var metrics = Map.of("factSurvivalRate",
                    new BenchmarkStatistics(100.0, 0.0, 100.0, 100.0, 100.0, 100.0, 3));
            var summaries = List.of(
                    new ConditionSummary("FULL_UNITS", metrics, 3),
                    new ConditionSummary("NO_UNITS", metrics, 3));

            var narrative = builder.generateNarrative(summaries, List.of(), List.of());
            assertThat(narrative).contains("100.0%");
            assertThat(narrative).doesNotContain("10000");
        }
    }

    private static ResilienceReport reportWithModelId(String modelId) {
        var metrics = Map.of("factSurvivalRate",
                new BenchmarkStatistics(90.0, 5.0, 85.0, 95.0, 90.0, 94.0, 3));
        var section = new ScenarioSection("test", "Test Scenario",
                List.of(new ConditionSummary("FULL_UNITS", metrics, 3)),
                List.of(), List.of(), List.of(), "Sample narrative.");
        return new ResilienceReport(
                "Test Report", Instant.now(), "test-exp",
                List.of("FULL_UNITS"), List.of("test"), 3, false,
                SAMPLE_SCORE, Map.of("FULL_UNITS", SAMPLE_SCORE),
                List.of(section), null, null, modelId);
    }
}
