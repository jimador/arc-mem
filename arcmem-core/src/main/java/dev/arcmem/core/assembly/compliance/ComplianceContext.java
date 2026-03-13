package dev.arcmem.core.assembly.compliance;

import dev.arcmem.core.memory.model.MemoryUnit;

import java.util.List;

/**
 * All inputs needed by a {@link ComplianceEnforcer} to validate a single LLM response.
 *
 * @param responseText the LLM response text to validate
 * @param activeUnits  active memory units to check the response against
 * @param policy       strictness configuration controlling which authority tiers are enforced
 */
public record ComplianceContext(
        String responseText,
        List<MemoryUnit> activeUnits,
        CompliancePolicy policy
) {

    /**
     * Configuration controlling which memory unit authority levels are subject to enforcement.
     * <p>
     * Decouples the enforcement scope from the {@link ComplianceEnforcer} strategy,
     * allowing callers to tune verification overhead independently of how enforcement
     * is performed.
     *
     * @param enforceCanon       validate responses against CANON memory units (MUST-level)
     * @param enforceReliable    validate responses against RELIABLE memory units (SHOULD-level)
     * @param enforceUnreliable  validate responses against UNRELIABLE memory units (MAY-level)
     * @param enforceProvisional validate responses against PROVISIONAL memory units
     */
    public record CompliancePolicy(
            boolean enforceCanon,
            boolean enforceReliable,
            boolean enforceUnreliable,
            boolean enforceProvisional
    ) {

        /**
         * Enforces only CANON memory units. Suitable for production with minimal overhead.
         */
        public static CompliancePolicy canonOnly() {
            return new CompliancePolicy(true, false, false, false);
        }

        /**
         * Enforces CANON and RELIABLE memory units. Stricter coverage for high-stakes contexts.
         */
        public static CompliancePolicy tiered() {
            return new CompliancePolicy(true, true, false, false);
        }
    }
}
