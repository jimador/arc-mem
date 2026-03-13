# Context Unit Maintenance Strategy -- Delta Spec (wire-simulation-pipeline)

## MODIFIED Requirements

### REQ-SIM-INTEGRATION: MaintenanceStrategy MUST be called from SimulationTurnExecutor

`SimulationTurnExecutor.buildResult()` MUST invoke `MaintenanceStrategy.onTurnComplete()` after the reinforcement loop completes. It MUST then check `shouldRunSweep()` and, if true, call `executeSweep()` and capture the `SweepResult`.

The `MaintenanceContext` passed to these methods MUST contain:
- `contextId` from the current simulation run
- `activeUnits` from the post-reinforcement context unit state
- `turnNumber` of the current turn
- `metadata` map with key `"pressureOverride"` set to `true` when `MemoryPressureGauge.computePressure().total() >= config.softPrunePressureThreshold()`

#### Scenario: onTurnComplete fires after reinforcement
- **GIVEN** a simulation turn that reinforces 3 context units
- **WHEN** `buildResult()` completes the reinforcement loop
- **THEN** `MaintenanceStrategy.onTurnComplete()` MUST be called exactly once
- **AND** the `MaintenanceContext` MUST contain the post-reinforcement context unit list

#### Scenario: Sweep triggered by turn interval
- **GIVEN** `minTurnsBetweenSweeps` is 5
- **AND** the last sweep was 6 turns ago
- **AND** memory pressure exceeds the light sweep threshold
- **WHEN** `shouldRunSweep()` is evaluated
- **THEN** it MUST return `true`
- **AND** `executeSweep()` MUST be called

#### Scenario: Sweep triggered by pressure override
- **GIVEN** `minTurnsBetweenSweeps` is 10
- **AND** the last sweep was 3 turns ago (below interval)
- **AND** `MemoryPressureGauge.computePressure().total() >= softPrunePressureThreshold`
- **WHEN** `shouldRunSweep()` is evaluated with `metadata.pressureOverride = true`
- **THEN** it MUST return `true` (pressure override bypasses turn interval)

#### Scenario: Sweep result captured on ContextTrace
- **GIVEN** a sweep executes during `buildResult()`
- **WHEN** the enriched `ContextTrace` is constructed
- **THEN** `sweepSnapshot().executed()` MUST be `true`
- **AND** `sweepSnapshot().summary()` MUST contain the `SweepResult.summary()`

#### Scenario: No sweep when conditions not met
- **GIVEN** memory pressure below threshold AND turn interval not elapsed
- **WHEN** `shouldRunSweep()` is evaluated
- **THEN** it MUST return `false`
- **AND** `executeSweep()` MUST NOT be called

### REQ-PRESSURE-OVERRIDE: ProactiveMaintenanceStrategy MUST support pressure override via metadata

`ProactiveMaintenanceStrategy.shouldRunSweep()` MUST check for a `"pressureOverride"` key in `MaintenanceContext.metadata()`. When this key is present and `true`, the turn-interval guard MUST be bypassed -- the sweep fires immediately regardless of turns since last sweep.

#### Scenario: Pressure override bypasses turn interval
- **GIVEN** a `MaintenanceContext` with `metadata = {"pressureOverride": true}`
- **AND** only 2 turns since last sweep (below `minTurnsBetweenSweeps`)
- **WHEN** `shouldRunSweep()` is called
- **THEN** it MUST return `true`

#### Scenario: No override without metadata key
- **GIVEN** a `MaintenanceContext` with null or empty metadata
- **AND** only 2 turns since last sweep
- **WHEN** `shouldRunSweep()` is called
- **THEN** it MUST return `false` (normal turn-interval logic applies)

## Invariants

All invariants from the base `unit-maintenance-strategy` spec remain in effect. Additionally:

- **I7**: MaintenanceStrategy invocation in the simulation loop MUST NOT block turn completion. Errors from onTurnComplete, shouldRunSweep, or executeSweep MUST be caught, logged, and ignored.
- **I8**: The sweep result MUST be informational only. It MUST NOT alter the turn's verdict or scoring outcome.
