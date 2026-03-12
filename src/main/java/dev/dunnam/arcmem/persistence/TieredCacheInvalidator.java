package dev.dunnam.diceanchors.persistence;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Maintains cache coherence for {@link AnchorCache} by responding to anchor lifecycle events.
 * <p>
 * Event handling matrix:
 * <ul>
 *   <li>{@link AnchorLifecycleEvent.TierChanged}(WARM→HOT) — loads anchor from Neo4j, adds to cache</li>
 *   <li>{@link AnchorLifecycleEvent.TierChanged}(HOT→WARM or HOT→COLD) — removes from cache</li>
 *   <li>{@link AnchorLifecycleEvent.Evicted} — removes from cache if present</li>
 *   <li>{@link AnchorLifecycleEvent.Archived} — removes from cache if present</li>
 *   <li>{@link AnchorLifecycleEvent.Promoted} — classifies tier; if HOT, loads and adds to cache</li>
 *   <li>{@link AnchorLifecycleEvent.Reinforced} — if in cache, updates cached entry (rank changed)</li>
 *   <li>{@link AnchorLifecycleEvent.AuthorityChanged} — if in cache, updates cached entry</li>
 * </ul>
 * <p>
 * Only active when {@code dice-anchors.tiered-storage.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "dice-anchors.tiered-storage.enabled", havingValue = "true")
public class TieredCacheInvalidator {

    private static final Logger logger = LoggerFactory.getLogger(TieredCacheInvalidator.class);

    private final AnchorCache cache;
    private final AnchorRepository repository;
    private final DiceAnchorsProperties properties;

    public TieredCacheInvalidator(AnchorCache cache, AnchorRepository repository,
                                  DiceAnchorsProperties properties) {
        this.cache = cache;
        this.repository = repository;
        this.properties = properties;
    }

    @EventListener
    public void onTierChanged(AnchorLifecycleEvent.TierChanged event) {
        var previousTier = event.getPreviousTier();
        var newTier = event.getNewTier();
        if (newTier == MemoryTier.HOT) {
            loadAndCache(event.getContextId(), event.getAnchorId());
        } else if (previousTier == MemoryTier.HOT) {
            cache.invalidate(event.getContextId(), event.getAnchorId());
            logger.debug("Removed anchor {} from cache on tier change {}→{}", event.getAnchorId(), previousTier, newTier);
        }
    }

    @EventListener
    public void onEvicted(AnchorLifecycleEvent.Evicted event) {
        cache.invalidate(event.getContextId(), event.getAnchorId());
        logger.debug("Removed anchor {} from cache on eviction", event.getAnchorId());
    }

    @EventListener
    public void onArchived(AnchorLifecycleEvent.Archived event) {
        cache.invalidate(event.getContextId(), event.getAnchorId());
        logger.debug("Removed anchor {} from cache on archive", event.getAnchorId());
    }

    @EventListener
    public void onPromoted(AnchorLifecycleEvent.Promoted event) {
        loadAndCache(event.getContextId(), event.getAnchorId());
    }

    @EventListener
    public void onReinforced(AnchorLifecycleEvent.Reinforced event) {
        if (cache.get(event.getContextId(), event.getAnchorId()) != null) {
            loadAndCache(event.getContextId(), event.getAnchorId());
        }
    }

    @EventListener
    public void onAuthorityChanged(AnchorLifecycleEvent.AuthorityChanged event) {
        if (cache.get(event.getContextId(), event.getAnchorId()) != null) {
            loadAndCache(event.getContextId(), event.getAnchorId());
        }
    }

    private void loadAndCache(String contextId, String anchorId) {
        var tierConfig = tierConfig();
        repository.findActiveAnchors(contextId).stream()
                .filter(node -> anchorId.equals(node.getId()))
                .map(node -> toAnchor(node, tierConfig))
                .filter(anchor -> anchor != null && anchor.memoryTier() == MemoryTier.HOT)
                .findFirst()
                .ifPresent(anchor -> {
                    cache.put(contextId, anchor);
                    logger.debug("Added anchor {} to cache (tier=HOT)", anchorId);
                });
    }

    private Anchor toAnchor(PropositionNode node, DiceAnchorsProperties.TierConfig tierConfig) {
        if (node.getRank() == 0) {
            return null;
        }
        var rank = node.getRank();
        var tier = tierConfig != null
                ? MemoryTier.fromRank(rank, tierConfig.hotThreshold(), tierConfig.warmThreshold())
                : MemoryTier.WARM;
        var authorityStr = node.getAuthority();
        var authority = authorityStr != null ? Authority.valueOf(authorityStr) : Authority.PROVISIONAL;
        return new Anchor(node.getId(), node.getText(), rank, authority, node.isPinned(),
                node.getConfidence(), node.getReinforcementCount(), null, 0.0, 1.0, tier);
    }

    private DiceAnchorsProperties.TierConfig tierConfig() {
        var anchor = properties.anchor();
        return anchor != null ? anchor.tier() : null;
    }
}
