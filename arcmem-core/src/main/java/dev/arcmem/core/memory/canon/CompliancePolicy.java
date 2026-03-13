package dev.arcmem.core.memory.canon;

import dev.arcmem.core.memory.model.Authority;

/**
 * Policy that maps authority levels to compliance strengths.
 * Controls how strongly the LLM is instructed to respect units
 * at different authority tiers.
 */
public interface CompliancePolicy {

    ComplianceStrength getStrengthFor(Authority authority);

    /**
     * All authorities treated as {@link ComplianceStrength#STRICT}. Backward-compatible default.
     */
    static CompliancePolicy flat() {
        return _ -> ComplianceStrength.STRICT;
    }

    /**
     * Maps each authority to an appropriate compliance strength.
     * CANON/RELIABLE → STRICT, UNRELIABLE → MODERATE, PROVISIONAL → PERMISSIVE.
     */
    static CompliancePolicy tiered() {
        return authority -> switch (authority) {
            case CANON, RELIABLE -> ComplianceStrength.STRICT;
            case UNRELIABLE -> ComplianceStrength.MODERATE;
            case PROVISIONAL -> ComplianceStrength.PERMISSIVE;
        };
    }
}
