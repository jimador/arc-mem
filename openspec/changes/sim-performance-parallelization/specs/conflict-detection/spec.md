## ADDED Requirements

### Requirement: Batch conflict detection API
The `ConflictDetector` interface SHALL add a `batchDetect(List<String> candidateTexts, List<Anchor> existingAnchors)` method that evaluates multiple candidates in a single operation. The default implementation SHALL delegate to individual `detect()` calls. `LlmConflictDetector` SHALL override with a batched LLM prompt. `NegationConflictDetector` SHALL override with parallel lexical evaluation.

#### Scenario: LLM batch conflict detection
- **GIVEN** the conflict detection strategy is `llm`
- **WHEN** `batchDetect()` is called with 5 candidates and 10 anchors
- **THEN** a single LLM call SHALL evaluate all 5 candidates against all 10 anchors
- **AND** the result SHALL map each candidate to its list of conflicting anchors

#### Scenario: Lexical batch conflict detection
- **GIVEN** the conflict detection strategy is `lexical`
- **WHEN** `batchDetect()` is called with 5 candidates
- **THEN** each candidate SHALL be evaluated via negation pattern matching (no LLM call)
- **AND** evaluations MAY run in parallel using virtual threads

#### Scenario: Fallback on batch failure
- **GIVEN** a batched LLM call fails
- **WHEN** the error is caught
- **THEN** the system SHALL fall back to individual `detect()` calls per candidate
