# Feature: Precomputed Conflict Index

## Feature ID

`F05`

## Summary

Build a conflict adjacency structure backed by Neo4j `CONFLICTS_WITH` relationships, maintained incrementally when context units change, enabling fast conflict lookup during promotion instead of per-candidate LLM calls. The index updates on `ContextUnitLifecycleEvent` emissions and supports batch population for cold-start scenarios. Inspired by STATIC's offline-index-for-online-lookup pattern. Neo4j is the natural storage layer since the project already models all context unit state as graph data via Drivine.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: Conflict detection is the most LLM-call-heavy operation in the context unit pipeline. Each promotion candidate is checked against all existing context units via LLM semantic analysis. This is O(N) LLM calls per candidate, where N is the active context unit count. For a budget of 20 context units and 5 promotion candidates per turn, that is up to 100 LLM calls for conflict detection alone.
2. **Value delivered**: Precomputing conflict relationships enables O(1) conflict lookup during promotion, eliminating redundant LLM calls for context unit pairs already in the index. This reduces latency, cost, and enables F06 (pipeline optimization) and F10 (interference-density budget).
3. **Why now**: Wave 2. LLM call reduction directly impacts latency and cost for every promotion in the pipeline. F06 depends on this feature for the pre-extraction conflict check. F10 depends on the conflict adjacency structure for interference density calculation.

## Scope

### In Scope

1. `ConflictIndex` interface: `getConflicts(String unitId) -> Set<ConflictEntry>`, `update(ContextUnitLifecycleEvent event)`, `populate(List<Context Unit> context units)`.
2. `ConflictEntry` record: context unit ID, context unit text, authority, conflict type, confidence score.
3. Neo4j `CONFLICTS_WITH` relationships between `PropositionNode` entities with properties: `confidence`, `reason`, `detectedAt`.
4. Incremental maintenance on `ContextUnitLifecycleEvent` (Promoted, Archived, AuthorityChanged, RankDecayed events).
5. Batch population for cold-start (when index is empty, detect all pairs via existing `LlmConflictDetector`).
6. Stacked data layout: `ConflictEntry` bundles all fields needed for conflict resolution, pre-computed for fast access.
7. Integration with `CompositeConflictDetector` as a new strategy (INDEXED) alongside LEXICAL and SEMANTIC.
8. Automatic cleanup via existing `clearByContext` — conflict edges are deleted when the context is torn down.
9. Future LOGICAL conflict detection strategy via DICE Prolog projection MAY complement the index. Prolog backward chaining can detect logical contradictions (e.g., "X is mortal" + "X is immortal") that lexical negation misses and LLM calls are too expensive for. This is a detection mechanism that populates the index — Neo4j remains the storage layer.
10. **A/B testability**: `ConflictDetectionStrategy` MUST support per-simulation selection. The simulator MUST be able to compare detection strategies head-to-head (e.g., SEMANTIC-only vs. INDEXED+SEMANTIC vs. LOGICAL+INDEXED+SEMANTIC) by configuring the strategy chain per scenario.

### Out of Scope

1. Cross-context conflict index sharing.
3. Conflict type classification (REVISION vs. CONTRADICTION -- that is a separate concern from F01 of the collaborative-unit-mutation roadmap).
4. Real-time index visualization in the UI.

## Dependencies

1. Feature dependencies: none.
2. Priority: SHOULD.
3. OpenSpec change slug: `precomputed-conflict-index`.
4. Research rec: F (precomputed conflict index), I (stacked data layout for coalesced access).

## Research Requirements

1. **Prolog-based logical conflict detection (future)**: DICE 0.1.0-SNAPSHOT includes tuProlog (2p-kt) for Prolog projection — already on the classpath, zero new dependencies. Propositions are projected to Prolog facts; queries run via `PrologEngine.query()`, `queryAll()`, `findAll()`. A LOGICAL conflict detection strategy using Prolog backward chaining MAY be added alongside INDEXED. This requires designing Prolog rules for domain-independent contradiction patterns. Currently zero Prolog usage in context units.

## Impacted Areas

1. **`context unit/` package (primary)**: New types -- `ConflictIndex` (interface), `Neo4jConflictIndex` (implementation), `ConflictEntry` (record), `ConflictDetectionStrategy` update (add INDEXED option).
2. **`persistence/` package**: New `CONFLICTS_WITH` relationship type in `ContextUnitRepository`. Cypher queries for conflict edge CRUD and lookup.
3. **`context unit/` package (integration)**: `CompositeConflictDetector` gains INDEXED as a strategy option. Index lookup is the fast path; LLM detection is the fallback for index misses. A future LOGICAL strategy MAY use DICE Prolog for rule-based contradiction detection, complementing INDEXED.
4. **`extract/` package**: `UnitPromoter` conflict gate uses `ConflictIndex` for pre-check before full LLM detection.
5. **`context unit/event/` package**: `ConflictIndex` subscribes to `ContextUnitLifecycleEvent` for incremental updates.

## Visibility Requirements

### UI Visibility

1. Conflict index size (number of conflict edges) MAY be displayed in RunInspectorView.

### Observability Visibility

1. Index operations MUST be logged: `conflict.index.update` (event type, context units affected), `conflict.index.lookup` (hit/miss, context unit text queried).
2. Index metrics MUST be trackable: index size (edge count), cache hit rate (lookups served from index vs. LLM fallback), LLM calls saved per promotion cycle.
3. Cold-start population MUST log duration and pair count: `conflict.index.populate` (pairs checked, duration, conflicts found).

## Acceptance Criteria

