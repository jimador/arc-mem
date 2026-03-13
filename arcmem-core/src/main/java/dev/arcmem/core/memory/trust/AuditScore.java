package dev.arcmem.core.memory.trust;

/**
 * Per-unit relevance score computed during the audit step of a proactive sweep.
 *
 * <p>{@code heuristicScore} is always computed from rank, memory tier, and recency signals.
 * {@code finalScore} is the score used by downstream steps (refresh, consolidate, prune);
 * it equals {@code heuristicScore} for light sweeps and may differ for full sweeps when
 * {@code llmRefined} is true.
 */
public record AuditScore(
        String unitId,
        double heuristicScore,
        double finalScore,
        boolean llmRefined
) {}
