package dev.dunnam.diceanchors.anchor.event;

/**
 * Reason one anchor superseded another.
 * <p>
 * <b>Alpha note:</b> This enum is intentionally separate from {@link ArchiveReason} despite
 * near-total overlap. {@code ArchiveReason} describes <em>why an anchor was deactivated</em>
 * (which may or may not involve supersession). {@code SupersessionReason} describes
 * <em>why one anchor replaced another</em> (which always implies archival of the predecessor).
 * The overlap is intentional — see design decision D2/R5.
 * <p>
 * <b>Current model:</b> Supersession is 1:1 (one successor replaces one predecessor).
 * Fan-in (merge: multiple predecessors replaced by one successor) and fan-out
 * (split: one predecessor refined into multiple successors) are not yet modeled.
 * <p>
 * <b>Known gaps:</b>
 * <ul>
 *   <li>{@link #DECAY_DEMOTION} exists for completeness but may never produce a
 *       {@code SUPERSEDES} relationship in practice — decayed anchors typically fade
 *       out with no explicit successor.</li>
 *   <li>Future reasons to consider: {@code MERGE} (many-to-one), {@code REFINEMENT}
 *       (one-to-many), {@code SOURCE_RETRACTION} (authoritative source withdrawal),
 *       {@code USER_CORRECTION} (explicit user-provided replacement).</li>
 * </ul>
 *
 * @see ArchiveReason
 */
public enum SupersessionReason {
    /** Superseded because a conflicting proposition with higher authority/confidence replaced it. */
    CONFLICT_REPLACEMENT,
    /** Superseded due to budget eviction of the lowest-ranked anchor. */
    BUDGET_EVICTION,
    /**
     * Superseded due to decay-triggered demotion.
     * Note: In practice, decay rarely produces an explicit successor — this reason
     * is included for completeness but may not be used with a SUPERSEDES relationship.
     */
    DECAY_DEMOTION,
    /** Superseded via user-intended revision. */
    USER_REVISION,
    /** Superseded via explicit operator or system action. */
    MANUAL;

    /**
     * Map an {@link ArchiveReason} to the corresponding {@code SupersessionReason}.
     * <p>
     * <b>Alpha note:</b> The naming inconsistency between {@link ArchiveReason#DORMANCY_DECAY}
     * and {@link #DECAY_DEMOTION} is a known simplification. Both refer to the same
     * lifecycle trigger (anchor decayed below viability threshold) but use different
     * terminology. This will be harmonized in a future change.
     *
     * @param archiveReason the archive reason to map
     * @return the corresponding supersession reason
     */
    public static SupersessionReason fromArchiveReason(ArchiveReason archiveReason) {
        return switch (archiveReason) {
            case CONFLICT_REPLACEMENT -> CONFLICT_REPLACEMENT;
            case BUDGET_EVICTION -> BUDGET_EVICTION;
            case DORMANCY_DECAY -> DECAY_DEMOTION;
            case REVISION -> USER_REVISION;
            case MANUAL -> MANUAL;
            case PROACTIVE_MAINTENANCE -> MANUAL;
        };
    }
}
