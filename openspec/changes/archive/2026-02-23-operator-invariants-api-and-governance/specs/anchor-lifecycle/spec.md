## ADDED Requirements

### Requirement: Invariant enforcement hook before archive

`AnchorEngine.archive()` SHALL evaluate all applicable invariants via `InvariantEvaluator.evaluate(ARCHIVE, anchorId, contextId)` before committing the archive operation. If the evaluation returns `blocked = true` (a MUST-strength invariant is violated), the archive SHALL NOT proceed and the method SHALL return without modifying anchor state.

When the evaluation returns `blocked = false` but contains SHOULD-strength violations, the archive SHALL proceed and violation events SHALL be published.

The invariant evaluation SHALL occur after the anchor lookup but before calling `repository.archiveAnchor()`.

#### Scenario: MUST-strength invariant blocks archive

- **GIVEN** a MUST-strength `ANCHOR_PROTECTED` invariant for anchor "A1"
- **WHEN** `AnchorEngine.archive("A1", ArchiveReason.MANUAL)` is called
- **THEN** the archive SHALL be blocked, anchor "A1" SHALL remain active, and an `InvariantViolation` event SHALL be published with `blocked = true`

#### Scenario: SHOULD-strength invariant warns but allows archive

- **GIVEN** a SHOULD-strength `ANCHOR_PROTECTED` invariant for anchor "A1"
- **WHEN** `AnchorEngine.archive("A1", ArchiveReason.MANUAL)` is called
- **THEN** the archive SHALL proceed, an `InvariantViolation` event SHALL be published with `blocked = false`, and an `Archived` event SHALL be published

#### Scenario: No invariants allows archive normally

- **GIVEN** no invariants are registered
- **WHEN** `AnchorEngine.archive("A1", ArchiveReason.MANUAL)` is called
- **THEN** the archive SHALL proceed normally with no invariant evaluation overhead

### Requirement: Invariant enforcement hook before eviction

`AnchorEngine.promote()` SHALL evaluate invariants via `InvariantEvaluator.evaluate(EVICT, candidateAnchorId, contextId)` for each eviction candidate during budget enforcement. If the evaluation returns `blocked = true`, that candidate SHALL be skipped and the next-lowest-ranked non-pinned anchor SHALL be considered.

The eviction loop SHALL iterate through candidates in rank order (ascending) until either:
1. A non-blocked candidate is found and evicted, or
2. All candidates are blocked by MUST-strength invariants, in which case the budget temporarily exceeds the limit and a WARN-level log is emitted

#### Scenario: Protected anchor skipped during eviction

- **GIVEN** a MUST-strength `ANCHOR_PROTECTED` invariant for anchor "A1"
- **AND** the budget is 3 with 3 active anchors, and "A1" has the lowest rank
- **WHEN** a new anchor is promoted
- **THEN** "A1" SHALL be skipped and the next-lowest-ranked non-pinned, non-protected anchor SHALL be evicted

#### Scenario: Multiple protected anchors in eviction candidates

- **GIVEN** MUST-strength `ANCHOR_PROTECTED` invariants for anchors "A1" and "A2"
- **AND** the budget is 3 with 3 active anchors, "A1" and "A2" being the two lowest-ranked
- **WHEN** a new anchor is promoted
- **THEN** "A1" and "A2" SHALL be skipped and the third-lowest-ranked non-pinned anchor SHALL be evicted

#### Scenario: All eviction candidates protected

- **GIVEN** MUST-strength `ANCHOR_PROTECTED` invariants for all non-pinned anchors
- **AND** the budget is full
- **WHEN** a new anchor is promoted
- **THEN** no anchor SHALL be evicted, the budget SHALL temporarily exceed the limit, and a WARN-level log SHALL be emitted: "Invariant protection prevented eviction; budget exceeded"

### Requirement: Invariant enforcement hook before demotion

`AnchorEngine.demote()` SHALL evaluate invariants via `InvariantEvaluator.evaluate(DEMOTE, anchorId, contextId)` before committing the demotion. If the evaluation returns `blocked = true`, the demotion SHALL NOT proceed.

The invariant evaluation SHALL occur after the canonization gate check (CANON anchors route through the gate first) but before computing `previousLevel()` and writing the new authority.

