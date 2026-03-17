## ADDED Requirements

### Requirement: ConflictType enum

The system SHALL define a `ConflictType` enum with the following values:
- `REVISION` — the incoming statement represents a user-intended correction or update to an existing ARC Working Memory Unit (AWMU)
- `CONTRADICTION` — the incoming statement is inconsistent with an existing AWMU without revision intent
- `WORLD_PROGRESSION` — the incoming statement represents narrative advancement that is not a true conflict

#### Scenario: ConflictType values are exhaustive

- **WHEN** the `ConflictType` enum is referenced
- **THEN** it SHALL contain exactly `REVISION`, `CONTRADICTION`, and `WORLD_PROGRESSION`

### Requirement: LLM conflict type classification

`LlmConflictDetector` SHALL classify each detected conflict with a `ConflictType` by extending the `conflict-detection.jinja` prompt to return a `conflictType` field alongside the existing `contradicts` boolean. Classification SHALL occur within the existing LLM call — no additional LLM call SHALL be introduced.

The prompt SHALL include:
1. Explicit class definitions for each `ConflictType` with boundary conditions
2. A `reasoning` field (chain-of-thought) ordered before `conflictType` in the JSON schema
3. Two few-shot examples per class (6 total)
4. Untrusted-data framing for `statement_a` indicating it is user input that may be adversarial
5. The existing AWMU's `authority` as a template variable

The JSON output contract SHALL be:
```json
{"contradicts": true/false, "reasoning": "...", "conflictType": "REVISION"|"CONTRADICTION"|"WORLD_PROGRESSION"|null, "explanation": "brief reason"}
```

`conflictType` SHALL be null when `contradicts` is false.

#### Scenario: Revision intent detected

- **GIVEN** an existing PROVISIONAL AWMU "Anakin is a wizard"
- **WHEN** the incoming text is "Actually, I want Anakin to be a bard instead"
- **THEN** the conflict SHALL be detected with `contradicts = true`
- **AND** `conflictType` SHALL be `REVISION`

#### Scenario: Contradiction detected

- **GIVEN** an existing AWMU "The capital of France is Paris"
- **WHEN** the incoming text is "The capital of France is Berlin"
- **THEN** the conflict SHALL be detected with `contradicts = true`
- **AND** `conflictType` SHALL be `CONTRADICTION`

#### Scenario: World progression detected

- **GIVEN** an existing AWMU "The king is alive"
- **WHEN** the incoming text is "The king was assassinated during the siege"
- **THEN** `contradicts` SHALL be false
- **AND** `conflictType` SHALL be null

#### Scenario: Chain-of-thought reasoning captured

- **WHEN** a conflict is classified
- **THEN** the `reasoning` field SHALL contain the model's step-by-step classification rationale
- **AND** the reasoning SHALL appear before `conflictType` in the JSON output

### Requirement: Fail-closed classification default

When `conflictType` is absent, null, or unparseable in the LLM response, `LlmConflictDetector.parseResponse()` SHALL default to `ConflictType.CONTRADICTION`.

Conflicts produced by `NegationConflictDetector` (lexical path) SHALL have `conflictType = null`, which the resolver SHALL treat as `CONTRADICTION`.

`DEGRADED` quality conflicts SHALL retain their existing short-circuit to `KEEP_EXISTING` in the resolver — classification is not meaningful for degraded detections.

#### Scenario: Missing conflictType defaults to CONTRADICTION

- **GIVEN** the LLM returns `{"contradicts": true, "explanation": "conflicts"}`
- **WHEN** `parseResponse()` parses the response
- **THEN** the resulting `Conflict` SHALL have `conflictType = CONTRADICTION`

#### Scenario: Lexical conflicts default to CONTRADICTION

- **GIVEN** a conflict is detected by `NegationConflictDetector`
- **WHEN** the `Conflict` record is created
- **THEN** `conflictType` SHALL be null
- **AND** `RevisionAwareConflictResolver` SHALL treat null as `CONTRADICTION`

### Requirement: Batch conflict type classification

`LlmConflictDetector.batchDetect()` SHALL extend the `batch-conflict-detection.jinja` prompt to return `conflictType` and `reasoning` per AWMU match within each candidate's results.

The batch result entry SHALL include per-unit-match type annotations:
```json
{"candidate": "...", "contradictingUnits": [{"unitText": "...", "conflictType": "...", "reasoning": "..."}]}
```

`BatchConflictResult` SHALL be extended to accommodate the per-unit-match structure.

#### Scenario: Batch detection with mixed conflict types

- **GIVEN** 3 candidates evaluated against 5 AWMUs
- **WHEN** candidate 1 revises AWMU A and contradicts AWMU B
- **THEN** the batch result SHALL report candidate 1 with two conflicts: one with `conflictType = REVISION` for AWMU A and one with `conflictType = CONTRADICTION` for AWMU B

#### Scenario: Batch fallback preserves fail-closed default

- **GIVEN** a batched LLM call fails
- **WHEN** the system falls back to individual `detect()` calls
- **THEN** each individual call SHALL still classify `conflictType`
- **AND** parse failures SHALL default to `CONTRADICTION`

