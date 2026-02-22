## ADDED Requirements

### Requirement: TierChanged lifecycle event

The system SHALL publish a `TierChanged` lifecycle event whenever an anchor's `memoryTier` changes as a result of a rank-modifying operation (reinforce, decay, promote). The event SHALL be a member of the `AnchorLifecycleEvent` sealed hierarchy.

The `TierChanged` event SHALL include:
- `anchorId` (String)
- `previousTier` (MemoryTier)
- `newTier` (MemoryTier)
- `contextId` (String)
- `occurredAt` (Instant)

Event publishing SHALL be gated by `anchorConfig.lifecycleEventsEnabled()`, consistent with all other lifecycle events.

#### Scenario: Reinforcement causes tier upgrade event

- **GIVEN** an anchor in WARM tier with `rank = 580` and `hotThreshold = 600`
- **WHEN** `AnchorEngine.reinforce()` boosts rank to 630
- **THEN** a `TierChanged` event SHALL be published with `previousTier = WARM` and `newTier = HOT`

#### Scenario: Decay causes tier downgrade event

- **GIVEN** an anchor in WARM tier with `rank = 360` and `warmThreshold = 350`
- **WHEN** decay reduces rank to 340
- **THEN** a `TierChanged` event SHALL be published with `previousTier = WARM` and `newTier = COLD`

#### Scenario: Rank change without tier change

- **GIVEN** an anchor in HOT tier with `rank = 800`
- **WHEN** decay reduces rank to 750 (still above `hotThreshold = 600`)
- **THEN** no `TierChanged` event SHALL be published

#### Scenario: Events disabled

- **GIVEN** `anchorConfig.lifecycleEventsEnabled() = false`
- **WHEN** a tier transition occurs
- **THEN** no `TierChanged` event SHALL be published

### Requirement: AnchorEngine tier tracking

`AnchorEngine` SHALL compute and compare memory tier before and after every rank-modifying operation (`promote`, `reinforce`, `applyDecay`). When the tier changes, the engine SHALL:
1. Update the persisted `memoryTier` on the anchor
2. Publish a `TierChanged` event (if events enabled)

#### Scenario: Promote with tier tracking

- **GIVEN** a proposition promoted with `initialRank = 700`
- **WHEN** `AnchorEngine.promote()` completes
- **THEN** the anchor SHALL have `memoryTier = HOT` persisted and no `TierChanged` event (initial assignment, not a transition)

#### Scenario: Sequential reinforcements crossing tiers

- **GIVEN** an anchor with `rank = 340` (COLD, `warmThreshold = 350`)
- **WHEN** `AnchorEngine.reinforce()` boosts rank to 390
- **THEN** `memoryTier` SHALL be updated to WARM and a `TierChanged(COLD → WARM)` event SHALL be published
