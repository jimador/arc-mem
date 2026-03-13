package dev.arcmem.core.persistence;

/**
 * Context-scoped entity mention graph payload.
 */
public record EntityMentionGraph(java.util.List<EntityMentionNode> nodes,
                                 java.util.List<EntityMentionEdge> edges) {

    public static EntityMentionGraph empty() {
        return new EntityMentionGraph(java.util.List.of(), java.util.List.of());
    }
}
