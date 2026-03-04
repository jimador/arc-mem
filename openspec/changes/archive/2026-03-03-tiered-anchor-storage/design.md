## Context

dice-anchors is a working demo of adversarial drift resistance using Anchors — enriched DICE Propositions with rank, authority, and budget management. The `MemoryTier` enum (COLD, WARM, HOT) already classifies anchors by rank thresholds, but the classification is purely informational: all anchors traverse the same Neo4j query path regardless of tier.

This design adds differentiated storage and access patterns that make `MemoryTier` actionable:
- **HOT**: Caffeine in-memory cache, O(1) lookup, populated lazily and maintained via lifecycle events
- **WARM**: Standard Neo4j queries (current behavior preserved)
- **COLD**: Excluded from prompt assembly, loaded only for maintenance/audit

### Research Foundation

**Google AI STATIC** (Su et al., 2026) introduced a hybrid dense/sparse index for constrained decoding. Dense layers (first L steps) serve high-frequency access with O(1) lookups; sparse layers handle infrequent access via CSR matrices. The architectural insight — matching storage strategy to access frequency — directly informs the three-tier approach. HOT anchors are the "dense layers" (always cached), WARM are the "sparse layers" (queried on demand), and COLD are excluded entirely.

**Sleeping-llm** (Guo et al., 2025) demonstrated that working memory capacity has a sharp phase transition (0.92 recall at 13 facts, 0.57 at 14). This reinforces the importance of keeping only high-value anchors in the prompt "hot" zone. COLD exclusion is not just a performance optimization — it is a fidelity optimization that prevents low-relevance anchors from diluting attention on high-relevance ones.

### Current Code Organization (relevant files)

```
persistence/
  AnchorRepository.java       # Neo4j via Drivine — findActiveAnchorsByContext(), etc.
  PropositionNode.java         # Neo4j entity

anchor/
  MemoryTier.java              # Enum: COLD, WARM, HOT with fromRank()
  Anchor.java                  # Record with memoryTier field
  AnchorEngine.java            # inject() returns ranked anchors
  event/AnchorLifecycleEvent.java  # Sealed event hierarchy (includes TierChanged)

assembly/
  AnchorsLlmReference.java     # Loads anchors via engine.inject(), formats for prompt
  AnchorCacheInvalidator.java  # Tracks dirty contextIds for reference cache
  PromptBudgetEnforcer.java    # Token budget enforcement
```

## Goals / Non-Goals

**Goals:**

- **G1**: HOT anchors served from in-memory cache with O(1) lookup, eliminating Neo4j round-trips for frequently-accessed anchors.
- **G2**: COLD anchors excluded from prompt assembly, reducing prompt size and improving attention focus (per sleeping-llm capacity findings).
- **G3**: Cache coherence maintained via event-driven invalidation — no stale data served after lifecycle events.
- **G4**: Feature disabled by default (`tiered-storage.enabled=false`). All existing tests pass unchanged in fallback mode.
- **G5**: Observability: cache hit/miss rates and tier distribution logged.
- **G6**: Full-tier access available for maintenance sweeps and audit operations.

**Non-Goals:**

- **NG1**: COLD tier compression or summarized representations. Initial COLD tier simply excludes from loading.
- **NG2**: Distributed caching. Cache is JVM-local, per-instance.
- **NG3**: Predictive pre-fetch or cache warming strategies beyond lazy initialization.
- **NG4**: Changes to `MemoryTier` threshold calculation. Existing rank-based thresholds preserved.
- **NG5**: Neo4j schema changes. Tiered access is query-routing, not storage-schema.
- **NG6**: Performance benchmarking. This is a demo repo; the architecture demonstrates the pattern.

## Decisions

### D1: Decorator Pattern for TieredAnchorRepository

**Decision**: `TieredAnchorRepository` wraps `AnchorRepository` as a decorator rather than extending it or modifying it directly. The decorator intercepts anchor retrieval calls and routes them through the tier-aware logic.

**Rationale**: The existing `AnchorRepository` is a complex Drivine-backed service with ~20 methods. Modifying it directly would mix tier logic with persistence logic. The decorator pattern keeps concerns separated: `AnchorRepository` knows about Neo4j, `TieredAnchorRepository` knows about tiers and caching.

**Implementation**:

