# Feature: Unified Maintenance Strategy

## Feature ID

`F02`

## Summary

Refactor `DecayPolicy` and `ReinforcementPolicy` into a unified `MaintenanceStrategy` interface that supports both reactive (current per-turn) and proactive (periodic sweep) maintenance models. The simulator MUST be able to configure and compare both models via scenario YAML. Current behavior becomes the `ReactiveMaintenanceStrategy` default -- zero behavioral change without explicit opt-in.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: Decay and reinforcement are reactive -- they happen per-turn in response to events. Anchor health is never proactively assessed. Degraded anchors persist until they happen to be touched by a conversation turn. Research (sleeping-llm) shows proactive maintenance cycles recover degraded knowledge from 40% to 100% recall within 4 sleep cycles.
2. **Value delivered**: A unified interface that supports reactive, proactive, and hybrid maintenance models, all testable in the simulator. This enables direct A/B comparison of maintenance approaches using existing adversarial scenarios.
3. **Why now**: Wave 1 foundation. This interface is foundational for F04 (memory pressure gauge triggers sweeps), F07 (proactive maintenance cycle implementation), and simulator A/B testing of maintenance approaches.

## Scope

### In Scope

1. `MaintenanceStrategy` interface with reactive, proactive, and hybrid mode support.
2. `ReactiveMaintenanceStrategy` wrapping current `DecayPolicy` + `ReinforcementPolicy` behavior.
3. `ProactiveMaintenanceStrategy` interface defining the periodic sweep contract (audit, refresh, consolidate, prune, validate steps).
4. `HybridMaintenanceStrategy` composing reactive per-turn hooks AND proactive sweep triggers.
5. Simulator configuration: `maintenanceStrategy: REACTIVE | PROACTIVE | HYBRID` per scenario YAML.
6. `DiceAnchorsProperties` config section for maintenance strategy selection and sweep parameters.
7. Backward compatibility: `DecayPolicy` and `ReinforcementPolicy` interfaces remain as public APIs.

### Out of Scope

1. Implementation of the proactive sweep logic (deferred to F07 -- this feature defines the interface only).
2. Memory pressure gauge integration (deferred to F04 -- this feature defines `shouldRunSweep()` but not the pressure calculation).
3. UI for strategy switching (configuration is via YAML/properties only).
4. Changes to existing decay/reinforcement algorithm behavior.

## Dependencies

1. Feature dependencies: none.
2. Priority: MUST.
3. OpenSpec change slug: `unified-maintenance-strategy`.
4. Research rec: A (partial -- interface definition; full implementation deferred to F07).

## Research Requirements

None. The reactive model is the existing implementation. The proactive model interface is derived from sleeping-llm's 8-step sleep cycle architecture. No open research questions for the interface definition.

## Impacted Areas

1. **`anchor/` package (primary)**: New types -- `MaintenanceStrategy` (interface), `MaintenanceMode` (enum: REACTIVE, PROACTIVE, HYBRID), `ReactiveMaintenanceStrategy` (wraps existing policies), `MaintenanceContext` (turn state for strategy decisions).
2. **`anchor/` package (refactor)**: `AnchorEngine` delegates decay/reinforcement through `MaintenanceStrategy` instead of directly calling `DecayPolicy`/`ReinforcementPolicy`. The delegation is transparent -- `ReactiveMaintenanceStrategy` calls the same policies.
3. **`DiceAnchorsProperties`**: New `maintenance` config section with `mode` (default: REACTIVE) and sweep parameters (thresholds, intervals).
4. **`sim/engine/` package**: `SimulationTurnExecutor` and scenario YAML loader consume the strategy selection. `ScenarioLoader` reads `maintenanceStrategy` from scenario YAML.
5. **Backward compatibility**: `DecayPolicy` and `ReinforcementPolicy` remain as injectable Spring beans. Code that injects these directly continues to work.

## Visibility Requirements

### UI Visibility

1. SimulationView SHOULD display the active maintenance strategy for each run.
2. RunInspectorView MUST display maintenance events (reactive decay/reinforcement, proactive sweep triggers) with strategy attribution.

### Observability Visibility

1. Maintenance strategy mode MUST be logged at simulation start: `maintenance.strategy.mode=REACTIVE|PROACTIVE|HYBRID`.
2. Proactive sweep triggers MUST emit structured log events: `maintenance.sweep.trigger` with context ID, trigger reason, and pressure score (when F04 is integrated).
3. Per-turn maintenance actions MUST be attributable to their strategy (reactive hook vs. proactive sweep step).

## Acceptance Criteria

