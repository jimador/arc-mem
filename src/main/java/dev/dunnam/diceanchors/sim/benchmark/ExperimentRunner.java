package dev.dunnam.diceanchors.sim.benchmark;

import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScenarioLoader;
import io.micrometer.observation.annotation.Observed;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Orchestrates ablation experiment execution: conditions × scenarios × repetitions.
 * <p>
 * Delegates to {@link BenchmarkRunner} for each cell (condition-scenario pair),
 * then computes cross-condition statistics via {@link EffectSizeCalculator}.
 * <p>
 * Invariants:
 * <ul>
 *   <li><strong>EX1</strong>: Each run uses a unique isolated contextId</li>
 *   <li><strong>EX2</strong>: Cells execute strictly sequentially</li>
 *   <li><strong>EX3</strong>: Does not modify SimulationService beyond BenchmarkRunner</li>
 *   <li><strong>EX4</strong>: Cancellation always produces a valid report with >= 1 cell</li>
 * </ul>
 */
@Service
public class ExperimentRunner {

    private static final Logger logger = LoggerFactory.getLogger(ExperimentRunner.class);

    private final BenchmarkRunner benchmarkRunner;
    private final EffectSizeCalculator effectSizeCalculator;
    private final ScenarioLoader scenarioLoader;
    private final RunHistoryStore runHistoryStore;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    public ExperimentRunner(BenchmarkRunner benchmarkRunner,
                            EffectSizeCalculator effectSizeCalculator,
                            ScenarioLoader scenarioLoader,
                            RunHistoryStore runHistoryStore) {
        this.benchmarkRunner = benchmarkRunner;
        this.effectSizeCalculator = effectSizeCalculator;
        this.scenarioLoader = scenarioLoader;
        this.runHistoryStore = runHistoryStore;
    }

    /**
     * Execute a full ablation experiment.
     *
     * @param definition             the experiment specification
     * @param injectionStateSupplier base injection state (overridden by conditions)
     * @param tokenBudgetSupplier    per-turn token budget
     * @param onProgress             callback invoked after each run completes
     * @return the assembled experiment report
     */
    @Observed(name = "experiment.run")
    public ExperimentReport runExperiment(
            ExperimentDefinition definition,
            Supplier<Boolean> injectionStateSupplier,
            Supplier<Integer> tokenBudgetSupplier,
            Consumer<ExperimentProgress> onProgress) {

        cancelRequested.set(false);
        var startTime = System.currentTimeMillis();
        var totalCells = definition.totalCells();

        // OTEL enrichment on the @Observed span
        var experimentSpan = Span.current();
        experimentSpan.setAttribute("experiment.name", definition.name());
        experimentSpan.setAttribute("experiment.condition_count", definition.conditions().size());
        experimentSpan.setAttribute("experiment.scenario_count", definition.scenarioIds().size());
        experimentSpan.setAttribute("experiment.total_cells", totalCells);
        experimentSpan.setAttribute("experiment.repetitions", definition.repetitionsPerCell());

        logger.info("Starting experiment '{}': {} conditions x {} scenarios x {} reps = {} cells",
                definition.name(), definition.conditions().size(), definition.scenarioIds().size(),
                definition.repetitionsPerCell(), totalCells);

        var cellReports = new LinkedHashMap<String, BenchmarkReport>();
        var cellIndex = 0;
        var cancelled = false;

        for (var condition : definition.conditions()) {
            for (var scenarioId : definition.scenarioIds()) {
                if (cancelRequested.get()) {
                    cancelled = true;
                    logger.info("Experiment '{}' cancelled after {} cells", definition.name(), cellIndex);
                    break;
                }

                cellIndex++;
                var cellKey = condition.name() + ":" + scenarioId;

                logger.info("Cell {}/{}: {} x {}", cellIndex, totalCells, condition.name(), scenarioId);

                var scenario = scenarioLoader.load(scenarioId);
                var finalCellIndex = cellIndex;

                var cellReport = benchmarkRunner.runBenchmark(
                        scenario,
                        scenario.maxTurns(),
                        definition.repetitionsPerCell(),
                        injectionStateSupplier,
                        tokenBudgetSupplier,
                        benchProgress -> {
                            onProgress.accept(new ExperimentProgress(
                                    finalCellIndex, totalCells,
                                    condition.name(), scenarioId,
                                    benchProgress.completedRuns(),
                                    definition.repetitionsPerCell()));
                        },
                        condition);

                cellReports.put(cellKey, cellReport);
            }
            if (cancelled) break;
        }

        var durationMs = System.currentTimeMillis() - startTime;

        // Compute cross-condition statistics
        var effectSizeMatrix = effectSizeCalculator.computeEffectSizes(cellReports, definition.conditions());
        var confidenceIntervals = effectSizeCalculator.computeConfidenceIntervals(cellReports);
        var strategyDeltas = effectSizeCalculator.computeStrategyDeltas(cellReports);

        var report = new ExperimentReport(
                "exp-" + UUID.randomUUID().toString().substring(0, 8),
                definition.name(),
                Instant.now(),
                definition.conditions().stream().map(AblationCondition::name).toList(),
                definition.scenarioIds(),
                definition.repetitionsPerCell(),
                durationMs,
                cellReports,
                effectSizeMatrix,
                confidenceIntervals,
                strategyDeltas,
                cancelled);

        runHistoryStore.saveExperimentReport(report);
        logger.info("Experiment '{}' complete: {} cells, {}ms, reportId='{}'",
                definition.name(), cellReports.size(), durationMs, report.reportId());

        return report;
    }

    /** Request cancellation. Current cell completes before stopping. */
    public void cancel() {
        cancelRequested.set(true);
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }
}
