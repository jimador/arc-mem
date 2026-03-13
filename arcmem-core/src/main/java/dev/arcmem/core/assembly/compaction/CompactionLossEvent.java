package dev.arcmem.core.assembly.compaction;

import dev.arcmem.core.memory.model.Authority;


/**
 * Records a protected unit that was not found in the compaction summary.
 */
public record CompactionLossEvent(
        String unitId,
        String unitText,
        Authority authority,
        int rank
) {}
