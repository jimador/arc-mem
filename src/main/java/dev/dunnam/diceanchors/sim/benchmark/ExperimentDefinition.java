package dev.dunnam.diceanchors.sim.benchmark;

import java.util.List;
import java.util.Optional;

/**
 * Declarative specification for an ablation experiment.
 * <p>
 * Defines the full experiment matrix: conditions × scenarios × repetitions.
 * The total number of cells is {@code conditions.size() × scenarioIds.size()},
 * and the total number of simulation runs is {@code cells × repetitionsPerCell}.
 *
 * @param name              human-readable experiment name
 * @param conditions        ablation conditions to test (non-empty)
 * @param scenarioIds       scenario IDs to run under each condition (non-empty)
 * @param repetitionsPerCell number of runs per condition-scenario cell (>= 2)
 * @param evaluatorModel    optional override for the drift evaluator model
 */
public record ExperimentDefinition(
        String name,
        List<AblationCondition> conditions,
        List<String> scenarioIds,
        int repetitionsPerCell,
        Optional<String> evaluatorModel
) {
    public ExperimentDefinition {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("Experiment requires at least one condition");
        }
        if (scenarioIds == null || scenarioIds.isEmpty()) {
            throw new IllegalArgumentException("Experiment requires at least one scenario");
        }
        if (repetitionsPerCell < 2) {
            throw new IllegalArgumentException(
                    "Experiment requires at least 2 repetitions per cell, got " + repetitionsPerCell);
        }
        conditions = List.copyOf(conditions);
        scenarioIds = List.copyOf(scenarioIds);
        evaluatorModel = evaluatorModel != null ? evaluatorModel : Optional.empty();
    }

    /** Total number of cells in the experiment matrix. */
    public int totalCells() {
        return conditions.size() * scenarioIds.size();
    }

    /** Total number of simulation runs across all cells. */
    public int totalRuns() {
        return totalCells() * repetitionsPerCell;
    }
}
