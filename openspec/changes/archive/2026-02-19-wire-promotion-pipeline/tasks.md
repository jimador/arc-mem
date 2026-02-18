# Implementation Tasks

## 1. Constructor Wiring

- [x] 1.1 Add `DuplicateDetector` to `AnchorPromoter` constructor parameters
- [x] 1.2 Store as final field
- [x] 1.3 Verify Spring context wires it automatically (existing @Service bean)

## 2. Duplicate Detection Gate

- [x] 2.1 Add dedup check in `evaluateAndPromote()` after confidence gate
- [x] 2.2 Call `duplicateDetector.isDuplicate(contextId, proposition.getText())`
- [x] 2.3 If duplicate: log and skip, increment dedup-filtered counter
- [x] 2.4 If novel: proceed to conflict detection

## 3. Conflict Resolution Gate

- [x] 3.1 Replace "skip on any conflict" logic with resolution-based logic
- [x] 3.2 For each detected conflict, call `conflictResolver.resolve(conflict)`
- [x] 3.3 Implement KEEP action: skip incoming proposition
- [x] 3.4 Implement REPLACE action:
  - [x] 3.4.1 Add `archiveAnchor(id)` to AnchorRepository if not present
  - [x] 3.4.2 Archive the existing conflicting anchor (rank=0, status=ARCHIVED)
  - [x] 3.4.3 Continue with promotion of incoming
- [x] 3.5 Implement COEXIST action: continue to trust evaluation
- [x] 3.6 Handle multiple conflicts: KEEP takes precedence over COEXIST
- [x] 3.7 Log resolution decisions with context

## 4. Promotion Funnel Logging

- [x] 4.1 Add counters for each gate (total, postConfidence, postDedup, postConflict, postTrust, promoted)
- [x] 4.2 Increment counters at each gate
- [x] 4.3 Log summary after processing batch
- [x] 4.4 Use placeholder-style logging per CLAUDE.md (`logger.info("Funnel: {} extracted, ...", ...)`)

## 5. Repository Support

- [x] 5.1 Check if `archiveAnchor(id)` exists in AnchorRepository
- [x] 5.2 If not, add Cypher query: `SET p.rank = 0, p.status = 'ARCHIVED'`
- [x] 5.3 Verify archive does not violate authority-upgrade-only invariant (rank change, not authority change)

## 6. Testing

- [x] 6.1 Update existing `AnchorPromoterTest` to account for new DuplicateDetector dependency
  - [x] 6.1.1 Mock DuplicateDetector in test setup
  - [x] 6.1.2 Verify existing tests still pass with mock returning false (novel)
- [x] 6.2 Test: Duplicate proposition rejected at dedup gate
  - [x] 6.2.1 Mock DuplicateDetector.isDuplicate() → true
  - [x] 6.2.2 Assert proposition not promoted
  - [x] 6.2.3 Assert conflict detection NOT called (short-circuit)
- [x] 6.3 Test: KEEP resolution skips incoming
  - [x] 6.3.1 Mock ConflictDetector → returns conflict
  - [x] 6.3.2 Mock ConflictResolver → returns KEEP
  - [x] 6.3.3 Assert proposition not promoted
- [x] 6.4 Test: REPLACE resolution archives existing, promotes incoming
  - [x] 6.4.1 Mock ConflictResolver → returns REPLACE
  - [x] 6.4.2 Assert archiveAnchor called on existing
  - [x] 6.4.3 Assert incoming promoted
- [x] 6.5 Test: COEXIST resolution allows promotion
  - [x] 6.5.1 Mock ConflictResolver → returns COEXIST
  - [x] 6.5.2 Assert incoming proceeds to trust evaluation
- [x] 6.6 Test: Funnel logging emits correct counts
- [x] 6.7 Test: Pipeline gate ordering (dedup before conflict, conflict before trust)

## 7. Verification

- [x] 7.1 Run full test suite: `./mvnw.cmd test`
- [x] 7.2 Build: `./mvnw.cmd clean compile -DskipTests`
- [ ] 7.3 Manual smoke test: Chat with multiple turns, verify promotion funnel log appears
- [ ] 7.4 Verify duplicate propositions no longer produce separate anchors
- [ ] 7.5 Verify conflicting propositions are resolved (not silently skipped)

## Definition of Done

- ✓ DuplicateDetector wired into AnchorPromoter and called during promotion
- ✓ ConflictResolver decisions acted upon (KEEP/REPLACE/COEXIST)
- ✓ Promotion funnel logged with gate counts
- ✓ All tests pass (27+)
- ✓ No duplicate anchors promoted in chat flow
- ✓ Conflict resolution visible in logs
