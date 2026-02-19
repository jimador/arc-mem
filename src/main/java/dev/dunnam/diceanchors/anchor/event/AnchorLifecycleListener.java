package dev.dunnam.diceanchors.anchor.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * Default listener that logs all anchor lifecycle events at INFO level.
 * <p>
 * Replace this bean to customize lifecycle event handling (e.g., metrics,
 * audit trail, external notifications).
 */
public class AnchorLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(AnchorLifecycleListener.class);

    @EventListener
    public void onPromoted(AnchorLifecycleEvent.Promoted event) {
        logger.info("[LIFECYCLE] Anchor promoted: {} rank={} context={}",
                event.getAnchorId(), event.getInitialRank(), event.getContextId());
    }

    @EventListener
    public void onReinforced(AnchorLifecycleEvent.Reinforced event) {
        logger.info("[LIFECYCLE] Anchor reinforced: {} rank={}->{} count={} context={}",
                event.getAnchorId(), event.getPreviousRank(), event.getNewRank(),
                event.getReinforcementCount(), event.getContextId());
    }

    @EventListener
    public void onArchived(AnchorLifecycleEvent.Archived event) {
        logger.info("[LIFECYCLE] Anchor archived: {} reason={} context={}",
                event.getAnchorId(), event.getReason(), event.getContextId());
    }

    @EventListener
    public void onEvicted(AnchorLifecycleEvent.Evicted event) {
        logger.info("[LIFECYCLE] Anchor evicted: {} previousRank={} context={}",
                event.getAnchorId(), event.getPreviousRank(), event.getContextId());
    }

    @EventListener
    public void onConflictDetected(AnchorLifecycleEvent.ConflictDetected event) {
        logger.info("[LIFECYCLE] Conflict detected: {} conflicts for '{}' context={}",
                event.getConflictCount(), event.getIncomingText(), event.getContextId());
    }

    @EventListener
    public void onConflictResolved(AnchorLifecycleEvent.ConflictResolved event) {
        logger.info("[LIFECYCLE] Conflict resolved: anchor={} resolution={} context={}",
                event.getExistingAnchorId(), event.getResolution(), event.getContextId());
    }

    @EventListener
    public void onAuthorityChanged(AnchorLifecycleEvent.AuthorityChanged event) {
        logger.info("[LIFECYCLE] Authority {}: {} {}->{} reason={} context={}",
                event.getDirection().name().toLowerCase(),
                event.getAnchorId(), event.getPreviousAuthority(), event.getNewAuthority(),
                event.getReason(), event.getContextId());
    }
}
