package dev.dunnam.diceanchors.anchor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes interference density and connected components from the conflict index
 * using BFS traversal.
 * <p>
 * Density formula: |E| / (n * (n-1) / 2) where |E| is the total number of undirected
 * conflict edges and n is the number of anchors. Returns 0.0 for n <= 1.
 * <p>
 * Isolated anchors (no conflict edges to any other anchor in the set) are excluded
 * from the cluster list. This matches the phase-transition insight from Guo et al.
 * (2025) "Sleeping LLMs" — isolated anchors contribute independently and do not
 * participate in the representational overlap that causes recall degradation.
 */
public class ConnectedComponentsCalculator implements InterferenceDensityCalculator {

    @Override
    public double computeDensity(List<Anchor> anchors, ConflictIndex conflictIndex) {
        int n = anchors.size();
        if (n <= 1) {
            return 0.0;
        }
        var anchorIds = anchorIdSet(anchors);
        int edgeCount = countEdges(anchors, anchorIds, conflictIndex);
        long maxEdges = (long) n * (n - 1) / 2;
        return maxEdges == 0 ? 0.0 : (double) edgeCount / maxEdges;
    }

    @Override
    public List<AnchorCluster> identifyClusters(List<Anchor> anchors, ConflictIndex conflictIndex) {
        if (anchors.isEmpty()) {
            return List.of();
        }
        var anchorIds = anchorIdSet(anchors);
        // Build adjacency: only include anchors in the provided set
        var adjacency = buildAdjacency(anchors, anchorIds, conflictIndex);

        // BFS connected components; skip isolated anchors (no neighbors in set)
        var visited = new HashSet<String>();
        var clusters = new ArrayList<AnchorCluster>();

        for (var anchor : anchors) {
            var id = anchor.id();
            if (visited.contains(id)) {
                continue;
            }
            var neighbors = adjacency.getOrDefault(id, Set.of());
            if (neighbors.isEmpty()) {
                visited.add(id);
                continue; // isolated — excluded from clusters
            }
            var component = bfs(id, adjacency, visited);
            clusters.add(toCluster(component, adjacency));
        }

        return clusters;
    }

    private Set<String> bfs(String start, Map<String, Set<String>> adjacency, Set<String> visited) {
        var component = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            var current = queue.poll();
            component.add(current);
            for (var neighbor : adjacency.getOrDefault(current, Set.of())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return component;
    }

    private AnchorCluster toCluster(Set<String> componentIds, Map<String, Set<String>> adjacency) {
        int n = componentIds.size();
        if (n <= 1) {
            return new AnchorCluster(Set.copyOf(componentIds), 0.0);
        }
        // Count edges within the component (each undirected edge counted once)
        int edgesWithin = 0;
        for (var id : componentIds) {
            for (var neighbor : adjacency.getOrDefault(id, Set.of())) {
                if (componentIds.contains(neighbor) && neighbor.compareTo(id) > 0) {
                    edgesWithin++;
                }
            }
        }
        long maxEdges = (long) n * (n - 1) / 2;
        double density = maxEdges == 0 ? 0.0 : (double) edgesWithin / maxEdges;
        return new AnchorCluster(Set.copyOf(componentIds), density);
    }

    private Map<String, Set<String>> buildAdjacency(
            List<Anchor> anchors, Set<String> anchorIds, ConflictIndex conflictIndex) {
        var adj = new HashMap<String, Set<String>>();
        for (var anchor : anchors) {
            var conflicts = conflictIndex.getConflicts(anchor.id());
            for (var entry : conflicts) {
                // Only include edges to anchors in the provided set
                if (anchorIds.contains(entry.anchorId())) {
                    adj.computeIfAbsent(anchor.id(), k -> new HashSet<>()).add(entry.anchorId());
                    adj.computeIfAbsent(entry.anchorId(), k -> new HashSet<>()).add(anchor.id());
                }
            }
        }
        return adj;
    }

    private int countEdges(List<Anchor> anchors, Set<String> anchorIds, ConflictIndex conflictIndex) {
        var counted = new HashSet<String>();
        int total = 0;
        for (var anchor : anchors) {
            var conflicts = conflictIndex.getConflicts(anchor.id());
            for (var entry : conflicts) {
                if (!anchorIds.contains(entry.anchorId())) {
                    continue;
                }
                // Deduplicate undirected edges using canonical pair key
                var key = anchor.id().compareTo(entry.anchorId()) < 0
                        ? anchor.id() + "|" + entry.anchorId()
                        : entry.anchorId() + "|" + anchor.id();
                if (counted.add(key)) {
                    total++;
                }
            }
        }
        return total;
    }

    private static Set<String> anchorIdSet(List<Anchor> anchors) {
        var ids = new HashSet<String>(anchors.size() * 2);
        for (var a : anchors) {
            ids.add(a.id());
        }
        return ids;
    }
}
