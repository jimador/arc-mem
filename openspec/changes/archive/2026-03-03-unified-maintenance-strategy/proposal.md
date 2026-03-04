## Why

Decay and reinforcement in dice-anchors are purely reactive -- they fire per-turn in response to conversation events. Anchor health is never proactively assessed: degraded anchors persist silently until a turn happens to touch them. Research from the Sleeping LLM project (Guo et al., 2025) demonstrates that proactive memory consolidation cycles recover degraded knowledge from 40% to 100% recall within 4 sleep cycles, confirming that reactive-only maintenance is insufficient for long-horizon memory stability.

A unified maintenance interface provides three benefits:
1. **Foundation for proactive maintenance (F07)**: Defines the sweep contract that F07 will implement with real consolidation logic.
2. **Foundation for memory pressure (F04)**: The `shouldRunSweep()` hook is the integration point where pressure thresholds trigger consolidation.
3. **Testability**: A single coordination interface enables A/B comparison of maintenance approaches in the simulation harness.

## What Changes

- **New `MaintenanceStrategy` sealed interface** in `anchor/` package with three methods: `onTurnComplete()` (reactive hook), `shouldRunSweep()` (proactive trigger), `executeSweep()` (proactive execution).
- **Three implementations**: `ReactiveMaintenanceStrategy` (wraps existing `DecayPolicy` + `ReinforcementPolicy`), `ProactiveMaintenanceStrategy` (stub for F07), `HybridMaintenanceStrategy` (composes reactive + proactive).
- **New `MaintenanceMode` enum**: REACTIVE, PROACTIVE, HYBRID.
- **New `MaintenanceContext` record**: contextId, activeAnchors, turnNumber, optional metadata map.
- **New `SweepResult` record**: counters for the 5-step sweep contract (audit, refresh, consolidate, prune, validate) plus duration and summary.
- **`DiceAnchorsProperties.MaintenanceConfig`**: New nested config record with `mode` field (default: REACTIVE).
- **`AnchorConfiguration` bean**: Produces the appropriate `MaintenanceStrategy` based on configured mode.
- **`application.yml`**: Default maintenance mode set to REACTIVE.

`DecayPolicy` and `ReinforcementPolicy` are NOT modified or replaced. They remain as independent Spring beans. `ReactiveMaintenanceStrategy` holds references to them for future scheduling coordination (F07) but does not alter their invocation semantics.

## Capabilities

### New Capabilities

- `anchor-maintenance-strategy`: Unified coordination interface for anchor memory maintenance with reactive, proactive, and hybrid modes. Provides the scheduling contract for when and how decay, reinforcement, and sweep operations execute relative to the conversation turn cycle.

### Modified Capabilities

- None. `DecayPolicy` and `ReinforcementPolicy` remain unchanged. `AnchorEngine` continues to invoke them directly; the strategy holds references for future use.

## Impact

- **`anchor/` package**: 7 new files -- `MaintenanceStrategy.java`, `MaintenanceMode.java`, `MaintenanceContext.java`, `SweepResult.java`, `ReactiveMaintenanceStrategy.java`, `ProactiveMaintenanceStrategy.java`, `HybridMaintenanceStrategy.java`
- **`DiceAnchorsProperties.java`**: New `MaintenanceConfig` nested record with `MaintenanceMode mode`
- **`AnchorConfiguration.java`**: New `@Bean` method producing `MaintenanceStrategy` based on configured mode
- **`application.yml`**: New `dice-anchors.maintenance.mode: REACTIVE` default
- **Behavioral change**: None. REACTIVE mode is the default and produces identical behavior to the current codebase. Proactive and hybrid modes are stubs until F07.
- **Breaking changes**: None. All existing APIs, beans, and configurations continue to work unchanged.

## Constitutional Alignment

- **Article I (RFC 2119)**: All requirements in the spec use RFC 2119 keywords.
- **Article III (Constructor Injection)**: All new strategy classes use constructor injection. No `@Autowired` annotations.
- **Article IV (Records for Immutable Data)**: `MaintenanceContext`, `SweepResult`, and `MaintenanceConfig` are Java records.
- **Article V (Anchor Invariants)**: Anchor invariants A1-A4 are preserved. The maintenance strategy coordinates scheduling; it does not modify rank clamping, budget enforcement, promotion rules, or authority transitions.
- **Article VII (Test-First for Domain Logic)**: `ReactiveMaintenanceStrategy` is the only implementation with business logic (delegation to existing policies); it receives unit test coverage.
