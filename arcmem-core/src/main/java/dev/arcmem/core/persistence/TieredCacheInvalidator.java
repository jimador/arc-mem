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
import dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Maintains cache coherence for {@link MemoryUnitCache} by responding to unit lifecycle events.
 * <p>
 * Event handling matrix:
 * <ul>
 *   <li>{@link MemoryUnitLifecycleEvent.TierChanged}(WARM→HOT) — loads unit from Neo4j, adds to cache</li>
 *   <li>{@link MemoryUnitLifecycleEvent.TierChanged}(HOT→WARM or HOT→COLD) — removes from cache</li>
 *   <li>{@link MemoryUnitLifecycleEvent.Evicted} — removes from cache if present</li>
 *   <li>{@link MemoryUnitLifecycleEvent.Archived} — removes from cache if present</li>
 *   <li>{@link MemoryUnitLifecycleEvent.Promoted} — classifies tier; if HOT, loads and adds to cache</li>
 *   <li>{@link MemoryUnitLifecycleEvent.Reinforced} — if in cache, updates cached entry (rank changed)</li>
 *   <li>{@link MemoryUnitLifecycleEvent.AuthorityChanged} — if in cache, updates cached entry</li>
 * </ul>
 * <p>
 * Only active when {@code arc-mem.tiered-storage.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "arc-mem.tiered-storage.enabled", havingValue = "true")
public class TieredCacheInvalidator {

    private static final Logger logger = LoggerFactory.getLogger(TieredCacheInvalidator.class);

    private final MemoryUnitCache cache;
    private final MemoryUnitRepository repository;
    private final ArcMemProperties properties;

    public TieredCacheInvalidator(MemoryUnitCache cache, MemoryUnitRepository repository,
                                  ArcMemProperties properties) {
        this.cache = cache;
        this.repository = repository;
        this.properties = properties;
    }

    @EventListener
    public void onTierChanged(MemoryUnitLifecycleEvent.TierChanged event) {
        var previousTier = event.getPreviousTier();
        var newTier = event.getNewTier();
        if (newTier == MemoryTier.HOT) {
            loadAndCache(event.getContextId(), event.getUnitId());
        } else if (previousTier == MemoryTier.HOT) {
            cache.invalidate(event.getContextId(), event.getUnitId());
            logger.debug("Removed unit {} from cache on tier change {}→{}", event.getUnitId(), previousTier, newTier);
        }
    }

    @EventListener
    public void onEvicted(MemoryUnitLifecycleEvent.Evicted event) {
        cache.invalidate(event.getContextId(), event.getUnitId());
        logger.debug("Removed unit {} from cache on eviction", event.getUnitId());
    }

    @EventListener
    public void onArchived(MemoryUnitLifecycleEvent.Archived event) {
        cache.invalidate(event.getContextId(), event.getUnitId());
        logger.debug("Removed unit {} from cache on archive", event.getUnitId());
    }

    @EventListener
    public void onPromoted(MemoryUnitLifecycleEvent.Promoted event) {
        loadAndCache(event.getContextId(), event.getUnitId());
    }

    @EventListener
    public void onReinforced(MemoryUnitLifecycleEvent.Reinforced event) {
        if (cache.get(event.getContextId(), event.getUnitId()) != null) {
            loadAndCache(event.getContextId(), event.getUnitId());
        }
    }

    @EventListener
    public void onAuthorityChanged(MemoryUnitLifecycleEvent.AuthorityChanged event) {
        if (cache.get(event.getContextId(), event.getUnitId()) != null) {
            loadAndCache(event.getContextId(), event.getUnitId());
        }
    }

    private void loadAndCache(String contextId, String unitId) {
        var tierConfig = tierConfig();
        repository.findActiveUnits(contextId).stream()
                .filter(node -> unitId.equals(node.getId()))
                .map(node -> toUnit(node, tierConfig))
                .filter(unit -> unit != null && unit.memoryTier() == MemoryTier.HOT)
                .findFirst()
                .ifPresent(unit -> {
                    cache.put(contextId, unit);
                    logger.debug("Added unit {} to cache (tier=HOT)", unitId);
                });
    }

    private MemoryUnit toUnit(PropositionNode node, ArcMemProperties.TierConfig tierConfig) {
        if (node.getRank() == 0) {
            return null;
        }
        var rank = node.getRank();
        var tier = tierConfig != null
                ? MemoryTier.fromRank(rank, tierConfig.hotThreshold(), tierConfig.warmThreshold())
                : MemoryTier.WARM;
        var authorityStr = node.getAuthority();
        var authority = authorityStr != null ? Authority.valueOf(authorityStr) : Authority.PROVISIONAL;
        return new MemoryUnit(node.getId(), node.getText(), rank, authority, node.isPinned(),
                node.getConfidence(), node.getReinforcementCount(), null, 0.0, 1.0, tier);
    }

    private ArcMemProperties.TierConfig tierConfig() {
        var unit = properties.unit();
        return unit != null ? unit.tier() : null;
    }
}
