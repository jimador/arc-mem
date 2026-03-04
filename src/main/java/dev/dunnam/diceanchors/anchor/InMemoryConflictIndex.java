package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conflict adjacency index backed by {@link ConcurrentHashMap}.
 * Thread-safe for concurrent simulation threads operating on different contexts.
 * Subscribes to anchor lifecycle events for incremental maintenance.
 */
@Component
public class InMemoryConflictIndex implements ConflictIndex {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryConflictIndex.class);

    private final ConcurrentHashMap<String, Set<ConflictEntry>> index = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> contextAnchors = new ConcurrentHashMap<>();

    @Override
    public Set<ConflictEntry> getConflicts(String anchorId) {
        var entries = index.get(anchorId);
        if (entries == null) {
            return Set.of();
        }
        return Set.copyOf(entries);
    }

    @Override
    public void recordConflict(String anchorId, ConflictEntry entry) {
        index.computeIfAbsent(anchorId, k -> ConcurrentHashMap.newKeySet()).add(entry);
    }

    @Override
    public void removeConflicts(String anchorId) {
        index.remove(anchorId);
        for (var entries : index.values()) {
            entries.removeIf(e -> e.anchorId().equals(anchorId));
        }
    }

    @Override
    public void clear(String contextId) {
        var anchors = contextAnchors.remove(contextId);
        if (anchors == null) {
            return;
        }
        int anchorCount = anchors.size();
        int entriesRemoved = 0;
        for (var anchorId : anchors) {
            var removed = index.remove(anchorId);
            if (removed != null) {
                entriesRemoved += removed.size();
            }
        }
        for (var entries : index.values()) {
            entries.removeIf(e -> anchors.contains(e.anchorId()));
        }
        logger.info("Cleared conflict index for context {}: {} anchors, {} entries removed",
                contextId, anchorCount, entriesRemoved);
    }

    @Override
    public boolean hasConflicts(String anchorId) {
        return !getConflicts(anchorId).isEmpty();
    }

    @Override
    public int size() {
        return index.values().stream().mapToInt(Set::size).sum();
    }

    /** Tracks anchor-to-context mapping to support context-scoped cleanup. */
    void registerAnchor(String anchorId, String contextId) {
        contextAnchors.computeIfAbsent(contextId, k -> ConcurrentHashMap.newKeySet()).add(anchorId);
    }

    @EventListener
    public void onPromoted(AnchorLifecycleEvent.Promoted event) {
        registerAnchor(event.getAnchorId(), event.getContextId());
        logger.debug("Registered anchor {} in context {} for conflict index tracking",
                event.getAnchorId(), event.getContextId());
    }

    @EventListener
    public void onArchived(AnchorLifecycleEvent.Archived event) {
        removeConflicts(event.getAnchorId());
        logger.debug("Removed conflict entries for archived anchor {}", event.getAnchorId());
    }

    @EventListener
    public void onEvicted(AnchorLifecycleEvent.Evicted event) {
        removeConflicts(event.getAnchorId());
        logger.debug("Removed conflict entries for evicted anchor {}", event.getAnchorId());
    }
}
