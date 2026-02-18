package dev.dunnam.diceanchors.persistence;

import org.jspecify.annotations.NonNull;

/**
 * Undirected weighted co-mention edge between two entity nodes.
 */
public record EntityMentionEdge(@NonNull String sourceEntityId,
                                @NonNull String targetEntityId,
                                int weight,
                                java.util.List<String> propositionIds) {}
