## Context

dice-anchors manages anchor health through reactive policies (decay, reinforcement) and a lifecycle event system. The `MaintenanceStrategy` sealed interface (F02) provides `shouldRunSweep(MaintenanceContext)` as the integration point for proactive maintenance triggers, but no pressure signal exists to drive those triggers.

The Sleeping LLM project (Guo et al., 2025) demonstrates a 3-dimensional health monitor that combines edit pressure, time pressure, and perplexity pressure into a composite drowsiness signal with thresholds at 0.4 (nap) and 0.8 (full sleep). Their edit pressure dimension uses a non-linear exponent of 1.5, creating a convex curve where pressure accelerates near capacity. This pattern maps to anchor memory: budget utilization replaces edit count, conflict rate replaces perplexity, and decay activity replaces time pressure. We add a fourth dimension (compaction pressure) that has no Sleeping LLM analog but addresses a dice-anchors-specific concern.

### Current Code Organization

```
anchor/
  AnchorEngine.java              # Core engine, publishes lifecycle events
  Anchor.java                    # Immutable anchor view
  MaintenanceStrategy.java       # Sealed interface (F02)
  MaintenanceContext.java        # Runtime context record
  attention/AttentionWindow.java # Sliding-window event tracking (per-anchor)

anchor/event/
  AnchorLifecycleEvent.java      # Sealed event hierarchy (10 subtypes)
  AnchorLifecycleListener.java   # Default event logger
  AnchorEventConfiguration.java  # Bean wiring

DiceAnchorsProperties.java       # Config records
AnchorConfiguration.java         # Bean factories
```

## Goals / Non-Goals

**Goals:**
- Compute a composite pressure score [0.0, 1.0] from four weighted dimensions (budget, conflict, decay, compaction).
- Emit threshold breach events when pressure crosses configurable light-sweep (0.4) and full-sweep (0.8) thresholds.
- Make all parameters (weights, thresholds, exponents, window size) configurable via `DiceAnchorsProperties`.
- Maintain per-turn pressure snapshots for trend analysis within a context's lifetime.
- Subscribe to lifecycle events for event-driven counter updates.

**Non-Goals:**
- Implementing pressure-triggered maintenance actions (F07 consumes the signal).
- Cross-context pressure aggregation (each context is independent).
- Persistent pressure history (in-memory only, lost on context cleanup).
- UI visualization of pressure (future enhancement).
- LLM-based pressure assessment (pressure is purely computational).

## Decisions

### D1: Composite Score Formula

**Decision**: The pressure score is a weighted sum of four independently-computed dimensions, each clamped to [0.0, 1.0] before weighting. The total is clamped to [0.0, 1.0] after summation.

```
total = clamp(w_budget * budget + w_conflict * conflict + w_decay * decay + w_compaction * compaction, 0.0, 1.0)
```

**Why weighted sum over max/geometric mean**: A weighted sum allows operators to tune the relative importance of each dimension. Budget pressure dominates (0.4 weight) because it is the most direct capacity signal. Geometric mean would penalize zero-valued dimensions excessively (any dimension at 0.0 collapses the total to 0.0). Max-based aggregation loses information about co-occurring pressures.

**Research attribution**: The weighted sum structure mirrors Sleeping LLM's drowsiness formula: `pressure = (0.6 * edit_pressure) + (0.3 * time_pressure) + (0.1 * perplexity_pressure)`. We redistribute weights across four dimensions instead of three and reduce the edit (budget) weight from 0.6 to 0.4 because conflict rate is a stronger degradation signal in the anchor domain than time-based pressure.

### D2: Non-Linear Budget Pressure Exponent

**Decision**: Budget pressure uses `(activeCount / budgetCap) ^ exponent` with a configurable exponent defaulting to 1.5.

```java
double budgetPressure = Math.pow((double) activeCount / budgetCap, exponent);
```

**Why non-linear**: A linear budget curve produces the same pressure increase per anchor regardless of utilization. At 50% utilization, adding one anchor should be less alarming than at 90% utilization. The 1.5 exponent creates a convex curve: low pressure under 50% utilization, rapidly rising as the budget cap is approached.

