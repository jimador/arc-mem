## MODIFIED Requirements

### Requirement: Hook evaluation order

Lifecycle hook evaluation SHALL run in deterministic order before state mutation: invariant checks, trust/authority policy checks, conflict policy checks, then mutation execution. A failed hook SHALL prevent mutation and SHALL emit a structured violation reason.

#### Scenario: Invariant failure blocks mutation
- **GIVEN** a pending lifecycle mutation with failing invariant check
- **WHEN** hook evaluation executes
- **THEN** mutation SHALL be blocked
- **AND** a structured violation reason SHALL be emitted

#### Scenario: Deterministic hook order across repeated runs
- **GIVEN** the same lifecycle input across repeated deterministic runs
- **WHEN** hook evaluation executes
- **THEN** hook stages SHALL execute in identical order

## Invariants

- **ALC1**: Lifecycle mutations SHALL NOT bypass invariant and policy hook evaluation.
- **ALC2**: Hook ordering SHALL be deterministic for reproducible evidence.
