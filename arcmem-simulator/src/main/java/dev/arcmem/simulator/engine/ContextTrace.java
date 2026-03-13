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
 * Captures what was injected into the LLM prompt for a single simulation turn,
 * plus any DICE extraction metadata when extraction is enabled.
 * <p>
 * Note: {@code assembledPrompt} contains the injected context blocks
 * (units and working propositions).
 * Use {@code fullSystemPrompt} and {@code fullUserPrompt} for the complete prompt.
 * <p>
 * Extraction fields default to zero/empty when extraction is not enabled for the scenario.
 */
public record ContextTrace(
        int turnNumber,
        int unitTokens,
        int totalTokens,
        List<MemoryUnit> injectedUnits,
        boolean injectionEnabled,
        String assembledPrompt,
        String fullSystemPrompt,
        String fullUserPrompt,
        boolean budgetApplied,
        int unitsExcluded,
        int propositionsExtracted,
        int propositionsPromoted,
        int degradedConflictCount,
        List<String> extractedTexts,
        int hotCount,
        int warmCount,
        int coldCount,
        ComplianceSnapshot complianceSnapshot,
        int injectionPatternsDetected,
        SweepSnapshot sweepSnapshot
) {
    /**
     * Convenience constructor for turns without extraction.
     */
    public ContextTrace(
            int turnNumber,
            int unitTokens,
            int totalTokens,
            List<MemoryUnit> injectedUnits,
            boolean injectionEnabled,
            String assembledPrompt,
            String fullSystemPrompt,
            String fullUserPrompt) {
        this(turnNumber, unitTokens, totalTokens, injectedUnits, injectionEnabled,
             assembledPrompt, fullSystemPrompt, fullUserPrompt, false, 0, 0, 0, 0, List.of(), 0, 0, 0,
             ComplianceSnapshot.none(), 0, SweepSnapshot.none());
    }

    /**
     * Convenience constructor for turns without extraction and with explicit budget metadata.
     */
    public ContextTrace(
            int turnNumber,
            int unitTokens,
            int totalTokens,
            List<MemoryUnit> injectedUnits,
            boolean injectionEnabled,
            String assembledPrompt,
            String fullSystemPrompt,
            String fullUserPrompt,
            boolean budgetApplied,
            int unitsExcluded) {
        this(turnNumber, unitTokens, totalTokens, injectedUnits, injectionEnabled,
             assembledPrompt, fullSystemPrompt, fullUserPrompt, budgetApplied, unitsExcluded, 0, 0, 0, List.of(), 0, 0, 0,
             ComplianceSnapshot.none(), 0, SweepSnapshot.none());
    }
}
