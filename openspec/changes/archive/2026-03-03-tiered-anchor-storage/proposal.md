## Why

All anchors are loaded uniformly via `findActiveAnchorsByContext()` regardless of how frequently they are accessed. A CANON anchor referenced every turn and a COLD anchor about to be evicted both traverse the same Neo4j query path. The `MemoryTier` classification (COLD, WARM, HOT) already exists and is computed from rank thresholds, but it has no influence on storage or access patterns — it is purely informational.

Research validates that differentiated access patterns dramatically improve performance:

- **Google AI STATIC** (Stogiannidis et al., 2024): Hybrid dense/sparse index architecture where dense layers serve high-frequency access with O(1) lookups while sparse layers handle infrequent access. The HOT/WARM/COLD anchor tiers map directly to STATIC's dense (HOT = always cached), sparse (WARM = standard query), and excluded (COLD = not loaded for assembly) access patterns.
- **Sleeping-llm** (Guo et al., 2025): Memory consolidation analogy where frequently-accessed MEMIT memories are transferred to stable LoRA storage. The tier transition pattern — HOT anchors promoted to cache on high access frequency, COLD anchors excluded from prompt assembly — mirrors the wake/sleep consolidation cycle. MEMIT's capacity ceiling finding (phase transition at 13-14 facts) reinforces the importance of keeping only the most relevant anchors in the prompt.

This change makes `MemoryTier` actionable: HOT anchors are served from a Caffeine in-memory cache with O(1) lookup, WARM anchors continue through standard Neo4j queries, and COLD anchors are excluded from prompt assembly entirely.

## What Changes

### Tiered Repository (Priority: MUST)

- **`TieredAnchorRepository` decorator** wrapping existing `AnchorRepository` with tier-aware query routing. When tiered storage is enabled, `findActiveAnchorsByContext()` returns only HOT + WARM anchors; COLD anchors are excluded. A separate `findAllTiersForContext()` method provides full-tier access for maintenance sweeps and audit operations.
- **Lazy cache population**: On first tiered access for a context, the HOT cache is populated from the Neo4j result. Subsequent HOT-tier reads are O(1) from cache.
- **Fallback mode**: With `tiered-storage.enabled=false` (the default), behavior is identical to current uniform loading. Feature is opt-in.

### HOT-Tier Cache (Priority: MUST)

- **`AnchorCache` component** backed by Caffeine (`com.github.ben-manes.caffeine:caffeine`, version managed by Spring Boot BOM). Per-context cache keyed by `contextId → Map<anchorId, Anchor>`. Provides `get(contextId, anchorId)`, `putAll(contextId, anchors)`, `invalidate(contextId, anchorId)`, `invalidateAll(contextId)`.
- **Cache bounds**: Unbounded within tier for initial implementation. The natural bound (anchor budget * context count) is small enough that explicit size limits add complexity without benefit. A global `maximumSize` safety net prevents unbounded growth.
- **Built-in statistics**: Caffeine's `recordStats()` provides cache hit/miss metrics without custom instrumentation.

### Event-Driven Cache Invalidation (Priority: MUST)

- **`TieredCacheInvalidator`** Spring `@EventListener` for `AnchorLifecycleEvent`. On rank change: check if tier changed; if anchor moved from WARM to HOT, add to cache; if HOT to WARM/COLD, remove from cache. On evict/archive: remove from cache. On authority change: update cached entry. Uses the existing `TierChanged` lifecycle event for tier transitions.
- **Cache coherence guarantee**: Invalidation is atomic via Caffeine's lock-free concurrent structure. Concurrent readers get a cache miss (not stale data) and fall through to Neo4j.

### Assembly Integration (Priority: MUST)

- **`AnchorsLlmReference` modification**: When tiered storage is enabled, queries `TieredAnchorRepository` instead of `AnchorRepository`. HOT anchors served from cache (fast path), WARM from Neo4j, COLD excluded from prompt assembly.
- **`PromptBudgetEnforcer`**: Operates on the tiered result set (COLD already excluded by the repository layer). No behavioral change needed within the enforcer.

### Configuration (Priority: MUST)

- **New `TieredStorageConfig`** record in `DiceAnchorsProperties` with:
  - `enabled` (boolean, default: `false`) — feature toggle
  - `maxCacheSize` (int, default: `1000`) — global cache entry safety cap
  - `ttlMinutes` (int, default: `60`) — TTL safety net for cached entries

### Caffeine Dependency (Priority: MUST)

- **Add to pom.xml**: `com.github.ben-manes.caffeine:caffeine` with version managed by Spring Boot BOM (3.2.3). Apache 2.0 license, ~600 KB.

## Capabilities

### New Capabilities

- `tiered-storage`: Differentiated storage and access patterns based on `MemoryTier`. HOT tier uses Caffeine cache, WARM uses Neo4j, COLD excluded from assembly.
- `hot-cache`: In-memory O(1) anchor lookup for HOT-tier anchors with event-driven invalidation.

### Modified Capabilities

- `anchor-assembly`: `AnchorsLlmReference` gains tier-aware query routing (HOT from cache, WARM from Neo4j, COLD excluded) when tiered storage is enabled.

## Impact

**Code**: New files in `persistence/` (`AnchorCache`, `TieredAnchorRepository`, `TieredCacheInvalidator`). Modified `AnchorsLlmReference` in `assembly/` for tier-aware loading. New config record in `DiceAnchorsProperties`. Updated `application.yml`.

**APIs**: `TieredAnchorRepository` exposes `findAllTiersForContext()` for maintenance sweep access. All existing `AnchorRepository` operations work unchanged.

**Dependencies**: One new dependency — Caffeine (version managed by Spring Boot BOM, Apache 2.0, ~600 KB).

**Testing**: Unit tests for `AnchorCache` (put/get/invalidate), `TieredAnchorRepository` (tier routing, COLD exclusion, fallback mode), `TieredCacheInvalidator` (event-driven invalidation, tier transitions). All existing tests MUST pass with tiered storage disabled (default).

**Configuration**: New `dice-anchors.tiered-storage` section. Feature disabled by default — zero impact on existing deployments.

## Research References

- Feature doc: `openspec/roadmaps/anchor-memory-optimization/features/11-tiered-anchor-storage.md`
- Prep decisions: `openspec/roadmaps/anchor-memory-optimization/prep/11-tiered-anchor-storage-prep.md`
- Google AI STATIC analysis: `openspec/research/llm-optimization-external-research.md` (Section 3.2, Pattern G)
- Sleeping-llm analysis: `openspec/research/llm-optimization-external-research.md` (Section 3.1, Patterns A/C)
- Dependency research: `openspec/research/dependency-research-memory-optimization.md` (F11 section)
