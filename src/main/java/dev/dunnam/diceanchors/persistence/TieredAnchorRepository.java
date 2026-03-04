package dev.dunnam.diceanchors.persistence;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier-aware decorator over {@link AnchorEngine} that routes anchor retrieval through a
 * three-tier storage model: HOT (Caffeine in-memory cache), WARM (Neo4j standard query),
 * and COLD (excluded from prompt assembly).
 *
 * <p>Implements the hybrid dense/sparse index pattern from Google AI STATIC (Su et al., 2026):
 * HOT anchors are served from the "dense layer" (O(1) cache), WARM from the "sparse layer"
 * (Neo4j on-demand), and COLD are excluded entirely.
 *
 * <p>The COLD exclusion is not merely a performance optimization — per sleeping-llm
 * (Guo et al., 2025), which found a sharp recall phase transition at ~13 facts
 * (0.92 at 13, 0.57 at 14), excluding low-relevance COLD anchors prevents them from
 * diluting LLM attention on high-relevance HOT and WARM anchors.
 *
 * <p>Cache population is lazy: the first call to {@link #findActiveAnchorsForAssembly}
 * loads all active anchors from Neo4j, classifies them by tier, and populates the cache
 * with HOT anchors. Subsequent calls serve HOT from cache and reload WARM from Neo4j.
 *
 * <p>Only active when {@code dice-anchors.tiered-storage.enabled=true}.
 */
@Service
@ConditionalOnProperty(name = "dice-anchors.tiered-storage.enabled", havingValue = "true")
public class TieredAnchorRepository {

    private static final Logger logger = LoggerFactory.getLogger(TieredAnchorRepository.class);

    private final AnchorEngine engine;
    private final AnchorCache cache;
    private final DiceAnchorsProperties.TierConfig tierConfig;

    public TieredAnchorRepository(AnchorEngine engine, AnchorCache cache, DiceAnchorsProperties properties) {
        this.engine = engine;
        this.cache = cache;
        this.tierConfig = properties.anchor() != null ? properties.anchor().tier() : null;
    }

    /**
     * Returns HOT + WARM anchors for prompt assembly, excluding COLD anchors.
     * <p>
     * On first call for a context: loads all active anchors from Neo4j, classifies by tier,
     * populates cache with HOT anchors, returns HOT + WARM.
     * On subsequent calls: serves HOT from cache, reloads WARM from Neo4j.
     */
    public List<Anchor> findActiveAnchorsForAssembly(String contextId) {
        if (cache.isPopulated(contextId)) {
            var hotAnchors = cache.getAll(contextId);
            var warmAnchors = engine.inject(contextId).stream()
                    .filter(a -> tier(a) == MemoryTier.WARM)
                    .toList();
            var result = new ArrayList<Anchor>(hotAnchors.size() + warmAnchors.size());
            result.addAll(hotAnchors);
            result.addAll(warmAnchors);
            logTierDistribution(contextId, hotAnchors.size(), warmAnchors.size(), 0, true);
            return result;
        }

        var allAnchors = engine.inject(contextId);
        var hotAnchors = allAnchors.stream().filter(a -> tier(a) == MemoryTier.HOT).toList();
        var warmAnchors = allAnchors.stream().filter(a -> tier(a) == MemoryTier.WARM).toList();
        var coldCount = (int) allAnchors.stream().filter(a -> tier(a) == MemoryTier.COLD).count();

        if (!hotAnchors.isEmpty()) {
            cache.putAll(contextId, hotAnchors);
        } else {
            // Mark populated even with no HOT anchors to avoid repeated full loads
            cache.putAll(contextId, List.of());
        }

        logTierDistribution(contextId, hotAnchors.size(), warmAnchors.size(), coldCount, false);
        logCacheStats();

        var result = new ArrayList<Anchor>(hotAnchors.size() + warmAnchors.size());
        result.addAll(hotAnchors);
        result.addAll(warmAnchors);
        return result;
    }

    /**
     * Returns all tiers for maintenance and audit operations (does not exclude COLD).
     */
    public List<Anchor> findAllTiersForContext(String contextId) {
        return engine.findByContext(contextId);
    }

    private MemoryTier tier(Anchor anchor) {
        if (tierConfig == null) {
            return anchor.memoryTier();
        }
        return MemoryTier.fromRank(anchor.rank(), tierConfig.hotThreshold(), tierConfig.warmThreshold());
    }

    private void logTierDistribution(String contextId, int hot, int warm, int cold, boolean fromCache) {
        logger.debug("Tier distribution for context {} (cache={}): HOT={}, WARM={}, COLD={} (excluded)",
                contextId, fromCache, hot, warm, cold);
    }

    private void logCacheStats() {
        var stats = cache.stats();
        if (stats != null) {
            logger.debug("Cache stats: hits={}, misses={}, hitRate={:.2f}",
                    stats.hitCount(), stats.missCount(), stats.hitRate());
        }
    }
}
