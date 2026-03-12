## MODIFIED Requirements

### Requirement: DEMOTE_EXISTING conflict resolution option

**Modifies**: `ConflictResolver.Resolution` enum and `AuthorityConflictResolver`.

The `ConflictResolver.Resolution` enum SHALL include a `DEMOTE_EXISTING` value alongside the existing `KEEP_EXISTING`, `REPLACE`, and `COEXIST` values. When `DEMOTE_EXISTING` is returned, the existing anchor's authority is demoted one level (via `AnchorEngine.demote()`) and the incoming proposition is promoted.

The `AuthorityConflictResolver.byAuthority()` factory SHALL return `DEMOTE_EXISTING` when:
- The incoming proposition has high confidence (above the auto-activate threshold)
- The existing anchor is below CANON authority
- The conflict evidence is strong enough to warrant demotion but not full replacement

For CANON anchors, the resolver SHALL never return `DEMOTE_EXISTING` directly. Instead, the engine delegates to the `CanonizationGate` for decanonization approval.

The resolution decision SHALL follow this matrix, where thresholds are sourced from `ConflictConfig` properties (`replaceThreshold()` and `demoteThreshold()`) instead of hardcoded values:

| Existing Authority | Incoming Confidence | Resolution |
|--------------------|---------------------|------------|
| CANON              | any                 | KEEP_EXISTING |
| RELIABLE           | >= effective replace threshold | REPLACE |
| RELIABLE           | demote threshold -- replace threshold | DEMOTE_EXISTING |
| RELIABLE           | < effective demote threshold | KEEP_EXISTING |
| UNRELIABLE         | >= effective demote threshold | REPLACE |
| UNRELIABLE         | < effective demote threshold | DEMOTE_EXISTING |
| PROVISIONAL        | any                 | REPLACE |

The effective thresholds SHALL incorporate tier-aware modifiers from `ConflictConfig.tier()`. For each resolution comparison, the effective threshold is computed as: `effectiveThreshold = baseThreshold + tierModifier`, where `tierModifier` is determined by the existing anchor's current tier (HOT, WARM, or COLD). The modifier adjusts the threshold, not the incoming confidence score.

The confidence thresholds (default 0.8 for REPLACE, default 0.6 for DEMOTE_EXISTING) SHALL be sourced from `ConflictConfig.replaceThreshold()` and `ConflictConfig.demoteThreshold()` respectively, rather than from hardcoded values or `DiceAnchorsProperties.AnchorConfig.autoActivateThreshold`.

#### Scenario: Conflict with RELIABLE anchor returns DEMOTE_EXISTING

- **GIVEN** an existing anchor at RELIABLE authority
- **WHEN** a high-confidence incoming proposition contradicts the anchor
- **THEN** the conflict resolver returns `DEMOTE_EXISTING`

#### Scenario: Conflict with CANON anchor keeps existing

- **GIVEN** an existing anchor at CANON authority
- **WHEN** an incoming proposition contradicts the anchor
- **THEN** the conflict resolver returns `KEEP_EXISTING` (CANON is protected; decanonization goes through the gate separately)

#### Scenario: DEMOTE_EXISTING triggers demotion and promotion

- **GIVEN** conflict resolution returns `DEMOTE_EXISTING` for anchor "A1" at UNRELIABLE authority
- **WHEN** the engine processes the resolution
- **THEN** anchor "A1" is demoted to PROVISIONAL, the incoming proposition is promoted, and both `AuthorityChanged` (DEMOTED) and `Promoted` lifecycle events are published

#### Scenario: Tier-aware resolution adjusts effective thresholds

- **GIVEN** an existing HOT anchor at RELIABLE authority
- **AND** `replace-threshold` is `0.8` and `hot-defense-modifier` is `0.1`
- **WHEN** an incoming proposition with confidence `0.85` contradicts the anchor
- **THEN** the effective replace threshold SHALL be `0.9` (0.8 + 0.1)
- **AND** the effective demote threshold SHALL be `0.7` (0.6 + 0.1)
- **AND** the resolver SHALL return `DEMOTE_EXISTING` (confidence 0.85 falls between effective demote and replace thresholds)

#### Scenario: COLD anchor resolved with permissive thresholds

- **GIVEN** an existing COLD anchor at UNRELIABLE authority
- **AND** `demote-threshold` is `0.6` and `cold-defense-modifier` is `-0.1`
- **WHEN** an incoming proposition with confidence `0.55` contradicts the anchor
- **THEN** the effective demote threshold SHALL be `0.5` (0.6 - 0.1)
- **AND** the resolver SHALL return `REPLACE` (confidence 0.55 >= effective demote threshold for UNRELIABLE)

### Requirement: REPLACE outcome creates supersession link

**Modifies**: "DEMOTE_EXISTING conflict resolution option" requirement -- specifically the REPLACE outcome processing.

When `AuthorityConflictResolver` produces a REPLACE outcome and the engine processes the resolution, the resolution flow SHALL, in addition to archiving the existing anchor and promoting the incoming proposition:

1. Create a `SUPERSEDES` relationship from the incoming anchor to the replaced anchor with `reason = "CONFLICT_REPLACEMENT"` and `occurredAt = Instant.now()`
2. Set `validTo = Instant.now()` and `transactionEnd = Instant.now()` on the replaced anchor
3. Set `supersededBy` on the replaced anchor to the incoming anchor's ID
4. Set `supersedes` on the incoming anchor to the replaced anchor's ID
5. Publish a `Superseded` lifecycle event with the predecessor and successor IDs

