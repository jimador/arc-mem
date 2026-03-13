## ADDED Requirements

### Requirement: Promoted ARC-Mem state uses memory-unit terminology

The repository SHALL use `MemoryUnit` terminology for the promoted ARC-Mem object and for public API types whose responsibility is specifically about promoted ARC-Mem state.

#### Scenario: Core promoted model uses MemoryUnit
- **GIVEN** the primary promoted ARC-Mem model in `arcmem-core`
- **WHEN** its public Java type name is reviewed
- **THEN** the type SHALL be named `MemoryUnit`

#### Scenario: Repository and lifecycle types use MemoryUnit terminology
- **GIVEN** a public type centered on promoted ARC-Mem state persistence, lifecycle, or mutation
- **WHEN** its type name is reviewed
- **THEN** it SHALL use the `MemoryUnit` prefix

### Requirement: Extracted or generic semantic-content helpers use semantic-unit terminology

The repository SHALL use `SemanticUnit` terminology for helpers that operate on extracted semantic content or semantic constraints rather than on the promoted ARC-Mem model specifically.

#### Scenario: Promotion pipeline uses SemanticUnit terminology
- **GIVEN** the promotion pipeline entry point for extracted semantic content
- **WHEN** its public Java type name is reviewed
- **THEN** the type SHALL use the `SemanticUnit` prefix rather than the generic `Unit` prefix

#### Scenario: Semantic constraints use SemanticUnit terminology
- **GIVEN** a public type that constrains or indexes semantic-content fragments during prompt assembly
- **WHEN** its type name is reviewed
- **THEN** the type SHALL use the `SemanticUnit` prefix rather than the generic `Unit` prefix

### Requirement: User-facing descriptions distinguish semantic units from memory units

Documentation, prompts, and UI labels SHALL distinguish between extracted semantic units and promoted memory units.

#### Scenario: Architecture docs describe the pipeline with both terms
- **WHEN** architecture documentation describes the extraction-to-memory pipeline
- **THEN** it SHALL describe semantic units as extracted content
- **AND** it SHALL describe memory units as promoted ARC-Mem-managed objects
