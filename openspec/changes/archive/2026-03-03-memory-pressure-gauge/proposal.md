## Why

dice-anchors has no quantitative measure of anchor system health. Budget enforcement is binary (over or under the cap), decay is purely reactive, and conflict detection operates per-event with no aggregate trend signal. There is no early warning for degraded states: a maintenance sweep triggered too late finds irrecoverable damage, while one triggered too early wastes resources.

Research from the Sleeping LLM project (Guo et al., 2025, "Per-Fact Graduated Consolidation Resolves the Capacity Ceiling in Weight-Edited Language Models", DOI: 10.5281/zenodo.18779159) demonstrates that a composite drowsiness signal -- combining edit pressure, time pressure, and perplexity pressure -- effectively triggers maintenance before quality degrades. Their health monitor uses a 3-dimensional weighted formula with non-linear edit pressure (exponent 1.5) and thresholds at 0.4 (nap) and 0.8 (full sleep). This pattern maps directly to anchor memory: budget utilization is analogous to edit pressure, conflict rate to perplexity pressure, and decay activity to time pressure.

The `MaintenanceStrategy` interface (F02) provides `shouldRunSweep(MaintenanceContext)` as the integration point. Without a pressure gauge, `ProactiveMaintenanceStrategy` (F07) would need to invent its own trigger heuristic. A shared pressure metric enables consistent, configurable threshold-based triggering across all maintenance modes.

## What Changes

- **New `MemoryPressureGauge` Spring `@Component`** in `anchor/` package: computes a composite memory pressure score in the range [0.0, 1.0] from four weighted dimensions (budget, conflict, decay, compaction). Subscribes to `AnchorLifecycleEvent` emissions for event-driven updates. Maintains per-turn pressure history per context.
- **New `PressureScore` record**: immutable snapshot with total pressure + per-dimension breakdown. One snapshot per gauge evaluation.
- **New `PressureDimension` enum**: BUDGET, CONFLICT, DECAY, COMPACTION. Each dimension has a default weight and computation strategy.
- **New `PressureThreshold` record**: configurable light-sweep and full-sweep threshold levels.
- **New `PressureThresholdBreached` event**: extends `AnchorLifecycleEvent`. Published when pressure crosses a configured threshold. Consumable by `MaintenanceStrategy`.
- **`DiceAnchorsProperties` updated**: new `PressureConfig` nested record with weights, thresholds, exponents, and sliding window size.
- **`AnchorLifecycleEvent` updated**: new `PressureThresholdBreached` added to the sealed permits list.
- **`AnchorLifecycleListener` updated**: new handler logs threshold breach events at WARN level.

## Capabilities

### New Capabilities

- `memory-pressure-gauge`: Composite memory pressure computation with four weighted dimensions, configurable thresholds, event-driven updates, per-turn history tracking, and threshold breach events. Purely computational -- no LLM calls.

### Modified Capabilities

- `anchor-lifecycle-events`: Extended with `PressureThresholdBreached` event type.
- `dice-anchors-properties`: Extended with `PressureConfig` nested record.

## Impact

- **`anchor/` package**: 4 new files -- `MemoryPressureGauge.java`, `PressureScore.java`, `PressureDimension.java`, `PressureThreshold.java`
- **`anchor/event/AnchorLifecycleEvent.java`**: New `PressureThresholdBreached` inner class added to sealed permits list
- **`anchor/event/AnchorLifecycleListener.java`**: New `@EventListener` handler for `PressureThresholdBreached`
- **`DiceAnchorsProperties.java`**: New `PressureConfig` nested record
- **`application.yml`**: New `dice-anchors.pressure` configuration section
- **Behavioral change**: None by default. Pressure is computed but no actions are taken -- F07 consumes the signal. Threshold breach events are emitted and logged at WARN level.
- **Breaking changes**: None. `AnchorLifecycleEvent` sealed hierarchy is additive (new permitted subclass). All existing APIs continue unchanged.

## Constitutional Alignment

- **Article I (RFC 2119)**: All requirements in the spec use RFC 2119 keywords.
- **Article III (Constructor Injection)**: `MemoryPressureGauge` uses constructor injection for `AnchorRepository` and `DiceAnchorsProperties`. No `@Autowired` annotations.
- **Article IV (Records for Immutable Data)**: `PressureScore`, `PressureThreshold`, and `PressureConfig` are Java records. `PressureDimension` is an enum.
- **Article V (Anchor Invariants)**: Anchor invariants A1-A4 are preserved. The pressure gauge is read-only -- it observes anchor state but does not modify ranks, budgets, authority, or promotion rules.
- **Article VII (Test-First for Domain Logic)**: `MemoryPressureGauge` receives unit test coverage for deterministic pressure computation, threshold breach detection, and dimension independence.