1. `MaintenanceStrategy` interface MUST support reactive, proactive, and hybrid modes.
2. Current behavior (reactive decay + reinforcement) MUST be the default via `ReactiveMaintenanceStrategy` and MUST produce identical results to the current implementation.
3. Simulation scenarios MUST be able to specify maintenance strategy via a `maintenanceStrategy` field in scenario YAML.
4. `ReactiveMaintenanceStrategy` MUST delegate to existing `DecayPolicy` and `ReinforcementPolicy` implementations without behavioral change.
5. `ProactiveMaintenanceStrategy` MUST define the sweep contract (trigger condition, execution steps, completion callback) even though full implementation is deferred to F07.
6. Both strategies MUST be independently testable in the simulator with the same scenario, enabling A/B comparison of maintenance approaches.
7. `DecayPolicy` and `ReinforcementPolicy` interfaces MUST remain backward compatible -- existing code injecting these beans MUST NOT break.
8. `HybridMaintenanceStrategy` MUST compose reactive per-turn hooks AND proactive sweep triggers without interference.
9. Strategy selection MUST be configurable via `DiceAnchorsProperties` (global default) and scenario YAML (per-scenario override).

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Interface over-engineering** | Medium | Medium | Keep the interface minimal: `onTurnComplete()`, `shouldRunSweep()`, `executeSweep()`. Validate against F07's concrete use case before finalizing. |
| **Reactive strategy behavioral drift** | Low | High | The reactive strategy MUST be a thin wrapper. Unit tests MUST verify identical output for identical input compared to direct `DecayPolicy`/`ReinforcementPolicy` calls. |
| **HYBRID mode interaction effects** | Medium | Medium | Document HYBRID semantics clearly: reactive runs per-turn, proactive runs when triggered. If both modify the same anchor in the same turn, reactive wins (last-writer). |
| **Scenario YAML backward compatibility** | Low | Medium | Missing `maintenanceStrategy` field defaults to REACTIVE. No existing scenarios break. |

## Proposal Seed

### Change Slug

`unified-maintenance-strategy`

### Proposal Starter Inputs

1. **Problem statement**: Decay and reinforcement are reactive -- they happen per-turn in response to events. Anchor health is never proactively assessed. Degraded anchors persist until they happen to be touched. Research (sleeping-llm) shows proactive maintenance cycles recover degraded knowledge from 40% to 100% recall within 4 cycles.
2. **Why now**: This interface is foundational for F04 (pressure gauge), F07 (proactive maintenance), and simulator A/B testing.
3. **Constraints/non-goals**: MUST NOT break existing simulation scenarios. Reactive mode MUST be the default. Strategy SHOULD be selectable per-scenario. No implementation of proactive sweep logic (deferred to F07).
4. **Visible outcomes**: Strategy indicator in simulation runs. A/B comparison of reactive vs. proactive (once F07 implements the proactive strategy) using existing adversarial scenarios.

### Suggested Capability Areas

1. **Strategy interface**: `MaintenanceStrategy` with mode-specific implementations.
2. **Reactive wrapper**: Transparent delegation to existing `DecayPolicy` + `ReinforcementPolicy`.
3. **Proactive contract**: Interface for sweep trigger, execution, and completion.
4. **Configuration**: Strategy selection via properties and scenario YAML.

### Candidate Requirement Blocks

1. **REQ-INTERFACE**: The system SHALL define a `MaintenanceStrategy` interface supporting reactive, proactive, and hybrid maintenance modes.
2. **REQ-REACTIVE**: The `ReactiveMaintenanceStrategy` SHALL wrap existing `DecayPolicy` and `ReinforcementPolicy` with identical behavior.
3. **REQ-CONFIG**: The system SHALL support maintenance strategy selection via `DiceAnchorsProperties` and per-scenario YAML override.
4. **REQ-AB**: The simulator SHALL support running the same scenario with different maintenance strategies for A/B comparison.
5. **REQ-COMPAT**: Existing `DecayPolicy` and `ReinforcementPolicy` interfaces SHALL remain backward compatible.

## Validation Plan

1. **Unit tests** MUST verify `ReactiveMaintenanceStrategy` produces identical results to direct `DecayPolicy`/`ReinforcementPolicy` invocation for the same inputs.
2. **Unit tests** MUST verify strategy selection from `DiceAnchorsProperties`.
3. **Unit tests** MUST verify scenario YAML parsing for `maintenanceStrategy` field with default fallback.
4. **Integration test** SHOULD verify a simulation run with explicit `REACTIVE` strategy produces identical results to a run without strategy specification.
5. **Observability validation** MUST confirm strategy mode is logged at simulation start.
6. **Regression**: All existing simulation scenarios MUST pass without modification.

## Known Limitations

1. **Proactive strategy is interface-only in this feature.** `ProactiveMaintenanceStrategy` defines the contract but has no concrete implementation until F07. A no-op stub MAY be provided for testing purposes.
2. **HYBRID mode semantics are preliminary.** Interaction between reactive and proactive modifications to the same anchor in the same turn is defined as last-writer-wins. More sophisticated conflict resolution MAY be needed when F07 is implemented.
3. **No dynamic strategy switching.** Strategy is fixed at simulation start. Mid-run strategy switching (e.g., switch from reactive to hybrid when pressure exceeds threshold) is a candidate extension.

## Suggested Command

```
/opsx:new unified-maintenance-strategy
```