**Research attribution**: Sleeping LLM's edit pressure uses exponent 1.5 for the same rationale -- their capacity cliff at 13/50 edits produces a phase transition that non-linear scaling detects earlier.

### D3: Per-Context Event Counters with Sliding Window

**Decision**: The gauge maintains per-context sliding window counters for conflict, decay, and compaction events. Counters track the number of events in the last N turns (default 5). The window advances when `computePressure()` is called, not when events arrive.

```java
// Internal state per context
Map<String, ContextPressureState> contextStates = new ConcurrentHashMap<>();
```

`ContextPressureState` is a package-private mutable class (not a record) because it accumulates event counts across turns. It is accessed under `ConcurrentHashMap` key-level isolation.

**Why mutable internal state**: Event counters are incremented asynchronously by Spring event listeners. Records are immutable and would require replace-on-write semantics with `ConcurrentHashMap.compute()`. A mutable state object with `AtomicInteger` counters is simpler and avoids CAS retry loops.

**Why not reuse AttentionWindow**: `AttentionWindow` is per-anchor and tracks individual anchor event timelines. Pressure gauges need per-context aggregate counters across all anchors. The data models are incompatible.

### D4: Record Design for PressureScore

**Decision**: `PressureScore` is an immutable record with the total score and per-dimension breakdown.

```java
public record PressureScore(
        double total,
        double budget,
        double conflict,
        double decay,
        double compaction,
        Instant computedAt
) {}
```

All values are post-weighting (the contribution of each dimension to the total). Raw dimension values (pre-weighting) are not exposed because the weighted contribution is the actionable signal.

### D5: PressureThresholdBreached as Lifecycle Event

**Decision**: `PressureThresholdBreached` extends `AnchorLifecycleEvent` as a new permitted subclass. It carries the threshold type, pressure score, and context ID.

**Why lifecycle event**: Threshold breaches are significant state transitions in the anchor system's health. Publishing them through the existing event infrastructure means `MaintenanceStrategy`, `AnchorLifecycleListener`, and any other event consumers receive them without new wiring. The sealed hierarchy ensures exhaustive handling in switch expressions.

**Why not a separate event type**: A standalone event would require parallel event infrastructure. The lifecycle event system already handles per-context events with timestamps and source tracking.

### D6: Threshold Hysteresis via State Tracking

**Decision**: The gauge tracks whether each threshold is currently breached per context. A `PressureThresholdBreached` event fires only on the transition from below to at-or-above the threshold. No event fires while the score remains above the threshold.

```java
// Per-context: which thresholds are currently breached
Map<PressureThresholdType, Boolean> breachedThresholds;
```

**Why simple hysteresis over sustained-pressure**: Sustained-pressure (requiring N consecutive turns above threshold) adds complexity and delays response. Simple transition-based hysteresis prevents event storms while remaining responsive. If threshold noise becomes a problem, sustained-pressure can be added as a refinement without changing the event contract.

### D7: Configuration Structure

**Decision**: A new `PressureConfig` nested record in `DiceAnchorsProperties` with validation constraints.

```java
public record PressureConfig(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0.4") double budgetWeight,
        @DefaultValue("0.3") double conflictWeight,
        @DefaultValue("0.2") double decayWeight,
        @DefaultValue("0.1") double compactionWeight,
        @DefaultValue("0.4") double lightSweepThreshold,
        @DefaultValue("0.8") double fullSweepThreshold,
        @DefaultValue("1.5") double budgetExponent,
        @DefaultValue("5") int conflictWindowSize
) {}
```

Validation: weights sum to 1.0 (tolerance 0.001), fullSweep > lightSweep, exponent > 0, windowSize >= 1.

## File Inventory

### New Files (4)

