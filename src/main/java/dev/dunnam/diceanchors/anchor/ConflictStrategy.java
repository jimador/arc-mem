package dev.dunnam.diceanchors.anchor;

/**
 * Top-level conflict detection strategy selection for configuration.
 * Maps to the conflict detector bean wired in {@link AnchorConfiguration}.
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
