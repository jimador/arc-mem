# Prep: Tiered Anchor Storage

**Feature**: F11 (`tiered-anchor-storage`)
**Wave**: 4
**Priority**: MAY
**Depends on**: F02 (unified maintenance strategy)
**Research rec**: G

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

---

## Locked Decisions

These decisions are final and MUST NOT be revisited during implementation.

### L1: HOT = Cached O(1) Access

HOT-tier anchors MUST be served from an in-memory cache with O(1) lookup by anchor ID. The cache is populated on context initialization and maintained via lifecycle event invalidation. There is no database round-trip for HOT anchor reads during prompt assembly.

### L2: WARM = Standard Neo4j Queries

WARM-tier anchors MUST be served from standard `AnchorRepository` Neo4j queries. No caching, no special treatment. This is the current behavior for all anchors, preserved for the WARM tier.

### L3: COLD = Lazy-Loaded, Excluded from Prompt Assembly

COLD-tier anchors MUST NOT be included in standard prompt assembly. They are loaded only during maintenance sweeps (F07), audit operations, or explicit retrieval requests. Standard `AnchorsLlmReference` assembly skips COLD anchors entirely.

### L4: Event-Driven Cache Invalidation

HOT cache MUST be invalidated by `AnchorLifecycleEvent`. The following events MUST trigger cache invalidation for the affected anchor:
- Promote (authority change)
- Archive (deactivation)
- Rank change (may cause tier transition)
- Evict (removal)
- Authority change (any direction)
- Pin/unpin state change

Cache invalidation MUST be atomic: concurrent readers get a cache miss (not stale data) and fall through to Neo4j.

### L5: Fallback to Uniform Loading

A configuration option (`tiered-storage.enabled`, default: `false`) MUST allow disabling tiered storage entirely, reverting to current uniform loading behavior. This is the safety valve for production rollback.

### L6: Feature Disabled by Default

Tiered storage MUST be disabled by default (`tiered-storage.enabled=false`). Current behavior is preserved until explicitly opted in. No existing tests or scenarios break.

### L7: Caffeine for HOT-Tier Cache

The HOT-tier cache MUST use Caffeine (`com.github.ben-manes.caffeine:caffeine`, version managed by Spring Boot BOM). Caffeine provides O(1) lookup, size-bounded eviction (W-TinyLFU), lock-free concurrent reads, removal listeners, and built-in hit/miss statistics. `ConcurrentHashMap` and Spring Cache abstraction were evaluated and rejected (see O1 resolution below).

---

## Open Questions

These decisions require further investigation or prototyping before implementation.

### O1: Cache Implementation — DECIDED

**Decision**: **Caffeine** (`com.github.ben-manes.caffeine:caffeine`, version 3.2.3 managed by Spring Boot BOM, Apache 2.0, ~600 KB).

**Rationale**: Caffeine satisfies all decision criteria:
- Explicit invalidation via `Cache.invalidate(key)` and `Cache.invalidateAll()`.
- Built-in hit/miss statistics via `Cache.stats()` — fulfills the observability requirement with no custom instrumentation.
- O(1) lookup with lock-free concurrent reads.
- Size-bounded eviction using the W-TinyLFU algorithm — provides automatic eviction as a safety net beyond event-driven invalidation.
- Removal listeners for cache invalidation callbacks — integrates with lifecycle event logging.
- Version managed by Spring Boot BOM — no explicit version tag REQUIRED.

**Rejected alternatives**:
- `ConcurrentHashMap`: lacks built-in metrics and automatic eviction. Would require manual reimplementation of statistics and size bounds that Caffeine provides out of the box.
- Spring Cache abstraction (`@Cacheable`): annotation-driven model does not fit the explicit invalidation pattern needed for lifecycle-event-driven cache management.

### O2: Cache Size Bounds

**Question**: How large should the HOT cache be?

**Context**: HOT tier contains anchors above the hot-threshold rank. Typical context has 15-20 active anchors; HOT tier is roughly the top 5-10 by rank. Each cached anchor is lightweight (ID, text, rank, authority, metadata -- roughly 1-2 KB).

