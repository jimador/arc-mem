## ADDED Requirements

### REQ-INTERFACE: ConflictIndex interface

The system SHALL define a `ConflictIndex` interface in `dev.arcmem.arcmem` with the following methods:

1. `Set<ConflictEntry> getConflicts(String unitId)` -- returns all known conflicts for the given memory unit. MUST return an empty set (never null) if no conflicts are indexed.
2. `void recordConflict(String unitId, ConflictEntry entry)` -- stores a conflict entry for the given memory unit. MUST be idempotent -- recording the same entry twice SHALL NOT produce duplicates.
3. `void removeConflicts(String unitId)` -- removes all conflict entries involving the given memory unit (both as source and as target). MUST be a no-op if the memory unit has no indexed conflicts.
4. `void clear(String contextId)` -- removes all conflict entries associated with the given context. MUST support simulation isolation per Article VI of the constitution.
5. `boolean hasConflicts(String unitId)` -- returns `true` if the index contains any conflict entries for the given memory unit. MUST be consistent with `getConflicts()` -- `hasConflicts()` returns `true` if and only if `getConflicts()` returns a non-empty set.
6. `int size()` -- returns the total number of conflict entries in the index. MUST be consistent with the sum of all per-unit entry sets.

`ConflictIndex` MUST NOT be sealed. The interface is intentionally open to allow future implementations (Neo4j-backed, Caffeine-cached) without API changes.

All implementations MUST be thread-safe. `getConflicts()` and `recordConflict()` MAY be called concurrently from multiple threads.

#### Scenario: Interface is open for extension

- **GIVEN** the `ConflictIndex` interface
- **WHEN** a new class implements `ConflictIndex`
- **THEN** no sealed modifier or permits clause SHALL prevent the implementation

#### Scenario: getConflicts returns empty set for unknown memory unit

- **GIVEN** an empty `ConflictIndex`
- **WHEN** `getConflicts("unknown-id")` is called
- **THEN** the result SHALL be an empty set, not null

---

### REQ-ENTRY: ConflictEntry record

The system SHALL define a `ConflictEntry` record in `dev.arcmem.arcmem` with the following components:

| Component | Type | Description |
|-----------|------|-------------|
| `unitId` | `String` | Neo4j node ID of the conflicting memory unit |
| `unitText` | `String` | Proposition text of the conflicting memory unit |
| `authority` | `Authority` | Authority level of the conflicting memory unit at detection time |
| `conflictType` | `ConflictType` | Type of conflict (CONTRADICTION, REVISION, WORLD_PROGRESSION) |
| `confidence` | `double` | Detection confidence (0.0--1.0) |
| `detectedAt` | `Instant` | When the conflict was detected |

`ConflictEntry` MUST bundle all fields needed for conflict resolution without secondary lookups (stacked data layout, per STATIC recommendation I for coalesced access). The record MUST be immutable.

#### Scenario: Entry captures all resolution fields

- **GIVEN** a `ConflictEntry` created with memory unit ID, text, authority, conflict type, confidence, and timestamp
- **WHEN** the entry is inspected
- **THEN** all six components SHALL be accessible without additional repository lookups

#### Scenario: Entries are value-equal by content

- **GIVEN** two `ConflictEntry` instances with identical component values
- **WHEN** `equals()` is called
- **THEN** the result SHALL be `true`

---

### REQ-LOOKUP: O(1) conflict lookup

`ConflictIndex.getConflicts(String unitId)` SHALL provide O(1) amortized lookup time for indexed memory unit pairs. The lookup MUST NOT invoke any LLM calls.

When conflicts exist for the given memory unit, the returned set SHALL contain `ConflictEntry` records with all fields populated. The caller MUST be able to make conflict resolution decisions using only the returned entries, without additional repository queries.

#### Scenario: Indexed conflicts returned without LLM call

- **GIVEN** a memory unit "A" with a recorded conflict against memory unit "B"
- **WHEN** `getConflicts("A")` is called
- **THEN** the result SHALL contain a `ConflictEntry` referencing memory unit "B"
- **AND** no LLM call SHALL be made

