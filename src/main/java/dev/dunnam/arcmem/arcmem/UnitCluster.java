package dev.dunnam.diceanchors.anchor;

import java.util.Set;

/**
 * A connected component of the conflict graph, representing a group of anchors
 * that share semantic overlap via recorded conflict edges.
 *
 * @param anchorIds       IDs of anchors in this cluster
 * @param internalDensity edge density within the cluster: |E| / (n*(n-1)/2)
 *                        in [0.0, 1.0]; 0.0 for single-anchor clusters
 */
public record AnchorCluster(Set<String> anchorIds, double internalDensity) {

    public int size() {
        return anchorIds.size();
    }
}
