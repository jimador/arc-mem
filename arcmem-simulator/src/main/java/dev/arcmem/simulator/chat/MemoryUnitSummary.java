package dev.arcmem.simulator.chat;
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

import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * Structured summary of an unit for LLM tool responses.
 * Carries the essential fields an LLM needs to reason about an unit:
 * identity, content, rank, authority level, pin status, and confidence.
 */
@JsonClassDescription("Summary of an established fact (unit) with its rank, authority level, and confidence")
public record MemoryUnitSummary(
        String id,
        String text,
        int rank,
        String authority,
        boolean pinned,
        double confidence
) {}
