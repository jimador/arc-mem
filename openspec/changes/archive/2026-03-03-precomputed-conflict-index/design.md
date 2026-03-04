## Context

Conflict detection in dice-anchors is the most LLM-call-intensive operation in the promotion pipeline. `AnchorPromoter` calls `AnchorEngine.detectConflicts()` (which delegates to `ConflictDetector.detect()`) for each promotion candidate against all existing anchors. With the `SEMANTIC_ONLY` or `LEXICAL_THEN_SEMANTIC` strategies, this produces O(N) LLM calls per candidate where N is the active anchor count.

The current conflict detection infrastructure:

```
anchor/
  ConflictDetector.java           # Interface: detect() + batchDetect()
  ConflictDetectionStrategy.java  # Enum: LEXICAL_ONLY, SEMANTIC_ONLY, LEXICAL_THEN_SEMANTIC
  CompositeConflictDetector.java  # Chains lexical + semantic based on strategy
  LlmConflictDetector.java        # LLM-based semantic detection
  NegationConflictDetector.java   # Lexical negation detection
  SubjectFilter.java              # Pre-filters anchors by shared subjects
  AnchorConfiguration.java        # Wires ConflictDetector bean
anchor/event/
  AnchorLifecycleEvent.java       # Sealed event hierarchy (Promoted, Archived, Evicted, etc.)
  AnchorLifecycleListener.java    # Default logging listener
  AnchorEventConfiguration.java   # Registers listener bean
```

Research from the Google AI STATIC paper ("Efficient Sparse Matrix Constrained Decoding", 2024) demonstrates that precomputing constraint relationships as a sparse matrix enables O(1) online lookup. The conflict index applies the same principle: precompute conflict pairs offline (during lifecycle events), look them up online (during promotion).

## Goals / Non-Goals

**Goals:**
- Define a `ConflictIndex` interface for O(1) conflict lookup with incremental maintenance
- Implement `InMemoryConflictIndex` using `ConcurrentHashMap` for thread-safe, per-context conflict caching
- Add `ConflictEntry` record with stacked data layout (all resolution fields in one object)
- Add `INDEXED` to `ConflictDetectionStrategy` with index-first detection and LLM fallback
- Reserve `LOGICAL` in `ConflictDetectionStrategy` for future Prolog-based detection
- Subscribe to `AnchorLifecycleEvent` for incremental index maintenance (Promoted, Archived, Evicted)
- Integrate with `CompositeConflictDetector` as a new strategy branch

**Non-Goals:**
- Neo4j `CONFLICTS_WITH` relationship backing (deferred -- see Deferred Work)
- Prolog-based `LOGICAL` conflict detection implementation (reserved enum value only)
- Cross-context conflict index sharing
- Conflict type classification (REVISION vs. CONTRADICTION -- separate concern)
- UI visualization of the conflict index
- A/B testability per-simulation scenario (future enhancement)

## Decisions

### D1: In-Memory First, Neo4j Deferred

**Decision**: The initial implementation uses `ConcurrentHashMap<String, Set<ConflictEntry>>` keyed by anchor ID. Neo4j `CONFLICTS_WITH` relationships are deferred to a follow-up change.

**Why**: This is a demo repo. In-memory is simpler, fully testable without Neo4j infrastructure, and demonstrates the index concept. The `ConflictIndex` interface is designed so a `Neo4jConflictIndex` can implement it later by storing entries as relationship properties and querying via Cypher.

**Trade-off**: The in-memory index is transient -- it does not survive application restarts. For a demo repo with per-simulation context isolation, this is acceptable. Production use would require the Neo4j backing.

### D2: ConcurrentHashMap Backing

**Decision**: `InMemoryConflictIndex` uses `ConcurrentHashMap<String, Set<ConflictEntry>>` where each key is an anchor ID and the value is a concurrent set of conflict entries referencing that anchor.

```java
public class InMemoryConflictIndex implements ConflictIndex {
    private final ConcurrentHashMap<String, Set<ConflictEntry>> index = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> contextAnchors = new ConcurrentHashMap<>();
}
```

The `contextAnchors` map tracks which anchor IDs belong to which context, enabling `clear(contextId)` without scanning the entire index.

