package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which contexts have stale cached anchor data by listening to lifecycle events.
 * Thread-safe via ConcurrentHashMap.newKeySet().
 */
@Service
public class AnchorCacheInvalidator {

    private final Set<String> dirtyContextIds = ConcurrentHashMap.newKeySet();

    @EventListener
    public void onLifecycleEvent(AnchorLifecycleEvent event) {
        dirtyContextIds.add(event.getContextId());
    }

    public boolean isDirty(String contextId) {
        return dirtyContextIds.contains(contextId);
    }

    public void markClean(String contextId) {
        dirtyContextIds.remove(contextId);
    }
}
