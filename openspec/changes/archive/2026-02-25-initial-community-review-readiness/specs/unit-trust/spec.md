## MODIFIED Requirements

### Requirement: Trust re-evaluation trigger

Trust re-evaluation SHALL occur when an context unit is reinforced, when contradictory evidence is introduced, and when trust-profile context changes. Every re-evaluation SHALL persist a trust decision record containing prior trust score, new trust score, trigger reason, and applied profile.

#### Scenario: Reinforcement triggers trust re-evaluation with audit record
- **GIVEN** an existing context unit that receives reinforcement
- **WHEN** reinforcement processing completes
- **THEN** trust SHALL be re-evaluated
- **AND** an audit record SHALL capture prior score, new score, trigger reason, and profile

#### Scenario: Profile change triggers trust re-evaluation
- **GIVEN** an context unit evaluated under profile A
- **WHEN** the profile changes to profile B for the same context
- **THEN** trust SHALL be re-evaluated under profile B

## Invariants

- **ATR1**: Trust decisions used in claim-grade evidence SHALL be auditable and reproducible.
