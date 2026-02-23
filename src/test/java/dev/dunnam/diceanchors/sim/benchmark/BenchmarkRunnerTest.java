package dev.dunnam.diceanchors.sim.benchmark;

import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScoringResult;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress.SimulationPhase;
import dev.dunnam.diceanchors.sim.engine.SimulationRunRecord;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import dev.dunnam.diceanchors.sim.engine.SimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BenchmarkRunner")
@ExtendWith(MockitoExtension.class)
class BenchmarkRunnerTest {

    @Mock
    private SimulationService simulationService;

    @Mock
    private BenchmarkAggregator aggregator;

    @Mock
    private RunHistoryStore runHistoryStore;

    private BenchmarkRunner runner;

    private static final ScoringResult SAMPLE_RESULT = new ScoringResult(
            90.0, 1, 0, 95.0, 3.0, 5, Map.of("SUBTLE_REFRAME", 0.5)
    );

    private static SimulationProgress completeProgress(ScoringResult scoringResult) {
        return new SimulationProgress(
                SimulationPhase.COMPLETE, null, List.of(),
                10, 10, "player msg", "dm response",
                List.of(), null, List.of(),
                true, "Complete", true, null, null,
                List.of(), scoringResult, 500L, null, null
        );
    }

    private static SimulationScenario minimalScenario() {
        var scenario = mock(SimulationScenario.class, org.mockito.Mockito.withSettings().lenient());
        when(scenario.id()).thenReturn("test-scenario");
        return scenario;
    }

    private static SimulationRunRecord runRecord(String runId, String scenarioId) {
        return new SimulationRunRecord(
                runId, scenarioId, Instant.now(), Instant.now(),
                List.of(), 0, List.of(), true, 4000, null, SAMPLE_RESULT
        );
    }

    @BeforeEach
    void setUp() {
        runner = new BenchmarkRunner(simulationService, aggregator, runHistoryStore);
    }

    @Nested
    @DisplayName("runBenchmark")
    class RunBenchmark {

        @Test
        @DisplayName("runCount < 2 throws IllegalArgumentException")
        void runCountLessThanTwoThrows() {
            var scenario = minimalScenario();
            assertThatThrownBy(() -> runner.runBenchmark(
                    scenario, 10, 1, () -> true, () -> 4000, p -> {}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 2 runs");
        }

        @Test
        @DisplayName("executes N sequential runs via SimulationService")
        void executesNRuns() {
            var scenario = minimalScenario();
            var runCount = 3;

            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<SimulationProgress> callback = invocation.getArgument(4);
                callback.accept(completeProgress(SAMPLE_RESULT));
                return null;
            }).when(simulationService).runSimulation(any(), anyInt(), any(), any(), any());

            when(runHistoryStore.listByScenario("test-scenario"))
                    .thenReturn(List.of(runRecord("run-1", "test-scenario")))
                    .thenReturn(List.of(runRecord("run-2", "test-scenario")))
                    .thenReturn(List.of(runRecord("run-3", "test-scenario")));

            var expectedReport = new BenchmarkReport(
                    "bench-abc", "test-scenario", Instant.now(), runCount, 1000L,
                    Map.of(), Map.of(), List.of("run-1", "run-2", "run-3"), null, null);
            when(aggregator.aggregate(any(), any(), any(), anyLong())).thenReturn(expectedReport);

            runner.runBenchmark(scenario, 10, runCount, () -> true, () -> 4000, p -> {});

            verify(simulationService, times(runCount)).runSimulation(any(), anyInt(), any(), any(), any());
        }

        @Test
        @DisplayName("progress callback invoked after each run with correct completedRuns/totalRuns")
        void progressCallbackInvokedPerRun() {
            var scenario = minimalScenario();
            var runCount = 3;

            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<SimulationProgress> callback = invocation.getArgument(4);
                callback.accept(completeProgress(SAMPLE_RESULT));
                return null;
            }).when(simulationService).runSimulation(any(), anyInt(), any(), any(), any());

            when(runHistoryStore.listByScenario("test-scenario"))
                    .thenReturn(List.of(runRecord("run-1", "test-scenario")));

            var expectedReport = new BenchmarkReport(
                    "bench-abc", "test-scenario", Instant.now(), runCount, 1000L,
                    Map.of(), Map.of(), List.of(), null, null);
            when(aggregator.aggregate(any(), any(), any(), anyLong())).thenReturn(expectedReport);

            var progressSnapshots = new ArrayList<BenchmarkProgress>();
            runner.runBenchmark(scenario, 10, runCount, () -> true, () -> 4000, progressSnapshots::add);

            assertThat(progressSnapshots).hasSize(runCount);
            for (int i = 0; i < runCount; i++) {
                assertThat(progressSnapshots.get(i).completedRuns()).isEqualTo(i + 1);
                assertThat(progressSnapshots.get(i).totalRuns()).isEqualTo(runCount);
                assertThat(progressSnapshots.get(i).latestScoringResult()).isEqualTo(SAMPLE_RESULT);
            }
        }

        @Test
        @DisplayName("report is persisted via runHistoryStore.saveBenchmarkReport")
        void reportIsPersisted() {
            var scenario = minimalScenario();

            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<SimulationProgress> callback = invocation.getArgument(4);
                callback.accept(completeProgress(SAMPLE_RESULT));
                return null;
            }).when(simulationService).runSimulation(any(), anyInt(), any(), any(), any());

            when(runHistoryStore.listByScenario("test-scenario"))
                    .thenReturn(List.of(runRecord("run-1", "test-scenario")));

            var expectedReport = new BenchmarkReport(
                    "bench-abc", "test-scenario", Instant.now(), 2, 1000L,
                    Map.of(), Map.of(), List.of(), null, null);
            when(aggregator.aggregate(any(), any(), any(), anyLong())).thenReturn(expectedReport);

            var report = runner.runBenchmark(scenario, 10, 2, () -> true, () -> 4000, p -> {});

            verify(runHistoryStore).saveBenchmarkReport(expectedReport);
            assertThat(report).isEqualTo(expectedReport);
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        @DisplayName("cancel sets cancelRequested flag")
        void cancelSetsCancelFlag() {
            assertThat(runner.isCancelRequested()).isFalse();
            runner.cancel();
            assertThat(runner.isCancelRequested()).isTrue();
            verify(simulationService).cancel();
        }
    }
}
