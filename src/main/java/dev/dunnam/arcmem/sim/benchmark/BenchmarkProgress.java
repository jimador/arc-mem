package dev.dunnam.diceanchors.sim.benchmark;

import dev.dunnam.diceanchors.sim.engine.ScoringResult;
import org.jspecify.annotations.Nullable;

/**
 * Progress snapshot delivered to the UI after each benchmark run completes.
 *
 * @param completedRuns       number of runs completed so far
 * @param totalRuns           total number of runs in this benchmark
 * @param latestScoringResult scoring result from the most recently completed run; null before first run
 */
public record BenchmarkProgress(
        int completedRuns,
        int totalRuns,
        @Nullable ScoringResult latestScoringResult
) {}
