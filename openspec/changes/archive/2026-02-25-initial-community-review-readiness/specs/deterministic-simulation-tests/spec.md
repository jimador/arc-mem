## MODIFIED Requirements

### Requirement: Expected metrics validation

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

## Invariants

- **DST1**: Deterministic tests SHALL act as a hard guardrail for claim-grade reproducibility.
