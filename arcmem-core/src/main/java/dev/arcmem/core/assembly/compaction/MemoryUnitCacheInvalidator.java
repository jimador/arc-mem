package dev.arcmem.core.assembly.compaction;
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