**Why ConcurrentHashMap over synchronized Map**: Finer-grained concurrency (per-bucket locks vs. global lock). Multiple simulation threads may operate on different contexts simultaneously.

### D3: Lazy Population

**Decision**: When a new anchor is promoted, the index does NOT eagerly detect conflicts against all existing anchors. Instead, conflicts are detected lazily on the first `getConflicts()` call for that anchor, or incrementally as new anchors are promoted.

The `Promoted` event handler records a conflict detection task. The `CompositeConflictDetector` INDEXED branch checks the index first; on miss, it delegates to the semantic detector and caches the result.

**Why lazy over eager**: Eager population on promotion would require O(N) LLM calls upfront for every promotion -- the same cost we are trying to avoid. Lazy population amortizes the cost: the first lookup for a pair incurs the LLM call, subsequent lookups are O(1).

**Alternative considered**: Background async population. Rejected -- adds concurrency complexity (index may be stale during the promotion cycle) without clear benefit for a synchronous turn pipeline.

### D4: ConflictDetectionStrategy Expansion

**Decision**: Add two values to `ConflictDetectionStrategy`:

```java
public enum ConflictDetectionStrategy {
    LEXICAL_ONLY,
    SEMANTIC_ONLY,
    LEXICAL_THEN_SEMANTIC,
    INDEXED,   // Index-first with LLM fallback
    LOGICAL    // Reserved: future Prolog-based detection
}
```

`INDEXED` uses the conflict index as the primary detection path. On index miss, it falls back to the semantic detector (same path as `SEMANTIC_ONLY`). Results are cached in the index.

`LOGICAL` is reserved for future Prolog-based contradiction detection via DICE tuProlog (2p-kt) projection. It compiles but throws `UnsupportedOperationException` in `CompositeConflictDetector`.

**Why add LOGICAL now**: The enum value costs nothing and signals the intended architecture. Prolog projection via `PrologEngine.query()` is available from DICE 0.1.0-SNAPSHOT (tuProlog/2p-kt on classpath). Implementation requires designing domain-independent contradiction rules (separate research).

### D5: Event-Driven Updates via Spring @EventListener

**Decision**: `InMemoryConflictIndex` subscribes to lifecycle events using Spring's `@EventListener` annotation, following the same pattern as `AnchorLifecycleListener`.

```java
@EventListener
public void onArchived(AnchorLifecycleEvent.Archived event) {
    removeConflicts(event.getAnchorId());
}

@EventListener
public void onEvicted(AnchorLifecycleEvent.Evicted event) {
    removeConflicts(event.getAnchorId());
}
```

**Why @EventListener over ApplicationListener interface**: Consistent with existing `AnchorLifecycleListener` pattern. Method-level granularity allows subscribing to specific event subtypes.

### D6: ConflictEntry as Stacked Data Layout

**Decision**: `ConflictEntry` bundles all fields needed for conflict resolution in a single record: anchor ID, text, authority, conflict type, confidence, and detection timestamp. No secondary lookups required.

This follows STATIC recommendation I (stacked data layout for coalesced access): the resolution decision can be made entirely from the entry without querying the repository for anchor details.

### D7: CompositeConflictDetector Integration

**Decision**: `CompositeConflictDetector` gains an optional `ConflictIndex` dependency (nullable, for backward compatibility). When the strategy is `INDEXED` and the index is available, the detector:

1. Checks the index for known conflicts
2. Converts `ConflictEntry` records to `ConflictDetector.Conflict` records
3. For index misses, falls back to the semantic detection path
4. Caches fallback results in the index

When the index is null and the strategy is `INDEXED`, the detector logs a warning and falls back to `LEXICAL_THEN_SEMANTIC`.

## Data Flow

```
Promotion Pipeline
    |
    v
CompositeConflictDetector (strategy=INDEXED)
    |
    +--> ConflictIndex.getConflicts(anchorId)
    |       |
    |       +--> HIT: return ConflictEntry -> Conflict conversion
    |       |
    |       +--> MISS: delegate to semanticDetector.detect()
    |               |
    |               +--> cache result in ConflictIndex
    |               |
    |               +--> return Conflict list
    |
    v
ConflictResolver (unchanged)

Lifecycle Events
    |
    +--> Promoted  -> (lazy: detect on first lookup)
    +--> Archived  -> removeConflicts(anchorId)
    +--> Evicted   -> removeConflicts(anchorId)
```

