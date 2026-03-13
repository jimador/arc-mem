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

/**
 * Carries the identity and previous rank of an unit that was evicted from the budget.
 * Returned by {@link dev.arcmem.core.persistence.MemoryUnitRepository#evictLowestRanked}
 * so that {@code ArcMemEngine.promote()} can publish an {@code Evicted} lifecycle event
 * for each evicted unit.
 *
 * @param unitId    the proposition node ID of the evicted unit
 * @param previousRank the rank the unit held before eviction
 */
public record MemoryUnitEvictionInfo(String unitId, int previousRank) {}
