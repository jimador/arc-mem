## ADDED Requirements

### Requirement: Canonical documentation surfaces

The repository SHALL use exactly two canonical technical documentation surfaces: `docs/dev` and `openspec`. Developer-facing architecture/design/implementation content SHALL live in `docs/dev`. Normative requirements/design/tasks SHALL live in `openspec`.

#### Scenario: Developer docs stored in canonical surface
- **GIVEN** a new architecture document is created
- **WHEN** documentation location is validated
- **THEN** the document SHALL be located under `docs/dev`

#### Scenario: Normative requirements remain in OpenSpec
- **GIVEN** a new normative requirement is authored
- **WHEN** documentation location is validated
- **THEN** the requirement SHALL be stored under `openspec`

### Requirement: Documentation consolidation migration

The repository SHALL perform a consolidation migration for existing `docs/` content. Each existing document SHALL be classified as one of: `MIGRATE_TO_DOCS_DEV`, `KEEP_IN_OPENSPEC`, or `ARCHIVE_DEPRECATE`. Migrated documents SHALL be moved or merged without losing substantive technical content.

#### Scenario: Existing docs inventory is fully classified
- **GIVEN** the current set of files under `docs/`
- **WHEN** consolidation migration is executed
- **THEN** each file SHALL have exactly one migration classification

#### Scenario: Redundant technical docs are consolidated
- **GIVEN** two or more documents with overlapping architecture or implementation content
- **WHEN** consolidation migration is executed
- **THEN** one canonical `docs/dev` document SHALL remain
- **AND** redundant copies SHALL be archived or replaced with canonical-path stubs

### Requirement: Canonical docs index and ownership

`docs/dev` SHALL include a canonical index defining document structure, owner, and synchronized OpenSpec references for each document.

#### Scenario: Canonical docs index includes required metadata
- **GIVEN** the `docs/dev` index
- **WHEN** index validation is executed
- **THEN** each listed document SHALL include owner and linked OpenSpec spec paths

#### Scenario: Missing index entry fails docs validation
- **GIVEN** a `docs/dev` document with no index entry
- **WHEN** docs validation runs
- **THEN** validation SHALL fail with missing-index-entry details

### Requirement: Required developer documentation set

The system SHALL maintain a concise developer documentation suite for share-ready releases. The suite SHALL include: architecture overview, DICE integration overview (including DICE working-memory layering and trust-overlay semantics), implementation notes, known issues/limitations, and evaluation interpretation guidance.

#### Scenario: Documentation suite completeness check
- **GIVEN** a candidate share-ready release
- **WHEN** documentation completeness is validated
- **THEN** all required documentation sections SHALL be present

#### Scenario: Missing required section fails validation
- **GIVEN** a candidate release missing the DICE integration overview
- **WHEN** documentation completeness is validated
- **THEN** the release SHALL fail share-readiness validation

### Requirement: Documentation scope control

Developer docs SHALL prioritize reviewer-relevant Anchors and DICE integration content. Redundant, exploratory, or off-scope technical narratives SHALL be consolidated or archived during migration.

#### Scenario: Redundant narrative content is not duplicated
- **GIVEN** two documents covering the same technical behavior
- **WHEN** docs validation runs
- **THEN** only one canonical document SHALL remain in `docs/dev`

#### Scenario: Off-scope detail is excluded from canonical docs
- **GIVEN** content not required to understand Anchors implementation or DICE integration fit
- **WHEN** canonical docs are reviewed
- **THEN** that content SHALL be archived/deprecated or moved out of canonical docs

### Requirement: Documentation traceability to code and specs

Each required document SHALL reference relevant OpenSpec capability paths and relevant implementation file paths. Claims in documentation SHALL be traceable to either executed evidence artifacts or cited external sources.

#### Scenario: Architecture doc includes traceable references
- **GIVEN** an updated architecture document
- **WHEN** traceability is checked
- **THEN** the document SHALL reference impacted `openspec/specs/*/spec.md` paths
- **AND** the document SHALL reference key implementation paths under `src/main/java/...`

#### Scenario: Unsupported claim is rejected
- **GIVEN** a documentation claim with no evidence or source reference
- **WHEN** documentation validation runs
- **THEN** the claim SHALL be flagged as unsupported

### Requirement: Dev-doc and OpenSpec sync contract

Changes to OpenSpec capabilities SHALL be synchronized with corresponding `docs/dev` updates in the same share-ready release. Each `docs/dev` document SHALL declare the OpenSpec spec paths it is synchronized with and a sync timestamp.

#### Scenario: OpenSpec capability change requires docs/dev sync
- **GIVEN** a capability requirement changes under `openspec/specs/*/spec.md`
- **WHEN** share-readiness validation runs
- **THEN** linked `docs/dev` sections SHALL be updated in the same release
- **AND** sync metadata SHALL reference the changed spec paths

#### Scenario: Unsynced docs fail validation
- **GIVEN** `docs/dev` content that does not match current OpenSpec capability behavior
- **WHEN** sync validation runs
- **THEN** documentation validation SHALL fail with unsynced-document findings

### Requirement: Known issues and limitations ledger

The documentation suite SHALL include a known issues and limitations ledger with severity, impact, current mitigation, and recommended follow-up. The ledger SHALL differentiate confirmed defects from open research uncertainty.

#### Scenario: Known issue entry contract
- **GIVEN** a newly identified failure mode
- **WHEN** it is added to the known issues ledger
- **THEN** the entry SHALL include severity, impact, mitigation, and follow-up owner

#### Scenario: Research uncertainty classified separately
- **GIVEN** an open technical uncertainty without confirmed defect status
- **WHEN** it is added to documentation
- **THEN** it SHALL be classified as research uncertainty rather than defect

### Requirement: Documentation freshness and release alignment

Documentation intended for sharing SHALL be stamped with a release identifier and generation timestamp. A shared documentation set SHALL NOT mix stale documents from a different release identifier.

#### Scenario: Mixed-release docs rejected
- **GIVEN** a shared docs set containing documents from two release IDs
- **WHEN** documentation validation runs
- **THEN** validation SHALL fail

#### Scenario: Freshness metadata attached
- **GIVEN** a valid share-ready documentation set
- **WHEN** documentation artifacts are prepared
- **THEN** each document SHALL include release ID and timestamp metadata

## Invariants

- **DDS1**: Share-ready documentation sets SHALL include the complete required documentation set.
- **DDS2**: Every normative claim in documentation SHALL have traceable support.
- **DDS3**: Known limitations SHALL be explicit and versioned in every share-ready release.
