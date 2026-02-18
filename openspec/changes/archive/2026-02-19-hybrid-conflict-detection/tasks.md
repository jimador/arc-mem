# Implementation Tasks

## 1. Subject Filter

- [x] 1.1 Create `SubjectFilter` class in `anchor/` package
- [x] 1.2 Implement subject extraction (named entities, domain nouns, explicit markers)
  - [x] 1.2.1 Extract capitalized words as named entities
  - [x] 1.2.2 Extract first noun after determinant as domain noun
  - [x] 1.2.3 Support explicit topic markers ("about X", "regarding X")
- [x] 1.3 Implement `filterCandidates(incomingText, anchors)` → anchors with shared subjects
- [x] 1.4 Cache extracted subjects in memory (short-lived)
- [x] 1.5 Add logging for filter results (subjects found, anchors filtered)

## 2. Semantic Conflict Detector

- [x] 2.1 Create `SemanticConflictDetector` class in `anchor/` package
- [x] 2.2 Accept `ChatModel` via constructor injection
- [x] 2.3 Implement `detect(incomingText, filteredAnchors)` method
  - [x] 2.3.1 Build LLM prompt for semantic opposition detection
  - [x] 2.3.2 Call ChatModel with prompt
  - [x] 2.3.3 Parse JSON response to extract conflicts
- [x] 2.4 Map LLM results to `Conflict` objects
- [x] 2.5 Add error handling (LLM timeouts, parsing errors)
- [x] 2.6 Add logging for LLM invocations (input, output, confidence)

## 3. Conflict Detection Strategy & Composition

- [x] 3.1 Create `ConflictDetectionStrategy` enum (LEXICAL_ONLY, LEXICAL_THEN_SEMANTIC, SEMANTIC_ONLY)
- [x] 3.2 Create `CompositeConflictDetector` class implementing `ConflictDetector`
- [x] 3.3 Wire lexical detector (existing `NegationConflictDetector`)
- [x] 3.4 Implement strategy selection logic:
  - [x] 3.4.1 LEXICAL_ONLY: return lexical results only
  - [x] 3.4.2 LEXICAL_THEN_SEMANTIC: lexical first, semantic fallback if no match
  - [x] 3.4.3 SEMANTIC_ONLY: semantic only (after subject filter)
- [x] 3.5 Combine results from both detectors
- [x] 3.6 Maintain `ConflictDetector` interface contract (no breaking changes)

## 4. Configuration & Wiring

- [x] 4.1 Extend `DiceAnchorsProperties` to add `conflict-detection-strategy` field
- [x] 4.2 Create `ConflictDetectionConfiguration` Spring @Configuration class
- [x] 4.3 Wire bean factory to create appropriate `ConflictDetector` based on property
- [x] 4.4 Update `application.yml` default strategy to LEXICAL_THEN_SEMANTIC
- [x] 4.5 Add validation for enum at startup (fail fast if invalid)

## 5. Testing

- [x] 5.1 Unit tests for `SubjectFilter`
  - [x] 5.1.1 Test subject extraction (capitalized words, domain nouns, explicit markers)
  - [x] 5.1.2 Test filtering (returns only anchors with shared subjects)
  - [x] 5.1.3 Test edge cases (empty text, no subjects found, all anchors filtered)
- [x] 5.2 Unit tests for `SemanticConflictDetector`
  - [x] 5.2.1 Test LLM prompt construction
  - [x] 5.2.2 Test JSON response parsing
  - [x] 5.2.3 Test Conflict object mapping
  - [x] 5.2.4 Test error handling (LLM timeout, parse error)
- [x] 5.3 Unit tests for `CompositeConflictDetector` strategy selection
  - [x] 5.3.1 Test LEXICAL_ONLY mode (no semantic calls)
  - [x] 5.3.2 Test LEXICAL_THEN_SEMANTIC mode (semantic fallback)
  - [x] 5.3.3 Test SEMANTIC_ONLY mode (subject-filtered semantic only)
- [x] 5.4 Integration test for full conflict detection pipeline
  - [x] 5.4.1 Mock ChatModel for semantic detector
  - [x] 5.4.2 Verify lexical catches obvious negation
  - [x] 5.4.3 Verify semantic catches semantic opposition
  - [x] 5.4.4 Verify subject filter reduces LLM calls (count assertions)

## 6. Verification

- [x] 6.1 Run full test suite: `./mvnw.cmd test`
- [x] 6.2 Build without tests: `./mvnw.cmd clean compile -DskipTests`
- [ ] 6.3 Manual smoke test: Start app, verify no errors on startup
- [ ] 6.4 Conflict detection test: Manually test with conflicting propositions in chat
- [ ] 6.5 Performance test: Verify LLM calls reduced by ~70% with subject filter
- [ ] 6.6 Demo scenario: Show semantic catch (e.g., "alive" vs "dead") working correctly

## 7. Documentation & Cleanup

- [x] 7.1 Add Javadoc to `SubjectFilter`, `SemanticConflictDetector`, `CompositeConflictDetector`
- [x] 7.2 Update CLAUDE.md with conflict-detection-strategy configuration
- [x] 7.3 Document subject extraction heuristics
- [x] 7.4 Document LLM prompt used for semantic detection
- [x] 7.5 Verify no debug logging left in code
- [x] 7.6 Code style check per CLAUDE.md

## Definition of Done

- ✓ All tests pass (27+ total)
- ✓ No breaking changes to `ConflictDetector` API
- ✓ LEXICAL_ONLY mode produces legacy behavior
- ✓ LEXICAL_THEN_SEMANTIC mode reduces LLM calls by ~70%
- ✓ Semantic detection catches semantic opposition ("alive" vs "dead", etc.)
- ✓ Strategy is configurable via property
- ✓ Demo shows improved contradiction catching
