package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;

import java.util.List;

/**
 * All inputs needed by a {@link ComplianceEnforcer} to validate a single LLM response.
 *
 * @param responseText  the LLM response text to validate
 * @param activeAnchors active anchors to check the response against
 * @param policy        strictness configuration controlling which authority tiers are enforced
 */
public record ComplianceContext(
        String responseText,
        List<Anchor> activeAnchors,
        CompliancePolicy policy
) {

    /**
     * Configuration controlling which anchor authority levels are subject to enforcement.
     * <p>
     * Decouples the enforcement scope from the {@link ComplianceEnforcer} strategy,
     * allowing callers to tune verification overhead independently of how enforcement
     * is performed.
     *
     * @param enforceCanon       validate responses against CANON anchors (MUST-level)
     * @param enforceReliable    validate responses against RELIABLE anchors (SHOULD-level)
     * @param enforceUnreliable  validate responses against UNRELIABLE anchors (MAY-level)
     * @param enforceProvisional validate responses against PROVISIONAL anchors
     */
    public record CompliancePolicy(
            boolean enforceCanon,
            boolean enforceReliable,
            boolean enforceUnreliable,
            boolean enforceProvisional
    ) {

        /** Enforces only CANON anchors. Suitable for production with minimal overhead. */
        public static CompliancePolicy canonOnly() {
            return new CompliancePolicy(true, false, false, false);
        }

        /** Enforces CANON and RELIABLE anchors. Stricter coverage for high-stakes contexts. */
        public static CompliancePolicy tiered() {
            return new CompliancePolicy(true, true, false, false);
        }
    }
}
