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
