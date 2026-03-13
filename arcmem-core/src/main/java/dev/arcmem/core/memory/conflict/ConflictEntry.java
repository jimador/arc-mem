package dev.arcmem.core.memory.conflict;

import dev.arcmem.core.memory.model.Authority;

import java.time.Instant;

/**
 * Stacked data layout for a precomputed conflict pair.
 * Carries all fields required for conflict resolution without secondary repository lookups.
 * Follows STATIC recommendation I for coalesced access.
 *
 * @param unitId       ID of the conflicting unit
 * @param unitText     proposition text of the conflicting unit
 * @param authority    authority of the conflicting unit at detection time
 * @param conflictType classification of the conflict (REVISION vs. CONTRADICTION)
 * @param confidence   detection confidence (0.0–1.0)
 * @param detectedAt   timestamp when the conflict was first detected
 */
public record ConflictEntry(
        String unitId,
        String unitText,
        Authority authority,
        ConflictType conflictType,
        double confidence,
        Instant detectedAt
) {}
