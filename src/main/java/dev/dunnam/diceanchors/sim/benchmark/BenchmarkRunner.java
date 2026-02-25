package dev.dunnam.diceanchors.sim.benchmark;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScoringResult;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import dev.dunnam.diceanchors.sim.engine.SimulationRuntimeConfig;
import dev.dunnam.diceanchors.sim.engine.SimulationService;
import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Orchestrates multi-run benchmark execution for a single simulation scenario.
 * <p>
 * Executes N simulation runs with configurable parallelism (via
 * {@code dice-anchors.sim.benchmark-parallelism}), collects per-run
 * {@link ScoringResult}s, aggregates them via {@link BenchmarkAggregator},
 * and persists the resulting {@link BenchmarkReport}. Each run uses an
 * isolated context ID (handled by {@link SimulationService}).
 * <p>
 * Concurrency is bounded by a {@link Semaphore} so that at most
 * {@code benchmarkParallelism} runs execute simultaneously. Virtual threads
 * via {@link StructuredTaskScope} handle the scheduling.
 */
@Service
public class BenchmarkRunner {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final SimulationService simulationService;
    private final BenchmarkAggregator aggregator;
    private final RunHistoryStore runHistoryStore;
    private final DiceAnchorsProperties properties;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    public BenchmarkRunner(SimulationService simulationService,
                           BenchmarkAggregator aggregator,
                           RunHistoryStore runHistoryStore,
                           DiceAnchorsProperties properties) {
        this.simulationService = simulationService;
        this.aggregator = aggregator;
        this.runHistoryStore = runHistoryStore;
        this.properties = properties;
    }

    /**
     * Execute a benchmark: run the scenario {@code runCount} times in parallel,
     * aggregate results, and persist the report.
     * <p>
     * Delegates to the condition-aware overload with {@link AblationCondition#FULL_ANCHORS}
     * (backward compatible).
     *
     * @param scenario               the scenario to benchmark
     * @param maxTurns               max turns per run
     * @param runCount               number of repetitions (must be >= 2)
     * @param injectionStateSupplier evaluated per-turn to determine anchor injection
     * @param tokenBudgetSupplier    per-turn token budget
     * @param onProgress             callback invoked after each run completes
     * @return the aggregated benchmark report
     * @throws IllegalArgumentException if runCount < 2
     */
    @Observed(name = "benchmark.run")
    public BenchmarkReport runBenchmark(
            SimulationScenario scenario,
            int maxTurns,
            int runCount,
            Supplier<Boolean> injectionStateSupplier,
            Supplier<Integer> tokenBudgetSupplier,
            Consumer<BenchmarkProgress> onProgress) {

        return runBenchmark(scenario, maxTurns, runCount, injectionStateSupplier,
                tokenBudgetSupplier, onProgress, AblationCondition.FULL_ANCHORS);
    }

