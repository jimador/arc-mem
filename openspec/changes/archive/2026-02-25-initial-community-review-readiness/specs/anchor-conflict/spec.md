## MODIFIED Requirements

### Requirement: DICE conflict detection strategy

The conflict detection strategy SHALL support lexical, LLM, and hybrid execution modes while enforcing share-grade safety semantics. Parse failures in conflict detection SHALL NOT default to no-conflict acceptance for claim-grade runs. Instead, parse failures SHALL emit explicit degraded outcomes and SHALL be surfaced in run artifacts and reports.

#### Scenario: Parse failure in claim-grade run is degraded, not accepted
- **GIVEN** a claim-grade run and an LLM conflict parse failure
- **WHEN** conflict evaluation is finalized
- **THEN** the proposition SHALL be marked degraded-review-required
- **AND** the system SHALL NOT auto-accept it as no-conflict

#### Scenario: Degraded conflict decision is visible in report artifacts
- **GIVEN** a run containing one degraded conflict decision
- **WHEN** benchmark and resilience reports are generated
- **THEN** degraded conflict counts SHALL be included in report metadata

## Invariants

- **ACON1**: Claim-grade evaluation SHALL NOT silently fail-open on conflict parser errors.
