# Prep: Precomputed Conflict Index

**Feature**: F05 — `precomputed-conflict-index`
**Wave**: 2
**Priority**: SHOULD
**Depends on**: none

## RFC 2119 Compliance

All normative statements use RFC 2119 keywords.

## Locked Decisions

1. **Neo4j-backed storage**: Conflict adjacency stored as `CONFLICTS_WITH` relationships between `PropositionNode` entities in Neo4j. Properties: `confidence`, `reason`, `detectedAt`. Leverages existing Drivine ORM and `AnchorRepository` patterns. No new dependency needed.
2. **Incremental maintenance**: Index updates on individual `AnchorLifecycleEvent` emissions. No full rebuild. On Promoted: compute conflicts between new anchor and all existing anchors, create edges. On Archived/Evicted: delete all edges involving the anchor.
3. **Fast lookup**: `getConflicts(String anchorId)` queries `MATCH (a)-[:CONFLICTS_WITH]->(b) WHERE a.id = $anchorId`. No LLM call at lookup time.
4. **Lifecycle-event-driven updates**: `ConflictIndex` subscribes to `AnchorLifecycleEvent` (Promoted, Archived, AuthorityChanged). Updates are synchronous within the turn pipeline, ensuring the index is current by the next turn's promotion cycle.
5. **ConflictEntry stacked layout**: Each entry bundles anchor ID, text, authority, conflict type, confidence — all fields needed for resolution in a single object. No secondary lookups required (recommendation I).
6. **Fallback on miss**: Index miss (pair not present) falls back to existing `LlmConflictDetector`. The index is additive (reduces LLM calls), not substitutive (replaces LLM detection). Zero false-negative risk.
7. **INDEXED strategy in CompositeConflictDetector**: New `ConflictDetectionStrategy.INDEXED` option. When selected, index is the primary path with LLM fallback. Can be composed with LEXICAL and SEMANTIC strategies.
8. **Context cleanup**: Conflict edges are automatically deleted by existing `clearByContext` — no orphaned relationships.
9. **A/B testability**: `ConflictDetectionStrategy` MUST be configurable per-simulation via scenario YAML. The `CompositeConflictDetector` strategy chain MUST be selectable per-scenario so the simulator can compare detection approaches head-to-head (e.g., SEMANTIC-only baseline vs. INDEXED+SEMANTIC vs. LOGICAL+INDEXED+SEMANTIC).
10. **DICE Prolog as future detection layer**: DICE 0.1.0-SNAPSHOT includes tuProlog (2p-kt) for Prolog projection — already on the classpath with zero new dependencies. A future LOGICAL `ConflictDetectionStrategy` MAY use Prolog backward chaining to detect logical contradictions (e.g., "X is mortal" + "X is immortal") that lexical negation misses and LLM calls are too expensive for. Prolog is a detection mechanism that populates the Neo4j index — it does not replace Neo4j as the storage layer. This is out of scope for the initial F05 implementation but the strategy enum and `CompositeConflictDetector` architecture SHOULD accommodate it.

## Open Questions

| # | Question | Options | Leaning | Resolution Trigger |
|---|----------|---------|---------|-------------------|
| 1 | **Storage backend** | **Decided: Neo4j `CONFLICTS_WITH` relationships.** Conflict edges are graph data — they belong in the graph database alongside the propositions they connect. Persists across restarts, cleaned up with context isolation, no new dependency. If query latency is a concern, Caffeine (F11) can cache lookups. | N/A (decided) | N/A |
| 2 | **Cold-start population strategy** | (a) Eager: on first promotion in a new context, batch-detect all existing anchor pairs. (b) Lazy: populate incrementally as promotions occur (new anchor vs. all existing). (c) Background: populate asynchronously after context creation. | (b) Lazy. Each promotion adds edges for the new anchor only (N-1 LLM calls for N existing anchors). Amortized over promotions. Eager (a) is O(N^2) upfront; background (c) adds async complexity. | Design phase. Profile cold-start cost in simulation. |
| 3 | **Staleness bounds** | (a) Strict: index is always current (synchronous updates on events). (b) Bounded: index may be stale for up to 1 turn (async updates). (c) Best-effort: updates are fire-and-forget. | (a) Strict synchronous updates. Lifecycle events are already processed synchronously within the turn pipeline. Index update is O(1) per event (map put/remove). No reason to accept staleness. | Confirmed by architecture review. |
| 4 | **Conflict confidence threshold for index entry** | (a) Store all detected conflicts regardless of confidence. (b) Store only conflicts above a configurable confidence threshold. (c) Store all, but mark low-confidence entries for LLM re-verification. | (a) Store all. The index is conservative (may flag non-conflicts). LLM verification handles false positives. Filtering by confidence at index time risks false negatives. | Design phase. |
| 5 | **Prolog LOGICAL strategy (future)** | (a) Add LOGICAL to the strategy enum now but defer implementation. (b) Wait until Prolog rule design is complete to add the enum value. (c) Reserve the name in documentation only. | (a) Add LOGICAL to the enum. The enum value costs nothing and signals the intended architecture. Implementation is deferred. Prolog projection via `PrologEngine.query()` is available from DICE 0.1.0-SNAPSHOT (tuProlog/2p-kt on classpath). | Design phase when implementing strategy enum. |

