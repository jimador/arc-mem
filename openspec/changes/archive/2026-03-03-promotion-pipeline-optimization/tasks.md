# Implementation Tasks

## 1. Add conflict pre-check gate to AnchorPromoter

- [x] 1.1 Add `Optional<ConflictIndex>` as the last constructor parameter of `AnchorPromoter`. Store as a nullable `ConflictIndex` field (unwrap in constructor via `orElse(null)`). The existing 5-parameter constructor signature is replaced by a 6-parameter constructor.
- [x] 1.2 Add private helper method `shouldRunPrecheck()` that returns `true` when the `ConflictIndex` field is non-null and `size() > 0`.
- [x] 1.3 Add private helper method `isFilteredByPrecheck(String propositionText, List<Anchor> existingAnchors)` that: (a) iterates over existing anchors, (b) calls `conflictIndex.getConflicts(anchor.id())` for each, (c) checks if any returned `ConflictEntry` has `anchorText` matching the proposition text AND `authority.isAtLeast(Authority.RELIABLE)`, (d) returns `true` if a matching RELIABLE+ conflict is found. Log at INFO for each rejection with proposition text (truncated), conflicting anchor ID, authority, and confidence.

**Verification**: `./mvnw clean compile -DskipTests`

## 2. Integrate pre-check into sequential promotion path

- [x] 2.1 In `evaluateAndPromoteWithOutcome`, add a `postPrecheck` counter. After the confidence gate and before the dedup gate, add: if `shouldRunPrecheck()` is true, call `isFilteredByPrecheck(prop.getText(), anchors)`. If filtered, increment the counter and `continue`. If not filtered or pre-check is skipped, fall through to the dedup gate.
- [x] 2.2 Update the funnel summary log at the end of `evaluateAndPromoteWithOutcome` to include `post-precheck` between `post-confidence` and `post-dedup`.
- [x] 2.3 Update the Javadoc on `AnchorPromoter` to reflect the new gate order: confidence -> conflict pre-check -> dedup -> conflict -> trust -> promote. Update invariant P3 to note the pre-check is skipped when the index is absent or empty.

**Verification**: `./mvnw clean compile -DskipTests`

## 3. Integrate pre-check into batch promotion path

- [x] 3.1 In `batchEvaluateAndPromoteWithOutcome`, after the confidence filtering and before the existing-anchor dedup, add: if `shouldRunPrecheck()` is true, filter the `confident` list using `isFilteredByPrecheck`. Collect the filtered list as `postPrecheck`. Use `postPrecheck` as the input to the subsequent existing-anchor dedup step.
- [x] 3.2 Update the batch funnel summary log to include `post-precheck` count between `post-confidence` and `post-dedup`.

**Verification**: `./mvnw clean compile -DskipTests`

## 4. Unit tests for pre-check behavior

- [x] 4.1 In `AnchorPromoterTest`, add a `@Nested @DisplayName("conflict pre-check gate")` test group. Mock `ConflictIndex` to return `ConflictEntry` instances with various authorities.
- [x] 4.2 Test: `precheckFiltersPropositionConflictingWithReliableAnchor` -- mock index to return a RELIABLE `ConflictEntry` for the incoming text. Verify the proposition is not promoted. Verify `DuplicateDetector` is never called for that proposition.
- [x] 4.3 Test: `precheckPassesPropositionConflictingWithProvisionalAnchor` -- mock index to return a PROVISIONAL `ConflictEntry`. Verify the proposition proceeds to the dedup gate.
- [x] 4.4 Test: `precheckSkippedWhenIndexIsEmpty` -- construct `AnchorPromoter` with an empty `ConflictIndex` (size == 0). Verify all candidates reach the dedup gate (same behavior as pre-F06 pipeline).
- [x] 4.5 Test: `precheckSkippedWhenIndexIsAbsent` -- construct `AnchorPromoter` with `Optional.empty()`. Verify all candidates reach the dedup gate.
- [x] 4.6 Test: `batchPathIncludesPrecheck` -- verify pre-check filtering works in `batchEvaluateAndPromoteWithOutcome` with the same authority threshold behavior.

**Verification**: `./mvnw test -Dtest=AnchorPromoterTest`

## 5. Verify full test suite

- [x] 5.1 Run the full test suite to ensure no regressions from the constructor change or gate addition.

**Verification**: `./mvnw test`

## Definition of Done

- [x] All compilation succeeds: `./mvnw clean compile -DskipTests`
- [x] All tests pass: `./mvnw test`
- [x] `AnchorPromoter` has a 6-parameter constructor accepting `Optional<ConflictIndex>`
- [x] Pre-check gate filters propositions with RELIABLE+ indexed conflicts before the dedup gate
- [x] Pre-check is skipped when `ConflictIndex` is absent or empty (graceful fallback)
- [x] Pre-check applies to both sequential and batch code paths
- [x] Pre-check rejections logged at INFO with anchor ID, authority, and confidence
- [x] Funnel summary includes `post-precheck` count
- [x] No LLM calls made by the pre-check gate
- [x] No anchor state modifications by the pre-check gate
- [x] All existing tests pass without modification (backward compatibility via Optional injection)
