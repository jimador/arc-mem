package dev.dunnam.diceanchors.sim.benchmark;

import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScenarioLoader;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ExperimentRunner")
@ExtendWith(MockitoExtension.class)
class ExperimentRunnerTest {

    @Mock
    private BenchmarkRunner benchmarkRunner;

    @Mock
    private EffectSizeCalculator effectSizeCalculator;

    @Mock
    private ScenarioLoader scenarioLoader;

    @Mock
    private RunHistoryStore runHistoryStore;

    private ExperimentRunner runner;

    private static SimulationScenario minimalScenario(String id) {
        return new SimulationScenario(
                id, null, null, null, 10, 0, false, null,
                List.of(), List.of(), List.of(), null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static BenchmarkReport sampleBenchmarkReport(String scenarioId) {
        return new BenchmarkReport(
                "bench-001", scenarioId, Instant.now(), 3, 1000L,
                Map.of("factSurvivalRate", new BenchmarkStatistics(0.85, 0.05, 0.8, 0.9, 0.85, 0.89, 3)),
                Map.of(), List.of(), null, null);
    }

    private ExperimentDefinition twoByTwoDefinition() {
        return new ExperimentDefinition(
                "test-experiment",
                List.of(AblationCondition.FULL_ANCHORS, AblationCondition.NO_ANCHORS),
                List.of("scenario-1", "scenario-2"),
                3,
                Optional.empty());
    }

    @BeforeEach
    void setUp() {
        runner = new ExperimentRunner(benchmarkRunner, effectSizeCalculator, scenarioLoader, runHistoryStore);
    }

    @Nested
    @DisplayName("runExperiment")
    class RunExperiment {

        @Test
        @DisplayName("2 conditions x 2 scenarios invokes BenchmarkRunner 4 times")
        void matrixExecutionCount() {
            var definition = twoByTwoDefinition();

            when(scenarioLoader.load("scenario-1")).thenReturn(minimalScenario("scenario-1"));
            when(scenarioLoader.load("scenario-2")).thenReturn(minimalScenario("scenario-2"));
            when(benchmarkRunner.runBenchmark(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                    .thenReturn(sampleBenchmarkReport("s1"));
            when(effectSizeCalculator.computeEffectSizes(any(), any())).thenReturn(Map.of());
            when(effectSizeCalculator.computeConfidenceIntervals(any())).thenReturn(Map.of());
            when(effectSizeCalculator.computeStrategyDeltas(any())).thenReturn(Map.of());

            var report = runner.runExperiment(definition, () -> true, () -> 4000, p -> {});

            verify(benchmarkRunner, times(4)).runBenchmark(any(), anyInt(), anyInt(), any(), any(), any(), any());
            assertThat(report.cellReports()).hasSize(4);
        }

        @Test
        @DisplayName("progress callback is invoked for each cell via BenchmarkProgress bridge")
        void progressCallbackInvoked() {
            var definition = twoByTwoDefinition();

            when(scenarioLoader.load("scenario-1")).thenReturn(minimalScenario("scenario-1"));
            when(scenarioLoader.load("scenario-2")).thenReturn(minimalScenario("scenario-2"));

            // When BenchmarkRunner runs, invoke the BenchmarkProgress callback which bridges to ExperimentProgress
            when(benchmarkRunner.runBenchmark(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        Consumer<BenchmarkProgress> benchCallback = inv.getArgument(5);
                        benchCallback.accept(new BenchmarkProgress(1, 3, null));
                        return sampleBenchmarkReport("s1");
                    });
            when(effectSizeCalculator.computeEffectSizes(any(), any())).thenReturn(Map.of());
            when(effectSizeCalculator.computeConfidenceIntervals(any())).thenReturn(Map.of());
            when(effectSizeCalculator.computeStrategyDeltas(any())).thenReturn(Map.of());

            var progressSnapshots = new ArrayList<ExperimentProgress>();
            runner.runExperiment(definition, () -> true, () -> 4000, progressSnapshots::add);

            assertThat(progressSnapshots).isNotEmpty();
            assertThat(progressSnapshots).allSatisfy(p -> {
                assertThat(p.totalCells()).isEqualTo(4);
                assertThat(p.currentCell()).isBetween(1, 4);
            });
        }

        @Test
        @DisplayName("cancellation mid-experiment produces report with cancelled=true and fewer cells")
        void cancellationProducesPartialReport() {
            var definition = twoByTwoDefinition();

            when(scenarioLoader.load("scenario-1")).thenReturn(minimalScenario("scenario-1"));

            // Cancel after the first BenchmarkRunner call
            when(benchmarkRunner.runBenchmark(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                    .thenAnswer(inv -> {
                        runner.cancel();
                        return sampleBenchmarkReport("s1");
                    });
            when(effectSizeCalculator.computeEffectSizes(any(), any())).thenReturn(Map.of());
            when(effectSizeCalculator.computeConfidenceIntervals(any())).thenReturn(Map.of());
            when(effectSizeCalculator.computeStrategyDeltas(any())).thenReturn(Map.of());

            var report = runner.runExperiment(definition, () -> true, () -> 4000, p -> {});

            assertThat(report.cancelled()).isTrue();
            assertThat(report.cellReports().size()).isLessThan(4);
        }

        @Test
        @DisplayName("report is saved to RunHistoryStore")
        void reportSavedToRunHistoryStore() {
            var definition = new ExperimentDefinition(
                    "persist-test",
                    List.of(AblationCondition.FULL_ANCHORS),
                    List.of("scenario-1"),
                    3,
                    Optional.empty());

            when(scenarioLoader.load("scenario-1")).thenReturn(minimalScenario("scenario-1"));
            when(benchmarkRunner.runBenchmark(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                    .thenReturn(sampleBenchmarkReport("scenario-1"));
            when(effectSizeCalculator.computeEffectSizes(any(), any())).thenReturn(Map.of());
            when(effectSizeCalculator.computeConfidenceIntervals(any())).thenReturn(Map.of());
            when(effectSizeCalculator.computeStrategyDeltas(any())).thenReturn(Map.of());

            runner.runExperiment(definition, () -> true, () -> 4000, p -> {});

            verify(runHistoryStore).saveExperimentReport(any());
        }

        @Test
        @DisplayName("each cell passes the correct AblationCondition to BenchmarkRunner")
        void eachCellUsesCorrectCondition() {
            var definition = twoByTwoDefinition();

            when(scenarioLoader.load("scenario-1")).thenReturn(minimalScenario("scenario-1"));
            when(scenarioLoader.load("scenario-2")).thenReturn(minimalScenario("scenario-2"));
            when(benchmarkRunner.runBenchmark(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                    .thenReturn(sampleBenchmarkReport("s1"));
            when(effectSizeCalculator.computeEffectSizes(any(), any())).thenReturn(Map.of());
            when(effectSizeCalculator.computeConfidenceIntervals(any())).thenReturn(Map.of());
            when(effectSizeCalculator.computeStrategyDeltas(any())).thenReturn(Map.of());

            runner.runExperiment(definition, () -> true, () -> 4000, p -> {});

            var conditionCaptor = ArgumentCaptor.forClass(AblationCondition.class);
            verify(benchmarkRunner, times(4)).runBenchmark(
                    any(), anyInt(), anyInt(), any(), any(), any(), conditionCaptor.capture());

            var capturedConditions = conditionCaptor.getAllValues();
            // Order: FULL_ANCHORS x scenario-1, FULL_ANCHORS x scenario-2, NO_ANCHORS x scenario-1, NO_ANCHORS x scenario-2
            assertThat(capturedConditions.get(0).name()).isEqualTo("FULL_ANCHORS");
            assertThat(capturedConditions.get(1).name()).isEqualTo("FULL_ANCHORS");
            assertThat(capturedConditions.get(2).name()).isEqualTo("NO_ANCHORS");
            assertThat(capturedConditions.get(3).name()).isEqualTo("NO_ANCHORS");
        }
    }
}
