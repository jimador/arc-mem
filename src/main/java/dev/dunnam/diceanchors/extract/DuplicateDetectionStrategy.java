package dev.dunnam.diceanchors.extract;

/**
 * Strategy for duplicate detection in the promotion pipeline.
 * Controls whether fast normalized-string matching, LLM-based detection,
 * or both are used.
 */
public enum DuplicateDetectionStrategy {
    /** Normalized-string matching only. Never calls LLM. */
    FAST_ONLY,
    /** LLM-based matching only. Skips fast-path. */
    LLM_ONLY,
    /** Fast-path first, LLM fallback for non-matches. Default. */
    FAST_THEN_LLM
}
