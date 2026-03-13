# Prep: Unified Maintenance Strategy

**Feature**: F02 — `unified-maintenance-strategy`
**Wave**: 1
**Priority**: MUST
**Depends on**: none

## RFC 2119 Compliance

All normative statements use RFC 2119 keywords.

## Locked Decisions

1. **MaintenanceStrategy interface**: Single interface with three methods: `onTurnComplete(MaintenanceContext)` (reactive hook), `shouldRunSweep(MaintenanceContext) -> boolean` (proactive trigger), `executeSweep(MaintenanceContext) -> SweepResult` (proactive execution).
2. **Reactive wraps existing policies**: `ReactiveMaintenanceStrategy` delegates to `DecayPolicy.applyDecay()` and `ReinforcementPolicy.calculateRankBoost()` + `shouldUpgradeAuthority()`. Identical behavior to current code path in `ArcMemEngine`.
3. **Simulator strategy selection**: Scenario YAML gains `maintenanceStrategy: REACTIVE | PROACTIVE | HYBRID` field. Missing field defaults to `REACTIVE`. `ScenarioLoader` reads and validates the field.
4. **Backward compatibility**: `DecayPolicy` and `ReinforcementPolicy` interfaces remain as public APIs. Code injecting these beans directly continues to work. `MaintenanceStrategy` composes them; it does not replace them.
5. **Three mode enum**: `MaintenanceMode` enum with `REACTIVE`, `PROACTIVE`, `HYBRID`. Strategy implementations are selected by mode via Spring configuration.

## Open Questions

| # | Question | Options | Leaning | Resolution Trigger |
|---|----------|---------|---------|-------------------|
| 1 | **Proactive sweep step ordering** | (a) Fixed order: audit -> refresh -> consolidate -> prune -> validate (sleeping-llm 5 of 8 applicable steps). (b) Configurable step ordering. (c) Steps defined by implementation, interface only specifies `executeSweep()`. | (c) Interface only. `ProactiveMaintenanceStrategy` defines `executeSweep()` as a single method; step ordering is an implementation detail of F07. This feature defines the contract, not the algorithm. | Design phase for F07. |
| 2 | **HYBRID mode interaction semantics** | (a) Reactive runs per-turn unconditionally; proactive runs when triggered. If both modify the same context unit in the same turn, reactive result is overwritten by proactive. (b) Same as (a) but proactive wins. (c) Proactive sweep is deferred to end-of-turn, after reactive completes. | (c) Sequential: reactive first (during turn), proactive second (end-of-turn if triggered). No conflict because they run in sequence. | Design phase. Validate with F04 pressure gauge integration. |
| 3 | **Config schema for proactive parameters** | (a) Flat properties: `maintenance.proactive.sweepInterval`, `maintenance.proactive.pressureThreshold`. (b) Nested config record: `maintenance.proactive.*` mapped to a `ProactiveMaintenanceConfig` record. (c) Defer proactive config to F07; this feature only adds `maintenance.mode`. | (c) Defer. This feature adds `maintenance.mode` only. Proactive parameters are defined when F07 provides concrete implementation. Avoids speculative config that changes later. | F07 design phase. |
| 4 | **ArcMemEngine delegation** | (a) `ArcMemEngine` calls `MaintenanceStrategy.onTurnComplete()` directly after decay/reinforcement. (b) `SimulationTurnExecutor` calls `MaintenanceStrategy` instead of `ArcMemEngine` calling decay/reinforcement directly. (c) Both: engine delegates internally, executor has override capability. | (a) `ArcMemEngine` delegates internally. This keeps the strategy transparent to callers. `SimulationTurnExecutor` calls `engine.applyDecay()` as before; engine internally routes through `MaintenanceStrategy`. | Design phase. |

## Visibility Contract

| Surface | What | When | Format |
|---------|------|------|--------|
| SimulationView | Active maintenance strategy per run | Run start | Badge: `REACTIVE` / `PROACTIVE` / `HYBRID` |
| RunInspectorView | Maintenance events with strategy attribution | Per-turn | Event list: `[REACTIVE] decay applied`, `[PROACTIVE] sweep triggered` |
| Structured logs | Strategy mode at simulation start | Simulation start | INFO: `maintenance.strategy.mode=REACTIVE` |
| Structured logs | Sweep trigger events | When proactive threshold breached | WARN: `maintenance.sweep.trigger context={id} reason={pressure}` |

## Acceptance Gates

| Gate | Verification | Command |
|------|-------------|---------|
| Reactive strategy produces identical results to current code | Unit test: same context unit + same hours-since-reinforcement -> identical rank after `ReactiveMaintenanceStrategy.onTurnComplete()` vs direct `DecayPolicy.applyDecay()`. | `./mvnw test -pl . -Dtest=ReactiveMaintenanceStrategyTest` |
| Strategy selectable per scenario | Unit test: parse scenario YAML with `maintenanceStrategy: HYBRID`, verify strategy is `HYBRID`. Parse YAML without the field, verify default `REACTIVE`. | `./mvnw test -pl . -Dtest=ScenarioLoaderTest` |
| DecayPolicy/ReinforcementPolicy backward compatible | Regression: all existing tests pass without modification. | `./mvnw test` |
| Proactive contract defined (no-op implementation) | Unit test: `ProactiveMaintenanceStrategy` no-op stub can be instantiated and called without error. | `./mvnw test -pl . -Dtest=ProactiveMaintenanceStrategyTest` |

## Small-Model Constraints

- **Max 4 files per task** (interface + implementations + config + tests)
- **Verification**: `./mvnw test` MUST pass after each task
- **No behavioral change**: Reactive strategy MUST be a transparent wrapper
- **Scope boundary**: `context unit/` package for strategy types; `sim/engine/` for scenario config only

## Task Sketch

| # | Scope | Files | Gate |
|---|-------|-------|------|
| T1 | `MaintenanceStrategy` interface + `MaintenanceMode` enum + `MaintenanceContext` record | `MaintenanceStrategy.java`, `MaintenanceMode.java`, `MaintenanceContext.java` | Interface compiles, mode enum has 3 values |
| T2 | `ReactiveMaintenanceStrategy` wrapping `DecayPolicy` + `ReinforcementPolicy` | `ReactiveMaintenanceStrategy.java`, `ReactiveMaintenanceStrategyTest.java` | Identical output to direct policy invocation |
| T3 | `ArcMemEngine` delegation through `MaintenanceStrategy` | `ArcMemEngine.java` (refactor), `ArcMemEngineTest.java` (verify) | All existing engine tests pass |
| T4 | Scenario YAML `maintenanceStrategy` field + `ScenarioLoader` parsing + `ArcMemProperties` config | `ScenarioLoader.java`, `ArcMemProperties.java`, `ScenarioLoaderTest.java` | YAML parsing with default fallback |

## Risks Requiring Design Attention

1. **ArcMemEngine refactor scope**: Delegating through `MaintenanceStrategy` requires changing how `ArcMemEngine.applyDecay()` and `reinforce()` invoke the policies. Must be a transparent refactor -- no test failures.
2. **Scenario YAML schema**: Adding an optional field to scenario YAML. Existing scenarios without the field MUST default to REACTIVE. Verify all existing scenario YAML files parse correctly after the change.
