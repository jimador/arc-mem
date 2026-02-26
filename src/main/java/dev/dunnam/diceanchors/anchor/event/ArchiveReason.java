package dev.dunnam.diceanchors.anchor.event;

/**
 * Reason an anchor was archived (transitioned from active to inactive).
 */
public enum ArchiveReason {
    /** Archived because a conflicting proposition replaced it. */
    CONFLICT_REPLACEMENT,
    /** Archived due to budget eviction of the lowest-ranked anchor. */
    BUDGET_EVICTION,
    /** Archived because it stayed dormant and decayed below lifecycle threshold. */
    DORMANCY_DECAY,
    /** Archived because user-intended revision superseded the anchor. */
    REVISION,
    /** Archived via explicit operator or system action. */
    MANUAL
}
