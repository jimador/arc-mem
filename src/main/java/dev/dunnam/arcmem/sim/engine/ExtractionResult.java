package dev.dunnam.diceanchors.sim.engine;

import java.util.List;

/**
 * Result of DICE proposition extraction on a single DM response during simulation.
 *
 * @param extractedCount        number of propositions extracted from the DM response
 * @param promotedCount         number of extracted propositions promoted to anchors
 * @param degradedConflictCount count of conflict checks that degraded to review/quarantine
 * @param extractedTexts        brief summaries of extracted proposition texts
 */
public record ExtractionResult(
        int extractedCount,
        int promotedCount,
        int degradedConflictCount,
        List<String> extractedTexts
) {
    /**
     * Empty result for when extraction is disabled or yields nothing.
     */
    public static ExtractionResult empty() {
        return new ExtractionResult(0, 0, 0, List.of());
    }
}