**Candidates**:
- **Unbounded within tier**: Cache all HOT anchors. Size is naturally bounded by anchor budget (max ~20-50 per context). With 100 concurrent contexts, that is ~5000 entries, ~10 MB. Minimal memory concern.
- **Fixed size per context**: Cap at N entries per context. Evict least-recently-used when exceeded.
- **Global size cap**: Cap total cache entries across all contexts. Contexts compete for cache space.

**Recommendation**: Unbounded within tier is RECOMMENDED for the initial implementation. The natural bound (anchor budget * context count) is small enough that explicit size limits add complexity without meaningful benefit.

### O3: COLD Compression Format

**Question**: Should COLD anchors be stored in a compressed representation?

**Context**: Initial implementation excludes COLD anchors from loading. Actual compression (storing a summarized or reduced representation) is a future optimization.

**Decision**: COLD compression is OUT OF SCOPE for initial implementation. COLD anchors exist in Neo4j with full fidelity; they are simply not loaded during standard prompt assembly. Compression MAY be added as a follow-up optimization.

### O4: Migration from Uniform Loading

**Question**: How do in-flight contexts transition when tiered storage is enabled?

**Candidates**:
- **Clean-start only**: Tiered storage applies to new contexts only. Existing contexts continue with uniform loading until they complete.
- **Lazy migration**: On next prompt assembly for an existing context, populate HOT cache from current active anchors. Seamless but adds one-time latency.
- **Eager migration**: On feature enablement, scan all active contexts and populate caches. More complex, ensures immediate benefit.

**Recommendation**: Lazy migration is RECOMMENDED. On first tiered access for a context, populate the HOT cache from Neo4j. Subsequent accesses benefit from cache. One-time cost is a single Neo4j query (same as current behavior).

---

## Small-Model Task Constraints

All implementation tasks MUST adhere to these constraints to remain small-model friendly.

- **Max 4 files per task**: Each implementation task MUST touch at most 4 source files (excluding test files).
- **Verification**: Every task MUST be verified with `./mvnw test` before completion.
- **Incremental delivery**: Each task MUST leave the codebase in a compilable, test-passing state.
- **No speculative implementation**: Tasks implement what is specified, nothing more.

---

## Implementation Tasks

### Task 1: AnchorCache Component

**Files** (3):
1. `src/main/java/dev/dunnam/diceanchors/persistence/AnchorCache.java` -- new class
2. `src/main/java/dev/dunnam/diceanchors/DiceAnchorsProperties.java` -- add `tiered-storage` config section
3. `src/test/java/dev/dunnam/diceanchors/persistence/AnchorCacheTest.java` -- new test

**Work**:
1. Implement `AnchorCache` with `get(anchorId) -> Optional<Anchor>`, `put(Anchor)`, `invalidate(anchorId)`, `invalidateAll(contextId)`, `isPresent(anchorId) -> boolean`.
2. Use Caffeine cache implementation (per O1 decision -- `com.github.ben-manes.caffeine:caffeine`, version managed by Spring Boot BOM).
3. Add `tiered-storage.enabled` property (default: false).
4. Unit tests: put/get round-trip, invalidate removes entry, concurrent access safety.

**Verification**: `./mvnw test`

### Task 2: TieredAnchorRepository Decorator

**Files** (4):
1. `src/main/java/dev/dunnam/diceanchors/persistence/TieredAnchorRepository.java` -- new decorator
2. `src/main/java/dev/dunnam/diceanchors/persistence/AnchorCache.java` -- minor updates if needed
3. `src/main/java/dev/dunnam/diceanchors/anchor/MemoryTier.java` -- verify tier classification (read-only)
4. `src/test/java/dev/dunnam/diceanchors/persistence/TieredAnchorRepositoryTest.java` -- new test

**Work**:
1. Implement `TieredAnchorRepository` wrapping `AnchorRepository`.
2. Route `findActiveAnchorsByContext()` to: HOT from cache, WARM from Neo4j, COLD excluded.
3. Provide `findAllTiersForContext()` for maintenance sweep access.
4. Lazy cache population: on first tiered access for a context, populate HOT cache from Neo4j result.
5. Unit tests: HOT served from cache, WARM from repository, COLD excluded, all-tiers access returns everything.

**Verification**: `./mvnw test`

### Task 3: Cache Invalidation via Lifecycle Events

