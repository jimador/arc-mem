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
