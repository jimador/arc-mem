package dev.dunnam.diceanchors.persistence;

import com.embabel.dice.incremental.ChunkHistoryStore;
import com.embabel.dice.incremental.InMemoryChunkHistoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of {@link DiceAnchorsChunkHistoryStore} that wraps
 * the DICE library's {@link InMemoryChunkHistoryStore}.
 * <p>
 * Thread-safe: the underlying DICE store handles its own concurrency,
 * and lifecycle operations ({@link #clearAll()}, {@link #clearByContext(String)})
 * are synchronized and replace the delegate atomically.
 * <p>
 * Suitable for single-instance deployments. For persistence across restarts,
 * a Neo4j-backed implementation of {@link DiceAnchorsChunkHistoryStore} can
 * replace this bean.
 */
@Component
public class InMemoryDiceAnchorsChunkHistoryStore implements DiceAnchorsChunkHistoryStore {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryDiceAnchorsChunkHistoryStore.class);

    private volatile ChunkHistoryStore store;

    public InMemoryDiceAnchorsChunkHistoryStore() {
        this.store = new InMemoryChunkHistoryStore();
    }

    @Override
    public ChunkHistoryStore delegate() {
        return store;
    }

    @Override
    public synchronized void clearByContext(String contextId) {
        // InMemoryChunkHistoryStore does not support per-context clearing,
        // so we reset the entire store. Acceptable for the in-memory case
        // since simulation contexts are isolated and short-lived.
        logger.debug("Clearing chunk history for context {} (full reset in memory store)", contextId);
        this.store = new InMemoryChunkHistoryStore();
    }

    @Override
    public synchronized void clearAll() {
        logger.debug("Clearing all chunk history");
        this.store = new InMemoryChunkHistoryStore();
    }
}
