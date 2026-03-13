package dev.arcmem.core.assembly.retrieval;
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
 * An unit paired with its computed relevance score for retrieval ranking.
 * Scores are in [0.0, 1.0] where higher values indicate greater relevance.
 */
public record ScoredMemoryUnit(
        String id,
        String text,
        int rank,
        Authority authority,
        double confidence,
        MemoryTier memoryTier,
        double relevanceScore
) {}
