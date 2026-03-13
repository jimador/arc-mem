package dev.arcmem.core.memory.model;
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

import java.util.Set;

/**
 * A connected component of the conflict graph, representing a group of units
 * that share semantic overlap via recorded conflict edges.
 *
 * @param unitIds       IDs of units in this cluster
 * @param internalDensity edge density within the cluster: |E| / (n*(n-1)/2)
 *                        in [0.0, 1.0]; 0.0 for single-unit clusters
 */
public record MemoryUnitCluster(Set<String> unitIds, double internalDensity) {

    public int size() {
        return unitIds.size();
    }
}
