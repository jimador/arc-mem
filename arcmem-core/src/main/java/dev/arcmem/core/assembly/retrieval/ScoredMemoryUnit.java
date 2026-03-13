package dev.arcmem.core.assembly.retrieval;

import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryTier;


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
