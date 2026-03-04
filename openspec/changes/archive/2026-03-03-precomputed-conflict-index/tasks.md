# Implementation Tasks

## 1. Create ConflictEntry record and ConflictIndex interface

- [x] 1.1 Create `ConflictEntry` record in `anchor/` with components: `anchorId` (String), `anchorText` (String), `authority` (Authority), `conflictType` (ConflictType), `confidence` (double), `detectedAt` (Instant)
- [x] 1.2 Create `ConflictIndex` interface in `anchor/` with methods: `getConflicts(String anchorId) -> Set<ConflictEntry>`, `recordConflict(String anchorId, ConflictEntry entry)`, `removeConflicts(String anchorId)`, `clear(String contextId)`, `hasConflicts(String anchorId) -> boolean`, `size() -> int`. Interface MUST NOT be sealed.

**Verification**: `./mvnw clean compile -DskipTests`

## 2. Create InMemoryConflictIndex implementation

- [x] 2.1 Create `InMemoryConflictIndex` class in `anchor/` implementing `ConflictIndex`. Use `ConcurrentHashMap<String, Set<ConflictEntry>>` for the index and `ConcurrentHashMap<String, Set<String>>` for context-to-anchor tracking. Annotate with `@Component`.
- [x] 2.2 Implement `getConflicts()` -- return defensive copy of the entry set (or empty set if absent). Implement `recordConflict()` -- use `computeIfAbsent()` to create the set, add the entry idempotently. Implement `removeConflicts()` -- remove entries from the index map AND scan other anchors' sets for entries referencing the removed anchor. Implement `hasConflicts()` -- delegate to `getConflicts().isEmpty()` negation. Implement `size()` -- sum of all entry set sizes.
- [x] 2.3 Implement `clear(String contextId)` -- look up anchor IDs in `contextAnchors`, call `removeConflicts()` for each, then remove the context entry. Log at INFO: context ID, number of anchors cleared, number of entries removed.
- [x] 2.4 Add `registerAnchor(String anchorId, String contextId)` package-private method to track anchor-to-context mapping. Called during event handling to support context-scoped cleanup.
- [x] 2.5 Add `@EventListener` methods for `AnchorLifecycleEvent.Archived` and `AnchorLifecycleEvent.Evicted` -- both call `removeConflicts(event.getAnchorId())`. Add `@EventListener` for `AnchorLifecycleEvent.Promoted` -- call `registerAnchor(event.getAnchorId(), event.getContextId())`. Log at DEBUG for each event processed.

**Verification**: `./mvnw clean compile -DskipTests`

## 3. Update ConflictDetectionStrategy and CompositeConflictDetector

- [x] 3.1 Add `INDEXED` and `LOGICAL` values to `ConflictDetectionStrategy` enum. Add Javadoc: `INDEXED` is index-first with LLM fallback; `LOGICAL` is reserved for future Prolog-based detection.
- [x] 3.2 Update `CompositeConflictDetector` constructor to accept an optional `@Nullable ConflictIndex conflictIndex` parameter (4th parameter after `SubjectFilter`, before `ConflictDetectionStrategy`). Existing 4-parameter constructor delegates to the new 5-parameter constructor with `null` index for backward compatibility.
- [x] 3.3 Add `INDEXED` branch to the `detect()` switch expression: check index for conflicts matching the incoming text against existing anchors. For hits, convert `ConflictEntry` to `Conflict` record. For misses, delegate to `detectSemantic()` and cache results in the index. If `conflictIndex` is null, log a warning and fall back to `LEXICAL_THEN_SEMANTIC`.
- [x] 3.4 Add `LOGICAL` branch to the `detect()` switch expression: throw `UnsupportedOperationException("LOGICAL conflict detection is reserved for future Prolog-based implementation")`.
- [x] 3.5 Add `INDEXED` and `LOGICAL` branches to the `batchDetect()` switch expression. `INDEXED` delegates to per-candidate `detect()` (index lookup is cheap, no need for batch optimization). `LOGICAL` throws the same `UnsupportedOperationException`.

**Verification**: `./mvnw clean compile -DskipTests`

## 4. Wire ConflictIndex bean in AnchorConfiguration

