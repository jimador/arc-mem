## ADDED Requirements

### Requirement: AblationCondition declarative model

The system SHALL define ablation conditions as a declarative configuration type (enum or sealed interface) in the `sim.benchmark` package. The system SHALL provide at minimum four built-in conditions: `FULL_ANCHORS`, `NO_ANCHORS`, `FLAT_AUTHORITY`, and `NO_RANK_DIFFERENTIATION`. Each condition SHALL be identifiable by a unique name suitable for use as map keys and display labels.

#### Scenario: Built-in conditions are available

- **WHEN** the `AblationCondition` type is referenced
- **THEN** at least four conditions SHALL be available: `FULL_ANCHORS`, `NO_ANCHORS`, `FLAT_AUTHORITY`, `NO_RANK_DIFFERENTIATION`

#### Scenario: Each condition has a unique name

- **WHEN** the names of all built-in conditions are collected
- **THEN** no two conditions SHALL share the same name

### Requirement: Condition configuration fields

Each `AblationCondition` SHALL specify the following configuration fields: `injectionEnabled` (boolean), `authorityOverride` (nullable `Authority`), `rankOverride` (nullable `Integer`), `rankMutationEnabled` (boolean), and `authorityPromotionEnabled` (boolean). These fields SHALL fully determine the anchor subsystem behavior for any simulation run using that condition.

#### Scenario: All configuration fields are accessible

- **WHEN** any `AblationCondition` instance is inspected
- **THEN** it SHALL expose `injectionEnabled`, `authorityOverride`, `rankOverride`, `rankMutationEnabled`, and `authorityPromotionEnabled` fields

### Requirement: FULL_ANCHORS condition

The `FULL_ANCHORS` condition SHALL configure: `injectionEnabled = true`, `authorityOverride = null` (no override), `rankOverride = null` (no override), `rankMutationEnabled = true`, and `authorityPromotionEnabled = true`. This condition SHALL represent the default/control configuration where all anchor subsystems are active.

#### Scenario: FULL_ANCHORS enables all subsystems

- **WHEN** the `FULL_ANCHORS` condition is inspected
- **THEN** `injectionEnabled` SHALL be `true`
- **AND** `authorityOverride` SHALL be `null`
- **AND** `rankOverride` SHALL be `null`
- **AND** `rankMutationEnabled` SHALL be `true`
- **AND** `authorityPromotionEnabled` SHALL be `true`

#### Scenario: FULL_ANCHORS produces identical behavior to unconditioned runs

- **GIVEN** a scenario with seed anchors at various ranks and authorities
- **WHEN** the scenario is run with `FULL_ANCHORS` applied
- **THEN** the seed anchors SHALL retain their original rank and authority values
- **AND** rank mutation and authority promotion SHALL operate normally during the run

### Requirement: NO_ANCHORS condition

The `NO_ANCHORS` condition SHALL configure: `injectionEnabled = false`. All other configuration fields are irrelevant when injection is disabled and MAY take any value.

#### Scenario: NO_ANCHORS disables injection

- **WHEN** the `NO_ANCHORS` condition is inspected
- **THEN** `injectionEnabled` SHALL be `false`

#### Scenario: Simulation runs with NO_ANCHORS receive no anchor context

- **GIVEN** a scenario with seed anchors
- **WHEN** the scenario is run with `NO_ANCHORS` applied
- **THEN** the simulation SHALL proceed without injecting anchor context into the LLM prompt

### Requirement: FLAT_AUTHORITY condition

The `FLAT_AUTHORITY` condition SHALL configure: `injectionEnabled = true`, `authorityOverride = RELIABLE` (all seed anchors receive the same authority), `rankOverride = null` (no rank override), `rankMutationEnabled = true`, and `authorityPromotionEnabled = false` (no authority promotion during the run).

#### Scenario: FLAT_AUTHORITY overrides authority to RELIABLE

- **GIVEN** a scenario with seed anchors at authorities PROVISIONAL, UNRELIABLE, and RELIABLE
- **WHEN** the `FLAT_AUTHORITY` condition is applied
- **THEN** all seed anchors SHALL have their authority set to `RELIABLE`
- **AND** authority promotion SHALL be disabled for the duration of the run

#### Scenario: FLAT_AUTHORITY preserves original rank values

- **GIVEN** a scenario with seed anchors at ranks 200, 500, and 800
- **WHEN** the `FLAT_AUTHORITY` condition is applied
- **THEN** the seed anchors SHALL retain their original rank values of 200, 500, and 800

### Requirement: NO_RANK_DIFFERENTIATION condition

