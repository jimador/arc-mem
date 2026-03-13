package dev.arcmem.core.memory.event;

/**
 * Reason an unit was archived (transitioned from active to inactive).
 */
public enum ArchiveReason {
    /**
     * Archived because a conflicting proposition replaced it.
     */
    CONFLICT_REPLACEMENT,
    /**
     * Archived due to budget eviction of the lowest-ranked unit.
     */
    BUDGET_EVICTION,
    /**
     * Archived because it stayed dormant and decayed below lifecycle threshold.
     */
    DORMANCY_DECAY,
    /**
     * Archived because user-intended revision superseded the unit.
     */
    REVISION,
    /**
     * Archived via explicit operator or system action.
     */
    MANUAL,
    /**
     * Archived by the proactive maintenance sweep (low audit score).
     */
    PROACTIVE_MAINTENANCE
}
