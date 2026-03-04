# Simulation Turn Pipeline -- Delta Spec (wire-simulation-pipeline)

## ADDED Requirements

### REQ-TURN-SERVICES: SimulationTurnServices facade record

The system MUST define a `SimulationTurnServices` record in `dev.dunnam.diceanchors.sim.engine` that groups pipeline-phase dependencies:

| Component | Type | Description |
|-----------|------|-------------|
| `extractionService` | `SimulationExtractionService` | DICE extraction pipeline |
| `maintenanceStrategy` | `MaintenanceStrategy` | Per-turn and sweep coordination |
| `complianceEnforcer` | `ComplianceEnforcer` | Post-generation response validation |
| `pressureGauge` | `MemoryPressureGauge` | Memory pressure computation |

`SimulationTurnServices` MUST be a Spring bean created in the simulation configuration layer. It MUST use constructor injection for all four dependencies.

#### Scenario: Facade groups four dependencies
- **GIVEN** the four pipeline services exist as Spring beans
- **WHEN** `SimulationTurnServices` is constructed
- **THEN** all four services MUST be accessible via record accessors

### REQ-EXECUTOR-REFACTOR: SimulationTurnExecutor constructor uses facade

`SimulationTurnExecutor` MUST accept a `SimulationTurnServices` parameter instead of `SimulationExtractionService` directly. The constructor MUST extract `extractionService` from the facade for internal use while also holding the facade reference for maintenance, compliance, and pressure operations.

#### Scenario: Executor constructs with facade
- **GIVEN** a `SimulationTurnServices` instance
- **WHEN** `SimulationTurnExecutor` is constructed
- **THEN** the executor MUST access `extractionService` via `turnServices.extractionService()`
- **AND** the executor MUST access `maintenanceStrategy` via `turnServices.maintenanceStrategy()`

### REQ-CONTEXT-TRACE-EXTENSION: ContextTrace includes compliance and sweep snapshots

`ContextTrace` MUST be extended with three new fields:

| Field | Type | Description |
|-------|------|-------------|
| `complianceSnapshot` | `ComplianceSnapshot` | Compliance enforcement result for this turn |
| `injectionPatternsDetected` | `int` | Injection patterns found in player message |
| `sweepSnapshot` | `SweepSnapshot` | Maintenance sweep result for this turn |

Existing convenience constructors MUST default these fields to `ComplianceSnapshot.none()`, `0`, and `SweepSnapshot.none()` respectively, ensuring backward compatibility.

#### Scenario: Existing convenience constructor backward compatible
- **GIVEN** code using the 8-parameter ContextTrace constructor
- **WHEN** the code compiles after the extension
- **THEN** compilation MUST succeed
- **AND** `complianceSnapshot()` MUST return `ComplianceSnapshot.none()`
- **AND** `injectionPatternsDetected()` MUST return `0`
- **AND** `sweepSnapshot()` MUST return `SweepSnapshot.none()`

#### Scenario: Full constructor populates all fields
- **GIVEN** a ContextTrace constructed with compliance violations and a sweep result
- **WHEN** the trace is inspected
- **THEN** `complianceSnapshot().violationCount()` MUST match the violation count
- **AND** `sweepSnapshot().executed()` MUST be `true`

### REQ-PIPELINE-ORDER: Integration point ordering

The simulation turn pipeline MUST execute integration points in this order:

1. **Injection scan** -- `LoggingPromptInjectionEnforcer.scan(playerMessage)` before LLM call
2. **LLM generation** -- DM response via ChatModel
3. **Compliance check** -- `ComplianceEnforcer.enforce(dmResponse, anchors)` after generation
4. **Post-response** -- drift evaluation and extraction (parallel or sequential)
5. **Reinforcement** -- anchor reinforcement loop
6. **Maintenance** -- `onTurnComplete()` then conditional `shouldRunSweep()` / `executeSweep()`
7. **Dormancy** -- dormancy lifecycle
8. **Finalization** -- state diff, compaction, ContextTrace enrichment

Steps 1-3 are in the main turn methods (`executeTurn`, `executeTurnFullParallel`, `executeTurnFullSequential`). Steps 5-8 are in `buildResult()`.

#### Scenario: Compliance check precedes drift evaluation
- **GIVEN** a simulation turn with both compliance enforcement and drift evaluation
- **WHEN** the turn executes
- **THEN** `ComplianceEnforcer.enforce()` MUST complete before drift evaluation begins

#### Scenario: Maintenance follows reinforcement
- **GIVEN** a simulation turn with maintenance strategy wired
- **WHEN** `buildResult()` executes
- **THEN** reinforcement MUST complete before `onTurnComplete()` is called

### REQ-ERROR-ISOLATION: Pipeline extension errors MUST NOT fail turns

Errors from any pipeline extension point (injection scan, compliance check, maintenance strategy) MUST be caught, logged at WARN or ERROR level, and MUST NOT propagate to the caller. The turn MUST complete with default/empty values for the failed extension.

#### Scenario: ComplianceEnforcer throws
- **GIVEN** `ComplianceEnforcer.enforce()` throws a RuntimeException
- **WHEN** the turn executor catches the error
- **THEN** `ContextTrace.complianceSnapshot()` MUST be `ComplianceSnapshot.none()`
- **AND** the turn MUST complete normally with the DM response

#### Scenario: MaintenanceStrategy.onTurnComplete throws
- **GIVEN** `onTurnComplete()` throws a RuntimeException
- **WHEN** the turn executor catches the error
- **THEN** the error MUST be logged at ERROR level
- **AND** sweep check MUST be skipped for this turn
- **AND** the turn MUST complete normally

## Invariants

- **STP1**: `SimulationTurnServices` MUST be a record with exactly four components. Adding dependencies requires explicit design review.
- **STP2**: Pipeline extension errors MUST NOT fail turns. All new integration points are wrapped in try-catch with safe defaults.
- **STP3**: ContextTrace backward compatibility MUST be maintained. Existing convenience constructors MUST continue to compile without changes.
- **STP4**: The injection enforcer (`LoggingPromptInjectionEnforcer`) resides in `sim/engine`, not `assembly/`. It is simulation-specific.
- **STP5**: Compliance enforcement in simulation mode is observational. The `wouldHaveRetried` flag records what would have happened in production, but the simulation always proceeds.