1. Conflict lookup during promotion MUST NOT require LLM calls for context unit pairs already in the index.
2. Index MUST update incrementally on lifecycle events (Promoted, Archived, AuthorityChanged).
3. Index MUST be eventually consistent -- stale entries are acceptable briefly but MUST self-correct on the next relevant lifecycle event.
4. LLM call count during promotion MUST measurably decrease compared to baseline (measured in simulation runs).
5. Index SHOULD support batch population for cold-start scenarios (first promotion in a new context).
6. Stale index entries MUST NOT cause false-positive conflict blocking (an archived context unit should not block promotion).
7. Index miss (pair not in index) MUST fall back to existing LLM-based conflict detection -- no false negatives from index gaps.
8. `ConflictEntry` MUST bundle context unit ID, text, authority, conflict type, and confidence for fast resolution without additional lookups.
9. Entries involving archived or evicted context units MUST be removed from the index automatically.

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Cold-start cost** | Medium | Medium | First promotion in a context requires batch population (O(N^2) pairs via LLM). Mitigation: amortize over first few promotions; populate lazily on first conflict check, not eagerly on context creation. |
| **Index staleness** | Medium | Low | Brief staleness is acceptable (eventual consistency). Lifecycle events are synchronous within the turn pipeline, so the index is current by the next turn's promotion cycle. |
| **Neo4j query latency** | Low | Low | Conflict edge lookup is a simple relationship traversal — fast even without additional indexes. For hot-path performance, results can be cached via Caffeine (F11) if profiling shows a bottleneck. |
| **False negatives from index gaps** | Low | High | Index miss falls back to LLM detection. The index is additive (reduces LLM calls) not substitutive (replaces LLM detection). Zero risk of missed conflicts. |
| **Prolog rule coverage gaps** | Low | Low | Prolog LOGICAL detection is a future complementary strategy, not a replacement. It catches logical contradictions that lexical negation misses. LLM SEMANTIC detection remains the ultimate fallback. |

## Proposal Seed

### Change Slug

`precomputed-conflict-index`

### Proposal Starter Inputs

1. **Problem statement**: Conflict detection is the most LLM-call-heavy operation in the pipeline. Each promotion candidate is checked against all existing context units via LLM semantic analysis (O(N) per candidate). Research (STATIC) shows precomputing constraint relationships offline enables O(1) online enforcement.
2. **Why now**: LLM call reduction directly impacts latency and cost. Every promotion in the pipeline benefits. F06 (pipeline optimization) and F10 (interference-density budget) both depend on this feature.
3. **Constraints/non-goals**: MUST NOT produce false negatives (miss real conflicts). False positives are tolerable (conservative). Index staleness window SHOULD be bounded. Backed by Neo4j `CONFLICTS_WITH` relationships — no new dependency needed.
4. **Visible outcomes**: Measurable reduction in LLM calls per promotion cycle. Index hit rate trackable as a metric. Cold-start population logged with timing.

### Suggested Capability Areas

1. **Index interface**: O(1) conflict lookup with incremental maintenance.
2. **Lifecycle integration**: Automatic index updates on context unit lifecycle events.
3. **Batch population**: Cold-start population via existing conflict detection infrastructure.
4. **Strategy integration**: INDEXED as a new `ConflictDetectionStrategy` option in `CompositeConflictDetector`.
5. **Prolog conflict detection (future)**: LOGICAL as an additional `ConflictDetectionStrategy` option using DICE Prolog projection for rule-based contradiction detection. Complements INDEXED — Prolog detects logical contradictions, index stores the results.

### Candidate Requirement Blocks

1. **REQ-LOOKUP**: The system SHALL provide O(1) conflict lookup for context unit pairs present in the index.
2. **REQ-INCREMENTAL**: The index SHALL update incrementally on `ContextUnitLifecycleEvent` emissions.
3. **REQ-FALLBACK**: Index misses SHALL fall back to existing LLM-based conflict detection.
4. **REQ-INVALIDATE**: Entries involving archived or evicted context units SHALL be removed automatically.
5. **REQ-MEASURE**: LLM call reduction during promotion SHALL be measurable in simulation runs.

## Validation Plan

1. **Unit tests** MUST verify O(1) lookup returns correct conflicts for indexed pairs.
2. **Unit tests** MUST verify incremental update on Promoted, Archived, and AuthorityChanged events.
3. **Unit tests** MUST verify cache invalidation removes entries for archived context units.
4. **Unit tests** MUST verify index miss falls back to LLM detection (no false negatives).
5. **Unit tests** MUST verify `ConflictEntry` bundles all required fields.
6. **Integration test** SHOULD verify LLM call count reduction in a simulation run compared to baseline (same scenario, with and without index).
7. **Observability validation** MUST confirm index metrics (size, hit rate, LLM calls saved) appear in structured logs.

## Known Limitations

1. **Per-context scope.** Each context maintains its own conflict edges. There is no shared index across contexts.
2. **Cold-start cost.** The first promotion in a new context incurs LLM calls for batch population (new context unit vs. all existing). This is amortized over subsequent promotions.
4. **Conflict type not classified.** The index stores whether a conflict exists and its confidence, but does not classify REVISION vs. CONTRADICTION (that is the collaborative-unit-mutation roadmap's concern).
5. **Prolog detection not in initial scope.** LOGICAL conflict detection via Prolog is a future strategy option. DICE Prolog projection (tuProlog/2p-kt) is on the classpath but currently unused in context units. Designing domain-independent contradiction rules requires separate research.

## Suggested Command

```
/opsx:new precomputed-conflict-index
```
