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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import dev.arcmem.core.config.ArcMemProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * In-memory Caffeine cache for HOT-tier units, providing O(1) lookup to eliminate
 * Neo4j round-trips for frequently-accessed units during prompt assembly.
 * <p>
 * Implements the "dense layer" pattern from Google AI STATIC (Su et al., 2026):
 * high-frequency access paths are served from a fast in-memory structure, while
 * lower-frequency paths fall through to the backing store (Neo4j).
 * <p>
 * Uses composite keys ({@code contextId:unitId}) so a single Caffeine instance
 * bounds total entries across all contexts. The {@code maximumSize} is set from
 * {@link ArcMemProperties.TieredStorageConfig#maxCacheSize()}, defaulting to 1000.
 * <p>
 * Only active when {@code arc-mem.tiered-storage.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "arc-mem.tiered-storage.enabled", havingValue = "true")
public class MemoryUnitCache {

    private final Cache<String, MemoryUnit> cache;
    private final Set<String> populatedContexts = ConcurrentHashMap.newKeySet();

    public MemoryUnitCache(ArcMemProperties properties) {
        var config = properties.tieredStorage();
        var maxSize = config != null ? config.maxCacheSize() : 1000;
        var ttlMinutes = config != null ? config.ttlMinutes() : 60;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    public void put(String contextId, MemoryUnit unit) {
        cache.put(key(contextId, unit.id()), unit);
    }

    public void putAll(String contextId, List<MemoryUnit> units) {
        var entries = units.stream()
                .collect(Collectors.toMap(a -> key(contextId, a.id()), a -> a));
        cache.putAll(entries);
        populatedContexts.add(contextId);
    }

    public MemoryUnit get(String contextId, String unitId) {
        return cache.getIfPresent(key(contextId, unitId));
    }

    public List<MemoryUnit> getAll(String contextId) {
        var prefix = contextId + ":";
        return cache.asMap().entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .toList();
    }

    public void invalidate(String contextId, String unitId) {
        cache.invalidate(key(contextId, unitId));
    }

    public void invalidateAll(String contextId) {
        var prefix = contextId + ":";
        var keysToRemove = cache.asMap().keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .toList();
        cache.invalidateAll(keysToRemove);
        populatedContexts.remove(contextId);
    }

    public boolean isPopulated(String contextId) {
        return populatedContexts.contains(contextId);
    }

    public CacheStats stats() {
        return cache.stats();
    }

    private String key(String contextId, String unitId) {
        return contextId + ":" + unitId;
    }
}
