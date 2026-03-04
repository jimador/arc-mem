## Why

Conflict detection is the most LLM-call-heavy operation in the anchor promotion pipeline. Each promotion candidate is checked against all existing anchors via `LlmConflictDetector` semantic analysis -- O(N) LLM calls per candidate, where N is the active anchor count. For a budget of 20 anchors and 5 promotion candidates per turn, that is up to 100 LLM calls for conflict detection alone. This dominates both latency and cost in every promotion cycle.

Research from the Google AI STATIC paper ("Efficient Sparse Matrix Constrained Decoding", 2024) demonstrates that precomputing constraint relationships offline enables O(1) online enforcement. The same principle applies here: precomputing conflict relationships between anchor pairs enables O(1) conflict lookup during promotion, eliminating redundant LLM calls for pairs already evaluated.

## What Changes

Add a **Conflict Index** -- an in-memory precomputed conflict adjacency structure that caches conflict detection results between anchor pairs. The index is maintained incrementally via `AnchorLifecycleEvent` subscriptions and integrates with `CompositeConflictDetector` as a new `INDEXED` strategy.

### Core types (anchor/ package)

- **`ConflictIndex`**: Interface defining O(1) conflict lookup, recording, removal, and context cleanup.
- **`ConflictEntry`**: Record bundling all fields needed for conflict resolution in a single object (stacked data layout per STATIC recommendation I) -- anchor ID, anchor text, authority, conflict type, confidence.
- **`InMemoryConflictIndex`**: `ConcurrentHashMap`-backed implementation with lifecycle event subscription for incremental maintenance. Lazily populated on first conflict check per new anchor.
- **`ConflictDetectionStrategy.INDEXED`**: New enum value for index-backed conflict detection with LLM fallback on miss.
- **`ConflictDetectionStrategy.LOGICAL`**: Reserved enum value for future Prolog-based contradiction detection via DICE tuProlog projection.

### Integration

- **`CompositeConflictDetector`**: Gains `INDEXED` as a strategy option. Index lookup is the fast path; existing LLM detection is the fallback for index misses.
- **`AnchorLifecycleEvent` subscription**: On `Promoted` -- detect conflicts between new anchor and all existing anchors, store entries. On `Archived`/`Evicted` -- remove all entries involving the anchor.

### Scoping decision

This change implements an **in-memory** `ConflictIndex` backed by `ConcurrentHashMap`. The Neo4j `CONFLICTS_WITH` relationship backing described in the feature doc is deferred to a follow-up change. In-memory is simpler, fully testable without Neo4j, and sufficient for the demo repo's scope. The `ConflictIndex` interface is designed so a `Neo4jConflictIndex` can implement it later without API changes.

## Capabilities

### New Capabilities

- `conflict-index`: Precomputed conflict adjacency with O(1) lookup, incremental lifecycle-event-driven maintenance, and LLM fallback for index misses. Reduces LLM calls during promotion by caching previously evaluated conflict pairs.

### Modified Capabilities

- `conflict-detection`: `ConflictDetectionStrategy` gains `INDEXED` (and reserved `LOGICAL`) values. `CompositeConflictDetector` supports index-first detection with semantic fallback.

## Impact

### New files

- `src/main/java/dev/dunnam/diceanchors/anchor/ConflictIndex.java` -- interface
- `src/main/java/dev/dunnam/diceanchors/anchor/ConflictEntry.java` -- record
- `src/main/java/dev/dunnam/diceanchors/anchor/InMemoryConflictIndex.java` -- ConcurrentHashMap implementation

### Modified files

- `src/main/java/dev/dunnam/diceanchors/anchor/ConflictDetectionStrategy.java` -- add INDEXED, LOGICAL values (internal strategy enum)
- `src/main/java/dev/dunnam/diceanchors/anchor/ConflictStrategy.java` -- add INDEXED value (config-level enum)
- `src/main/java/dev/dunnam/diceanchors/anchor/CompositeConflictDetector.java` -- add INDEXED strategy branch with index + fallback
- `src/main/java/dev/dunnam/diceanchors/anchor/AnchorConfiguration.java` -- wire ConflictIndex bean, add INDEXED case to conflict detector wiring

### Constitutional alignment

- **Article I (RFC 2119)**: All normative statements in the spec use RFC 2119 keywords.
- **Article II (Neo4j only)**: No new persistence -- the in-memory index is transient. Neo4j backing is deferred. When implemented, it will go through `AnchorRepository` per Article II.
- **Article III (Constructor injection)**: `InMemoryConflictIndex` uses constructor injection for its dependencies.
- **Article IV (Records)**: `ConflictEntry` is a Java record.
- **Article V (Anchor invariants)**: Anchor invariants A1-A4 are preserved. The conflict index is read-only with respect to anchor state -- it caches detection results but does not modify ranks, authorities, or promotion decisions.
- **Article VI (Sim isolation)**: Per-context index isolation via `clear(contextId)`. Each simulation run's conflict data is cleaned up with the context.
- **Article VII (Test-first)**: Unit tests accompany the implementation.
