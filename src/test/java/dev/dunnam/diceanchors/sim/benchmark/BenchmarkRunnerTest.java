package dev.dunnam.diceanchors.sim.benchmark;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScoringResult;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress;
import dev.dunnam.diceanchors.sim.engine.SimulationProgress.SimulationPhase;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import dev.dunnam.diceanchors.sim.engine.SimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class BenchmarkRunnerTest {

    @Mock
    private SimulationService simulationService;

    @Mock
    private BenchmarkAggregator aggregator;

    @Mock
    private RunHistoryStore runHistoryStore;

    @Mock
    private DiceAnchorsProperties properties;

    @Mock
    private DiceAnchorsProperties.SimConfig simConfig;

    private BenchmarkRunner runner;

    private static final ScoringResult SAMPLE_RESULT = new ScoringResult(
            90.0, 1, 0, 95.0, 3.0, 5, Map.of("SUBTLE_REFRAME", 0.5)
    );

    private static SimulationProgress completeProgress(ScoringResult scoringResult) {
        return completeProgress(scoringResult, "run-abc");
    }

    private static SimulationProgress completeProgress(ScoringResult scoringResult, String runId) {
        return new SimulationProgress(
                SimulationPhase.COMPLETE, null, List.of(),
                10, 10, "player msg", "dm response",
                List.of(), null, List.of(),
                true, "Complete", true, null, null,
                List.of(), scoringResult, 500L, null, null, runId
        );
    }

    private static SimulationScenario minimalScenario() {
        var scenario = mock(SimulationScenario.class, org.mockito.Mockito.withSettings().lenient());
        when(scenario.id()).thenReturn("test-scenario");
        return scenario;
    }

    @BeforeEach
    void setUp() {
        when(properties.sim()).thenReturn(simConfig);
        when(simConfig.benchmarkParallelism()).thenReturn(4);
        runner = new BenchmarkRunner(simulationService, aggregator, runHistoryStore, properties);
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
        @DisplayName("executes N runs via SimulationService")
        void executesNRuns() {
            var scenario = minimalScenario();
            var runCount = 3;

            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<SimulationProgress> callback = invocation.getArgument(4);
                callback.accept(completeProgress(SAMPLE_RESULT));
                return null;
            }).when(simulationService).runSimulation(any(), anyInt(), any(), any(), any());

            var expectedReport = new BenchmarkReport(
                    "bench-abc", "test-scenario", Instant.now(), runCount, 1000L,
                    Map.of(), Map.of(), List.of("run-abc", "run-abc", "run-abc"), null, null);
            when(aggregator.aggregate(any(), any(), any(), anyLong())).thenReturn(expectedReport);

            runner.runBenchmark(scenario, 10, runCount, () -> true, () -> 4000, p -> {});

            verify(simulationService, times(runCount)).runSimulation(any(), anyInt(), any(), any(), any());
        }

        @Test
        @DisplayName("progress callback invoked after each run with correct totalRuns")
        void progressCallbackInvokedPerRun() {
            var scenario = minimalScenario();
            var runCount = 3;

            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<SimulationProgress> callback = invocation.getArgument(4);
                callback.accept(completeProgress(SAMPLE_RESULT));
                return null;
            }).when(simulationService).runSimulation(any(), anyInt(), any(), any(), any());

            var expectedReport = new BenchmarkReport(
                    "bench-abc", "test-scenario", Instant.now(), runCount, 1000L,
                    Map.of(), Map.of(), List.of(), null, null);
            when(aggregator.aggregate(any(), any(), any(), anyLong())).thenReturn(expectedReport);

            var progressSnapshots = new ArrayList<BenchmarkProgress>();
            runner.runBenchmark(scenario, 10, runCount, () -> true, () -> 4000, progressSnapshots::add);

            // Includes initial (completedRuns=0), per-run, and final caller-thread progress
            assertThat(progressSnapshots).hasSizeGreaterThanOrEqualTo(runCount);
            // All snapshots have correct totalRuns
            for (var snap : progressSnapshots) {
                assertThat(snap.totalRuns()).isEqualTo(runCount);
            }
            // Filter to run-completion progress (completedRuns > 0 with scoring result)
            var runCompletions = progressSnapshots.stream()
                    .filter(p -> p.completedRuns() > 0 && p.latestScoringResult() != null)
                    .toList();
            assertThat(runCompletions).hasSizeGreaterThanOrEqualTo(runCount);
            // completedRuns values should cover 1..runCount (order may vary with parallelism)
            var completedValues = runCompletions.stream()
                    .map(BenchmarkProgress::completedRuns)
                    .distinct()
                    .sorted()
                    .toList();
            assertThat(completedValues).containsExactly(1, 2, 3);
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

            var expectedReport = new BenchmarkReport(
                    "bench-abc", "test-scenario", Instant.now(), 2, 1000L,
                    Map.of(), Map.of(), List.of(), null, null);
            when(aggregator.aggregate(any(), any(), any(), anyLong())).thenReturn(expectedReport);

            var report = runner.runBenchmark(scenario, 10, 2, () -> true, () -> 4000, p -> {});

            verify(runHistoryStore).saveBenchmarkReport(expectedReport);
            assertThat(report).isEqualTo(expectedReport);
        }

        @Test
        @DisplayName("respects configured parallelism via semaphore")
        void respectsParallelismLimit() {
            when(simConfig.benchmarkParallelism()).thenReturn(2);
            runner = new BenchmarkRunner(simulationService, aggregator, runHistoryStore, properties);

            var scenario = minimalScenario();
            var maxConcurrent = new AtomicInteger(0);
            var currentConcurrent = new AtomicInteger(0);

            doAnswer(invocation -> {
                var concurrent = currentConcurrent.incrementAndGet();
                maxConcurrent.accumulateAndGet(concurrent, Math::max);
                try {
                    // Brief sleep to allow overlap detection
                    Thread.sleep(50);
                } finally {
                    currentConcurrent.decrementAndGet();
                }
                @SuppressWarnings("unchecked")
                Consumer<SimulationProgress> callback = invocation.getArgument(4);
                callback.accept(completeProgress(SAMPLE_RESULT));
                return null;
            }).when(simulationService).runSimulation(any(), anyInt(), any(), any(), any());

            var expectedReport = new BenchmarkReport(
                    "bench-abc", "test-scenario", Instant.now(), 4, 1000L,
                    Map.of(), Map.of(), List.of(), null, null);
            when(aggregator.aggregate(any(), any(), any(), anyLong())).thenReturn(expectedReport);

            runner.runBenchmark(scenario, 10, 4, () -> true, () -> 4000, p -> {});

            assertThat(maxConcurrent.get()).isLessThanOrEqualTo(2);
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