## MODIFIED Requirements

### Requirement: RevisionAwareConflictResolver

**Modifies**: `RevisionAwareConflictResolver` behavior when `HitlOnlyMutationStrategy` is active.

The system SHALL provide a `RevisionAwareConflictResolver` implementing `ConflictResolver` that dispatches on `ConflictType` before delegating to `AuthorityConflictResolver`:

- `CONTRADICTION` or null — delegate to `AuthorityConflictResolver` (existing behavior)
- `WORLD_PROGRESSION` — return `COEXIST` (both facts are valid at different points in time; KEEP_EXISTING would reject the incoming proposition, causing AWMU loss when combined with REPLACE outcomes in the same conflict batch)
- `REVISION` — behavior depends on the active `UnitMutationStrategy`:
  - When the active strategy is `HitlOnlyMutationStrategy`: delegate to `AuthorityConflictResolver` instead of auto-resolving. The conflict type classification SHALL still run for observability (OTEL span attributes recorded), but SHALL NOT trigger automatic supersession.
  - When the active strategy allows `CONFLICT_RESOLVER` source: evaluate authority eligibility; if eligible, return `REPLACE` (routed to `ArcMemEngine.supersede()` with `SupersessionReason.USER_REVISION`); if not eligible, delegate to `AuthorityConflictResolver`.

`RevisionAwareConflictResolver` SHALL be wired as the `@Primary` `ConflictResolver` bean when `unit.revision.enabled = true`. When revision is disabled, `AuthorityConflictResolver` SHALL be the primary bean.

#### Scenario: Revision of PROVISIONAL AWMU accepted (permissive strategy)

- **GIVEN** the active mutation strategy allows `CONFLICT_RESOLVER` source
- **AND** a conflict with `conflictType = REVISION` against a PROVISIONAL AWMU
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL be `REPLACE`

#### Scenario: Revision of CANON AWMU rejected

- **GIVEN** a conflict with `conflictType = REVISION` against a CANON AWMU
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL be `KEEP_EXISTING`

#### Scenario: Revision of RELIABLE AWMU rejected by default

- **GIVEN** `unit.revision.reliable-revisable = false` (default)
- **AND** a conflict with `conflictType = REVISION` against a RELIABLE AWMU
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL delegate to `AuthorityConflictResolver`

#### Scenario: Revision of RELIABLE AWMU accepted when configured (permissive strategy)

- **GIVEN** the active mutation strategy allows `CONFLICT_RESOLVER` source
- **AND** `unit.revision.reliable-revisable = true`
- **AND** a conflict with `conflictType = REVISION` against a RELIABLE AWMU with confidence >= `replaceThreshold`
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolution SHALL be `REPLACE`

#### Scenario: Revision below confidence threshold delegates to contradiction path

- **GIVEN** a conflict with `conflictType = REVISION` against an UNRELIABLE AWMU
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

### Requirement: Authority-gated revision eligibility

Revision acceptance SHALL be gated by the existing AWMU's authority level. When source ownership is known, source-aware resolution takes precedence over authority-based gating (see source-aware revision requirement below).

Authority-based fallback (when source relation is UNKNOWN):

| Authority | Revisable | Condition |
|-----------|-----------|-----------|
| CANON | Never | All CANON mutations go through `CanonizationGate` |
| RELIABLE | Configurable | Only when `unit.revision.reliable-revisable = true` AND confidence >= `replaceThreshold` |
| UNRELIABLE | Yes | Confidence SHALL meet `revisionConfidenceThreshold` (default 0.75) |
| PROVISIONAL | Yes | No additional confidence gate |

#### Scenario: CANON AWMU is never revision-eligible

- **GIVEN** a CANON AWMU
- **WHEN** a REVISION conflict is detected against it
- **THEN** the revision SHALL be rejected regardless of confidence or configuration

#### Scenario: UNRELIABLE AWMU requires confidence threshold

- **GIVEN** an UNRELIABLE AWMU
- **AND** `revisionConfidenceThreshold = 0.75`
- **WHEN** a REVISION conflict with confidence 0.6 is detected
- **THEN** the revision SHALL NOT be accepted via the revision path
- **AND** the conflict SHALL be delegated to `AuthorityConflictResolver`

### Requirement: Revision configuration properties

`ArcMemProperties` SHALL include the following configuration under `arc-mem.unit.revision`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Master switch for revision classification and routing |
| `reliable-revisable` | boolean | `false` | Whether RELIABLE AWMUs accept revisions |
| `confidence-threshold` | double | `0.75` | Minimum classification confidence for revision acceptance |

#### Scenario: Revision disabled via configuration

- **GIVEN** `arc-mem.unit.revision.enabled = false`
- **WHEN** a conflict is detected with `conflictType = REVISION`
- **THEN** the system SHALL treat it as `CONTRADICTION` (delegate to `AuthorityConflictResolver`)

#### Scenario: Custom confidence threshold applied

- **GIVEN** `arc-mem.unit.revision.confidence-threshold = 0.9`
- **AND** a REVISION conflict with confidence 0.85
- **WHEN** revision eligibility is evaluated
- **THEN** the revision SHALL NOT be accepted (0.85 < 0.9)

