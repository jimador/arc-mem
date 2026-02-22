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
