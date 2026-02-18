package dev.dunnam.diceanchors.persistence;

import org.jspecify.annotations.Nullable;

/**
 * Filter options for entity mention graph retrieval.
 */
public record EntityMentionGraphFilter(int minEdgeWeight,
                                       @Nullable String entityType,
                                       boolean activeOnly) {

    public EntityMentionGraphFilter {
        minEdgeWeight = Math.max(1, minEdgeWeight);
        if (entityType != null && entityType.isBlank()) {
            entityType = null;
        }
    }

    public static EntityMentionGraphFilter defaults() {
        return new EntityMentionGraphFilter(1, null, false);
    }
}
