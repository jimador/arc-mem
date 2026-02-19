## ADDED Requirements

### Requirement: Batch duplicate detection API
`DuplicateDetector` SHALL add a `batchIsDuplicate(String contextId, List<String> candidateTexts)` method. The fast-path (NormalizedStringDuplicateDetector) SHALL run per-candidate first. Candidates not resolved by fast-path SHALL be grouped and evaluated in a single batched LLM call. The method SHALL return `Map<String, Boolean>`.

#### Scenario: FAST_THEN_LLM batch strategy
- **GIVEN** dedup strategy is FAST_THEN_LLM
- **WHEN** `batchIsDuplicate()` is called with 5 candidates where 2 match via fast-path
- **THEN** 2 candidates SHALL be marked duplicate without LLM invocation
- **AND** remaining 3 SHALL be evaluated in one batched LLM call
- **AND** the result SHALL contain all 5 entries

#### Scenario: FAST_ONLY batch strategy
- **GIVEN** dedup strategy is FAST_ONLY
- **WHEN** `batchIsDuplicate()` is called
- **THEN** only fast-path normalization SHALL be used
- **AND** no LLM call SHALL be made regardless of fast-path results

#### Scenario: LLM_ONLY batch strategy
- **GIVEN** dedup strategy is LLM_ONLY
- **WHEN** `batchIsDuplicate()` is called with candidates
- **THEN** all candidates SHALL be evaluated in a single batched LLM call
- **AND** no fast-path check SHALL be performed

## MODIFIED Requirements

### Requirement: Backward-compatible single-item API
The existing `isDuplicate(String contextId, String candidateText)` method SHALL remain available and unchanged. It SHALL delegate to the batch method with a single-element list for consistency.

#### Scenario: Single-item dedup via legacy API
- **WHEN** `isDuplicate(contextId, candidateText)` is called
- **THEN** behavior SHALL be identical to pre-batch implementation
- **AND** the method SHALL return a boolean
