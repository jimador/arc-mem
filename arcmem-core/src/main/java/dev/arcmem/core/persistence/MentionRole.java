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
 * The role an entity mention plays in a proposition.
 */
public enum MentionRole {
    /**
     * The subject of the statement (e.g., "Jim" in "Jim knows Neo4j")
     */
    SUBJECT,

    /**
     * The object of the statement (e.g., "Neo4j" in "Jim knows Neo4j")
     */
    OBJECT,

    /**
     * Other mention that doesn't fit subject/object pattern
     */
    OTHER
}