**Files** (4):
1. `src/main/java/dev/dunnam/diceanchors/persistence/AnchorCacheInvalidator.java` -- new event listener
2. `src/main/java/dev/dunnam/diceanchors/persistence/AnchorCache.java` -- invalidation method enhancements
3. `src/main/java/dev/dunnam/diceanchors/persistence/TieredAnchorRepository.java` -- tier transition handling
4. `src/test/java/dev/dunnam/diceanchors/persistence/AnchorCacheInvalidatorTest.java` -- new test

**Work**:
1. Create `AnchorCacheInvalidator` as a Spring `@EventListener` for `AnchorLifecycleEvent`.
2. On rank change: check if tier changed; if anchor moved from WARM to HOT, add to cache; if HOT to WARM, remove from cache.
3. On evict/archive: remove from cache.
4. On authority change: update cached entry.
5. Unit tests: verify cache state after each lifecycle event type.

**Verification**: `./mvnw test`

### Task 4: Prompt Assembly Integration

**Files** (4):
1. `src/main/java/dev/dunnam/diceanchors/assembly/AnchorsLlmReference.java` -- tier-aware query
2. `src/main/java/dev/dunnam/diceanchors/assembly/PromptBudgetEnforcer.java` -- tier-aware budget
3. `src/main/java/dev/dunnam/diceanchors/assembly/CompactedContextProvider.java` -- tier-aware compaction (COLD excluded)
4. `src/test/java/dev/dunnam/diceanchors/assembly/AnchorsLlmReferenceTest.java` -- update tests

**Work**:
1. Modify `AnchorsLlmReference` to query `TieredAnchorRepository` when tiered storage is enabled; fall back to `AnchorRepository` when disabled.
2. HOT anchors served from cache (fast path). WARM anchors from Neo4j. COLD excluded.
3. `PromptBudgetEnforcer` operates on the tiered result set (COLD already excluded).
4. Unit tests: verify COLD exclusion from assembly, verify fallback to uniform loading when disabled.

**Verification**: `./mvnw test`

### Task 5: Observability and Fallback Verification

**Files** (4):
1. `src/main/java/dev/dunnam/diceanchors/persistence/TieredAnchorRepository.java` -- add metrics logging
2. `src/main/java/dev/dunnam/diceanchors/persistence/AnchorCache.java` -- add hit/miss counters
3. `src/main/resources/application.yml` -- add tiered-storage defaults
4. `src/test/java/dev/dunnam/diceanchors/persistence/TieredAnchorRepositoryTest.java` -- fallback regression test

**Work**:
1. Add structured logging: cache hit/miss rates, tier distribution, load latency per tier.
2. Add cache invalidation logging: reason, anchor ID.
3. Verify fallback mode: with `tiered-storage.enabled=false`, all behavior identical to pre-feature.
4. Regression test: run full test suite with tiered storage disabled, confirm zero test changes needed.

**Verification**: `./mvnw test`

---

## Implementation Gates

Each gate MUST be satisfied before proceeding to subsequent tasks. Gates are verified by running `./mvnw test` and inspecting test results.

### Gate 1: HOT Cache Operational (after Task 1)

- `AnchorCache` compiles and passes unit tests.
- Put/get round-trip works correctly.
- Invalidation removes entries atomically.
- Concurrent access does not produce stale reads.

### Gate 2: COLD Excluded from Assembly (after Task 2 + Task 4)

- `TieredAnchorRepository` routes queries by tier.
- COLD anchors are NOT returned by standard `findActiveAnchorsByContext()` when tiered storage is enabled.
- COLD anchors ARE returned by `findAllTiersForContext()` for maintenance access.
- `AnchorsLlmReference` does not include COLD anchors in prompt assembly.

### Gate 3: Cache Coherence on Events (after Task 3)

- Every `AnchorLifecycleEvent` type correctly updates or invalidates cached anchors.
- Tier transitions (WARM-to-HOT, HOT-to-WARM) are handled correctly.
- No stale data is served after any lifecycle event.

### Gate 4: Fallback to Uniform Loading (after Task 5)

- With `tiered-storage.enabled=false`, behavior is identical to pre-feature implementation.
- ALL existing tests pass without modification in fallback mode.
- Observability logging is present when tiered storage is active.
- Cache metrics (hit/miss rates, tier distribution) are logged.
