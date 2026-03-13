## ADDED Requirements

### Requirement: Tier-based decay multiplier

The `ExponentialDecayPolicy` SHALL accept a `tierMultiplier` parameter that modulates the effective half-life. The formula SHALL be:

```
effectiveHalfLife = baseHalfLife / max(diceDecay, 0.01) * tierMultiplier
```

The `tierMultiplier` SHALL be configurable per tier via properties:
- `arc-mem.unit.tier.hot-decay-multiplier` (default `1.5`)
- `arc-mem.unit.tier.warm-decay-multiplier` (default `1.0`)
- `arc-mem.unit.tier.cold-decay-multiplier` (default `0.6`)

#### Scenario: HOT memory unit decays slower

- **GIVEN** a HOT memory unit with `rank = 700`, `baseHalfLife = 24h`, `diceDecay = 1.0`, `hotDecayMultiplier = 1.5`
- **WHEN** decay is applied after 24 hours
- **THEN** `effectiveHalfLife` SHALL be `36h` (24 / 1.0 * 1.5)
- **AND** the resulting rank SHALL be higher than if `tierMultiplier` were `1.0`

#### Scenario: WARM memory unit decays at baseline

- **GIVEN** a WARM memory unit with `rank = 450`, `baseHalfLife = 24h`, `diceDecay = 1.0`, `warmDecayMultiplier = 1.0`
- **WHEN** decay is applied after 24 hours
- **THEN** `effectiveHalfLife` SHALL be `24h` (unchanged from current behavior)

#### Scenario: COLD memory unit decays faster

- **GIVEN** a COLD memory unit with `rank = 200`, `baseHalfLife = 24h`, `diceDecay = 1.0`, `coldDecayMultiplier = 0.6`
- **WHEN** decay is applied after 24 hours
- **THEN** `effectiveHalfLife` SHALL be `14.4h` (24 / 1.0 * 0.6)
- **AND** the resulting rank SHALL be lower than if `tierMultiplier` were `1.0`

### Requirement: Tier multiplier composes with diceDecay

The tier multiplier SHALL compose multiplicatively with `diceDecay`. A memory unit with `diceDecay = 2.0` (ephemeral) in the COLD tier SHALL decay faster than either signal alone.

#### Scenario: Ephemeral COLD memory unit

- **GIVEN** a COLD memory unit with `diceDecay = 2.0` and `coldDecayMultiplier = 0.6`
- **WHEN** decay is applied with `baseHalfLife = 24h`
- **THEN** `effectiveHalfLife` SHALL be `7.2h` (24 / 2.0 * 0.6)

#### Scenario: Permanent HOT memory unit

- **GIVEN** a HOT memory unit with `diceDecay = 0.0` and `hotDecayMultiplier = 1.5`
- **WHEN** decay is applied
- **THEN** `effectiveHalfLife` SHALL be very large (24 / 0.01 * 1.5 = 3600h), resulting in negligible decay

### Requirement: Decay multiplier validation

Tier decay multipliers SHALL be positive (> 0). The system SHALL validate this on startup and fail with a descriptive error if any multiplier is zero or negative.

#### Scenario: Zero multiplier rejected

- **GIVEN** `cold-decay-multiplier = 0`
- **WHEN** the application starts
- **THEN** startup SHALL fail with an error indicating decay multipliers MUST be positive

### Requirement: Backward compatibility

When no tier configuration is provided, all memory units SHALL use `tierMultiplier = 1.0` (WARM default), preserving identical decay behavior to the pre-tiering system.

#### Scenario: No tier config

- **GIVEN** no `arc-mem.unit.tier.*` properties configured
- **WHEN** decay is applied to any memory unit
- **THEN** behavior SHALL be identical to the current `ExponentialDecayPolicy` without tiering
