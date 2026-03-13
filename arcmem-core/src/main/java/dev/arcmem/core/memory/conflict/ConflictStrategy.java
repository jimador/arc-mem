package dev.arcmem.core.memory.conflict;
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
 * Top-level conflict detection strategy selection for configuration.
 * Maps to the conflict detector bean wired in {@link ArcMemConfiguration}.
 */
public enum ConflictStrategy {
    LEXICAL,
    HYBRID,
    LLM,
    /** Index-first with LLM fallback on cache miss. Requires {@link InMemoryConflictIndex}. */
    INDEXED,
    /**
     * Prolog-based deterministic contradiction detection via DICE tuProlog (2p-kt).
     * Resolves logically decidable contradictions (negation pairs, incompatible states)
     * without LLM calls. Inspired by Sleeping LLM (Guo et al., 2025).
     */
    LOGICAL
}
