package dev.arcmem.core.memory.event;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * Default listener that logs all unit lifecycle events at INFO level.
 * <p>
 * Replace this bean to customize lifecycle event handling (e.g., metrics,
 * audit trail, external notifications).
 */
public class MemoryUnitLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(MemoryUnitLifecycleListener.class);

    @EventListener
    public void onPromoted(MemoryUnitLifecycleEvent.Promoted event) {
        logger.info("[LIFECYCLE] MemoryUnit promoted: {} rank={} context={}",
                event.getUnitId(), event.getInitialRank(), event.getContextId());
    }

    @EventListener
    public void onReinforced(MemoryUnitLifecycleEvent.Reinforced event) {
        logger.info("[LIFECYCLE] MemoryUnit reinforced: {} rank={}->{} count={} context={}",
                event.getUnitId(), event.getPreviousRank(), event.getNewRank(),
                event.getReinforcementCount(), event.getContextId());
    }

    @EventListener
    public void onArchived(MemoryUnitLifecycleEvent.Archived event) {
        logger.info("[LIFECYCLE] MemoryUnit archived: {} reason={} context={}",
                event.getUnitId(), event.getReason(), event.getContextId());
    }

    @EventListener
    public void onEvicted(MemoryUnitLifecycleEvent.Evicted event) {
        logger.info("[LIFECYCLE] MemoryUnit evicted: {} previousRank={} context={}",
                event.getUnitId(), event.getPreviousRank(), event.getContextId());
    }

    @EventListener
    public void onConflictDetected(MemoryUnitLifecycleEvent.ConflictDetected event) {
        logger.info("[LIFECYCLE] Conflict detected: {} conflicts for '{}' context={}",
                event.getConflictCount(), event.getIncomingText(), event.getContextId());
    }

    @EventListener
    public void onConflictResolved(MemoryUnitLifecycleEvent.ConflictResolved event) {
        logger.info("[LIFECYCLE] Conflict resolved: unit={} resolution={} context={}",
                event.getExistingUnitId(), event.getResolution(), event.getContextId());
    }

    @EventListener
    public void onAuthorityChanged(MemoryUnitLifecycleEvent.AuthorityChanged event) {
        logger.info("[LIFECYCLE] Authority {}: {} {}->{} reason={} context={}",
                event.getDirection().name().toLowerCase(),
                event.getUnitId(), event.getPreviousAuthority(), event.getNewAuthority(),
                event.getReason(), event.getContextId());
    }

    @EventListener
    public void onInvariantViolation(MemoryUnitLifecycleEvent.InvariantViolation event) {
        logger.warn("[{}] Invariant violation — rule={}, strength={}, action={}, unit={}, constraint={}",
                event.getContextId(), event.getRuleId(), event.getStrength(),
                event.getBlockedAction(), event.getUnitId(), event.getConstraintDescription());
    }

    @EventListener
    public void onPressureThresholdBreached(MemoryUnitLifecycleEvent.PressureThresholdBreached event) {
        logger.warn("[LIFECYCLE] Pressure threshold breached: {} score={} context={}",
                event.getThresholdType(), event.getPressureScore().total(), event.getContextId());
    }
}
