package dev.dunnam.diceanchors.anchor;

/**
 * Strategy for conflict detection in the promotion pipeline.
 * Controls which detectors are used and in what order.
 */
public enum ConflictDetectionStrategy {
    /** Lexical negation detection only (fast, no LLM calls). */
    LEXICAL_ONLY,
    /** LLM-based semantic detection only. */
    SEMANTIC_ONLY,
    /** Lexical first, semantic fallback on subject-filtered candidates. Default. */
    LEXICAL_THEN_SEMANTIC,
    /**
     * Precomputed index first with LLM fallback on cache miss.
     * Eliminates redundant LLM calls for previously evaluated pairs.
     */
    INDEXED,
    /**
     * Reserved for future Prolog-based contradiction detection via DICE tuProlog (2p-kt).
     * Not yet implemented — throws {@link UnsupportedOperationException} at runtime.
     */
    LOGICAL
}