The `NO_RANK_DIFFERENTIATION` condition SHALL configure: `injectionEnabled = true`, `authorityOverride = null` (no authority override), `rankOverride = 500` (all seed anchors receive the same rank), `rankMutationEnabled = false` (no rank changes during the run), and `authorityPromotionEnabled = true`.

#### Scenario: NO_RANK_DIFFERENTIATION flattens rank to 500

- **GIVEN** a scenario with seed anchors at ranks 200, 500, and 800
- **WHEN** the `NO_RANK_DIFFERENTIATION` condition is applied
- **THEN** all seed anchors SHALL have their rank set to 500
- **AND** rank mutation (decay, reinforcement) SHALL be disabled for the duration of the run

#### Scenario: NO_RANK_DIFFERENTIATION preserves original authority values

- **GIVEN** a scenario with seed anchors at authorities PROVISIONAL, UNRELIABLE, and RELIABLE
- **WHEN** the `NO_RANK_DIFFERENTIATION` condition is applied
- **THEN** the seed anchors SHALL retain their original authority values

### Requirement: Condition application timing

Ablation conditions SHALL be applied at the seed-anchor level BEFORE the simulation loop starts. Conditions SHALL NOT mutate live anchor state mid-run. Specifically, when a condition specifies an `authorityOverride` or `rankOverride`, those overrides SHALL be applied to the seed anchor definitions prior to their insertion into the simulation context, not to anchors that are already persisted in the simulation.

#### Scenario: Condition applied before simulation loop

- **GIVEN** a scenario with 3 seed anchors and the `FLAT_AUTHORITY` condition
- **WHEN** the simulation begins
- **THEN** the seed anchors SHALL be modified to have `authority = RELIABLE` before the first turn executes
- **AND** no anchor state SHALL be mutated by the condition after turn 1 begins

#### Scenario: Condition does not affect anchors created during simulation

- **GIVEN** a scenario running under `NO_RANK_DIFFERENTIATION` with `rankOverride = 500`
- **WHEN** a new anchor is created during the simulation via the normal promotion path
- **THEN** the newly created anchor's rank SHALL NOT be overridden to 500 by the condition
- **AND** the anchor SHALL receive whatever rank the promotion logic assigns

### Requirement: Condition extensibility

The ablation condition model SHOULD support custom conditions beyond the four built-in ones. A custom condition SHOULD be constructable by specifying values for each configuration field without modifying the condition type definition.

#### Scenario: Custom condition with specific overrides

- **GIVEN** a need to test with all anchors at rank 300 and authority UNRELIABLE
- **WHEN** a custom condition is created with `injectionEnabled = true`, `authorityOverride = UNRELIABLE`, `rankOverride = 300`, `rankMutationEnabled = true`, `authorityPromotionEnabled = false`
- **THEN** the condition SHALL be usable in an experiment definition alongside built-in conditions

### Requirement: Condition respects anchor invariants

Ablation conditions SHALL NOT violate the existing anchor invariants: A1 (active count <= budget), A2 (rank clamped to [100, 900]), A3 (explicit promotion only), A4 (authority upgrade-only). If a `rankOverride` value falls outside [100, 900], it SHALL be clamped using `Anchor.clampRank()`. If an `authorityOverride` would represent a downgrade for a seed anchor, the override SHALL still be applied because the condition is configuring seed state, not mutating live anchor state.

#### Scenario: Rank override clamped to valid range

- **GIVEN** a custom condition with `rankOverride = 50`
- **WHEN** the condition is applied to a seed anchor
- **THEN** the seed anchor's rank SHALL be set to 100 (the minimum after clamping)

#### Scenario: Rank override above maximum is clamped

- **GIVEN** a custom condition with `rankOverride = 1000`
- **WHEN** the condition is applied to a seed anchor
- **THEN** the seed anchor's rank SHALL be set to 900 (the maximum after clamping)

#### Scenario: Authority override applied regardless of direction

- **GIVEN** a seed anchor with authority `RELIABLE` and a condition with `authorityOverride = PROVISIONAL`
- **WHEN** the condition is applied
- **THEN** the seed anchor's authority SHALL be set to `PROVISIONAL` (condition configures seed state, not live state)

## Invariants

- **AC1**: Built-in conditions SHALL be immutable. Their configuration fields SHALL NOT change after construction.
- **AC2**: Condition application SHALL be idempotent. Applying the same condition to the same seed anchors multiple times SHALL produce the same result.
- **AC3**: Conditions SHALL NOT violate anchor invariants A1, A2, A3, A4 as defined in the CLAUDE.md architecture section.