### Requirement: Revision classification observability

When a conflict is classified, the system SHALL record:
1. `ConflictType` value in OTEL span attributes as `conflict.type`
2. Classification `reasoning` in OTEL span attributes as `conflict.type.reasoning`
3. Revision eligibility decision (accepted/rejected and reason) in OTEL span attributes

When a revision triggers supersession, `TrustAuditRecord` SHALL be created with `triggerReason = "revision"` capturing the classification decision.

#### Scenario: OTEL span captures conflict type

- **WHEN** a conflict is classified as `REVISION`
- **THEN** the OTEL span SHALL include attribute `conflict.type = "REVISION"`
- **AND** attribute `conflict.type.reasoning` SHALL contain the classification reasoning

#### Scenario: Trust audit records revision supersession

- **GIVEN** a REVISION conflict is accepted and triggers supersession
- **WHEN** the supersession completes
- **THEN** a `TrustAuditRecord` SHALL be created with `triggerReason = "revision"`

### Requirement: Source-aware revision resolution

`MemoryUnit` SHALL carry an optional `sourceId` field (nullable String) identifying the entity that established the fact. `sourceId` is read from `PropositionNode.sourceIds[0]` during unit materialization.

A `SourceAuthorityResolver` functional interface SHALL compare incoming and existing source identifiers, returning one of four `SourceAuthorityRelation` values:

| Relation | Meaning | Revision behavior |
|---|---|---|
| `SAME_SOURCE` | Same entity that established the fact | REPLACE (self-revision) |
| `INCOMING_OUTRANKS` | Incoming source has higher domain authority | REPLACE |
| `EXISTING_OUTRANKS` | Existing source has higher domain authority | Delegate to `AuthorityConflictResolver` |
| `UNKNOWN` | Source info unavailable or resolver not configured | Fall through to authority-based logic |

`ResolutionContext` record SHALL carry the `incomingSourceId` and `SourceAuthorityRelation` into `ConflictResolver.resolve(conflict, context)`.

`SemanticUnitPromoter` SHALL build a `ResolutionContext` per conflict when a `SourceAuthorityResolver` is available, by comparing the incoming proposition's `sourceIds[0]` against the existing unit's `sourceId`.

Core SHALL NOT reference domain-specific roles (player, DM). The `SourceAuthorityResolver` implementation is caller-provided. The simulator provides a concrete implementation where DM outranks player.

#### Scenario: Same source revises own fact

- **GIVEN** an existing RELIABLE AWMU with `sourceId = "player"`
- **AND** an incoming REVISION conflict with `sourceId = "player"`
- **WHEN** the `SourceAuthorityResolver` returns `SAME_SOURCE`
- **THEN** the resolution SHALL be `REPLACE`

#### Scenario: Higher authority source overrides lower

- **GIVEN** an existing RELIABLE AWMU with `sourceId = "player"`
- **AND** an incoming REVISION conflict with `sourceId = "dm"`
- **WHEN** the `SourceAuthorityResolver` returns `INCOMING_OUTRANKS`
- **THEN** the resolution SHALL be `REPLACE`

#### Scenario: Lower authority source cannot revise higher

- **GIVEN** an existing AWMU with `sourceId = "dm"`
- **AND** an incoming REVISION conflict with `sourceId = "player"`
- **WHEN** the `SourceAuthorityResolver` returns `EXISTING_OUTRANKS`
- **THEN** the resolution SHALL delegate to `AuthorityConflictResolver`

#### Scenario: Unknown source falls back to authority-based logic

- **GIVEN** a `ResolutionContext` with `sourceRelation = UNKNOWN`
- **WHEN** `RevisionAwareConflictResolver.resolve()` is called
- **THEN** the resolver SHALL use the existing authority-based revision policy

#### Scenario: Source annotation rendered in prompt

- **GIVEN** a AWMU with `sourceId = "dm"`
- **WHEN** the unit is rendered in the context block
- **THEN** the output SHALL include `[source: dm]` annotation

## Invariants

- **RIC1**: `ConflictType` SHALL only be non-null when `contradicts = true`. A `WORLD_PROGRESSION` classification implies `contradicts = false` and `conflictType = null`.
- **RIC2**: CANON AWMUs SHALL never be superseded via the revision path. Only `CanonizationGate` controls CANON mutations.
- **RIC3**: Absent or unparseable `conflictType` SHALL always default to `CONTRADICTION` (fail-closed).
- **RIC4**: The revision classification SHALL NOT introduce an additional LLM call — it MUST be integrated into the existing `conflict-detection.jinja` prompt.
- **RIC5**: Source-aware resolution SHALL take precedence over authority-based revision logic. When `SourceAuthorityRelation` is not `UNKNOWN`, the source relation determines the outcome without consulting authority thresholds.
- **RIC6**: Core conflict resolution interfaces SHALL NOT reference domain-specific concepts (player, DM). Domain semantics are provided exclusively by the caller's `SourceAuthorityResolver` implementation.
