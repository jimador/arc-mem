# Feature: Tiered Anchor Storage

## Feature ID

`F11`

## Summary

Implement differentiated storage and access patterns based on `MemoryTier`, inspired by STATIC's hybrid dense/sparse index. HOT anchors use in-memory cached O(1) access; WARM anchors use standard Neo4j queries; COLD anchors use compressed, lazy-loaded representations excluded from standard prompt assembly. Currently all anchors are loaded uniformly via `findActiveAnchorsByContext()` regardless of tier.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: All anchors are loaded uniformly regardless of access frequency. A CANON anchor referenced every turn and a COLD anchor about to be evicted both traverse the same Neo4j query path. Research (STATIC) shows hybrid dense/sparse access patterns dramatically reduce latency by matching storage strategy to access frequency. The current uniform loading wastes I/O on cold data and misses the opportunity to serve hot data from memory.
2. **Value delivered**: Reduced prompt assembly latency for HOT-heavy contexts, reduced I/O for COLD anchors that are rarely needed, and a storage architecture that scales as anchor counts grow. Makes `MemoryTier` classification actionable rather than purely informational.
3. **Why now**: Wave 4. The `MemoryTier` classification (COLD, WARM, HOT) already exists and is computed from rank thresholds, but it has no influence on storage or access patterns. This feature makes the tiering meaningful. Depends on F02 (unified maintenance strategy) for maintenance sweep integration.

## Scope

### In Scope

1. `TieredAnchorRepository` decorator wrapping `AnchorRepository` with tier-aware routing.
2. HOT tier: in-memory cache with O(1) lookup by anchor ID. Pre-loaded on context initialization. Cache bounded by hot-tier rank threshold.
3. WARM tier: standard Neo4j query path. Loaded on demand for prompt assembly.
4. COLD tier: excluded from standard prompt assembly. Loaded only during maintenance sweeps, audit operations, or explicit retrieval.
5. Cache invalidation driven by `AnchorLifecycleEvent` (promote, archive, rank change, evict, authority change).
6. Prompt assembly integration: `AnchorsLlmReference` queries HOT tier first (cached), then WARM as needed. COLD excluded.
7. Maintenance sweep access: proactive maintenance cycle (F07) accesses ALL tiers during audit.
8. Fallback mode: uniform loading (current behavior) available as configuration option.

### Out of Scope

1. COLD tier compression format (initial COLD tier simply skips loading; actual compression is a future optimization).
2. Distributed caching (cache is JVM-local, per-instance).
3. Cache warming strategies beyond initial context load (predictive pre-fetch, etc.).
4. Changes to `MemoryTier` threshold calculation (existing rank-based thresholds are preserved).
5. Neo4j schema changes (tiered access is a query-routing concern, not a persistence schema concern).

## Dependencies

1. Feature dependencies: F02 (unified maintenance strategy -- maintenance sweeps access all tiers).
2. Priority: MAY.
3. Wave: 4.
4. OpenSpec change slug: `tiered-anchor-storage`.
5. Research rec: G (hybrid hot/cold anchor access).

### Library Dependencies

| Dependency | Artifact | Version | Status | License | Size |
|-----------|----------|---------|--------|---------|------|
| **Caffeine** | `com.github.ben-manes.caffeine:caffeine` | 3.2.3 (managed by Spring Boot BOM) | Confirmed choice for HOT-tier cache | Apache 2.0 | ~600 KB |

Caffeine provides O(1) lookup, size-bounded eviction (W-TinyLFU algorithm), TTL support, removal listeners for invalidation callbacks, lock-free concurrent reads, and built-in hit/miss statistics. Version is managed by the Spring Boot BOM — no explicit version tag REQUIRED in the POM.

## Research Requirements

1. **Cache implementation**: Decided — **Caffeine** is the confirmed choice. See Dependencies above.
2. **Cache size bounds**: The relationship between cache size, hot-tier threshold, and actual anchor counts under typical scenarios needs empirical measurement.
3. **Migration strategy**: How contexts with in-flight operations transition from uniform loading to tiered loading without data loss or inconsistency requires careful design.

## Impacted Areas

1. **`persistence/` package (primary)**: New `TieredAnchorRepository` decorator wrapping `AnchorRepository`. New `AnchorCache` component for HOT tier in-memory storage.
2. **`assembly/` package**: `AnchorsLlmReference` and `PromptBudgetEnforcer` modified to query tiers in order (HOT first, WARM second, COLD excluded from assembly).
3. **`anchor/` package**: Cache invalidation wired to `AnchorLifecycleEvent` bus. No changes to `MemoryTier` enum or tier calculation.
4. **`DiceAnchorsProperties`**: New `tiered-storage` config section with `enabled` (default: false), cache implementation, cache size bounds, and fallback behavior.
5. **`sim/engine/` package**: `SimulationTurnExecutor` MAY configure tiered storage per scenario YAML. Maintenance sweeps (F07) access all tiers.

## Visibility Requirements

### UI Visibility

1. RunInspectorView SHOULD display tier distribution for each turn (HOT count, WARM count, COLD count).
2. Context traces SHOULD indicate when anchors were served from cache vs. Neo4j.

### Observability Visibility

1. Cache hit/miss rates MUST be logged per tier: `cache.hot.hits`, `cache.hot.misses`.
2. Load latency per tier SHOULD be measured: `storage.hot.latency`, `storage.warm.latency`.
3. Tier distribution MUST be logged at each prompt assembly: `tier.hot.count`, `tier.warm.count`, `tier.cold.count`.
4. Cache invalidation events MUST be logged: `cache.invalidate.reason`, `cache.invalidate.anchorId`.

## Acceptance Criteria

