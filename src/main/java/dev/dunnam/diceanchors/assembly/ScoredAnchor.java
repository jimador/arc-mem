package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.MemoryTier;

/**
 * An anchor paired with its computed relevance score for retrieval ranking.
 * Scores are in [0.0, 1.0] where higher values indicate greater relevance.
 */
public record ScoredAnchor(
        String id,
        String text,
        int rank,
        Authority authority,
        double confidence,
        MemoryTier memoryTier,
        double relevanceScore
) {}