## File Inventory

### New Files (3)

| File | Package | Type | Description |
|------|---------|------|-------------|
| `ConflictIndex.java` | `anchor/` | Interface | O(1) conflict lookup with incremental maintenance |
| `ConflictEntry.java` | `anchor/` | Record | Stacked data layout for conflict resolution fields |
| `InMemoryConflictIndex.java` | `anchor/` | Class | ConcurrentHashMap-backed implementation with event subscription |

### Modified Files (4)

| File | Change |
|------|--------|
| `ConflictDetectionStrategy.java` | Add `INDEXED` and `LOGICAL` values (internal strategy enum used by `CompositeConflictDetector`) |
| `ConflictStrategy.java` | Add `INDEXED` value (config-level enum used by `DiceAnchorsProperties.ConflictDetectionConfig`) |
| `CompositeConflictDetector.java` | Add optional `ConflictIndex` dependency, `INDEXED` strategy branch, `LOGICAL` UnsupportedOperationException |
| `AnchorConfiguration.java` | Wire `InMemoryConflictIndex` bean, add `INDEXED` case to `conflictDetector()` switch, pass index to `CompositeConflictDetector` |

## Research Attribution

The precomputed conflict index applies the offline-index-for-online-lookup pattern from the Google AI STATIC paper ("Efficient Sparse Matrix Constrained Decoding", 2024):

- **Source**: Google AI Research
- **Key concept**: Precomputing constraint relationships as a sparse matrix enables O(1) online enforcement, eliminating per-token (or in our case, per-candidate) constraint checking at generation time.
- **Specific application**: Conflict detection results between anchor pairs are precomputed during lifecycle events (offline phase) and looked up during promotion (online phase). The stacked `ConflictEntry` record follows recommendation I for coalesced data access -- all resolution fields in one object, no secondary lookups.

The `LOGICAL` strategy value (reserved, not implemented) anticipates future integration with DICE's tuProlog (2p-kt) Prolog projection for rule-based contradiction detection. Prolog backward chaining can detect logical contradictions (e.g., "X is mortal" + "X is immortal") that lexical negation misses and LLM calls are too expensive for.

## Deferred Work

| Item | Deferred To | Reason |
|------|-------------|--------|
| Neo4j `CONFLICTS_WITH` relationship backing | Follow-up change | Requires Drivine query additions to `AnchorRepository`. In-memory is sufficient for demo. |
| Prolog `LOGICAL` conflict detection | Separate research | Designing domain-independent contradiction rules requires dedicated research. DICE Prolog projection is available but unused. |
| Per-simulation strategy selection via scenario YAML | Future | Configuration via properties is sufficient for initial use. A/B testability is a Wave 2-3 enhancement. |
| Caffeine caching layer for Neo4j-backed index | F11 | Only relevant if Neo4j query latency is a bottleneck. Profile first. |
| Conflict index size in RunInspectorView | Future | UI visibility is a nice-to-have, not critical for the demo. |
| Batch population for cold-start | Future | Lazy population is sufficient. Batch population adds complexity for marginal benefit in a demo context. |

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| **Cold-start cost**: First lookup for each pair incurs an LLM call | Amortized over subsequent lookups. Same cost as current behavior, but only paid once per pair. |
| **Memory pressure from large indexes**: Many anchor pairs = many entries | Bounded by anchor budget (default 20). Maximum entries = N*(N-1)/2 = 190 for 20 anchors. Negligible memory. |
| **Transient index**: Lost on restart | Acceptable for demo repo. Neo4j backing (deferred) provides persistence. |
| **Stale entries if events are missed** | Lifecycle events are synchronous within the turn pipeline. Missing an event requires a framework bug, not a race condition. |
| **CompositeConflictDetector complexity**: Adding a 4th strategy branch | Switch expression remains exhaustive. Each branch is self-contained. |

## Open Questions

None. All design questions from the prep document have been resolved:
- Storage backend: In-memory (Neo4j deferred).
- Cold-start population: Lazy (on first lookup per pair).
- Staleness bounds: Strict synchronous updates via lifecycle events.
- Confidence threshold: Store all detected conflicts regardless of confidence.
- LOGICAL strategy: Reserved enum value, implementation deferred.
