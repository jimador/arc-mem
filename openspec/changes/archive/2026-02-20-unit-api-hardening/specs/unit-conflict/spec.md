## MODIFIED Requirements

### Requirement: DEMOTE_EXISTING conflict resolution option

**Modifies**: `ConflictResolver.Resolution` enum and `AuthorityConflictResolver`.

The `ConflictResolver.Resolution` enum SHALL include a `DEMOTE_EXISTING` value alongside the existing `KEEP_EXISTING`, `REPLACE`, and `COEXIST` values. When `DEMOTE_EXISTING` is returned, the existing context unit's authority is demoted one level (via `ArcMemEngine.demote()`) and the incoming proposition is promoted.

The `AuthorityConflictResolver.byAuthority()` factory SHALL return `DEMOTE_EXISTING` when:
- The incoming proposition has high confidence (above the auto-activate threshold)
- The existing context unit is below CANON authority
- The conflict evidence is strong enough to warrant demotion but not full replacement

For CANON context units, the resolver SHALL never return `DEMOTE_EXISTING` directly. Instead, the engine delegates to the `CanonizationGate` for decanonization approval.

The resolution decision SHALL follow this matrix:

| Existing Authority | Incoming Confidence | Resolution |
|--------------------|---------------------|------------|
| CANON              | any                 | KEEP_EXISTING |
| RELIABLE           | >= 0.8              | REPLACE |
| RELIABLE           | 0.6 – 0.8          | DEMOTE_EXISTING |
| RELIABLE           | < 0.6              | KEEP_EXISTING |
| UNRELIABLE         | >= 0.6              | REPLACE |
| UNRELIABLE         | < 0.6              | DEMOTE_EXISTING |
| PROVISIONAL        | any                 | REPLACE |

The confidence thresholds (0.8 for REPLACE, 0.6 for DEMOTE_EXISTING) SHALL be sourced from `ArcMemProperties.UnitConfig.autoActivateThreshold` and a new `demoteThreshold` property respectively, rather than hardcoded.

#### Scenario: Conflict with RELIABLE context unit returns DEMOTE_EXISTING

- **GIVEN** an existing context unit at RELIABLE authority
- **WHEN** a high-confidence incoming proposition contradicts the context unit
- **THEN** the conflict resolver returns `DEMOTE_EXISTING`

#### Scenario: Conflict with CANON context unit keeps existing

- **GIVEN** an existing context unit at CANON authority
- **WHEN** an incoming proposition contradicts the context unit
- **THEN** the conflict resolver returns `KEEP_EXISTING` (CANON is protected; decanonization goes through the gate separately)

#### Scenario: DEMOTE_EXISTING triggers demotion and promotion

- **GIVEN** conflict resolution returns `DEMOTE_EXISTING` for context unit "A1" at UNRELIABLE authority
- **WHEN** the engine processes the resolution
- **THEN** context unit "A1" is demoted to PROVISIONAL, the incoming proposition is promoted, and both `AuthorityChanged` (DEMOTED) and `Promoted` lifecycle events are published

## ADDED Requirements

### Requirement: DICE revision classification conflict detector

> **Conditional on Q1**: This requirement is contingent on DICE's `LlmPropositionReviser` supporting standalone comparison of text against existing propositions outside the extraction pipeline. If Q1 investigation reveals this is not supported, this requirement and its scenarios are dropped. The existing `LlmConflictDetector` with improved documentation remains the semantic conflict detection path.

The system SHALL provide a `DiceRevisionConflictDetector` that implements `ConflictDetector` and uses DICE's `LlmPropositionReviser` for semantic relationship classification. DICE classifies relationships as: IDENTICAL, SIMILAR, CONTRADICTORY, GENERALIZES, or UNRELATED.

The detector SHALL map DICE classifications to conflict signals:
- `CONTRADICTORY` → Conflict with high confidence
- `SIMILAR` → Potential conflict with lower confidence (propositions overlap but don't directly contradict)
- `IDENTICAL` → Not a conflict (signals duplication; delegate to dedup pipeline)
- `GENERALIZES` → No conflict
- `UNRELATED` → No conflict

#### Scenario: CONTRADICTORY classification produces conflict

- **GIVEN** an incoming text that DICE classifies as CONTRADICTORY to existing context unit "A1"
- **WHEN** the DICE revision conflict detector evaluates the pair
- **THEN** a `Conflict` is returned with the existing context unit and high confidence

#### Scenario: SIMILAR classification produces low-confidence conflict

- **GIVEN** an incoming text that DICE classifies as SIMILAR to existing context unit "A1"
- **WHEN** the DICE revision conflict detector evaluates the pair
- **THEN** a `Conflict` is returned with lower confidence than CONTRADICTORY

#### Scenario: UNRELATED classification produces no conflict

- **GIVEN** an incoming text that DICE classifies as UNRELATED to existing context unit "A1"
- **WHEN** the DICE revision conflict detector evaluates the pair
- **THEN** no conflict is returned

#### Scenario: IDENTICAL classification defers to dedup

- **GIVEN** an incoming text that DICE classifies as IDENTICAL to existing context unit "A1"
- **WHEN** the DICE revision conflict detector evaluates the pair
- **THEN** no conflict is returned (IDENTICAL is handled by the dedup pipeline, not conflict detection)

### Requirement: DICE conflict detection strategy

> **Conditional on Q1**: This requirement depends on the `DiceRevisionConflictDetector` being feasible (see Q1 in design.md). If Q1 resolves negatively, the `"dice"` strategy option is not added.

**Modifies**: `context unit.conflict-detection-strategy` configuration property.

The system SHALL add `"dice"` as a valid strategy value for the `context unit.conflict-detection-strategy` property, alongside existing values `"lexical"`, `"hybrid"`, and `"llm"`. When `"dice"` is selected, the `DiceRevisionConflictDetector` is used.

The composite conflict detector pipeline with `"dice"` strategy SHALL chain: lexical first (fast-path), DICE-based second (semantic). This preserves the optimization of filtering obvious non-conflicts lexically before invoking the DICE API.

#### Scenario: DICE strategy configured

- **GIVEN** `context units.context unit.conflict-detection-strategy` is set to `"dice"`
- **WHEN** conflict detection is initialized
- **THEN** the `DiceRevisionConflictDetector` is wired as the semantic conflict detector in the composite pipeline

#### Scenario: Default strategy unchanged

- **GIVEN** no explicit conflict detection strategy is configured
- **WHEN** conflict detection is initialized
- **THEN** the default strategy (`"hybrid"` / lexical-then-LLM) is used, preserving backward compatibility

### Requirement: ConflictDetector SPI Javadoc contracts

**Modifies**: `ConflictDetector` and `ConflictResolver` interfaces.

All SPI interfaces in the conflict detection subsystem SHALL have comprehensive Javadoc documenting:
- **Contract**: What the implementation must do and return
- **Thread safety**: All implementations MUST be thread-safe (stateless or using concurrent data structures)
- **Error handling**: On failure, conflict detectors SHOULD return an empty list (no conflicts detected) and log at WARN level. Conflict resolvers SHOULD return `KEEP_EXISTING` as the safe default.
- **Invariants**: The detector SHALL NOT modify any context unit state. The resolver SHALL NOT directly modify context unit state; it returns a decision that the engine executes.

#### Scenario: Conflict detector failure returns safe default

- **GIVEN** a `ConflictDetector` implementation encounters an internal error
- **WHEN** `detect()` is called
- **THEN** an empty conflict list is returned and a WARN-level log is emitted with the error details
