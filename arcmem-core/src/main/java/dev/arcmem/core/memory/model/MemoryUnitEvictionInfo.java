package dev.arcmem.core.memory.model;

/**
 * Carries the identity and previous rank of an unit that was evicted from the budget.
 * Returned by {@link dev.arcmem.core.persistence.MemoryUnitRepository#evictLowestRanked}
 * so that {@code ArcMemEngine.promote()} can publish an {@code Evicted} lifecycle event
 * for each evicted unit.
 *
 * @param unitId       the proposition node ID of the evicted unit
 * @param previousRank the rank the unit held before eviction
 */
public record MemoryUnitEvictionInfo(String unitId, int previousRank) {}