```java
@Service
@ConditionalOnProperty(name = "dice-anchors.tiered-storage.enabled", havingValue = "true")
public class TieredAnchorRepository {

    private final AnchorRepository repository;
    private final AnchorCache cache;
    private final DiceAnchorsProperties.TierConfig tierConfig;

    public List<Anchor> findActiveAnchorsForAssembly(String contextId) {
        // 1. Load all active anchors from Neo4j
        // 2. Classify by tier
        // 3. Populate cache with HOT anchors (lazy)
        // 4. Return HOT (from cache) + WARM (from query)
        // COLD excluded
    }

    public List<Anchor> findAllTiersForContext(String contextId) {
        // Full access for maintenance — delegates to repository
    }
}
```

**Why not a common interface?** `AnchorRepository` implements `PropositionRepository` (DICE contract) plus anchor-specific methods. Extracting a common interface for the subset of methods the decorator needs would create a thin wrapper interface used only by the decorator — unnecessary indirection. The decorator holds a direct reference to `AnchorRepository`.

### D2: Caffeine Cache with Composite Key

**Decision**: Use a single Caffeine `Cache<String, Anchor>` with composite keys (`contextId + ":" + anchorId`) rather than a `Map<String, Cache<String, Anchor>>` (per-context caches).

**Rationale**: A single cache with composite keys:
- Uses one Caffeine instance (one eviction policy, one stats tracker)
- Allows a single `maximumSize` to bound total entries across all contexts
- Simplifies invalidation: `invalidateAll(contextId)` uses Caffeine's `asMap().keySet()` filtered by prefix
- Avoids creating/managing cache instances per context

Per the prep doc (O2), the cache is unbounded within tier — the natural bound is anchor budget (~20) * concurrent contexts. A single `maximumSize(1000)` provides a safety net.

**Implementation**:

```java
@Component
public class AnchorCache {

    private final Cache<String, Anchor> cache;
    private final Set<String> populatedContexts = ConcurrentHashMap.newKeySet();

    public AnchorCache(DiceAnchorsProperties properties) {
        var config = properties.tieredStorage();
        this.cache = Caffeine.newBuilder()
                .maximumSize(config != null ? config.maxCacheSize() : 1000)
                .expireAfterWrite(config != null ? config.ttlMinutes() : 60, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}
```

**Key format**: `contextId + ":" + anchorId`. The `:` separator is safe because neither contextId (UUIDs or `sim-{uuid}`) nor anchorId (Neo4j node IDs) contain colons.

### D3: Lazy Cache Population (per Prep O4)

**Decision**: The HOT cache is populated lazily on first `findActiveAnchorsForAssembly()` call for each context. No eager population at context creation.

**Rationale**: Lazy population is one Neo4j query — the same query that currently loads all anchors. The first call to `findActiveAnchorsForAssembly()` loads from Neo4j, filters by tier, caches HOT anchors, and returns HOT + WARM. Subsequent calls serve HOT from cache and reload WARM from Neo4j.

**Flow**:

```
findActiveAnchorsForAssembly(contextId):
    if cache.isPopulated(contextId):
        hotAnchors = cache.getAll(contextId)
        warmAnchors = repository.findActiveAnchorsByContext(contextId)
                          .filter(tier == WARM)
        return hotAnchors + warmAnchors
    else:
        allAnchors = repository.findActiveAnchorsByContext(contextId)
        classify by tier using MemoryTier.fromRank()
        cache.putAll(contextId, hotAnchors)
        return hotAnchors + warmAnchors  // COLD excluded
```

**Trade-off**: After cache is populated, WARM anchors are still loaded from Neo4j each time. This is by design — WARM is the "standard" tier where caching adds no value (these anchors are accessed only during prompt assembly, not O(1) hot-path). The performance win comes from HOT cache hits and COLD exclusion.

### D4: Event-Driven Cache Invalidation via TierChanged Events

**Decision**: `TieredCacheInvalidator` listens primarily for `TierChanged` events (already published by `AnchorEngine`) to handle tier transitions. It also listens for `Evicted`, `Archived`, `AuthorityChanged`, and `Promoted` events for cache updates that do not involve tier transitions.

**Rationale**: The `TierChanged` event already carries `previousTier` and `newTier`, making it the natural trigger for cache add/remove on tier transitions. Other events (evict, archive) require cache cleanup regardless of tier.

**Event handling matrix**:

