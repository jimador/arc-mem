## 1. Domain Model — Temporal Fields and Supersession Enum

- [x] 1.1 Add `validFrom`, `validTo`, `transactionStart`, `transactionEnd` (`@Nullable Instant`) fields to `PropositionNode` and update `@JsonCreator` constructor with `@JsonProperty` defaults (D1, D8)
- [x] 1.2 Add `supersededBy` and `supersedes` (`@Nullable String`) fields to `PropositionNode` and update `@JsonCreator` constructor (D2, D8)
- [x] 1.3 Update convenience constructors on `PropositionNode` to default new fields to null (D8, R3)
- [x] 1.4 Create `SupersessionReason` enum with `CONFLICT_REPLACEMENT`, `BUDGET_EVICTION`, `DECAY_DEMOTION`, `MANUAL` and add `fromArchiveReason()` mapping utility (D2, R5)
- [x] 1.5 Update `PropositionView` Drivine wrapper to map the 6 new `PropositionNode` fields to/from Neo4j properties (D8)
- [x] 1.6 Verify existing tests pass — no regressions from model changes

## 2. Lifecycle Event — Superseded

- [x] 2.1 Add `Superseded` subtype to sealed `AnchorLifecycleEvent` with `predecessorId`, `successorId`, `SupersessionReason reason` fields (D5)
- [x] 2.2 Update `permits` clause on `AnchorLifecycleEvent` sealed class to include `Superseded` (D5)
- [x] 2.3 Add static factory method `AnchorLifecycleEvent.superseded(...)` following existing pattern (D5)

## 3. Repository — Supersession Link and Temporal Queries

- [x] 3.1 Add `createSupersessionLink(String successorId, String predecessorId, SupersessionReason reason)` to `AnchorRepository` — single Cypher statement that atomically creates `SUPERSEDES` relationship and sets both denormalized node fields (D2, R1)
- [x] 3.2 Extend `promoteToAnchor()` (or add `setTemporalStartFields()`) to set `validFrom` and `transactionStart` on the promoted anchor and optionally set `supersedes` field (D3b)
- [x] 3.3 Extend archive Cypher in `AnchorRepository` to set `validTo` and `transactionEnd` on archived anchors, and optionally set `supersededBy` when `successorId` is provided (D3a)
- [x] 3.4 Add `findValidAt(String contextId, Instant pointInTime)` query method with null-safe handling for legacy nodes (D4, D7)
- [x] 3.5 Add `findSupersessionChain(String anchorId)` with bounded depth (`*0..50`) returning `List<String>` in chronological order (D4, R2)
- [x] 3.6 Add `findPredecessor(String anchorId)` and `findSuccessor(String anchorId)` returning `Optional<String>` via denormalized field lookups (D4)

## 4. Engine — Temporal and Supersession Integration

- [x] 4.1 Add `archive(String anchorId, ArchiveReason reason, @Nullable String successorId)` overload to `AnchorEngine`; delegate existing `archive(String, ArchiveReason)` to it with `successorId = null` (D3a)
- [x] 4.2 Update `AnchorEngine.promote()` to set `validFrom` and `transactionStart` via repository (D3b)
- [x] 4.3 Wire supersession flow into `AnchorEngine` conflict resolution: on REPLACE, call `archive(predecessorId, reason, successorId)`, `createSupersessionLink()`, and publish `Superseded` event (D3a, D5)
- [x] 4.4 Update budget eviction flow in `AnchorRepository.evictLowestRanked()` to set `validTo` and `transactionEnd` on evicted anchors without creating `SUPERSEDES` relationship (D3c)
- [x] 4.5 Expose `findValidAt()`, `findSupersessionChain()`, `findPredecessor()`, `findSuccessor()` through `AnchorEngine` (delegating to repository) (D4)

## 5. OTEL Observability

- [x] 5.1 Set OTEL span attributes (`supersession.reason`, `supersession.predecessor_id`, `supersession.successor_id`, `supersession.predecessor_authority`, `supersession.predecessor_rank`) on `Span.current()` at the supersession call site in `AnchorEngine` (D6)

## 6. Alpha Annotations

- [x] 6.1 Add Javadoc on `SupersessionReason` documenting alpha status: current 1:1 supersession model, near-total overlap with `ArchiveReason` (and rationale for keeping separate), that `DECAY_DEMOTION` may never produce a `SUPERSEDES` link in practice, and potential future reasons (`MERGE`, `REFINEMENT`, `SOURCE_RETRACTION`)
- [x] 6.2 Add comment on `createSupersessionLink()` noting the 1:1 relationship assumption and that fan-in (merge) / fan-out (split) supersession is not yet modeled
- [x] 6.3 Add comment on `fromArchiveReason()` noting the naming inconsistency between `DORMANCY_DECAY` and `DECAY_DEMOTION` as a known simplification

## 7. Testing

- [x] 7.1 Unit tests for `PropositionNode` temporal field serialization — verify `@JsonCreator` handles null defaults and round-trips correctly (D1, D8)
- [x] 7.2 Unit tests for `SupersessionReason` enum and `fromArchiveReason()` mapping (D2, R5)
- [x] 7.3 Unit tests for `AnchorLifecycleEvent.Superseded` event creation via factory method (D5)
- [x] 7.4 Unit tests for `AnchorEngine` supersession flow — verify archive sets temporal end fields, promote sets temporal start fields, REPLACE creates supersession link and publishes event (D3)
- [x] 7.5 Unit tests for temporal query methods — `findValidAt` null-safe handling, `findPredecessor`/`findSuccessor` O(1) lookups (D4, D7)
- [x] 7.6 Unit test for budget eviction — verify `validTo`/`transactionEnd` set but no `SUPERSEDES` relationship created (D3c)
- [x] 7.7 Verify all existing tests pass — full regression suite
- [x] 7.8 Regression test new features in the UI with Playwright via Docker MCP Gateway