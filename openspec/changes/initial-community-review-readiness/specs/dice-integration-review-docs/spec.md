## ADDED Requirements

### Requirement: DICE integration architecture document

The repository SHALL include a canonical DICE integration architecture document under `docs/dev` that explains how Anchors composes with DICE proposition extraction and lifecycle behavior.

#### Scenario: Architecture mapping is explicit
- **GIVEN** the DICE integration architecture document
- **WHEN** a reviewer reads the document
- **THEN** the document SHALL describe proposition extraction, promotion, conflict handling, trust evaluation, and lifecycle transitions as an end-to-end flow

#### Scenario: Code touchpoints are linked
- **GIVEN** the DICE integration architecture document
- **WHEN** implementation references are validated
- **THEN** the document SHALL link relevant code paths for each integration stage

### Requirement: DICE memory layering and trust-overlay clarity

The DICE integration documentation SHALL explicitly describe how Anchors relates to DICE Agent Memory. It SHALL state that Anchors augments DICE memory retrieval with a governed working set for prompt assembly, rather than replacing DICE memory primitives.

#### Scenario: Memory layering is explicit
- **GIVEN** the DICE integration documentation
- **WHEN** memory behavior is reviewed
- **THEN** the docs SHALL describe DICE retrieval surfaces and Anchors working-set injection behavior as separate layers

#### Scenario: Useful low-trust knowledge path is documented
- **GIVEN** knowledge with uncertain or low trust
- **WHEN** trust/authority behavior is reviewed
- **THEN** the docs SHALL describe how such knowledge can remain available with constrained authority and provenance qualifiers

### Requirement: DICE integration boundary and extension-point clarity

The DICE integration documentation SHALL define integration boundaries and extension points. It SHALL explicitly distinguish behavior implemented in this repo from behavior expected in upstream DICE or adjacent systems.

#### Scenario: Boundary ownership is unambiguous
- **GIVEN** a component in the integration flow
- **WHEN** ownership is described in docs
- **THEN** the docs SHALL state whether the behavior is local to this repo or expected upstream in DICE

#### Scenario: Extension points are documented
- **GIVEN** the integration documentation
- **WHEN** extension points are reviewed
- **THEN** each extension point SHALL include purpose, expected contract, and current limitations

### Requirement: DICE-focused review summary

The canonical docs set SHALL include a concise DICE-focused review summary that states intent, current fit, known gaps, and recommended next integration steps without extraneous narrative content.

#### Scenario: Summary is concise and actionable
- **GIVEN** the DICE-focused review summary
- **WHEN** documentation quality is validated
- **THEN** the summary SHALL include intent, fit, known gaps, and next steps
- **AND** the summary SHALL exclude unrelated exploratory detail

## Invariants

- **DIR1**: DICE integration documentation SHALL be sufficient for architecture-level review without requiring ad hoc context from non-canonical docs.
- **DIR2**: DICE integration docs SHALL remain synchronized with relevant OpenSpec capability changes.