| Event | Cache Action |
|-------|-------------|
| `TierChanged(WARM→HOT)` | Load anchor from Neo4j, add to cache |
| `TierChanged(HOT→WARM)` | Remove from cache |
| `TierChanged(HOT→COLD)` | Remove from cache |
| `Evicted` | Remove from cache if present |
| `Archived` | Remove from cache if present |
| `Promoted` | Classify tier; if HOT, add to cache |
| `Reinforced` | If in cache, update cached entry (rank changed) |
| `AuthorityChanged` | If in cache, update cached entry (authority changed) |

**Implementation note**: When a `TierChanged(WARM→HOT)` event fires, the `TieredCacheInvalidator` needs the full `Anchor` object to cache it. It loads this from `AnchorRepository.findPropositionNodeById()` and converts to `Anchor`. This is a single Neo4j read — acceptable for the low-frequency tier transition event.

### D5: Assembly Integration via Constructor Injection

**Decision**: `AnchorsLlmReference` receives an optional `TieredAnchorRepository` via constructor injection. When present and enabled, it uses tiered loading; otherwise, it falls back to `AnchorEngine.inject()`.

**Rationale**: `AnchorsLlmReference` is not a Spring bean — it is instantiated directly in `ChatActions` and `SimulationTurnExecutor`. The tiered repository is injected into those orchestrators, which pass it to `AnchorsLlmReference` via a new constructor overload.

**Implementation**:

```java
// In AnchorsLlmReference - new overload
public AnchorsLlmReference(AnchorEngine engine, String contextId, int budget,
                            CompliancePolicy compliancePolicy,
                            /* ... existing params ... */,
                            @Nullable TieredAnchorRepository tieredRepository) {
    // ...
}

// In ensureAnchorsLoaded() / loadBulkMode():
private void loadBulkMode() {
    if (cachedAnchors == null) {
        cachedAnchors = (tieredRepository != null)
            ? tieredRepository.findActiveAnchorsForAssembly(contextId)
            : engine.inject(contextId);
    }
    // ... rest unchanged
}
```

This preserves all existing constructor overloads (backward compatible) and adds tiered support as an additive change.

### D6: Feature Disabled by Default

**Decision**: `tiered-storage.enabled` defaults to `false`. The feature is opt-in.

**Rationale**: Per prep decision L6 — no existing tests or scenarios break. The feature can be enabled for specific scenarios via YAML configuration.

**Consequence**: All existing tests pass without modification. The `TieredAnchorRepository` bean is only created when the feature is enabled (via `@ConditionalOnProperty`).

## Risks / Trade-offs

**[R1] Cache coherence on concurrent mutations**: If two threads modify the same anchor concurrently, the cache could temporarily serve a stale entry.
→ **Mitigation**: Caffeine's lock-free reads ensure no torn reads. Event-driven invalidation fires after every mutation. The worst case is a single prompt assembly cycle with a slightly stale HOT anchor — acceptable for a demo, and self-correcting on the next event.

**[R2] COLD exclusion misses relevant anchors**: A COLD anchor might become relevant (e.g., user asks about a previously-mentioned topic).
→ **Mitigation**: If a COLD anchor is reinforced, its rank rises, moving it to WARM or HOT tier. The `TierChanged` event fires, and the anchor enters the prompt on the next assembly cycle. Maintenance sweeps (F07) access all tiers to catch anchors that need re-evaluation.

**[R3] Constructor overload proliferation in AnchorsLlmReference**: Adding another optional parameter to the already-long constructor chain.
→ **Mitigation**: This is the last planned addition. If more optional dependencies accumulate, refactor to a builder pattern. For now, one more overload is acceptable.

**[R4] TTL-based expiry may evict valid HOT entries**: The `expireAfterWrite` TTL is a safety net that may evict entries that are still HOT.
→ **Mitigation**: TTL is set to 60 minutes by default — much longer than a typical simulation run or chat session. If an anchor is still HOT after 60 minutes, the next access triggers lazy repopulation. The TTL prevents unbounded memory growth from abandoned contexts.

**[R5] WARM anchors still hit Neo4j every assembly cycle**: Only HOT anchors benefit from caching; WARM anchors are loaded fresh each time.
→ **Mitigation**: This is by design. WARM is the "standard" tier — these anchors are not accessed frequently enough to warrant caching. The performance win comes from HOT cache hits (eliminating the most frequent lookups) and COLD exclusion (reducing the result set size).
