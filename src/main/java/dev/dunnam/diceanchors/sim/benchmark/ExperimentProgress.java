package dev.dunnam.diceanchors.sim.benchmark;

/**
 * Progress snapshot for experiment execution, reported at run granularity.
 *
 * @param currentCell   current cell index (1-based)
 * @param totalCells    total number of cells in the experiment
 * @param conditionName the ablation condition for the current cell
 * @param scenarioId    the scenario ID for the current cell
 * @param currentRun    current run index within the cell (1-based)
 * @param totalRuns     total repetitions per cell
 */
public record ExperimentProgress(
        int currentCell,
        int totalCells,
        String conditionName,
        String scenarioId,
        int currentRun,
        int totalRuns
) {
    public String message() {
        return "Cell %d/%d: %s x %s, Run %d/%d".formatted(
                currentCell, totalCells, conditionName, scenarioId, currentRun, totalRuns);
    }
}