#### Scenario: Lookup for memory unit with no conflicts

- **GIVEN** a memory unit "C" with no recorded conflicts
- **WHEN** `getConflicts("C")` is called
- **THEN** the result SHALL be an empty set

---

### REQ-INCREMENTAL: Lifecycle-event-driven incremental updates

`InMemoryConflictIndex` SHALL subscribe to `UnitLifecycleEvent` emissions and update the index incrementally:

1. **On `Promoted`**: The index SHOULD detect conflicts between the newly promoted memory unit and all existing memory units in the same context. Detected conflicts SHALL be recorded via `recordConflict()`. This population MAY be deferred to the first `getConflicts()` call for the new memory unit (lazy population).
2. **On `Archived`**: All conflict entries involving the archived memory unit (as both source and target) SHALL be removed via `removeConflicts()`.
3. **On `Evicted`**: All conflict entries involving the evicted memory unit SHALL be removed via `removeConflicts()`.
4. **On `AuthorityChanged`**: Conflict entries involving the affected memory unit SHOULD be updated if the authority change is relevant to conflict resolution. At minimum, stale entries MUST NOT cause incorrect resolution decisions.

Updates MUST be synchronous within the event processing pipeline to ensure the index is current by the next promotion cycle.

#### Scenario: Promoted memory unit triggers conflict detection

- **GIVEN** an existing memory unit "A" in the index
- **WHEN** memory unit "B" is promoted in the same context
- **THEN** conflicts between "A" and "B" SHALL be detected and recorded in the index

#### Scenario: Archived memory unit entries removed

- **GIVEN** memory unit "A" has conflict entries in the index
- **WHEN** memory unit "A" is archived
- **THEN** all entries referencing "A" (as source or target) SHALL be removed
- **AND** `getConflicts("A")` SHALL return an empty set

#### Scenario: Evicted memory unit entries removed

- **GIVEN** memory unit "A" has conflict entries in the index
- **WHEN** memory unit "A" is evicted
- **THEN** all entries referencing "A" SHALL be removed

---

### REQ-FALLBACK: Index miss falls back to LLM detection

When `CompositeConflictDetector` uses the `INDEXED` strategy, an index miss (memory unit pair not present in the index) MUST fall back to existing LLM-based conflict detection via `LlmConflictDetector` or the configured semantic detector.

The index MUST NOT claim "no conflict" for a pair it has not evaluated. An empty result from `getConflicts()` means "not indexed" and MUST trigger fallback detection, not "no conflict exists."

The fallback detection result SHOULD be recorded in the index for future lookups.

#### Scenario: Index miss triggers LLM fallback

- **GIVEN** an `INDEXED` strategy with no entry for the memory unit pair (A, B)
- **WHEN** conflict detection is requested for incoming text against memory unit A
- **THEN** the detector SHALL fall back to LLM-based detection
- **AND** the result SHALL be equivalent to `SEMANTIC_ONLY` detection for that pair

#### Scenario: Fallback result cached in index

- **GIVEN** an index miss that triggers LLM fallback
- **WHEN** the fallback detects a conflict between pair (A, B)
- **THEN** the conflict SHALL be recorded in the index
- **AND** subsequent lookups for the same pair SHALL return the cached result without an LLM call

---

### REQ-INVALIDATE: Automatic entry removal for inactive memory units

Conflict entries involving archived, evicted, or otherwise inactive memory units MUST be removed from the index automatically. Stale entries referencing inactive memory units MUST NOT cause false-positive conflict blocking during promotion.

Removal SHALL be triggered by `UnitLifecycleEvent.Archived` and `UnitLifecycleEvent.Evicted` events. The index MUST process these events before the next promotion cycle's conflict check.

#### Scenario: Stale entry does not block promotion

- **GIVEN** memory unit "A" was archived and its entries were removed from the index
- **WHEN** a new proposition is checked for conflicts
- **THEN** no conflict with memory unit "A" SHALL be reported from the index

---

### REQ-STRATEGY: INDEXED ConflictDetectionStrategy

