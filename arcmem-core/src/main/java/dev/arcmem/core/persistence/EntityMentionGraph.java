package dev.arcmem.core.persistence;
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
 * Context-scoped entity mention graph payload.
 */
public record EntityMentionGraph(java.util.List<EntityMentionNode> nodes,
                                 java.util.List<EntityMentionEdge> edges) {

    public static EntityMentionGraph empty() {
        return new EntityMentionGraph(java.util.List.of(), java.util.List.of());
    }
}