1. HOT anchors MUST be served from in-memory cache with O(1) lookup by anchor ID.
2. WARM anchors MUST be served from standard Neo4j queries.
3. COLD anchors MUST NOT be loaded for standard prompt assembly.
4. Cache MUST invalidate on relevant lifecycle events (promote, archive, rank change, evict, authority change).
5. Prompt assembly latency SHOULD decrease for HOT-heavy contexts compared to uniform loading baseline.
6. All tiers MUST be accessible for maintenance sweeps and audit operations.
7. Default behavior (all tiers loaded uniformly) MUST remain available as fallback via configuration.
8. Cache coherence MUST be maintained: no stale data served after a lifecycle event modifies an anchor's state.
9. Tier transitions (anchor moves from WARM to HOT due to rank increase) MUST update cache accordingly.
10. No anchors MUST be lost during tier transitions or cache invalidation.

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Cache coherence failure** | Medium | High | Cache invalidation is event-driven via existing `AnchorLifecycleEvent` bus. Every mutation that changes rank/authority/pinned status emits an event. Test cache state after each lifecycle event type. |
| **COLD exclusion misses relevant anchors** | Medium | Medium | COLD tier contains anchors with rank below the cold threshold -- they are near-eviction candidates. If a COLD anchor becomes relevant (e.g., reinforced), its rank rises, moving it to WARM or HOT tier and into the cache. Maintenance sweeps access all tiers to catch anchors that need re-evaluation. |
| **Cache memory pressure** | Low | Medium | Cache is bounded by hot-tier count (typically 5-10 anchors per context). Each anchor is lightweight (text + metadata). Even with 100 concurrent contexts, memory is negligible. Size bounds are configurable. |
| **Fallback mode behavioral drift** | Low | High | Fallback mode MUST be tested alongside tiered mode. Regression tests run with `tiered-storage.enabled=false` to verify identical results to pre-feature behavior. |
| **Concurrent access to cache during invalidation** | Medium | Medium | Use thread-safe cache implementation. Invalidation removes entry atomically. Concurrent readers get cache miss and fall through to Neo4j (correct behavior). |

## Proposal Seed

### Change Slug

`tiered-anchor-storage`

### Proposal Starter Inputs

1. **Problem statement**: All anchors are loaded uniformly regardless of how frequently they're used. A CANON anchor referenced every turn and a COLD anchor about to be evicted both go through the same Neo4j query path. Research (STATIC) shows hybrid dense/sparse access patterns dramatically reduce latency by matching storage strategy to access frequency.
2. **Why now**: Wave 4 -- the `MemoryTier` classification already exists but doesn't influence storage or access patterns. This feature makes tiering actionable rather than purely informational.
3. **Constraints/non-goals**: MUST NOT lose anchors during tier transitions. Cache coherence MUST be maintained via event-driven invalidation. Fallback to uniform loading MUST be available. No distributed caching. No Neo4j schema changes.
4. **Visible outcomes**: Cache hit/miss rates visible in observability. Tier distribution visible in simulation traces. Measurable latency reduction for HOT-heavy contexts.

### Suggested Capability Areas

1. **Tiered repository**: `TieredAnchorRepository` decorator routing queries by `MemoryTier`.
2. **HOT cache**: In-memory O(1) lookup with event-driven invalidation.
3. **Assembly integration**: `AnchorsLlmReference` queries tiers in priority order (HOT, WARM; COLD excluded).
4. **Maintenance access**: Full-tier access for audit and maintenance sweeps.

### Candidate Requirement Blocks

1. **REQ-HOT-CACHE**: The system SHALL serve HOT-tier anchors from an in-memory cache with O(1) lookup.
2. **REQ-COLD-EXCLUDE**: The system SHALL NOT include COLD-tier anchors in standard prompt assembly.
3. **REQ-INVALIDATE**: The system SHALL invalidate cached anchors when lifecycle events modify their state.
4. **REQ-COHERENCE**: The system SHALL NOT serve stale anchor data from cache after a state-modifying lifecycle event.
5. **REQ-FALLBACK**: The system SHALL support a configuration option to disable tiered storage and revert to uniform loading.

## Validation Plan

1. **Unit tests** MUST verify HOT cache returns correct anchor data after cache population.
2. **Unit tests** MUST verify cache invalidation removes or updates entries on each lifecycle event type (promote, archive, rank change, evict, authority change).
3. **Unit tests** MUST verify COLD anchors are excluded from prompt assembly query results.
4. **Unit tests** MUST verify tier transitions (WARM-to-HOT on rank increase) update cache correctly.
5. **Unit tests** MUST verify fallback mode (tiered storage disabled) produces identical results to pre-feature behavior.
6. **Unit tests** SHOULD verify concurrent cache access during invalidation (thread safety).
7. **Integration test** SHOULD verify prompt assembly latency improvement with HOT cache enabled vs. disabled.
8. **Integration test** SHOULD verify maintenance sweep accesses all tiers including COLD.
9. **Regression**: All existing tests MUST pass with tiered storage disabled (default).

## Known Limitations

1. **JVM-local cache only**: Cache is not shared across instances. In a multi-instance deployment, each instance maintains its own HOT cache. Cache warming happens independently per instance.
2. **No predictive pre-fetch**: Cache is populated on context initialization and maintained via events. There is no predictive loading based on expected access patterns.
3. **COLD compression deferred**: Initial implementation simply excludes COLD anchors from loading. Actual compressed storage representation is a candidate extension.
4. **Tier threshold sensitivity**: Tiered behavior depends on `MemoryTier` rank thresholds. If thresholds are misconfigured (e.g., all anchors are HOT), the cache provides no benefit and adds overhead.

## Suggested Command

```
/opsx:new tiered-anchor-storage
```
