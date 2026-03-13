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

import dev.arcmem.core.config.ArcMemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier-aware decorator over {@link ArcMemEngine} that routes unit retrieval through a
 * three-tier storage model: HOT (Caffeine in-memory cache), WARM (Neo4j standard query),
 * and COLD (excluded from prompt assembly).
 *
 * <p>Implements the hybrid dense/sparse index pattern from Google AI STATIC (Su et al., 2026):
 * HOT units are served from the "dense layer" (O(1) cache), WARM from the "sparse layer"
 * (Neo4j on-demand), and COLD are excluded entirely.
 *
 * <p>The COLD exclusion is not merely a performance optimization — per sleeping-llm
 * (Guo et al., 2025), which found a sharp recall phase transition at ~13 facts
 * (0.92 at 13, 0.57 at 14), excluding low-relevance COLD units prevents them from
 * diluting LLM attention on high-relevance HOT and WARM units.
 *
 * <p>Cache population is lazy: the first call to {@link #findActiveUnitsForAssembly}
 * loads all active units from Neo4j, classifies them by tier, and populates the cache
 * with HOT units. Subsequent calls serve HOT from cache and reload WARM from Neo4j.
 *
 * <p>Only active when {@code arc-mem.tiered-storage.enabled=true}.
 */
@Service
@ConditionalOnProperty(name = "arc-mem.tiered-storage.enabled", havingValue = "true")
public class TieredMemoryUnitRepository {

    private static final Logger logger = LoggerFactory.getLogger(TieredMemoryUnitRepository.class);

    private final ArcMemEngine engine;
    private final MemoryUnitCache cache;
    private final ArcMemProperties.TierConfig tierConfig;

    public TieredMemoryUnitRepository(ArcMemEngine engine, MemoryUnitCache cache, ArcMemProperties properties) {
        this.engine = engine;
        this.cache = cache;
        this.tierConfig = properties.unit() != null ? properties.unit().tier() : null;
    }

    /**
     * Returns HOT + WARM units for prompt assembly, excluding COLD units.
     * <p>
     * On first call for a context: loads all active units from Neo4j, classifies by tier,
     * populates cache with HOT units, returns HOT + WARM.
     * On subsequent calls: serves HOT from cache, reloads WARM from Neo4j.
     */
    public List<MemoryUnit> findActiveUnitsForAssembly(String contextId) {
        if (cache.isPopulated(contextId)) {
            var hotUnits = cache.getAll(contextId);
            var warmUnits = engine.inject(contextId).stream()
                    .filter(a -> tier(a) == MemoryTier.WARM)
                    .toList();
            var result = new ArrayList<MemoryUnit>(hotUnits.size() + warmUnits.size());
            result.addAll(hotUnits);
            result.addAll(warmUnits);
            logTierDistribution(contextId, hotUnits.size(), warmUnits.size(), 0, true);
            return result;
        }

        var allUnits = engine.inject(contextId);
        var hotUnits = allUnits.stream().filter(a -> tier(a) == MemoryTier.HOT).toList();
        var warmUnits = allUnits.stream().filter(a -> tier(a) == MemoryTier.WARM).toList();
        var coldCount = (int) allUnits.stream().filter(a -> tier(a) == MemoryTier.COLD).count();

        if (!hotUnits.isEmpty()) {
            cache.putAll(contextId, hotUnits);
        } else {
            // Mark populated even with no HOT units to avoid repeated full loads
            cache.putAll(contextId, List.of());
        }

        logTierDistribution(contextId, hotUnits.size(), warmUnits.size(), coldCount, false);
        logCacheStats();

        var result = new ArrayList<MemoryUnit>(hotUnits.size() + warmUnits.size());
        result.addAll(hotUnits);
        result.addAll(warmUnits);
        return result;
    }

    /**
     * Returns all tiers for maintenance and audit operations (does not exclude COLD).
     */
    public List<MemoryUnit> findAllTiersForContext(String contextId) {
        return engine.findByContext(contextId);
    }

    private MemoryTier tier(MemoryUnit unit) {
        if (tierConfig == null) {
            return unit.memoryTier();
        }
        return MemoryTier.fromRank(unit.rank(), tierConfig.hotThreshold(), tierConfig.warmThreshold());
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
