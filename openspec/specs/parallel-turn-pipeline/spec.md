## ADDED Requirements

### Requirement: Parallel post-response execution
After the DM response LLM call completes, `SimulationTurnExecutor` SHALL fork into parallel branches using `StructuredTaskScope`:
- **Branch A**: Drift evaluation (only on ATTACK phase turns)
- **Branch B**: DICE extraction + batched promotion

Both branches MUST complete before the turn proceeds to reinforcement, dormancy lifecycle, and state diffing.

#### Scenario: ATTACK turn with extraction enabled
- **GIVEN** an ATTACK phase turn with `extractionEnabled = true`
- **WHEN** the DM response is received
- **THEN** drift evaluation and extraction+promotion SHALL execute concurrently
- **AND** the turn SHALL wait for both branches to complete before proceeding

#### Scenario: ESTABLISH turn with extraction enabled
- **GIVEN** an ESTABLISH phase turn (no drift evaluation needed)
- **WHEN** the DM response is received
- **THEN** only extraction+promotion SHALL execute (no forking needed)

#### Scenario: Turn with extraction disabled
- **GIVEN** a turn with `extractionEnabled = false`
- **WHEN** the DM response is received
- **THEN** only drift evaluation SHALL execute (if ATTACK phase), with no forking

#### Scenario: Branch failure handling
- **GIVEN** parallel branches are executing
- **WHEN** one branch fails with an exception
- **THEN** the other branch SHALL be cancelled via `ShutdownOnFailure`
- **AND** the exception SHALL propagate to the turn executor
- **AND** the turn SHALL be marked as failed in the trace

### Requirement: Post-fork sequential operations
Reinforcement, dormancy lifecycle, memory unit state diffing, and compaction SHALL remain sequential and MUST execute only after all parallel branches have joined.

#### Scenario: Sequential post-join operations
- **GIVEN** parallel branches have completed successfully
- **WHEN** the turn pipeline resumes after join
- **THEN** reinforcement SHALL execute first
- **AND** dormancy lifecycle SHALL execute after reinforcement
- **AND** state diffing SHALL execute after dormancy lifecycle

## Invariants
- I1: The DM response LLM call MUST complete before any parallel branches start
- I2: All parallel branches MUST complete before sequential post-join operations begin
- I3: Turn-level state (ContextTrace) MUST be assembled from both branch results after join
