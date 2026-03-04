# Wire Simulation Pipeline

## Problem

Waves 1-4 introduced MaintenanceStrategy, ComplianceEnforcer, MemoryPressureGauge, and ProactiveMaintenanceStrategy APIs, but none are called from the simulation turn loop. `SimulationTurnExecutor.buildResult()` reinforces anchors and applies dormancy decay, then exits -- it never invokes `MaintenanceStrategy.onTurnComplete()`, never checks `shouldRunSweep()`, and never calls `ComplianceEnforcer.enforce()` on the DM response. The PromptInjectionEnforcer is a no-op stub that returns `ComplianceResult.compliant(Duration.ZERO)` unconditionally, offering no observability into injection-pattern frequency in adversary messages.

These are dead APIs in the critical path.

## Proposed Solution

Wire the existing Wave 1-4 APIs into SimulationTurnExecutor at well-defined integration points:

1. **MaintenanceStrategy.onTurnComplete()** -- call after reinforcement in `buildResult()`. If `shouldRunSweep()` returns true (dual-condition: turns-since-last-sweep OR memory pressure threshold), execute the sweep and capture the result.

2. **ComplianceEnforcer.enforce()** -- call after the DM response is generated, before drift evaluation. Always ACCEPT in simulation mode (no retry loop), but log violations and surface them on ContextTrace for analysis.

3. **LoggingPromptInjectionEnforcer** -- new implementation replacing the no-op PromptInjectionEnforcer for simulation use. Heuristic scan of player messages for injection patterns. Always returns ACCEPT but populates injection detection count on ContextTrace.

4. **ContextTrace extension** -- add compliance result fields, injection detection count, and sweep result summary.

## Constructor Dependency Concern

SimulationTurnExecutor currently has 9 constructor parameters. Adding MaintenanceStrategy, ComplianceEnforcer, and MemoryPressureGauge would push it to 12, violating the project's 3-4 deps guideline. The design MUST introduce a facade to group related dependencies.

## Scope

- SimulationTurnExecutor (primary integration target)
- ContextTrace (record extension)
- LoggingPromptInjectionEnforcer (new class)
- SimulationTurnServices facade (new record)
- Test updates for constructor changes

## Out of Scope

- Retry loops for non-compliant DM responses (future work)
- Constrained decoding integration (F12)
- Chat flow compliance wiring (separate change)
