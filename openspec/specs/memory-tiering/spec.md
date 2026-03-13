## ADDED Requirements

### Requirement: MemoryTier enum

The system SHALL provide a `MemoryTier` enum with values `COLD`, `WARM`, `HOT` ordered by ascending priority. `MemoryTier.compareTo()` SHALL reflect this ordering (`COLD < WARM < HOT`). Documentation SHALL reference these names exclusively; aliases or prefixed variants (`T0_`/`T1_`/`T2_`) SHALL NOT appear in any project documentation or specs.

#### Scenario: Enum ordering

- **GIVEN** the `MemoryTier` enum
- **WHEN** comparing tier values
- **THEN** `COLD.compareTo(WARM) < 0` AND `WARM.compareTo(HOT) < 0`

#### Scenario: Documentation consistency

- **GIVEN** all project documentation (CLAUDE.md, openspec/project.md, specs)
- **WHEN** referencing MemoryTier values
- **THEN** only `COLD`, `WARM`, `HOT` SHALL appear; no `T0_INVARIANT`, `T1_WORKING`, `T2_EPISODIC` references SHALL exist

### Requirement: Tier boundary thresholds

The system SHALL compute a memory unit's memory tier from its current rank using two configurable thresholds:
- `rank >= hotThreshold` → HOT
- `rank >= warmThreshold AND rank < hotThreshold` → WARM
- `rank < warmThreshold` → COLD

Default thresholds SHALL be `hotThreshold = 600` and `warmThreshold = 350`. Thresholds SHALL be configurable via `arc-mem.unit.tier.hot-threshold` and `arc-mem.unit.tier.warm-threshold` properties.

#### Scenario: Memory unit in HOT tier

- **GIVEN** `hotThreshold = 600`
- **WHEN** a memory unit has `rank = 650`
- **THEN** its `memoryTier` SHALL be `HOT`

#### Scenario: Memory unit in WARM tier

- **GIVEN** `hotThreshold = 600` and `warmThreshold = 350`
- **WHEN** a memory unit has `rank = 450`
- **THEN** its `memoryTier` SHALL be `WARM`

#### Scenario: Memory unit in COLD tier

- **GIVEN** `warmThreshold = 350`
- **WHEN** a memory unit has `rank = 200`
- **THEN** its `memoryTier` SHALL be `COLD`

#### Scenario: Memory unit at exact boundary

- **GIVEN** `hotThreshold = 600`
- **WHEN** a memory unit has `rank = 600`
- **THEN** its `memoryTier` SHALL be `HOT`

### Requirement: Tier on ContextUnit record

The `ContextUnit` record SHALL include a `memoryTier` field of type `MemoryTier`. The field SHALL be set on construction and SHALL be consistent with the memory unit's rank and the configured thresholds.

#### Scenario: Memory unit record exposes tier

- **GIVEN** a memory unit with `rank = 500` and `warmThreshold = 350`, `hotThreshold = 600`
- **WHEN** the `ContextUnit` record is constructed
- **THEN** `unit.memoryTier()` SHALL return `WARM`

### Requirement: Tier persistence

The `PropositionNode` SHALL persist `memoryTier` as a string property on the Neo4j `Proposition` node. The persisted value SHALL be the enum name (`HOT`, `WARM`, `COLD`).

#### Scenario: Tier saved to Neo4j

- **GIVEN** a memory unit promoted with `rank = 700`
- **WHEN** the memory unit is persisted
- **THEN** the `Proposition` node SHALL have property `memoryTier = "HOT"`

#### Scenario: Tier loaded from Neo4j

- **GIVEN** a `Proposition` node with `memoryTier = "COLD"`
- **WHEN** the node is loaded as an `ContextUnit`
- **THEN** `unit.memoryTier()` SHALL return `MemoryTier.COLD`

### Requirement: Tier computed on promotion

When a proposition is promoted to a memory unit via `ArcMemEngine.promote()`, the system SHALL compute the initial `memoryTier` from the `initialRank` and persist it.

#### Scenario: Promotion sets initial tier

- **GIVEN** `hotThreshold = 600` and a proposition promoted with `initialRank = 500`
- **WHEN** `ArcMemEngine.promote()` completes
- **THEN** the new memory unit SHALL have `memoryTier = WARM`

### Requirement: Tier updated on reinforcement

When `ArcMemEngine.reinforce()` boosts a memory unit's rank, the system SHALL recompute `memoryTier` and update the persisted value.

#### Scenario: Reinforcement causes tier upgrade

- **GIVEN** a memory unit with `rank = 580` (WARM) and `hotThreshold = 600`
- **WHEN** `ArcMemEngine.reinforce()` applies a +50 rank boost
- **THEN** the memory unit's `memoryTier` SHALL change to `HOT`

### Requirement: Tier updated on decay

When decay reduces a memory unit's rank, the system SHALL recompute `memoryTier` and update the persisted value.

#### Scenario: Decay causes tier downgrade

- **GIVEN** a memory unit with `rank = 360` (WARM) and `warmThreshold = 350`
- **WHEN** decay reduces rank to `340`
- **THEN** the memory unit's `memoryTier` SHALL change to `COLD`

### Requirement: Threshold validation

The system SHALL validate on startup that `hotThreshold > warmThreshold`, both are within [100, 900], and `warmThreshold >= 100`. Invalid configuration SHALL prevent application startup with a descriptive error.

#### Scenario: Invalid threshold order

- **GIVEN** `hotThreshold = 300` and `warmThreshold = 500`
- **WHEN** the application starts
- **THEN** startup SHALL fail with an error indicating `hotThreshold` MUST be greater than `warmThreshold`

### Requirement: Migration of existing memory units

On first startup after deployment, existing active memory units without a `memoryTier` property SHALL have their tier computed from their current rank and persisted.

#### Scenario: Legacy memory unit gets tier

- **GIVEN** an active memory unit with `rank = 450` and no `memoryTier` property
- **WHEN** the application starts
- **THEN** the memory unit SHALL have `memoryTier = "WARM"` persisted

## Invariants

- **T1**: `memoryTier` SHALL always be consistent with current rank and configured thresholds after any rank-modifying operation.
- **T2**: Pinned memory units SHALL have their tier computed normally from rank. Pinning affects eviction immunity, not tier classification.
- **T3**: CANON authority memory units SHALL have their tier computed normally from rank. Authority and tier are orthogonal dimensions.
