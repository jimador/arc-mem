package dev.arcmem.core.memory.trust;
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
 * Per-unit relevance score computed during the audit step of a proactive sweep.
 *
 * <p>{@code heuristicScore} is always computed from rank, memory tier, and recency signals.
 * {@code finalScore} is the score used by downstream steps (refresh, consolidate, prune);
 * it equals {@code heuristicScore} for light sweeps and may differ for full sweeps when
 * {@code llmRefined} is true.
 */
public record AuditScore(
        String unitId,
        double heuristicScore,
        double finalScore,
        boolean llmRefined
) {}
