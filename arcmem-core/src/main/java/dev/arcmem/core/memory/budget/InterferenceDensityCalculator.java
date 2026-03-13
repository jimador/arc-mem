package dev.arcmem.core.memory.budget;
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
     * Computes global edge density of the conflict graph for the given units.
     *
     * @param units       units to consider
     * @param conflictIndex precomputed conflict adjacency structure
     * @return density in [0.0, 1.0]; 0.0 when fewer than 2 units
     */
    double computeDensity(List<MemoryUnit> units, ConflictIndex conflictIndex);

    /**
     * Identifies connected components (clusters) in the conflict graph.
     * Isolated units with no conflict edges are excluded from all clusters.
     *
     * @param units       units to consider
     * @param conflictIndex precomputed conflict adjacency structure
     * @return list of clusters; empty when no units have conflict edges
     */
    List<MemoryUnitCluster> identifyClusters(List<MemoryUnit> units, ConflictIndex conflictIndex);
}
