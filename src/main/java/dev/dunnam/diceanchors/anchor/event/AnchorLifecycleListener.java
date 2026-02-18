package dev.dunnam.diceanchors.anchor.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Default listener that logs all anchor lifecycle events at INFO level.
 * <p>
 * Replace this bean to customize lifecycle event handling (e.g., metrics,
 * audit trail, external notifications).
 */
@Component
@ConditionalOnMissingBean(AnchorLifecycleListener.class)
public class AnchorLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(AnchorLifecycleListener.class);

    @EventListener
    public void onPromoted(AnchorPromotedEvent event) {
        logger.info("[LIFECYCLE] Anchor promoted: {} rank={} context={}",
                event.getAnchorId(), event.getInitialRank(), event.getContextId());
    }

    @EventListener
    public void onReinforced(AnchorReinforcedEvent event) {
        logger.info("[LIFECYCLE] Anchor reinforced: {} rank={}->{} count={} context={}",
                event.getAnchorId(), event.getPreviousRank(), event.getNewRank(),
                event.getReinforcementCount(), event.getContextId());
    }

    @EventListener
    public void onArchived(AnchorArchivedEvent event) {
        logger.info("[LIFECYCLE] Anchor archived: {} reason={} context={}",
                event.getAnchorId(), event.getReason(), event.getContextId());
    }

    @EventListener
    public void onConflictDetected(ConflictDetectedEvent event) {
        logger.info("[LIFECYCLE] Conflict detected: {} conflicts for '{}' context={}",
                event.getConflictCount(), event.getIncomingText(), event.getContextId());
    }

    @EventListener
    public void onConflictResolved(ConflictResolvedEvent event) {
        logger.info("[LIFECYCLE] Conflict resolved: anchor={} resolution={} context={}",
                event.getExistingAnchorId(), event.getResolution(), event.getContextId());
    }

    @EventListener
    public void onAuthorityUpgraded(AuthorityUpgradedEvent event) {
        logger.info("[LIFECYCLE] Authority upgraded: {} {}->{} count={} context={}",
                event.getAnchorId(), event.getPreviousAuthority(), event.getNewAuthority(),
                event.getReinforcementCount(), event.getContextId());
    }
}
