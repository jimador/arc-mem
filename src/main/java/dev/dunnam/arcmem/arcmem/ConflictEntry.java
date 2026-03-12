package dev.dunnam.diceanchors.anchor;

import java.time.Instant;

/**
 * Stacked data layout for a precomputed conflict pair.
 * Carries all fields required for conflict resolution without secondary repository lookups.
 * Follows STATIC recommendation I for coalesced access.
 *
 * @param anchorId    ID of the conflicting anchor
 * @param anchorText  proposition text of the conflicting anchor
 * @param authority   authority of the conflicting anchor at detection time
 * @param conflictType classification of the conflict (REVISION vs. CONTRADICTION)
 * @param confidence  detection confidence (0.0–1.0)
 * @param detectedAt  timestamp when the conflict was first detected
 */
public record ConflictEntry(
        String anchorId,
        String anchorText,
        Authority authority,
        ConflictType conflictType,
        double confidence,
        Instant detectedAt
) {}
