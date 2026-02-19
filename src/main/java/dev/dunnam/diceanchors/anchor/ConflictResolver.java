package dev.dunnam.diceanchors.anchor;

/**
 * Strategy interface for resolving conflicts between existing anchors and incoming propositions.
 *
 * <h2>Thread safety</h2>
 * Implementations MUST be thread-safe. Multiple threads may call {@link #resolve}
 * concurrently with different conflicts.
 *
 * <h2>Error handling</h2>
 * Implementations MUST NOT throw from {@link #resolve}. On failure, return
 * {@link Resolution#KEEP_EXISTING} as the safe default to preserve existing state.
 */
public interface ConflictResolver {

    /**
     * The set of possible conflict resolution outcomes.
     */
    enum Resolution {
        /**
         * Retain the existing anchor unchanged; discard the incoming proposition.
         * Safe default when the outcome is uncertain.
         */
        KEEP_EXISTING,

        /**
         * Archive the existing anchor and allow the incoming proposition to be promoted.
         * Used when the incoming proposition has higher confidence than the existing anchor.
         */
        REPLACE,

        /**
         * Allow both the existing anchor and the incoming proposition to coexist in context.
         * Used when the conflict is ambiguous or when confidence is insufficient to decide.
         */
        COEXIST,

        /**
         * Demote the existing anchor one authority level (e.g., RELIABLE → UNRELIABLE)
         * and allow the incoming proposition to proceed to promotion.
         * Used when the incoming proposition is credible but not strong enough to replace
         * the existing anchor outright.
         */
        DEMOTE_EXISTING
    }

    /**
     * Resolve a detected conflict between an existing anchor and an incoming proposition.
     * <p>
     * Contract:
     * <ul>
     *   <li>MUST NOT throw. Return {@link Resolution#KEEP_EXISTING} on any error.</li>
     *   <li>MUST NOT mutate the anchor or proposition state — resolution is a decision only.</li>
     *   <li>Callers are responsible for applying the returned resolution (archiving, demoting, etc.).</li>
     * </ul>
     *
     * @param conflict the detected conflict; never null
     * @return the resolution decision; never null
     */
    Resolution resolve(ConflictDetector.Conflict conflict);

    /**
     * Resolves by authority level: defends RELIABLE/CANON anchors unconditionally;
     * replaces PROVISIONAL anchors when incoming confidence exceeds 0.8; coexists otherwise.
     */
    static ConflictResolver byAuthority() {
        return conflict -> {
            if (conflict.existing().authority().isAtLeast(Authority.RELIABLE)) {
                return Resolution.KEEP_EXISTING;
            }
            if (conflict.confidence() > 0.8) {
                return Resolution.REPLACE;
            }
            return Resolution.COEXIST;
        };
    }
}
