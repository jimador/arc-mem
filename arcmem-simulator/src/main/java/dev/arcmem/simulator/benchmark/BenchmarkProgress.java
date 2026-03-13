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

import dev.arcmem.simulator.engine.ScoringResult;
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
