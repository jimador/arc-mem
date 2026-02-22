## MODIFIED Requirements

### Requirement: Batch conflict detection API

The `ConflictDetector` interface SHALL add a `batchDetect(List<String> candidateTexts, List<Anchor> existingAnchors)` method that evaluates multiple candidates in a single operation. The default implementation SHALL delegate to individual `detect()` calls. `LlmConflictDetector` SHALL override with a batched LLM prompt. `NegationConflictDetector` SHALL override with parallel lexical evaluation.

`NegationConflictDetector` SHALL accept the overlap threshold from `ConflictConfig.negationOverlapThreshold()` via constructor injection instead of using the hardcoded value `0.5`. The injected threshold SHALL be used for both individual `detect()` and batch `batchDetect()` calls.

`LlmConflictDetector` SHALL accept the confidence score from `ConflictConfig.llmConfidence()` via constructor injection instead of using the hardcoded constant `LLM_CONFLICT_CONFIDENCE = 0.9`. The injected confidence SHALL be assigned to all LLM-detected conflicts in both individual and batch detection paths.

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

#### Scenario: Configurable negation overlap threshold

- **GIVEN** `dice-anchors.conflict.negation-overlap-threshold` is set to `0.7`
- **WHEN** `NegationConflictDetector.detect()` is called
- **THEN** the detector SHALL use `0.7` as the Jaccard similarity threshold for negation conflict detection
- **AND** the hardcoded value `0.5` SHALL NOT be referenced

#### Scenario: Configurable LLM confidence

- **GIVEN** `dice-anchors.conflict.llm-confidence` is set to `0.85`
- **WHEN** `LlmConflictDetector` detects a conflict
- **THEN** the returned `Conflict` object SHALL have confidence `0.85`
- **AND** the hardcoded constant `0.9` SHALL NOT be referenced
