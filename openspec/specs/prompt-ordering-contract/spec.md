# Prompt Ordering Contract Specification

## ADDED Requirements

### Requirement: Compliance block precedes persona block

The prompt assembly process SHALL render the compliance/memory-unit block before persona or task instruction blocks. The compliance block MUST appear at `indexOf(compliance) < indexOf(persona)` in the assembled prompt text.

#### Scenario: Compliance block appears first
- **WHEN** prompt is assembled with memory units and persona instructions
- **THEN** the string "CANON Facts" (or authority tier header) appears before "You are a helpful assistant"
- **AND** compliance block is rendered in full before persona section begins

#### Scenario: Test validates both DEFAULT and TIERED policies
- **WHEN** prompt is assembled with DEFAULT policy
- **THEN** compliance block still precedes persona
- **AND** WHEN assembled with TIERED policy, ordering is maintained

### Requirement: Authority tiers appear in correct order

When using TIERED compliance policy, authority blocks MUST appear in order of authority strength. CANON MUST precede RELIABLE, RELIABLE MUST precede UNRELIABLE, UNRELIABLE MUST precede PROVISIONAL.

#### Scenario: Authority blocks are ordered by strength
- **WHEN** prompt contains memory units of multiple authorities
- **THEN** CANON block appears before RELIABLE block
- **AND** RELIABLE appears before UNRELIABLE
- **AND** UNRELIABLE appears before PROVISIONAL

#### Scenario: Empty tiers are skipped without breaking order
- **WHEN** prompt has CANON and PROVISIONAL memory units (no RELIABLE/UNRELIABLE)
- **THEN** CANON block appears before PROVISIONAL block
- **AND** skipped tiers do not create empty sections

## Invariants

- **I1**: `indexOf(compliance) < indexOf(persona)` MUST always be true
- **I2**: When TIERED, `indexOf(CANON) < indexOf(RELIABLE) < indexOf(UNRELIABLE) < indexOf(PROVISIONAL)`
- **I3**: Ordering MUST be consistent across all prompt assembly calls
