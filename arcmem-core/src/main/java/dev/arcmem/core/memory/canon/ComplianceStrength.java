package dev.arcmem.core.memory.canon;

/**
 * Compliance obligation strength for unit facts in prompts.
 * Maps to RFC 2119 keyword levels.
 */
public enum ComplianceStrength {
    /**
     * "MUST be preserved" — absolute requirement (CANON, RELIABLE).
     */
    STRICT,
    /**
     * "SHOULD be trusted" — strong recommendation (UNRELIABLE).
     */
    MODERATE,
    /**
     * "MAY be reconsidered" — optional (PROVISIONAL).
     */
    PERMISSIVE
}
