## ADDED Requirements

### Requirement: Scene-setting turn 0 with initial extraction

Before the main turn loop begins, `SimulationService` SHALL execute a scene-setting turn 0 when the scenario has a non-blank `setting` and extraction is enabled. This turn SHALL use `TurnType.ESTABLISH` with a fixed prompt requesting the DM to narrate the scene. The DM response SHALL be processed through the full `executeTurnFull` pipeline, including DICE extraction and anchor promotion. The DM narration and the scene-setting prompt SHALL be added to conversation history so subsequent turns have context. The resulting anchor state SHALL be carried into the main turn loop as `previousAnchorState`.

This mirrors a real campaign where the DM introduces the scene before players act, giving the anchor framework initial propositions to accumulate and defend.

#### Scenario: Scene-setting extracts initial propositions

- **GIVEN** a scenario with a setting containing factual details and extraction enabled
- **WHEN** the simulation starts
- **THEN** turn 0 SHALL execute before turn 1, the DM SHALL narrate the setting, and DICE extraction SHALL run on the DM response to produce initial propositions

#### Scenario: Scene-setting skipped when no setting

- **GIVEN** a scenario with a null or blank setting
- **WHEN** the simulation starts
- **THEN** turn 0 SHALL be skipped and the main turn loop SHALL begin at turn 1 with no prior conversation history

#### Scenario: Scene-setting skipped when extraction disabled

- **GIVEN** a scenario with extraction disabled
- **WHEN** the simulation starts
- **THEN** turn 0 SHALL be skipped (there is no value in narrating the scene if propositions cannot be extracted)

#### Scenario: Anchors from scene-setting available in turn 1

- **GIVEN** a scene-setting turn 0 that extracts and promotes 3 propositions to anchors
- **WHEN** turn 1 begins with injection enabled
- **THEN** the 3 anchors from turn 0 SHALL be available for injection into the DM's system prompt

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
