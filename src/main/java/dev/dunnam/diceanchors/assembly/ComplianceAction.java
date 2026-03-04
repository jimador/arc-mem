package dev.dunnam.diceanchors.assembly;

/**
 * Action recommended by a {@link ComplianceEnforcer} after evaluating a response.
 * <p>
 * Callers use the suggested action to decide how to handle the validated response
 * without needing to inspect violation details directly.
 */
public enum ComplianceAction {

    /** Response is compliant; proceed normally. */
    ACCEPT,

    /** Response has violations; suggest regeneration before delivery. */
    RETRY,

    /** Response has serious violations; do not deliver. */
    REJECT
}
