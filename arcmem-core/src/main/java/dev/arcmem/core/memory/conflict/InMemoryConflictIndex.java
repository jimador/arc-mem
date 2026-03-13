package dev.arcmem.core.memory.conflict;

import dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conflict adjacency index backed by {@link ConcurrentHashMap}.
 * Thread-safe for concurrent simulation threads operating on different contexts.
 * Subscribes to unit lifecycle events for incremental maintenance.
 */
@Component
public class InMemoryConflictIndex implements ConflictIndex {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryConflictIndex.class);

    private final ConcurrentHashMap<String, Set<ConflictEntry>> index = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> contextUnits = new ConcurrentHashMap<>();

    @Override
    public Set<ConflictEntry> getConflicts(String unitId) {
        var entries = index.get(unitId);
        if (entries == null) {
            return Set.of();
        }
        return Set.copyOf(entries);
    }

    @Override
    public void recordConflict(String unitId, ConflictEntry entry) {
        index.computeIfAbsent(unitId, k -> ConcurrentHashMap.newKeySet()).add(entry);
    }

    @Override
    public void removeConflicts(String unitId) {
        index.remove(unitId);
        for (var entries : index.values()) {
            entries.removeIf(e -> e.unitId().equals(unitId));
        }
    }

    @Override
    public void clear(String contextId) {
        var units = contextUnits.remove(contextId);
        if (units == null) {
            return;
        }
        int unitCount = units.size();
        int entriesRemoved = 0;
        for (var unitId : units) {
            var removed = index.remove(unitId);
            if (removed != null) {
                entriesRemoved += removed.size();
            }
        }
        for (var entries : index.values()) {
            entries.removeIf(e -> units.contains(e.unitId()));
        }
        logger.info("Cleared conflict index for context {}: {} units, {} entries removed",
                    contextId, unitCount, entriesRemoved);
    }

    @Override
    public boolean hasConflicts(String unitId) {
        return !getConflicts(unitId).isEmpty();
    }

    @Override
    public int size() {
        return index.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Tracks unit-to-context mapping to support context-scoped cleanup.
     */
    void registerUnit(String unitId, String contextId) {
        contextUnits.computeIfAbsent(contextId, k -> ConcurrentHashMap.newKeySet()).add(unitId);
    }

    @EventListener
    public void onPromoted(MemoryUnitLifecycleEvent.Promoted event) {
        registerUnit(event.getUnitId(), event.getContextId());
        logger.debug("Registered unit {} in context {} for conflict index tracking",
                     event.getUnitId(), event.getContextId());
    }

    @EventListener
    public void onArchived(MemoryUnitLifecycleEvent.Archived event) {
        removeConflicts(event.getUnitId());
        logger.debug("Removed conflict entries for archived unit {}", event.getUnitId());
    }

    @EventListener
    public void onEvicted(MemoryUnitLifecycleEvent.Evicted event) {
        removeConflicts(event.getUnitId());
        logger.debug("Removed conflict entries for evicted unit {}", event.getUnitId());
    }
}