## Visibility Contract

| Surface | What | When | Format |
|---------|------|------|--------|
| Structured logs | `conflict.index.update` | Each lifecycle event that modifies the index | INFO: event type, anchors affected, edges added/removed |
| Structured logs | `conflict.index.lookup` | Each conflict check | DEBUG: query text, hit/miss, conflicts found |
| Structured logs | `conflict.index.populate` | Cold-start batch population | INFO: pairs checked, duration, conflicts found |
| Metrics | Index size, hit rate, LLM calls saved | Aggregated per context | Counters: `conflict.index.size`, `conflict.index.hit_rate`, `conflict.index.llm_calls_saved` |

## Acceptance Gates

| Gate | Verification | Command |
|------|-------------|---------|
| LLM calls reduced during promotion | Integration test: run same scenario with and without index, count LLM calls for conflict detection. Index run MUST have fewer calls. | `./mvnw test -pl . -Dtest=ConflictIndexIntegrationTest` |
| Index updates on lifecycle events | Unit test: promote anchor -> index gains edges. Archive anchor -> edges removed. | `./mvnw test -pl . -Dtest=InMemoryConflictIndexTest` |
| No false negatives | Unit test: index miss -> LLM fallback detects conflict. Index never claims "no conflict" for a pair it has not checked. | `./mvnw test -pl . -Dtest=InMemoryConflictIndexTest` |
| ConflictEntry has all resolution fields | Unit test: entry contains anchor ID, text, authority, conflict type, confidence. | `./mvnw test -pl . -Dtest=InMemoryConflictIndexTest` |

## Small-Model Constraints

- **Max 5 files per task** (interface + implementation + entry record + strategy integration + tests)
- **Verification**: `./mvnw test` MUST pass after each task
- **New Neo4j relationship type**: `CONFLICTS_WITH` between `PropositionNode` entities (new Drivine queries in `AnchorRepository`)
- **Scope boundary**: `anchor/` package for index types; `extract/` integration is a separate task

## Task Sketch

| # | Scope | Files | Gate |
|---|-------|-------|------|
| T1 | `ConflictIndex` interface + `ConflictEntry` record | `ConflictIndex.java`, `ConflictEntry.java` | Interface compiles, record has all required fields |
| T2 | `AnchorRepository` conflict edge queries (create, delete, lookup) | `AnchorRepository.java` (update), test | CONFLICTS_WITH relationships CRUD via Cypher |
| T3 | `Neo4jConflictIndex` with lifecycle event subscription + incremental update | `Neo4jConflictIndex.java`, `Neo4jConflictIndexTest.java` | Promote creates edges, archive deletes edges |
| T4 | `ConflictDetectionStrategy.INDEXED` + `CompositeConflictDetector` integration | `ConflictDetectionStrategy.java` (update), `CompositeConflictDetector.java` (update), integration test | INDEXED strategy uses index with LLM fallback |
| T5 | Metrics logging + context cleanup verification | `Neo4jConflictIndex.java` (update), metrics test | Hit rate logged, clearByContext removes conflict edges |

## Risks Requiring Design Attention

1. **LlmConflictDetector batch API**: Cold-start population needs to call `LlmConflictDetector.batchDetect()` for new-anchor-vs-all-existing pairs. Verify the batch API supports this usage pattern (currently used by `AnchorPromoter` for candidate-vs-existing).
2. **CompositeConflictDetector strategy composition**: Adding INDEXED (and reserving LOGICAL) in the strategy enum. Verify that `CompositeConflictDetector` can chain INDEXED (fast path) with SEMANTIC (LLM fallback) correctly. The composite should try INDEXED first, fall back to SEMANTIC on miss. The LOGICAL strategy (Prolog-based) SHOULD slot into the same chain when implemented — between LEXICAL and SEMANTIC in cost, complementing INDEXED for detection.
3. **Drivine relationship mapping**: Verify that Drivine supports creating/querying relationships with properties (`confidence`, `reason`, `detectedAt`) between existing `PropositionNode` entities. The existing `CANONIZATION_REQUEST_FOR` relationship pattern in `AnchorRepository` is a good reference.
4. **clearByContext scope**: Verify that `clearByContext` deletes `CONFLICTS_WITH` relationships along with the proposition nodes, or add explicit cleanup if needed.
5. **Prolog rule design (future)**: Designing domain-independent Prolog contradiction rules is non-trivial. Simple antonym detection (mortal/immortal) is straightforward, but general logical contradiction requires careful rule authoring. DICE's `PrologEngine` API (`query()`, `queryAll()`, `findAll()`) and Prolog fact projection from Propositions are available, but the rule set needs dedicated research. This is explicitly out of scope for F05's initial implementation.