    /**
     * Execute a benchmark with an ablation condition applied before each run.
     * <p>
     * Runs execute in parallel up to the configured {@code benchmarkParallelism} limit.
     * The condition overrides seed anchor authority/rank and controls injection state.
     * When the condition disables injection, the {@code injectionStateSupplier} is overridden.
     *
     * @param scenario               the scenario to benchmark
     * @param maxTurns               max turns per run
     * @param runCount               number of repetitions (must be >= 2)
     * @param injectionStateSupplier evaluated per-turn to determine anchor injection
     * @param tokenBudgetSupplier    per-turn token budget
     * @param onProgress             callback invoked after each run completes
     * @param condition              ablation condition to apply before each run
     * @return the aggregated benchmark report
     * @throws IllegalArgumentException if runCount < 2
     */
    public BenchmarkReport runBenchmark(
            SimulationScenario scenario,
            int maxTurns,
            int runCount,
            Supplier<Boolean> injectionStateSupplier,
            Supplier<Integer> tokenBudgetSupplier,
            Consumer<BenchmarkProgress> onProgress,
            AblationCondition condition) {

        if (runCount < 2) {
            throw new IllegalArgumentException("Benchmark requires at least 2 runs, got " + runCount);
        }

        cancelRequested.set(false);
        var startTime = System.currentTimeMillis();

        var modifiedSeedAnchors = condition.applySeedAnchors(
                scenario.seedAnchors() != null ? scenario.seedAnchors() : List.of());
        var conditionedScenario = new SimulationScenario(
                scenario.id(), scenario.persona(), scenario.model(), scenario.temperature(),
                scenario.maxTurns(), scenario.warmUpTurns(), scenario.adversarial(), scenario.setting(),
                scenario.groundTruth(), modifiedSeedAnchors, scenario.turns(),
                scenario.generatorModel(), scenario.evaluatorModel(), scenario.trustConfig(),
                scenario.compactionConfig(), scenario.assertions(), scenario.dormancyConfig(),
                scenario.sessions(), scenario.category(), scenario.extractionEnabled(),
                scenario.title(), scenario.objective(), scenario.testFocus(), scenario.highlights(),
                scenario.adversaryMode(), scenario.adversaryConfig(), scenario.invariants());

        Supplier<Boolean> effectiveInjectionSupplier = condition.injectionEnabled()
                ? injectionStateSupplier
                : () -> false;

        var scoringResults = Collections.synchronizedList(new ArrayList<ScoringResult>());
        var runIds = Collections.synchronizedList(new ArrayList<String>());

        var currentSpan = Span.current();
        currentSpan.setAttribute("benchmark.scenario_id", scenario.id());
        currentSpan.setAttribute("benchmark.run_count", runCount);
        currentSpan.setAttribute("benchmark.condition", condition.name());
        currentSpan.setAttribute("benchmark.rank_mutation_enabled", condition.rankMutationEnabled());
        currentSpan.setAttribute("benchmark.authority_promotion_enabled", condition.authorityPromotionEnabled());

        var parallelism = properties.sim().benchmarkParallelism();
        logger.info("Starting benchmark: scenario='{}', runCount={}, condition='{}', parallelism={}",
                scenario.id(), runCount, condition.name(), parallelism);

        var completedCount = new AtomicInteger(0);
        var semaphore = new Semaphore(parallelism);
        var latestResult = new AtomicReference<ScoringResult>();

        // Fire initial progress on the caller's thread so Vaadin push delivers it
        onProgress.accept(new BenchmarkProgress(0, runCount, null));

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<Void>awaitAllSuccessfulOrThrow())) {

            for (int i = 0; i < runCount; i++) {
                final int runIndex = i + 1;
                scope.fork(() -> {
                    if (cancelRequested.get()) return null;
                    semaphore.acquire();
                    try {
                        if (cancelRequested.get()) return null;
                        logger.info("Benchmark run {}/{} for scenario '{}' [{}]",
                                runIndex, runCount, scenario.id(), condition.name());
                        var runtimeConfig = new SimulationRuntimeConfig(
                                condition.rankMutationEnabled(),
                                condition.authorityPromotionEnabled());
                        simulationService.runSimulation(conditionedScenario, maxTurns,
                                effectiveInjectionSupplier, tokenBudgetSupplier,
                                progress -> {
                                    if (progress.complete() && progress.scoringResult() != null) {
                                        scoringResults.add(progress.scoringResult());
                                        if (progress.runId() != null) {
                                            runIds.add(progress.runId());
                                        }
                                        var completed = completedCount.incrementAndGet();
                                        latestResult.set(progress.scoringResult());
                                        onProgress.accept(new BenchmarkProgress(
                                                completed, runCount, progress.scoringResult()));
                                    }
                                }, runtimeConfig);
                    } finally {
                        semaphore.release();
                    }
                    return null;
                });
            }
            scope.join();
        } catch (StructuredTaskScope.FailedException e) {
            var cause = e.getCause();
            logger.error("Benchmark run failed: {}", cause != null ? cause.getMessage() : e.getMessage(), cause);
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Benchmark run failed", cause != null ? cause : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Benchmark interrupted for scenario '{}'", scenario.id());
        }

        // Fire final progress on the caller's thread to guarantee Vaadin push delivery.
        // Virtual thread → ui.access() may not trigger push reliably; this ensures the
        // ExperimentProgressPanel sees at least one update per cell from the caller's thread.
        var finalCompleted = completedCount.get();
        if (finalCompleted > 0 && latestResult.get() != null) {
            onProgress.accept(new BenchmarkProgress(
                    finalCompleted, runCount, latestResult.get()));
        }

        var durationMs = System.currentTimeMillis() - startTime;
        currentSpan.setAttribute("benchmark.duration_ms", durationMs);

        if (scoringResults.isEmpty()) {
            logger.warn("Benchmark produced no scoring results for scenario '{}'", scenario.id());
            var report = aggregator.aggregate(
                    List.of(new ScoringResult(0, 0, 0, 0, Double.NaN, 0, Map.of())),
                    scenario.id(), List.of(), durationMs);
            return report;
        }

        var baseReport = aggregator.aggregate(List.copyOf(scoringResults), scenario.id(), List.copyOf(runIds), durationMs);

        String modelId = null;
        if (!runIds.isEmpty()) {
            var firstRun = runHistoryStore.load(runIds.getFirst());
            if (firstRun.isPresent()) {
                modelId = firstRun.get().modelId();
            }
        }
        var report = new BenchmarkReport(
                baseReport.reportId(), baseReport.scenarioId(), baseReport.createdAt(),
                baseReport.runCount(), baseReport.totalDurationMs(), baseReport.metricStatistics(),
                baseReport.strategyStatistics(), baseReport.runIds(),
                baseReport.baselineReportId(), baseReport.baselineDeltas(), modelId);

        currentSpan.setAttribute("benchmark.mean_survival_rate",
                report.metricStatistics().containsKey("factSurvivalRate")
                        ? report.metricStatistics().get("factSurvivalRate").mean()
                        : 0.0);

        runHistoryStore.saveBenchmarkReport(report);
        logger.info("Benchmark complete: scenario='{}', runs={}, duration={}ms, condition='{}', reportId='{}'",
                scenario.id(), scoringResults.size(), durationMs, condition.name(), report.reportId());

        return report;
    }

    /**
     * Request cancellation of the current benchmark. Running reps complete their
     * current turn and exit; new reps are not started. Partial results are aggregated.
     */
    public void cancel() {
        cancelRequested.set(true);
        simulationService.cancel();
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }
}