- [x] 4.1 Add `INDEXED` value to `ConflictStrategy` enum (the config-level enum in `anchor/` used by `DiceAnchorsProperties.ConflictDetectionConfig`). This is separate from `ConflictDetectionStrategy` (the internal enum used by `CompositeConflictDetector`).
- [x] 4.2 Add `@Bean @ConditionalOnMissingBean InMemoryConflictIndex conflictIndex()` to `AnchorConfiguration`. Returns a new `InMemoryConflictIndex()`.
- [x] 4.3 Update `conflictDetector()` method to accept `InMemoryConflictIndex conflictIndex` parameter. For the existing `HYBRID` case, pass the index to the 5-parameter `CompositeConflictDetector` constructor.
- [x] 4.4 Add `INDEXED` case to the `conflictDetector()` switch expression: create a `CompositeConflictDetector` with `ConflictDetectionStrategy.INDEXED`, lexical detector, semantic detector, subject filter, and the conflict index.

**Verification**: `./mvnw clean compile -DskipTests`

## 5. Unit tests for InMemoryConflictIndex

- [x] 5.1 Create `InMemoryConflictIndexTest` in the `anchor/` test package. Use JUnit 5 + AssertJ. Structure with `@Nested` + `@DisplayName`.
- [x] 5.2 Test `getConflicts`: returns empty set for unknown anchor; returns recorded entries; returns defensive copy (modifying returned set does not affect index).
- [x] 5.3 Test `recordConflict`: idempotent (same entry recorded twice produces one entry); entries for different anchors are independent.
- [x] 5.4 Test `removeConflicts`: removes entries for the given anchor; removes references to the anchor from other anchors' entry sets; no-op for unknown anchor.
- [x] 5.5 Test `clear`: removes all entries for the given context; entries for other contexts are unaffected; no-op for unknown context.
- [x] 5.6 Test `hasConflicts` and `size`: consistent with recorded/removed entries.

**Verification**: `./mvnw test -Dtest=InMemoryConflictIndexTest`

## 6. Unit tests for CompositeConflictDetector INDEXED strategy

- [x] 6.1 Create tests in the existing `CompositeConflictDetectorTest` (or new test class if none exists) for the `INDEXED` strategy branch.
- [x] 6.2 Test index hit: mock `ConflictIndex` to return entries, verify no LLM call is made, verify `Conflict` records are returned.
- [x] 6.3 Test index miss with fallback: mock `ConflictIndex` to return empty set, verify semantic detector is called, verify result is returned.
- [x] 6.4 Test `LOGICAL` strategy throws `UnsupportedOperationException`.
- [x] 6.5 Test null index with `INDEXED` strategy falls back to `LEXICAL_THEN_SEMANTIC`.

**Verification**: `./mvnw test -Dtest=CompositeConflictDetectorTest`

## 7. Verify full test suite

- [x] 7.1 Run full test suite to ensure no regressions from `ConflictDetectionStrategy` enum expansion or `CompositeConflictDetector` constructor changes.

**Verification**: `./mvnw test`

## Definition of Done

- [x] All compilation succeeds: `./mvnw clean compile -DskipTests`
- [x] All tests pass: `./mvnw test`
- [x] `ConflictIndex` interface is open (not sealed) with 6 methods
- [x] `ConflictEntry` record has 6 components (anchorId, anchorText, authority, conflictType, confidence, detectedAt)
- [x] `InMemoryConflictIndex` is thread-safe via `ConcurrentHashMap`
- [x] `InMemoryConflictIndex` subscribes to Promoted, Archived, and Evicted lifecycle events
- [x] `ConflictDetectionStrategy` has 5 values: LEXICAL_ONLY, SEMANTIC_ONLY, LEXICAL_THEN_SEMANTIC, INDEXED, LOGICAL
- [x] `CompositeConflictDetector` INDEXED branch checks index first, falls back to semantic on miss
- [x] `CompositeConflictDetector` LOGICAL branch throws `UnsupportedOperationException`
- [x] Backward compatibility: existing 4-parameter `CompositeConflictDetector` constructor still works
- [x] No new dependencies added
- [x] No anchor state modifications in index code (invariant CI5)
