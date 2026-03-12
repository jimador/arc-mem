package dev.dunnam.diceanchors.anchor;

/**
 * Reason for an authority demotion, carried in
 * {@link dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent.AuthorityChanged}.
 * <p>
 * Demotion reasons provide a structured audit trail for why an anchor's authority was
 * reduced. They are also used as the {@code reason} string in
 * {@code AuthorityChanged} events (via {@link #name()}).
 *
 * @see dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent.AuthorityChanged
 */
public enum DemotionReason {

    /**
     * Contradicting evidence found via conflict resolution.
     * Set when the conflict resolver returns {@code DEMOTE_EXISTING} for an incoming
     * proposition that contradicts this anchor.
     */
    CONFLICT_EVIDENCE,

    /**
     * Trust re-evaluation scored the anchor below the threshold for its current authority
     * level (see anchor-trust spec: "Trust ceiling enforcement on re-evaluation").
     * Applied after conflict resolution or at reinforcement milestone thresholds (3x, 7x).
     */
    TRUST_DEGRADATION,

    /**
     * Rank dropped below the authority-specific threshold via decay.
     * Default thresholds: RELIABLE anchors demoted when rank &lt; 400;
     * UNRELIABLE anchors demoted when rank &lt; 200.
     */
    RANK_DECAY,

    /**
     * Explicit user or system action via tool call ({@code demoteAnchor}) or direct
     * {@link dev.dunnam.diceanchors.anchor.AnchorEngine#demote} API call.
     */
    MANUAL
}
