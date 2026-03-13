package dev.arcmem.core.assembly.retrieval;
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

/**
 * Controls how units are selected for prompt injection.
 * <ul>
 *   <li>{@code BULK} — all active units within budget (legacy behavior)</li>
 *   <li>{@code TOOL} — units retrieved on-demand via tool calls (empty baseline)</li>
 *   <li>{@code HYBRID} — heuristic/LLM-scored selection of top-k units</li>
 * </ul>
 */
public enum RetrievalMode { BULK, TOOL, HYBRID }
