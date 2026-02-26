## ADDED Requirements

### Requirement: ConflictType enum

The system SHALL define a `ConflictType` enum with the following values:
- `REVISION` — the incoming statement represents a user-intended correction or update to an existing anchor
- `CONTRADICTION` — the incoming statement is inconsistent with an existing anchor without revision intent
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
5. The existing anchor's `authority` as a template variable

The JSON output contract SHALL be:
```json
{"contradicts": true/false, "reasoning": "...", "conflictType": "REVISION"|"CONTRADICTION"|"WORLD_PROGRESSION"|null, "explanation": "brief reason"}
```

`conflictType` SHALL be null when `contradicts` is false.

#### Scenario: Revision intent detected

- **GIVEN** an existing PROVISIONAL anchor "Anakin is a wizard"
- **WHEN** the incoming text is "Actually, I want Anakin to be a bard instead"
- **THEN** the conflict SHALL be detected with `contradicts = true`
- **AND** `conflictType` SHALL be `REVISION`

#### Scenario: Contradiction detected

- **GIVEN** an existing anchor "The capital of France is Paris"
- **WHEN** the incoming text is "The capital of France is Berlin"
- **THEN** the conflict SHALL be detected with `contradicts = true`
- **AND** `conflictType` SHALL be `CONTRADICTION`

#### Scenario: World progression detected

- **GIVEN** an existing anchor "The king is alive"
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

`LlmConflictDetector.batchDetect()` SHALL extend the `batch-conflict-detection.jinja` prompt to return `conflictType` and `reasoning` per anchor match within each candidate's results.

The batch result entry SHALL include per-anchor-match type annotations:
```json
{"candidate": "...", "contradictingAnchors": [{"anchorText": "...", "conflictType": "...", "reasoning": "..."}]}
```

`BatchConflictResult` SHALL be extended to accommodate the per-anchor-match structure.

#### Scenario: Batch detection with mixed conflict types

- **GIVEN** 3 candidates evaluated against 5 anchors
- **WHEN** candidate 1 revises anchor A and contradicts anchor B
- **THEN** the batch result SHALL report candidate 1 with two conflicts: one with `conflictType = REVISION` for anchor A and one with `conflictType = CONTRADICTION` for anchor B

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

### Requirement: Authority-gated revision eligibility

Revision acceptance SHALL be gated by the existing anchor's authority level:

| Authority | Revisable | Condition |
|-----------|-----------|-----------|
| CANON | Never | All CANON mutations go through `CanonizationGate` |
| RELIABLE | Configurable | Only when `anchor.revision.reliable-revisable = true` AND confidence >= `replaceThreshold` |
| UNRELIABLE | Yes | Confidence SHALL meet `revisionConfidenceThreshold` (default 0.75) |
| PROVISIONAL | Yes | No additional confidence gate |

#### Scenario: CANON anchor is never revision-eligible

- **GIVEN** a CANON anchor
- **WHEN** a REVISION conflict is detected against it
- **THEN** the revision SHALL be rejected regardless of confidence or configuration

#### Scenario: UNRELIABLE anchor requires confidence threshold

- **GIVEN** an UNRELIABLE anchor
- **AND** `revisionConfidenceThreshold = 0.75`
- **WHEN** a REVISION conflict with confidence 0.6 is detected
- **THEN** the revision SHALL NOT be accepted via the revision path
- **AND** the conflict SHALL be delegated to `AuthorityConflictResolver`

### Requirement: Revision configuration properties

`DiceAnchorsProperties` SHALL include the following configuration under `dice-anchors.anchor.revision`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Master switch for revision classification and routing |
| `reliable-revisable` | boolean | `false` | Whether RELIABLE anchors accept revisions |
| `confidence-threshold` | double | `0.75` | Minimum classification confidence for revision acceptance |

#### Scenario: Revision disabled via configuration

- **GIVEN** `dice-anchors.anchor.revision.enabled = false`
- **WHEN** a conflict is detected with `conflictType = REVISION`
- **THEN** the system SHALL treat it as `CONTRADICTION` (delegate to `AuthorityConflictResolver`)

#### Scenario: Custom confidence threshold applied

- **GIVEN** `dice-anchors.anchor.revision.confidence-threshold = 0.9`
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

## Invariants

- **RIC1**: `ConflictType` SHALL only be non-null when `contradicts = true`. A `WORLD_PROGRESSION` classification implies `contradicts = false` and `conflictType = null`.
- **RIC2**: CANON anchors SHALL never be superseded via the revision path. Only `CanonizationGate` controls CANON mutations.
- **RIC3**: Absent or unparseable `conflictType` SHALL always default to `CONTRADICTION` (fail-closed).
- **RIC4**: The revision classification SHALL NOT introduce an additional LLM call — it MUST be integrated into the existing `conflict-detection.jinja` prompt.
