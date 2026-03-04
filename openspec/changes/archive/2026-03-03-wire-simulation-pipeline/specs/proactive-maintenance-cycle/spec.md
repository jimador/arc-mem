# Proactive Maintenance Cycle -- Delta Spec (wire-simulation-pipeline)

## MODIFIED Requirements

### REQ-DUAL-TRIGGER: shouldRunSweep supports pressure override

`ProactiveMaintenanceStrategy.shouldRunSweep()` MUST support a dual-condition trigger. The existing logic checks turn interval AND pressure threshold. The new pressure override adds an OR path:

```
shouldSweep =
    (existing logic: pressure >= lightThreshold AND turnsSinceLastSweep >= minTurnsBetweenSweeps)
    OR
    (metadata["pressureOverride"] == true)
```

When `MaintenanceContext.metadata()` contains key `"pressureOverride"` with value `Boolean.TRUE`, the sweep MUST fire regardless of the turn interval. The pressure threshold check is still performed by the caller (SimulationTurnExecutor) before setting the override flag.

#### Scenario: Override fires sweep despite recent previous sweep
- **GIVEN** last sweep was 2 turns ago (minTurnsBetweenSweeps = 10)
- **AND** metadata contains `"pressureOverride" = true`
- **WHEN** `shouldRunSweep()` is called
- **THEN** it MUST return `true`

#### Scenario: Normal path unchanged without override
- **GIVEN** last sweep was 2 turns ago (minTurnsBetweenSweeps = 10)
- **AND** metadata is null or does not contain `"pressureOverride"`
- **AND** pressure is above light threshold
- **WHEN** `shouldRunSweep()` is called
- **THEN** it MUST return `false` (turn interval not met)

### REQ-NULL-METADATA: Null metadata handling

`shouldRunSweep()` MUST handle null `metadata` gracefully. When `metadata` is null, it MUST be treated as an empty map -- no pressure override, no crash.

#### Scenario: Null metadata does not throw
- **GIVEN** a `MaintenanceContext` with `metadata = null`
- **WHEN** `shouldRunSweep()` is called
- **THEN** it MUST NOT throw
- **AND** the normal turn-interval + pressure logic applies

## Invariants

All invariants from the base `proactive-maintenance-cycle` spec remain in effect.
