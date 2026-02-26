## ADDED Requirements

### Requirement: AnchorMutationStrategy SPI

The system SHALL provide an `AnchorMutationStrategy` interface that gates all anchor text mutation requests. Every anchor mutation — regardless of origin (UI, LLM tool, conflict resolver) — MUST route through this SPI before execution. The interface is unsealed to allow test doubles; the `HitlOnlyMutationStrategy` implementation is `final`.

The interface SHALL define:
- `MutationDecision evaluate(MutationRequest request)` — evaluates whether a mutation is allowed
- `MutationRequest` record: `anchorId`, `revisedText`, `source` (enum: `UI`, `LLM_TOOL`, `CONFLICT_RESOLVER`), `requesterId`
- `MutationDecision` sealed interface with permits: `Allow`, `Deny(String reason)`, `PendingApproval(String approvalId)`

#### Scenario: Strategy receives UI-originated mutation request

- **WHEN** a mutation request with `source = UI` is evaluated
- **THEN** the strategy SHALL return a `MutationDecision`

#### Scenario: Strategy receives LLM-originated mutation request

- **WHEN** a mutation request with `source = LLM_TOOL` is evaluated
- **THEN** the strategy SHALL return a `MutationDecision`

#### Scenario: Strategy receives conflict-resolver-originated mutation request

- **WHEN** a mutation request with `source = CONFLICT_RESOLVER` is evaluated
- **THEN** the strategy SHALL return a `MutationDecision`

### Requirement: HitlOnlyMutationStrategy default implementation

The system SHALL provide `HitlOnlyMutationStrategy` as the default implementation of `AnchorMutationStrategy`. This strategy SHALL allow mutations only from `MutationSource.UI` and deny all other sources.

#### Scenario: UI mutation allowed

- **WHEN** `HitlOnlyMutationStrategy` evaluates a request with `source = UI`
- **THEN** it SHALL return `MutationDecision.Allow`

#### Scenario: LLM tool mutation denied

- **WHEN** `HitlOnlyMutationStrategy` evaluates a request with `source = LLM_TOOL`
- **THEN** it SHALL return `MutationDecision.Deny` with a source-specific reason

#### Scenario: Conflict resolver mutation denied

- **WHEN** `HitlOnlyMutationStrategy` evaluates a request with `source = CONFLICT_RESOLVER`
- **THEN** it SHALL return `MutationDecision.Deny` with a source-specific reason

### Requirement: Mutation strategy configuration

The active `AnchorMutationStrategy` SHALL be configurable via `dice-anchors.anchor.mutation.strategy` property. The default value SHALL be `hitl-only`.

#### Scenario: Default configuration

- **WHEN** no `anchor.mutation.strategy` property is set
- **THEN** `HitlOnlyMutationStrategy` SHALL be the active bean

#### Scenario: Explicit hitl-only configuration

- **WHEN** `anchor.mutation.strategy = hitl-only` is configured
- **THEN** `HitlOnlyMutationStrategy` SHALL be the active bean

### Requirement: ChatView revision routes through SPI

The `ChatView.reviseAnchor()` method SHALL route all revision requests through `AnchorMutationStrategy.evaluate()` before executing the mutation. If the decision is `Deny`, the UI SHALL display an error notification with the denial reason.

#### Scenario: ChatView revision allowed by strategy

- **WHEN** user clicks "Revise" in ChatView AND the strategy returns `Allow`
- **THEN** the anchor SHALL be revised (predecessor archived, successor created)

#### Scenario: ChatView revision denied by strategy

- **WHEN** user clicks "Revise" in ChatView AND the strategy returns `Deny`
- **THEN** the anchor SHALL NOT be revised AND an error notification SHALL display the denial reason

### Requirement: Sim panel revision UI

The `AnchorManipulationPanel` SHALL include an inline text field and "Revise" button for each anchor in the edit card. Revision requests SHALL route through `AnchorMutationStrategy` with `source = UI`.

#### Scenario: Sim panel revision

- **WHEN** operator enters revised text and clicks "Revise" in the sim anchor edit card
- **THEN** the mutation request SHALL be evaluated by `AnchorMutationStrategy`
- **AND** if allowed, the anchor SHALL be revised and an intervention event SHALL be logged

## Invariants

- **CAM1**: Every anchor text mutation MUST route through `AnchorMutationStrategy.evaluate()` — no bypass paths
- **CAM2**: `HitlOnlyMutationStrategy` SHALL never return `Allow` for non-UI sources
- **CAM3**: The SPI SHALL NOT enforce authority gates (CANON/RELIABLE immutability) — authority gating remains in the mutation executor, not the strategy
