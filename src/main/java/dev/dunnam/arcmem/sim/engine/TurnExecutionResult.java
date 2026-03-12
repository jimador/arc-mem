package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.assembly.CompactionResult;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Extended result from a single turn execution, carrying the turn record,
 * the current anchor state snapshot (for diffing into the next turn),
 * and an optional compaction result.
 */
public record TurnExecutionResult(
        SimulationTurn turn,
        Map<String, Anchor> currentAnchorState,
        @Nullable CompactionResult compactionResult
) {}
