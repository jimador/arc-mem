package dev.dunnam.diceanchors.persistence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * In-memory Caffeine cache for HOT-tier anchors, providing O(1) lookup to eliminate
 * Neo4j round-trips for frequently-accessed anchors during prompt assembly.
 * <p>
 * Implements the "dense layer" pattern from Google AI STATIC (Su et al., 2026):
 * high-frequency access paths are served from a fast in-memory structure, while
 * lower-frequency paths fall through to the backing store (Neo4j).
 * <p>
 * Uses composite keys ({@code contextId:anchorId}) so a single Caffeine instance
 * bounds total entries across all contexts. The {@code maximumSize} is set from
 * {@link DiceAnchorsProperties.TieredStorageConfig#maxCacheSize()}, defaulting to 1000.
 * <p>
 * Only active when {@code dice-anchors.tiered-storage.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "dice-anchors.tiered-storage.enabled", havingValue = "true")
public class AnchorCache {

    private final Cache<String, Anchor> cache;
    private final Set<String> populatedContexts = ConcurrentHashMap.newKeySet();

    public AnchorCache(DiceAnchorsProperties properties) {
        var config = properties.tieredStorage();
        var maxSize = config != null ? config.maxCacheSize() : 1000;
        var ttlMinutes = config != null ? config.ttlMinutes() : 60;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    public void put(String contextId, Anchor anchor) {
        cache.put(key(contextId, anchor.id()), anchor);
    }

    public void putAll(String contextId, List<Anchor> anchors) {
        var entries = anchors.stream()
                .collect(Collectors.toMap(a -> key(contextId, a.id()), a -> a));
        cache.putAll(entries);
        populatedContexts.add(contextId);
    }

    public Anchor get(String contextId, String anchorId) {
        return cache.getIfPresent(key(contextId, anchorId));
    }

    public List<Anchor> getAll(String contextId) {
        var prefix = contextId + ":";
        return cache.asMap().entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .toList();
    }

    public void invalidate(String contextId, String anchorId) {
        cache.invalidate(key(contextId, anchorId));
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

    private String key(String contextId, String anchorId) {
        return contextId + ":" + anchorId;
    }
}
