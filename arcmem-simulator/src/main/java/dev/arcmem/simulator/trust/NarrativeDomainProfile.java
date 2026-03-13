package dev.arcmem.simulator.trust;

import dev.arcmem.core.memory.model.DomainProfile;

import java.util.Map;

/**
 * Source-heavy weights with permissive thresholds for narrative/RPG domains.
 * Trusts DM-sourced propositions more readily than the default BALANCED profile.
 */
public final class NarrativeDomainProfile {

    private NarrativeDomainProfile() {}

    public static final DomainProfile NARRATIVE = new DomainProfile("NARRATIVE",
            Map.of("sourceAuthority", 0.35, "extractionConfidence", 0.25,
                   "graphConsistency", 0.15, "corroboration", 0.15,
                   "novelty", 0.05, "importance", 0.05),
            0.60, 0.35, 0.20);
}
