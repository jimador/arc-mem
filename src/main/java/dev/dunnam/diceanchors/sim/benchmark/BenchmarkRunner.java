package dev.dunnam.diceanchors.sim.benchmark;

import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScoringResult;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import dev.dunnam.diceanchors.sim.engine.SimulationService;
import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Orchestrates multi-run benchmark execution for a single simulation scenario.
 * <p>
 * Executes N sequential simulation runs, collects per-run {@link ScoringResult}s,
 * aggregates them via {@link BenchmarkAggregator}, and persists the resulting
 * {@link BenchmarkReport}. Each run uses an isolated context ID (handled by
 * {@link SimulationService}).
 * <p>
 * Invariant: runs are strictly sequential to avoid concurrent Neo4j context collisions.
 */
@Service
public class BenchmarkRunner {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final SimulationService simulationService;
    private final BenchmarkAggregator aggregator;
    private final RunHistoryStore runHistoryStore;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    public BenchmarkRunner(SimulationService simulationService,
                           BenchmarkAggregator aggregator,
                           RunHistoryStore runHistoryStore) {
        this.simulationService = simulationService;
        this.aggregator = aggregator;
        this.runHistoryStore = runHistoryStore;
    }

    /**
     * Execute a benchmark: run the scenario {@code runCount} times sequentially,
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

        // Apply condition to seed anchors
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

        // Override injection supplier if condition disables injection
        Supplier<Boolean> effectiveInjectionSupplier = condition.injectionEnabled()
                ? injectionStateSupplier
                : () -> false;

        var scoringResults = new ArrayList<ScoringResult>();
        var runIds = new ArrayList<String>();

        // OTEL enrichment
        var currentSpan = Span.current();
        currentSpan.setAttribute("benchmark.scenario_id", scenario.id());
        currentSpan.setAttribute("benchmark.run_count", runCount);
        currentSpan.setAttribute("benchmark.condition", condition.name());

        logger.info("Starting benchmark: scenario='{}', runCount={}, condition='{}'",
                scenario.id(), runCount, condition.name());

        for (int i = 0; i < runCount && !cancelRequested.get(); i++) {
            var runIndex = i + 1;
            logger.info("Benchmark run {}/{} for scenario '{}' [{}]",
                    runIndex, runCount, scenario.id(), condition.name());

            var scoringResultRef = new AtomicReference<ScoringResult>();
            var runIdRef = new AtomicReference<String>();

            // Capture scoring result from the final progress callback
            simulationService.runSimulation(conditionedScenario, maxTurns,
                    effectiveInjectionSupplier, tokenBudgetSupplier,
                    progress -> {
                        if (progress.complete() && progress.scoringResult() != null) {
                            scoringResultRef.set(progress.scoringResult());
                        }
                    });

            // Capture the run ID from the run store (most recent run for this scenario)
            var recentRuns = runHistoryStore.listByScenario(scenario.id());
            if (!recentRuns.isEmpty()) {
                var latestRun = recentRuns.getFirst();
                runIdRef.set(latestRun.runId());
            }

            var scoringResult = scoringResultRef.get();
            if (scoringResult != null) {
                scoringResults.add(scoringResult);
                if (runIdRef.get() != null) {
                    runIds.add(runIdRef.get());
                }
            }

            onProgress.accept(new BenchmarkProgress(runIndex, runCount, scoringResult));
        }

        var durationMs = System.currentTimeMillis() - startTime;
        currentSpan.setAttribute("benchmark.duration_ms", durationMs);

        if (scoringResults.isEmpty()) {
            logger.warn("Benchmark produced no scoring results for scenario '{}'", scenario.id());
            // Return a minimal report
            var report = aggregator.aggregate(
                    List.of(new ScoringResult(0, 0, 0, 0, Double.NaN, 0, Map.of())),
                    scenario.id(), List.of(), durationMs);
            return report;
        }

        var report = aggregator.aggregate(scoringResults, scenario.id(), List.copyOf(runIds), durationMs);
        currentSpan.setAttribute("benchmark.mean_survival_rate",
                report.metricStatistics().containsKey("factSurvivalRate")
                        ? report.metricStatistics().get("factSurvivalRate").mean()
                        : 0.0);

        // Persist
        runHistoryStore.saveBenchmarkReport(report);
        logger.info("Benchmark complete: scenario='{}', runs={}, duration={}ms, condition='{}', reportId='{}'",
                scenario.id(), scoringResults.size(), durationMs, condition.name(), report.reportId());

        return report;
    }

    /**
     * Request cancellation of the current benchmark. The current run will complete
     * before the benchmark stops. The partial results are aggregated into a report.
     */
    public void cancel() {
        cancelRequested.set(true);
        simulationService.cancel();
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }
}
