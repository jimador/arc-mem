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
 * conflict edges and n is the number of units. Returns 0.0 for n <= 1.
 * <p>
 * Isolated units (no conflict edges to any other unit in the set) are excluded
 * from the cluster list. This matches the phase-transition insight from Guo et al.
 * (2025) "Sleeping LLMs" — isolated units contribute independently and do not
 * participate in the representational overlap that causes recall degradation.
 */
public class ConnectedComponentsCalculator implements InterferenceDensityCalculator {

    @Override
    public double computeDensity(List<MemoryUnit> units, ConflictIndex conflictIndex) {
        int n = units.size();
        if (n <= 1) {
            return 0.0;
        }
        var unitIds = unitIdSet(units);
        int edgeCount = countEdges(units, unitIds, conflictIndex);
        long maxEdges = (long) n * (n - 1) / 2;
        return maxEdges == 0 ? 0.0 : (double) edgeCount / maxEdges;
    }

    @Override
    public List<MemoryUnitCluster> identifyClusters(List<MemoryUnit> units, ConflictIndex conflictIndex) {
        if (units.isEmpty()) {
            return List.of();
        }
        var unitIds = unitIdSet(units);
        // Build adjacency: only include units in the provided set
        var adjacency = buildAdjacency(units, unitIds, conflictIndex);

        // BFS connected components; skip isolated units (no neighbors in set)
        var visited = new HashSet<String>();
        var clusters = new ArrayList<MemoryUnitCluster>();

        for (var unit : units) {
            var id = unit.id();
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

    private MemoryUnitCluster toCluster(Set<String> componentIds, Map<String, Set<String>> adjacency) {
        int n = componentIds.size();
        if (n <= 1) {
            return new MemoryUnitCluster(Set.copyOf(componentIds), 0.0);
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
        return new MemoryUnitCluster(Set.copyOf(componentIds), density);
    }

    private Map<String, Set<String>> buildAdjacency(
            List<MemoryUnit> units, Set<String> unitIds, ConflictIndex conflictIndex) {
        var adj = new HashMap<String, Set<String>>();
        for (var unit : units) {
            var conflicts = conflictIndex.getConflicts(unit.id());
            for (var entry : conflicts) {
                // Only include edges to units in the provided set
                if (unitIds.contains(entry.unitId())) {
                    adj.computeIfAbsent(unit.id(), k -> new HashSet<>()).add(entry.unitId());
                    adj.computeIfAbsent(entry.unitId(), k -> new HashSet<>()).add(unit.id());
                }
            }
        }
        return adj;
    }

    private int countEdges(List<MemoryUnit> units, Set<String> unitIds, ConflictIndex conflictIndex) {
        var counted = new HashSet<String>();
        int total = 0;
        for (var unit : units) {
            var conflicts = conflictIndex.getConflicts(unit.id());
            for (var entry : conflicts) {
                if (!unitIds.contains(entry.unitId())) {
                    continue;
                }
                // Deduplicate undirected edges using canonical pair key
                var key = unit.id().compareTo(entry.unitId()) < 0
                        ? unit.id() + "|" + entry.unitId()
                        : entry.unitId() + "|" + unit.id();
                if (counted.add(key)) {
                    total++;
                }
            }
        }
        return total;
    }

    private static Set<String> unitIdSet(List<MemoryUnit> units) {
        var ids = new HashSet<String>(units.size() * 2);
        for (var a : units) {
            ids.add(a.id());
        }
        return ids;
    }
}
