package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Authority;

/**
 * Records a protected anchor that was not found in the compaction summary.
 */
public record CompactionLossEvent(
        String anchorId,
        String anchorText,
        Authority authority,
        int rank
) {}
