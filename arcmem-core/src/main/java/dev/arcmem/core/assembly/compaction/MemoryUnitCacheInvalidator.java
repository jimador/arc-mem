package dev.arcmem.core.assembly.compaction;

import dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which contexts have stale cached unit data by listening to lifecycle events.
 * Thread-safe via ConcurrentHashMap.newKeySet().
 */
@Service
public class MemoryUnitCacheInvalidator {

    private final Set<String> dirtyContextIds = ConcurrentHashMap.newKeySet();

    @EventListener
    public void onLifecycleEvent(MemoryUnitLifecycleEvent event) {
        dirtyContextIds.add(event.getContextId());
    }

    public boolean isDirty(String contextId) {
        return dirtyContextIds.contains(contextId);
    }

    public void markClean(String contextId) {
        dirtyContextIds.remove(contextId);
    }
}
