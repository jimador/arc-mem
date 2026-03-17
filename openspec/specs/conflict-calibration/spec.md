## ADDED Requirements

### Requirement: Conflict detection configuration properties

A `ConflictConfig` record SHALL be added to `ArcMemProperties` providing the following externalized thresholds:

- `arc-mem.conflict.negation-overlap-threshold` (default: `0.5`) -- minimum Jaccard similarity for negation conflict detection in `NegationConflictDetector`
- `arc-mem.conflict.llm-confidence` (default: `0.9`) -- confidence score assigned to LLM-detected conflicts in `LlmConflictDetector`
- `arc-mem.conflict.replace-threshold` (default: `0.8`) -- minimum confidence for `REPLACE` resolution in `AuthorityConflictResolver`
- `arc-mem.conflict.demote-threshold` (default: `0.6`) -- minimum confidence for `DEMOTE_EXISTING` resolution in `AuthorityConflictResolver`

All thresholds SHALL be in range (0.0, 1.0]. Default values SHALL match current hardcoded behavior so that no behavioral change occurs without explicit configuration.

#### Scenario: Default values match current behavior

- **GIVEN** no explicit `arc-mem.conflict.*` properties are configured
- **WHEN** the application starts
- **THEN** `ConflictConfig.negationOverlapThreshold()` SHALL return `0.5`
- **AND** `ConflictConfig.llmConfidence()` SHALL return `0.9`
- **AND** `ConflictConfig.replaceThreshold()` SHALL return `0.8`
- **AND** `ConflictConfig.demoteThreshold()` SHALL return `0.6`

#### Scenario: Custom values override defaults

- **GIVEN** `arc-mem.conflict.negation-overlap-threshold` is set to `0.7`
- **AND** `arc-mem.conflict.replace-threshold` is set to `0.85`
- **WHEN** the application starts
- **THEN** `ConflictConfig.negationOverlapThreshold()` SHALL return `0.7`
- **AND** `ConflictConfig.replaceThreshold()` SHALL return `0.85`
- **AND** all other thresholds SHALL retain their default values

### Requirement: Tier-aware resolution modifiers

The `ConflictConfig` SHALL include a nested `TierConfig` record providing tier-based confidence modifiers:

- `arc-mem.conflict.tier.hot-defense-modifier` (default: `0.1`) -- added to resolution thresholds for HOT ARC Working Memory Units (AWMUs), making them harder to replace or demote
- `arc-mem.conflict.tier.warm-defense-modifier` (default: `0.0`) -- no modifier for WARM AWMUs (baseline behavior)
- `arc-mem.conflict.tier.cold-defense-modifier` (default: `-0.1`) -- subtracted from thresholds for COLD AWMUs, making them easier to replace or demote

`AuthorityConflictResolver` SHALL apply the tier modifier to the effective threshold before comparing against incoming confidence. The modifier adjusts the threshold, not the confidence score. The effective threshold is computed as: `effectiveThreshold = baseThreshold + tierModifier`.

Result: a HOT AWMU requires confidence >= 0.9 (0.8 + 0.1) to trigger REPLACE, while a COLD AWMU requires only 0.7 (0.8 - 0.1).

CANON AWMUs SHALL remain immune to replacement regardless of tier modifier -- `KEEP_EXISTING` is always returned for CANON authority.

#### Scenario: HOT AWMU harder to replace

- **GIVEN** an existing HOT AWMU at RELIABLE authority
- **AND** `replace-threshold` is `0.8` and `hot-defense-modifier` is `0.1`
- **WHEN** an incoming proposition with confidence `0.85` conflicts with the AWMU
- **THEN** the effective replace threshold SHALL be `0.9`
- **AND** the resolver SHALL return `DEMOTE_EXISTING` (confidence 0.85 < effective threshold 0.9)

#### Scenario: COLD AWMU easier to replace

- **GIVEN** an existing COLD AWMU at RELIABLE authority
- **AND** `replace-threshold` is `0.8` and `cold-defense-modifier` is `-0.1`
- **WHEN** an incoming proposition with confidence `0.75` conflicts with the AWMU
- **THEN** the effective replace threshold SHALL be `0.7`
- **AND** the resolver SHALL return `REPLACE` (confidence 0.75 >= effective threshold 0.7)

#### Scenario: WARM AWMU at baseline

- **GIVEN** an existing WARM AWMU at RELIABLE authority
- **AND** `replace-threshold` is `0.8` and `warm-defense-modifier` is `0.0`
- **WHEN** an incoming proposition with confidence `0.85` conflicts with the AWMU
- **THEN** the effective replace threshold SHALL be `0.8`
- **AND** the resolver SHALL return `REPLACE` (confidence 0.85 >= effective threshold 0.8)

#### Scenario: CANON immune regardless of tier

- **GIVEN** an existing COLD AWMU at CANON authority
- **AND** `cold-defense-modifier` is `-0.1`
- **WHEN** an incoming proposition with confidence `0.95` conflicts with the AWMU
- **THEN** the resolver SHALL return `KEEP_EXISTING`
- **AND** the tier modifier SHALL NOT affect CANON resolution

### Requirement: Conflict configuration validation

The system SHALL validate all conflict configuration properties at startup. Validation rules:

- All thresholds (`negation-overlap-threshold`, `llm-confidence`, `replace-threshold`, `demote-threshold`) SHALL be in range (0.0, 1.0]
- `replace-threshold` SHALL be strictly greater than `demote-threshold`
- All tier modifiers (`hot-defense-modifier`, `warm-defense-modifier`, `cold-defense-modifier`) SHALL be in range [-0.5, 0.5]

Invalid configuration SHALL cause startup failure with an `IllegalStateException` containing a descriptive error message identifying the violated constraint.

#### Scenario: Invalid threshold rejected

- **GIVEN** `arc-mem.conflict.negation-overlap-threshold` is set to `0.0`
- **WHEN** the application starts
- **THEN** startup SHALL fail with `IllegalStateException`
- **AND** the error message SHALL indicate the threshold must be in range (0.0, 1.0]

#### Scenario: Inverted thresholds rejected

- **GIVEN** `arc-mem.conflict.replace-threshold` is set to `0.5`
- **AND** `arc-mem.conflict.demote-threshold` is set to `0.7`
- **WHEN** the application starts
- **THEN** startup SHALL fail with `IllegalStateException`
- **AND** the error message SHALL indicate that replace-threshold must be greater than demote-threshold

#### Scenario: Valid config passes

- **GIVEN** all conflict thresholds are within valid ranges
- **AND** `replace-threshold` is greater than `demote-threshold`
- **AND** all tier modifiers are within [-0.5, 0.5]
- **WHEN** the application starts
- **THEN** validation SHALL pass and `ConflictConfig` SHALL be available for injection
