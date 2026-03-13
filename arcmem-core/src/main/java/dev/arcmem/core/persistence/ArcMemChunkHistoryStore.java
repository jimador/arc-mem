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

import com.embabel.dice.incremental.ChunkHistoryStore;

/**
 * SPI for managing chunk history in the arc-mem project.
 * Provides access to the underlying DICE {@link ChunkHistoryStore} plus
 * project-specific lifecycle methods for context cleanup.
 * <p>
 * The default in-memory implementation ({@link InMemoryArcMemChunkHistoryStore})
 * is suitable for single-instance deployments. A Neo4j-backed implementation
 * can be added for persistence across restarts.
 * <p>
 * Implementations MUST be thread-safe, as the extraction pipeline runs
 * asynchronously via {@code @Async} event listeners.
 *
 * @see InMemoryArcMemChunkHistoryStore
 */
public interface ArcMemChunkHistoryStore {

    /**
     * Returns the underlying DICE {@link ChunkHistoryStore} for use with
     * the incremental analysis pipeline.
     *
     * @return the DICE chunk history store instance
     */
    ChunkHistoryStore delegate();

    /**
     * Remove all chunk history entries for a given context.
     * Used during simulation cleanup to reset extraction state.
     *
     * @param contextId the context whose chunk history should be cleared
     */
    void clearByContext(String contextId);

    /**
     * Remove all chunk history entries.
     * Used during application startup when {@code persistence.clearOnStart} is true.
     */
    void clearAll();
}
