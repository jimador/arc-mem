## MODIFIED Requirements

### Requirement: RevisionAwareConflictResolver

**Modifies**: `RevisionAwareConflictResolver` behavior when `HitlOnlyMutationStrategy` is active.

The system SHALL provide a `RevisionAwareConflictResolver` implementing `ConflictResolver` that dispatches on `ConflictType` before delegating to `AuthorityConflictResolver`:

- `CONTRADICTION` or null — delegate to `AuthorityConflictResolver` (existing behavior)
- `WORLD_PROGRESSION` — return `COEXIST` (both facts are valid at different points in time; KEEP_EXISTING would reject the incoming proposition, causing anchor loss when combined with REPLACE outcomes in the same conflict batch)
- `REVISION` — behavior depends on the active `AnchorMutationStrategy`:
  - When the active strategy is `HitlOnlyMutationStrategy`: delegate to `AuthorityConflictResolver` instead of auto-resolving. The conflict type classification SHALL still run for observability (OTEL span attributes recorded), but SHALL NOT trigger automatic supersession.
  - When the active strategy allows `CONFLICT_RESOLVER` source: evaluate authority eligibility; if eligible, return `REPLACE` (routed to `AnchorEngine.supersede()` with `SupersessionReason.USER_REVISION`); if not eligible, delegate to `AuthorityConflictResolver`.

`RevisionAwareConflictResolver` SHALL be wired as the `@Primary` `ConflictResolver` bean when `anchor.revision.enabled = true`. When revision is disabled, `AuthorityConflictResolver` SHALL be the primary bean.

#### Scenario: Revision of PROVISIONAL anchor accepted (permissive strategy)

- **GIVEN** the active mutation strategy allows `CONFLICT_RESOLVER` source
- **AND** a conflict with `conflictType = REVISION` against a PROVISIONAL anchor
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL be `REPLACE`

#### Scenario: Revision of CANON anchor rejected

- **GIVEN** a conflict with `conflictType = REVISION` against a CANON anchor
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL be `KEEP_EXISTING`

#### Scenario: Revision of RELIABLE anchor rejected by default

- **GIVEN** `anchor.revision.reliable-revisable = false` (default)
- **AND** a conflict with `conflictType = REVISION` against a RELIABLE anchor
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL delegate to `AuthorityConflictResolver`

#### Scenario: Revision of RELIABLE anchor accepted when configured (permissive strategy)

- **GIVEN** the active mutation strategy allows `CONFLICT_RESOLVER` source
- **AND** `anchor.revision.reliable-revisable = true`
- **AND** a conflict with `conflictType = REVISION` against a RELIABLE anchor with confidence >= `replaceThreshold`
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL be `REPLACE`

#### Scenario: Revision below confidence threshold delegates to contradiction path

- **GIVEN** a conflict with `conflictType = REVISION` against an UNRELIABLE anchor
- **AND** the conflict confidence is below `revisionConfidenceThreshold` (default 0.75)
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL delegate to `AuthorityConflictResolver`

#### Scenario: WORLD_PROGRESSION returns COEXIST

- **GIVEN** a conflict with `conflictType = WORLD_PROGRESSION`
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL be `COEXIST`

#### Scenario: Batch conflict detection excludes WORLD_PROGRESSION

- **GIVEN** an LLM batch conflict detection response that includes `WORLD_PROGRESSION` entries
- **WHEN** the response is parsed
- **THEN** `WORLD_PROGRESSION` entries SHALL be filtered out before creating `Conflict` objects
- **AND** the batch prompt SHALL NOT list `WORLD_PROGRESSION` as a valid output type

#### Scenario: Null conflictType treated as CONTRADICTION

- **GIVEN** a conflict with `conflictType = null`
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL delegate to `AuthorityConflictResolver`

#### Scenario: REVISION conflict with HITL-only strategy

- **GIVEN** a conflict with `conflictType = REVISION`
- **AND** the active mutation strategy is `HitlOnlyMutationStrategy`
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolver SHALL delegate to `AuthorityConflictResolver`
- **AND** OTEL span attributes `conflict.type` and `conflict.type.reasoning` SHALL still be recorded

#### Scenario: REVISION conflict with permissive strategy

- **GIVEN** a conflict with `conflictType = REVISION`
- **AND** the active mutation strategy allows `CONFLICT_RESOLVER` source
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolver SHALL evaluate authority eligibility and return `REPLACE` if eligible
