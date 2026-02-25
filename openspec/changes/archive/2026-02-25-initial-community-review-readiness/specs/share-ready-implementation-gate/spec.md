## ADDED Requirements

### Requirement: Share readiness gate evaluation

The project SHALL maintain a share-readiness checklist in `docs/dev/README.md`. The checklist SHALL enumerate required implementation safety checks, evidence/provenance checks, and documentation completeness checks. Before community sharing, a maintainer SHALL run through the checklist and record pass/fail for each item.

#### Scenario: Checklist covers required check categories
- **GIVEN** the share-readiness checklist in `docs/dev/README.md`
- **WHEN** a maintainer reviews it before sharing
- **THEN** the checklist SHALL include: implementation safety (conflict handling, lifecycle correctness), evidence/provenance (report accuracy, model identification), and documentation completeness (migration-tracker closed, docs/dev synced)

#### Scenario: Sharing requires checklist completion
- **GIVEN** a candidate build for community sharing
- **WHEN** the maintainer has not run through the share-readiness checklist
- **THEN** the build SHALL NOT be shared externally

### Requirement: Documentation sync validation

Before sharing, all legacy `docs/` files SHALL be classified in `docs/dev/migration-tracker.md`. Unclassified files under `docs/` SHALL block share-ready signoff.

#### Scenario: Migration tracker covers all legacy docs
- **GIVEN** the share-readiness checklist is being evaluated
- **WHEN** the documentation sync check is run
- **THEN** every file under `docs/` (excluding `docs/dev/`) SHALL appear in migration-tracker.md with a classification

### Requirement: Gate report contract

Completion of the share-readiness checklist SHALL be recorded as a brief summary note (inline in `docs/dev/README.md` or as a git-tagged commit message) including: date, what was checked, and outcome for each check.

## Invariants

- **SRG1**: Community sharing SHALL NOT proceed when any mandatory checklist item is unresolved.
- **SRG3**: Checklist output SHALL include actionable failure details so a maintainer can resolve and re-check.
