## ADDED Requirements

### Requirement: Batched duplicate detection
The system SHALL provide a `batchIsDuplicate(String contextId, List<String> candidateTexts)` method on `DuplicateDetector` that evaluates multiple candidates in a single LLM call. The method SHALL return a `Map<String, Boolean>` mapping each candidate text to its duplicate status. The fast-path (normalized string matching) SHALL still run per-candidate before batching LLM fallback candidates.

#### Scenario: Mixed fast-path and LLM-path candidates
- **GIVEN** 5 candidate propositions where 2 match existing anchors via fast-path
- **WHEN** `batchIsDuplicate()` is called
- **THEN** the 2 fast-path matches SHALL be resolved without LLM invocation
- **AND** the remaining 3 candidates SHALL be evaluated in a single batched LLM call
- **AND** the result map SHALL contain entries for all 5 candidates

#### Scenario: All candidates resolved by fast-path
- **GIVEN** all candidate propositions match existing anchors via normalized string comparison
- **WHEN** `batchIsDuplicate()` is called
- **THEN** no LLM call SHALL be made
- **AND** all candidates SHALL be marked as duplicates

#### Scenario: Empty candidate list
- **WHEN** `batchIsDuplicate()` is called with an empty list
- **THEN** an empty map SHALL be returned without any LLM call

### Requirement: Batched conflict detection
The system SHALL provide a `batchDetectConflicts(List<String> candidateTexts, List<Anchor> existingAnchors)` method on the conflict detector that evaluates multiple candidates against existing anchors in a single LLM call. The method SHALL return a `Map<String, List<Anchor>>` mapping each candidate to its list of conflicting anchors (empty list if no conflicts).

#### Scenario: Batch conflict evaluation
- **GIVEN** 4 candidate propositions and 10 existing anchors
- **WHEN** `batchDetectConflicts()` is called
- **THEN** all candidates SHALL be evaluated against all anchors in a single LLM call
- **AND** the result SHALL map each candidate to its conflicting anchors

#### Scenario: No conflicts found
- **GIVEN** candidate propositions that do not conflict with any existing anchors
- **WHEN** `batchDetectConflicts()` is called
- **THEN** all candidates SHALL map to empty conflict lists

### Requirement: Batched trust scoring
The system SHALL provide a `batchEvaluate(List<TrustContext> contexts)` method on the trust pipeline that scores multiple propositions in a single pass. Non-LLM trust signals (SourceAuthoritySignal, ExtractionConfidenceSignal, GraphConsistencySignal) SHALL be computed per-proposition without batching. LLM-based signals, if any, SHALL be batched.

#### Scenario: Batch trust evaluation
- **GIVEN** 4 candidate propositions with extraction metadata
- **WHEN** `batchEvaluate()` is called
- **THEN** each proposition SHALL receive trust scores from all configured signals
- **AND** non-LLM signals SHALL be computed independently per proposition
- **AND** the result SHALL be a `Map<String, TrustScore>` mapping candidate text to aggregate score

### Requirement: Batched promotion funnel
`AnchorPromoter` SHALL provide a `batchEvaluateAndPromote(String contextId, List<Proposition> candidates)` method that processes all candidates through the gate funnel using batched operations. The gate sequence (confidence -> dedup -> conflict -> trust -> promote) SHALL be preserved but each gate SHALL operate on the full batch.

#### Scenario: Full batch promotion pipeline
- **GIVEN** 6 extracted propositions
- **WHEN** `batchEvaluateAndPromote()` is called
- **THEN** confidence filtering SHALL remove low-confidence candidates from the batch
- **AND** batched dedup SHALL remove duplicates from the remaining batch
- **AND** batched conflict detection SHALL evaluate remaining candidates
- **AND** batched trust scoring SHALL score remaining candidates
- **AND** surviving candidates SHALL be promoted individually via `AnchorEngine.promote()`

#### Scenario: All candidates filtered at confidence gate
- **GIVEN** all propositions have confidence below threshold
- **WHEN** `batchEvaluateAndPromote()` is called
- **THEN** no LLM calls SHALL be made for dedup, conflict, or trust gates

## Invariants
- I1: Gate ordering MUST be preserved: confidence -> dedup -> conflict -> trust -> promote
- I2: Batch size MUST NOT exceed configured maximum (`dice-anchors.promotion.max-batch-size`, default 20)
- I3: If batch LLM call fails, the system SHALL fall back to individual per-proposition calls
- I4: Promotion itself (AnchorEngine.promote) SHALL remain individual per proposition -- only evaluation gates are batched
