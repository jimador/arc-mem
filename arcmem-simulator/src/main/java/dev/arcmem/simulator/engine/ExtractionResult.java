package dev.arcmem.simulator.engine;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import java.util.List;

/**
 * Result of DICE proposition extraction on a single DM response during simulation.
 *
 * @param extractedCount        number of propositions extracted from the DM response
 * @param promotedCount         number of extracted propositions promoted to units
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