The supersession link SHALL be created within the same operation as the archive, ensuring atomicity of the conflict resolution outcome.

#### Scenario: REPLACE resolution creates supersession chain

- **GIVEN** an existing anchor "A1" at UNRELIABLE authority
- **AND** an incoming proposition with confidence above the effective replace threshold
- **WHEN** conflict resolution returns REPLACE and the engine processes the resolution at instant T5
- **THEN** "A1" SHALL be archived with `validTo = T5` and `transactionEnd = T5`
- **AND** the incoming proposition SHALL be promoted as "A2" with `validFrom = T5` and `transactionStart = T5`
- **AND** a `SUPERSEDES` relationship SHALL exist from "A2" to "A1"
- **AND** a `Superseded` event SHALL be published

#### Scenario: KEEP_EXISTING resolution does not create supersession

- **GIVEN** an existing anchor "A1" at CANON authority
- **WHEN** conflict resolution returns KEEP_EXISTING
- **THEN** no `SUPERSEDES` relationship SHALL be created
- **AND** no `Superseded` event SHALL be published
- **AND** "A1" temporal fields SHALL remain unchanged

#### Scenario: DEMOTE_EXISTING resolution does not create supersession

- **GIVEN** an existing anchor "A1" at RELIABLE authority
- **WHEN** conflict resolution returns DEMOTE_EXISTING
- **THEN** no `SUPERSEDES` relationship SHALL be created (demotion is not replacement)
- **AND** "A1" temporal fields SHALL remain unchanged (the anchor is still active, just at lower authority)

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

- **GIVEN** an incoming text that DICE classifies as CONTRADICTORY to existing anchor "A1"
- **WHEN** the DICE revision conflict detector evaluates the pair
- **THEN** a `Conflict` is returned with the existing anchor and high confidence

#### Scenario: SIMILAR classification produces low-confidence conflict

- **GIVEN** an incoming text that DICE classifies as SIMILAR to existing anchor "A1"
- **WHEN** the DICE revision conflict detector evaluates the pair
- **THEN** a `Conflict` is returned with lower confidence than CONTRADICTORY

#### Scenario: UNRELATED classification produces no conflict

- **GIVEN** an incoming text that DICE classifies as UNRELATED to existing anchor "A1"
- **WHEN** the DICE revision conflict detector evaluates the pair
- **THEN** no conflict is returned

#### Scenario: IDENTICAL classification defers to dedup

- **GIVEN** an incoming text that DICE classifies as IDENTICAL to existing anchor "A1"
- **WHEN** the DICE revision conflict detector evaluates the pair
- **THEN** no conflict is returned (IDENTICAL is handled by the dedup pipeline, not conflict detection)

### Requirement: DICE conflict detection strategy

> **Conditional on Q1**: This requirement depends on the `DiceRevisionConflictDetector` being feasible (see Q1 in design.md). If Q1 resolves negatively, the `"dice"` strategy option is not added.

**Modifies**: `anchor.conflict-detection-strategy` configuration property.

The system SHALL add `"dice"` as a valid strategy value for the `anchor.conflict-detection-strategy` property, alongside existing values `"lexical"`, `"hybrid"`, and `"llm"`. When `"dice"` is selected, the `DiceRevisionConflictDetector` is used.

The composite conflict detector pipeline with `"dice"` strategy SHALL chain: lexical first (fast-path), DICE-based second (semantic). This preserves the optimization of filtering obvious non-conflicts lexically before invoking the DICE API.

#### Scenario: DICE strategy configured

- **GIVEN** `dice-anchors.anchor.conflict-detection-strategy` is set to `"dice"`
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
- **Invariants**: The detector SHALL NOT modify any anchor state. The resolver SHALL NOT directly modify anchor state; it returns a decision that the engine executes.

#### Scenario: Conflict detector failure returns safe default

- **GIVEN** a `ConflictDetector` implementation encounters an internal error
- **WHEN** `detect()` is called
- **THEN** an empty conflict list is returned and a WARN-level log is emitted with the error details

## Added Requirements (initial-community-review-readiness)

### Requirement: DICE conflict detection strategy (parse failure safety)

The conflict detection strategy SHALL support lexical, LLM, and hybrid execution modes while enforcing share-grade safety semantics. Parse failures in conflict detection SHALL NOT default to no-conflict acceptance for claim-grade runs. Instead, parse failures SHALL emit explicit degraded outcomes and SHALL be surfaced in run artifacts and reports.

#### Scenario: Parse failure in claim-grade run is degraded, not accepted
- **GIVEN** a claim-grade run and an LLM conflict parse failure
- **WHEN** conflict evaluation is finalized
- **THEN** the proposition SHALL be marked degraded-review-required
- **AND** the system SHALL NOT auto-accept it as no-conflict

#### Scenario: Degraded conflict decision is visible in report artifacts
- **GIVEN** a run containing one degraded conflict decision
- **WHEN** benchmark and resilience reports are generated
- **THEN** degraded conflict counts SHALL be included in report metadata

## Invariants (initial-community-review-readiness)

- **ACON1**: Claim-grade evaluation SHALL NOT silently fail-open on conflict parser errors.