`ConflictDetectionStrategy` SHALL include an `INDEXED` value. When `INDEXED` is the active strategy in `CompositeConflictDetector`:

1. The detector SHALL first consult the `ConflictIndex` for known conflicts.
2. For index hits, the detector SHALL convert `ConflictEntry` records to `ConflictDetector.Conflict` records and return them without LLM calls.
3. For index misses, the detector SHALL fall back to the existing semantic detection path (subject filter + LLM).
4. Fallback results SHOULD be recorded in the index.

`ConflictDetectionStrategy` SHALL also include a `LOGICAL` value reserved for future Prolog-based contradiction detection via DICE tuProlog projection. The `LOGICAL` value MUST compile but its `CompositeConflictDetector` branch MUST throw `UnsupportedOperationException` with a message indicating it is reserved for future implementation.

#### Scenario: INDEXED strategy uses index first

- **GIVEN** `ConflictDetectionStrategy.INDEXED` is active
- **AND** the index contains a conflict entry for memory unit "A"
- **WHEN** conflict detection is requested for text matching memory unit "A"
- **THEN** the conflict SHALL be returned from the index without an LLM call

#### Scenario: LOGICAL strategy throws for now

- **GIVEN** `ConflictDetectionStrategy.LOGICAL` is active
- **WHEN** conflict detection is requested
- **THEN** an `UnsupportedOperationException` SHALL be thrown with a message indicating future implementation

---

### REQ-CLEANUP: Context-scoped cleanup

`ConflictIndex.clear(String contextId)` SHALL remove all conflict entries associated with the given context. This MUST support simulation isolation: when a simulation run completes and its context is torn down via `MemoryUnitRepository.clearByContext()`, the conflict index for that context MUST also be cleared.

The `InMemoryConflictIndex` MUST track which memory unit IDs belong to which context to support context-scoped cleanup.

#### Scenario: Clear removes all context entries

- **GIVEN** a conflict index with entries for context "sim-abc123"
- **WHEN** `clear("sim-abc123")` is called
- **THEN** all entries for that context SHALL be removed
- **AND** entries for other contexts SHALL be unaffected

#### Scenario: Clear on empty context is a no-op

- **GIVEN** a conflict index with no entries for context "sim-xyz"
- **WHEN** `clear("sim-xyz")` is called
- **THEN** the operation SHALL complete without error

---

### REQ-THREAD-SAFE: Thread safety

All `ConflictIndex` implementations MUST be thread-safe. Concurrent calls to `getConflicts()`, `recordConflict()`, `removeConflicts()`, and `clear()` MUST NOT cause data corruption, lost updates, or `ConcurrentModificationException`.

`InMemoryConflictIndex` SHALL achieve thread safety through `ConcurrentHashMap` and `ConcurrentHashMap.newKeySet()` (or equivalent concurrent set). No external synchronization SHALL be required by callers.

#### Scenario: Concurrent record and lookup

- **GIVEN** two threads operating on the same `InMemoryConflictIndex`
- **WHEN** thread 1 calls `recordConflict()` while thread 2 calls `getConflicts()` for the same memory unit
- **THEN** thread 2 SHALL see either the pre-update or post-update state, never a corrupted state

---

## Invariants

- **CI1**: `ConflictIndex.getConflicts()` SHALL never return null. An empty set means no conflicts are indexed for that memory unit.
- **CI2**: `ConflictEntry` SHALL be immutable. All components are set at construction time and cannot be modified.
- **CI3**: The index SHALL NOT produce false negatives. An index miss (pair not evaluated) MUST trigger fallback detection, not report "no conflict."
- **CI4**: Stale entries referencing archived or evicted memory units MUST be removed before the next promotion cycle's conflict check.
- **CI5**: The index MUST NOT modify memory unit state (rank, authority, pinned status). It is a read-only cache of detection results.
- **CI6**: `ConflictDetectionStrategy.INDEXED` MUST compose with existing strategies. The index is additive (reduces LLM calls), not substitutive (replaces LLM detection).
- **CI7**: Per-context isolation MUST be maintained. Entries for one context MUST NOT leak into another context's lookups.
