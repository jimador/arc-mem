# Implementation Tasks

## 1. Configuration

- [x] 1.1 Extend `DiceAnchorsProperties` to add `dedup-strategy` field (enum: FAST_ONLY, LLM_ONLY, FAST_THEN_LLM)
- [x] 1.2 Update `application.yml` to set default strategy to FAST_THEN_LLM
- [x] 1.3 Add validation for strategy enum at startup (fail fast if invalid)

## 2. Core Implementation

- [x] 2.1 Create `NormalizedStringDuplicateDetector` class in `extract/` package
  - [x] 2.1.1 Implement normalization algorithm (lowercase, collapse whitespace, strip punctuation)
  - [x] 2.1.2 Implement `isDuplicate(contextId, candidateText, anchors)` method
  - [x] 2.1.3 Add logging for performance metrics (count of matches avoided)
- [x] 2.2 Create `DuplicateDetectionStrategy` enum with three values
- [x] 2.3 (Optional) Create `CompositeDuplicateDetector` if using SPI pattern

## 3. Integration with DuplicateDetector

- [x] 3.1 Refactor `DuplicateDetector` to accept `NormalizedStringDuplicateDetector` via constructor injection
- [x] 3.2 Inject `DiceAnchorsProperties` to read dedup strategy
- [x] 3.3 Update `isDuplicate()` logic to:
  - [x] 3.3.1 Call fast-path detector first (if strategy allows)
  - [x] 3.3.2 Return `true` immediately if fast-path matches
  - [x] 3.3.3 Fall back to LLM detector only if strategy is not FAST_ONLY and fast-path returned false
- [x] 3.4 Maintain backward-compatible API signature
- [x] 3.5 Add logging to trace which detector path was taken

## 4. Testing

- [x] 4.1 Unit tests for `NormalizedStringDuplicateDetector`
  - [x] 4.1.1 Test normalization: case, whitespace, punctuation
  - [x] 4.1.2 Test exact match detection
  - [x] 4.1.3 Test false positives/negatives (edge cases)
  - [x] 4.1.4 Test performance (no O(n²) behavior)
- [x] 4.2 Unit tests for `DuplicateDetector` strategy selection
  - [x] 4.2.1 Test FAST_ONLY mode (never calls LLM)
  - [x] 4.2.2 Test LLM_ONLY mode (skips fast-path)
  - [x] 4.2.3 Test FAST_THEN_LLM mode (fast first, LLM fallback)
- [x] 4.3 Integration test for full extraction pipeline
  - [x] 4.3.1 Verify duplicate propositions are rejected
  - [x] 4.3.2 Verify novel propositions proceed to promotion
  - [x] 4.3.3 Verify LLM calls are reduced (mock and count)

## 5. Verification

- [x] 5.1 Run full test suite: `./mvnw.cmd test`
- [x] 5.2 Build without tests: `./mvnw.cmd clean compile -DskipTests`
- [ ] 5.3 Manual smoke test: Start app, chat with multiple turns, verify no errors
- [ ] 5.4 Demo scenario: Show anchor list before/after with duplicates, verify fast-path catches them
- [ ] 5.5 Performance test: Compare LLM call count FAST_THEN_LLM vs LLM_ONLY in sim scenario

## 6. Documentation & Cleanup

- [x] 6.1 Add Javadoc to `NormalizedStringDuplicateDetector` class
- [x] 6.2 Update CLAUDE.md or relevant docs with dedup strategy configuration
- [x] 6.3 Verify no debug logging left in code
- [x] 6.4 Code style check: constructors, immutable collections, var usage per CLAUDE.md

## Definition of Done

- ✓ All tests pass (27+ total)
- ✓ No breaking changes to `DuplicateDetector` API
- ✓ Dedup strategy is configurable via property
- ✓ Fast-path avoids LLM calls for obvious dupes
- ✓ Demo scenario shows reduced API calls and cleaner anchor list
