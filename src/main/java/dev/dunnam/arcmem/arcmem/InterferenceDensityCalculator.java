package dev.dunnam.diceanchors.anchor;

import java.util.List;

/**
 * Computes interference density and cluster structure from the conflict index.
 * <p>
 * Implementations use the conflict graph as a proxy for semantic overlap, avoiding
 * per-check LLM embedding calls. The pluggable interface supports algorithm A/B testing
 * without changing the budget strategy.
 */
public interface InterferenceDensityCalculator {

    /**
     * Computes global edge density of the conflict graph for the given anchors.
     *
     * @param anchors       anchors to consider
     * @param conflictIndex precomputed conflict adjacency structure
     * @return density in [0.0, 1.0]; 0.0 when fewer than 2 anchors
     */
    double computeDensity(List<Anchor> anchors, ConflictIndex conflictIndex);

    /**
     * Identifies connected components (clusters) in the conflict graph.
     * Isolated anchors with no conflict edges are excluded from all clusters.
     *
     * @param anchors       anchors to consider
     * @param conflictIndex precomputed conflict adjacency structure
     * @return list of clusters; empty when no anchors have conflict edges
     */
    List<AnchorCluster> identifyClusters(List<Anchor> anchors, ConflictIndex conflictIndex);
}
