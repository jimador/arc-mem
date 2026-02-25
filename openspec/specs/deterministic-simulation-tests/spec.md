# Deterministic Simulation Tests Specification

## ADDED Requirements

### Requirement: Deterministic scenario execution with canned responses

The simulation executor SHALL support deterministic mode where turn responses are loaded from YAML instead of called via LLM. When scenario type is "deterministic", the executor MUST return the canned response for each turn number without invoking the ChatModel.

#### Scenario: Canned response returned for deterministic turn
- **WHEN** scenario type is "deterministic" and turn 1 is executed
- **THEN** the canned-response for turn 1 is retrieved from scenario YAML
- **AND** ChatModel is NOT called
- **AND** the canned response is returned as the LLM output

#### Scenario: Live mode still calls ChatModel
- **WHEN** scenario type is "live"
- **THEN** ChatModel is invoked normally
- **AND** canned-responses block (if present) is ignored

### Requirement: Expected metrics validation

Each deterministic scenario SHALL define expected-final-metrics. After scenario execution, the test MUST validate that actual metrics meet expected values using string-based operators (>, <, ==, >=, <=).

#### Scenario: Metrics validation passes
- **WHEN** scenario completes with actual `fact-survival-rate: 0.96`
- **AND** expected metric is `fact-survival-rate: ">0.95"`
- **THEN** test assertion passes

#### Scenario: Metrics validation fails with clear message
- **WHEN** actual `contradiction-count: 2`
- **AND** expected metric is `contradiction-count: "== 0"`
- **THEN** test fails with message "Expected contradiction-count == 0, got 2"

### Requirement: Turn-based canned response lookup

The canned-responses block SHALL be indexed by turn number. The executor SHALL look up `canned-responses[turn-number]` to retrieve the prepared response for each turn.

#### Scenario: Turn 1 response differs from turn 2
- **WHEN** executing turn 1, executor looks up `canned-responses[1]`
- **THEN** gets the turn 1 response
- **AND** when executing turn 2, looks up `canned-responses[2]` and gets different response

### Requirement: YAML scenario format backward compatibility

Existing live scenarios MUST continue to work unchanged. The deterministic structure MUST be optional and backward compatible with live scenario format.

#### Scenario: Live scenario without canned-responses works
- **WHEN** scenario has no `type: deterministic` and no canned-responses block
- **THEN** executor treats it as live mode
- **AND** calls ChatModel normally

#### Scenario: Scenario can be converted to deterministic
- **WHEN** canned-responses block is added to existing scenario
- **THEN** the scenario can be run in deterministic mode
- **AND** existing live test infrastructure remains unchanged

### Requirement: Deterministic test class structure

A deterministic simulation test class SHALL load scenarios from `deterministic-sim.yaml`, execute each scenario, and assert expected metrics are met. Each test method SHALL correspond to one scenario.

#### Scenario: Test runs scenario and validates metrics
- **WHEN** test executes "resist-direct-negation" scenario
- **THEN** all turns are executed deterministically
- **AND** final metrics are validated against expected values

#### Scenario: Test failure includes scenario context
- **WHEN** test fails
- **THEN** error message includes scenario name, failed metric, expected vs actual

## Invariants

- **I1**: Deterministic mode MUST NOT call ChatModel
- **I2**: Live mode MUST call ChatModel (ignore canned-responses if present)
- **I3**: Turn-number lookup MUST match exactly; missing turns MUST fail fast
- **I4**: Each scenario MUST define expected-final-metrics for validation
- **I5**: Metrics comparison MUST use string operators (>, <, ==, etc.)
- **DST1**: Deterministic tests SHALL act as a hard guardrail for claim-grade reproducibility.

## Added Requirements (initial-community-review-readiness)

### Requirement: Expected metrics validation (reproducibility)

Deterministic simulation tests SHALL validate metric reproducibility, not just point correctness. For a fixed deterministic scenario and fixed model/test harness configuration, repeated runs SHALL produce identical primary metrics and identical contradiction verdict sequences.

#### Scenario: Repeated deterministic runs are identical
- **GIVEN** a deterministic scenario with fixed harness configuration
- **WHEN** the test executes the scenario multiple times
- **THEN** primary metrics SHALL be identical across runs
- **AND** contradiction verdict sequences SHALL be identical across runs

#### Scenario: Drift in deterministic output fails regression test
- **GIVEN** a deterministic baseline snapshot
- **WHEN** a new run deviates in primary metrics or verdict sequence
- **THEN** the deterministic regression test SHALL fail
