## MODIFIED Requirements

### Requirement: Extraction timing and ordering
When `extractionEnabled` is `true`, DICE extraction SHALL run after the DM response is received. Extraction MAY run concurrently with drift evaluation rather than before it. The extracted anchors SHALL still participate in drift evaluation if drift evaluation has not yet completed -- but if drift evaluation completes before extraction, drift scores SHALL be computed without extracted anchors.

#### Scenario: Extraction completes before drift evaluation
- **GIVEN** an ATTACK turn with extraction enabled
- **WHEN** extraction completes before drift evaluation
- **THEN** extracted anchors SHALL be available for drift evaluation scoring

#### Scenario: Drift evaluation completes before extraction
- **GIVEN** an ATTACK turn with extraction enabled
- **WHEN** drift evaluation completes before extraction
- **THEN** drift scores SHALL be computed using only pre-existing anchors
- **AND** extraction results SHALL still be persisted and promoted after joining

### Requirement: Extraction concurrency safety
`SimulationExtractionService.extract()` MUST be safe for concurrent execution with other turn pipeline operations. The extraction method SHALL NOT depend on mutable state that may be modified by drift evaluation. Proposition persistence and promotion SHALL use the contextId for isolation.

#### Scenario: Concurrent extraction and drift evaluation
- **GIVEN** extraction and drift evaluation running in parallel
- **WHEN** both access the anchor repository
- **THEN** extraction SHALL read/write propositions scoped to contextId
- **AND** drift evaluation SHALL read anchors scoped to contextId
- **AND** no data corruption SHALL occur
