package dev.dunnam.diceanchors.persistence;

import com.embabel.dice.incremental.ChunkHistoryStore;

/**
 * SPI for managing chunk history in the dice-anchors project.
 * Provides access to the underlying DICE {@link ChunkHistoryStore} plus
 * project-specific lifecycle methods for context cleanup.
 * <p>
 * The default in-memory implementation ({@link InMemoryDiceAnchorsChunkHistoryStore})
 * is suitable for single-instance deployments. A Neo4j-backed implementation
 * can be added for persistence across restarts.
 * <p>
 * Implementations MUST be thread-safe, as the extraction pipeline runs
 * asynchronously via {@code @Async} event listeners.
 *
 * @see InMemoryDiceAnchorsChunkHistoryStore
 */
public interface DiceAnchorsChunkHistoryStore {

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