| File | Package | Type | Description |
|---|---|---|---|
| `PressureDimension.java` | `anchor/` | Enum | BUDGET, CONFLICT, DECAY, COMPACTION |
| `PressureScore.java` | `anchor/` | Record | Total + per-dimension pressure breakdown with timestamp |
| `PressureThreshold.java` | `anchor/` | Record | Light sweep and full sweep threshold levels |
| `MemoryPressureGauge.java` | `anchor/` | `@Component` | Composite pressure computation, event subscription, history tracking |

### Modified Files (4)

| File | Change |
|---|---|
| `anchor/event/AnchorLifecycleEvent.java` | Add `PressureThresholdBreached` inner class to sealed permits list; add static factory method |
| `anchor/event/AnchorLifecycleListener.java` | Add `@EventListener` handler for `PressureThresholdBreached` logging at WARN level |
| `DiceAnchorsProperties.java` | Add `PressureConfig` nested record; add `pressure` field to root record |
| `application.yml` | Add `dice-anchors.pressure` section with defaults |

### Test Files (1)

| File | Description |
|---|---|
| `MemoryPressureGaugeTest.java` | Unit tests: deterministic computation, threshold breach detection, dimension independence, history accumulation, weight configuration |

## Research Attribution

The pressure gauge design is directly inspired by the Sleeping LLM project's health monitor:

- **Source**: [github.com/vbario/sleeping-llm](https://github.com/vbario/sleeping-llm)
- **Key paper**: Guo et al., 2025, "Per-Fact Graduated Consolidation Resolves the Capacity Ceiling in Weight-Edited Language Models" (DOI: 10.5281/zenodo.18779159)
- **Specific pattern adopted**: The 3-dimensional drowsiness formula `pressure = (0.6 * edit_pressure) + (0.3 * time_pressure) + (0.1 * perplexity_pressure)` with non-linear edit pressure (exponent 1.5) and dual thresholds (nap at 0.4, full sleep at 0.8).
- **Adaptations**: Expanded to 4 dimensions (budget, conflict, decay, compaction). Redistributed weights (budget 0.4 vs. edit 0.6) to reflect the anchor domain where conflict rate is a stronger degradation signal than raw count. Added compaction dimension with no Sleeping LLM analog.

## Deferred Work

| Item | Deferred To | Reason |
|---|---|---|
| Pressure-triggered maintenance actions | F07 | This change provides the signal; F07 provides the response |
| MaintenanceStrategy integration | F07 | `shouldRunSweep()` consumes pressure; implementing the consumer is F07's scope |
| Cross-context pressure aggregation | Future | Each context is independent; system-wide health monitoring is a separate concern |
| Persistent pressure history | Future | In-memory is sufficient for bounded-lifetime contexts (simulation runs, chat sessions) |
| UI pressure visualization | Future | SimulationView and RunInspectorView enhancements |
| Micrometer gauge metrics | Future | Optional observability enhancement; not required for core computation |
| Sustained-pressure threshold hysteresis | Future | Simple transition hysteresis is sufficient initially; refine if threshold noise becomes a problem |

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| **Weight miscalibration** | Defaults are estimates from Sleeping LLM ratios. Weights are configurable; calibration via simulation A/B testing is expected post-implementation. |
| **Threshold noise** | Simple transition hysteresis prevents event storms. If insufficient, sustained-pressure hysteresis (N consecutive turns above threshold) can be added without changing the event contract. |
| **Stale pressure on lazy evaluation** | Pressure is recomputed on demand, not continuously. Between evaluations, the score may not reflect recent events. Acceptable because pressure is consumed at turn boundaries, not mid-turn. |
| **ConcurrentHashMap memory for idle contexts** | Context state is cleaned up when the context is cleared. For bounded-lifetime contexts this is negligible. |

## Open Questions

None. All design questions from the prep document have been resolved:
- Weight defaults: Option (a) -- budget 0.4, conflict 0.3, decay 0.2, compaction 0.1.
- Non-linear exponent: Configurable, default 1.5.
- History retention: Retain all snapshots for context lifetime (bounded).
- Conflict window size: Configurable, default 5 turns.
- Threshold hysteresis: Simple transition-based (no sustained-pressure).
