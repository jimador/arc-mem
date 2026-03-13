package dev.arcmem.core.memory.conflict;

import dev.arcmem.core.memory.engine.ArcMemConfiguration;

/**
 * Top-level conflict detection strategy selection for configuration.
 * Maps to the conflict detector bean wired in {@link ArcMemConfiguration}.
 */
public enum ConflictStrategy {
    LEXICAL,
    HYBRID,
    LLM,
    /**
     * Index-first with LLM fallback on cache miss. Requires {@link InMemoryConflictIndex}.
     */
    INDEXED
}