#### Scenario: AUTHORITY_FLOOR invariant blocks demotion

- **GIVEN** a MUST-strength `AUTHORITY_FLOOR` invariant for anchor "A1" with minimum authority RELIABLE
- **AND** anchor "A1" is at RELIABLE authority
- **WHEN** `demote("A1", DemotionReason.RANK_DECAY)` is called
- **THEN** the demotion SHALL be blocked and anchor "A1" SHALL remain at RELIABLE

#### Scenario: AUTHORITY_FLOOR allows demotion above floor

- **GIVEN** a MUST-strength `AUTHORITY_FLOOR` invariant for anchor "A1" with minimum authority UNRELIABLE
- **AND** anchor "A1" is at RELIABLE authority
- **WHEN** `demote("A1", DemotionReason.RANK_DECAY)` is called
- **THEN** the demotion SHALL proceed (RELIABLE to UNRELIABLE is at the floor, not below it)

#### Scenario: CONTEXT_FROZEN blocks all demotions in context

- **GIVEN** a MUST-strength `CONTEXT_FROZEN` invariant for context "ctx-1"
- **WHEN** `demote("A1", DemotionReason.CONFLICT_EVIDENCE)` is called for an anchor in "ctx-1"
- **THEN** the demotion SHALL be blocked

### Requirement: Invariant enforcement hook before authority change

All authority transitions (both promotion and demotion) SHALL be evaluated against `AUTHORITY_CHANGE` invariants via `InvariantEvaluator.evaluate(AUTHORITY_CHANGE, anchorId, contextId)`. This applies to:

1. Authority promotions in `AnchorEngine.reinforce()` (when `shouldUpgradeAuthority()` returns true)
2. Authority demotions in `AnchorEngine.demote()`
3. Authority changes via `CanonizationGate.approve()`

The `AUTHORITY_CHANGE` evaluation is in addition to the action-specific evaluation (e.g., `DEMOTE` check runs first, then `AUTHORITY_CHANGE` check). Both evaluations MUST pass for the action to proceed.

#### Scenario: Authority promotion blocked by CONTEXT_FROZEN

- **GIVEN** a MUST-strength `CONTEXT_FROZEN` invariant for context "ctx-1"
- **AND** an anchor in "ctx-1" reaches the reinforcement threshold for authority upgrade
- **WHEN** `reinforce()` attempts to promote authority
- **THEN** the authority promotion SHALL be blocked, but the rank boost SHALL still be applied

#### Scenario: Both DEMOTE and AUTHORITY_CHANGE evaluated

- **GIVEN** a MUST-strength `AUTHORITY_FLOOR` invariant (constrains DEMOTE and AUTHORITY_CHANGE)
- **WHEN** `demote("A1", DemotionReason.TRUST_DEGRADATION)` is called
- **THEN** both `evaluate(DEMOTE, ...)` and `evaluate(AUTHORITY_CHANGE, ...)` SHALL be called, and the demotion SHALL be blocked if either returns `blocked = true`

### Requirement: Hook evaluation order

When a lifecycle operation triggers invariant evaluation, the hooks SHALL be evaluated in the following order:

1. Canonization gate check (for CANON transitions only)
2. Invariant evaluation for the primary action (`ARCHIVE`, `EVICT`, or `DEMOTE`)
3. Invariant evaluation for `AUTHORITY_CHANGE` (if the action involves an authority transition)

If any step blocks the action, subsequent steps SHALL NOT be evaluated. This short-circuit behavior avoids unnecessary work and prevents confusing violation events for actions that were already blocked.

#### Scenario: Canonization gate blocks before invariant check

- **GIVEN** the canonization gate is enabled and a MUST-strength invariant exists
- **WHEN** `demote()` is called on a CANON anchor
- **THEN** the canonization gate creates a pending request and returns; invariant evaluation SHALL NOT run

#### Scenario: Primary action blocked before AUTHORITY_CHANGE check

- **GIVEN** a MUST-strength `ANCHOR_PROTECTED` invariant blocks `DEMOTE` for anchor "A1"
- **AND** a separate `AUTHORITY_CHANGE` invariant exists
- **WHEN** `demote("A1", ...)` is called
- **THEN** the `DEMOTE` invariant blocks the action and the `AUTHORITY_CHANGE` invariant SHALL NOT be evaluated
