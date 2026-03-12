package dev.dunnam.diceanchors.anchor;

/**
 * Lifecycle status of a {@link CanonizationRequest}.
 * <p>
 * Requests start as {@code PENDING} and transition to one of the terminal states:
 * {@code APPROVED}, {@code REJECTED}, or {@code STALE}.
 *
 * @see CanonizationRequest
 */
public enum CanonizationStatus {

    /** Request is awaiting human review. */
    PENDING,

    /** Approved — the authority transition was executed. */
    APPROVED,

    /** Rejected — the authority transition was cancelled. */
    REJECTED,

    /**
     * Stale — the anchor's authority changed since the request was created,
     * so the request is no longer applicable.
     */
    STALE
}
