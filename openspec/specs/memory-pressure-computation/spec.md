# Memory Pressure Computation Specification

## ADDED Requirements

### REQ-COMPUTE: Composite pressure score

`MemoryPressureGauge` MUST compute a composite memory pressure score as a weighted sum of four dimensions:

```
pressure = (w_budget * budget_pressure)
         + (w_conflict * conflict_pressure)
         + (w_decay * decay_pressure)
         + (w_compaction * compaction_pressure)
```

The result MUST be clamped to the range [0.0, 1.0]. The computation MUST be deterministic for the same inputs.

#### Scenario: Pressure computable for any context
- **GIVEN** a context with 15 active anchors and a budget cap of 20
- **AND** no recent conflicts, decays, or compactions
- **WHEN** `computePressure(contextId)` is called
- **THEN** the returned `PressureScore` MUST have a total in [0.0, 1.0]
- **AND** the budget dimension MUST equal `(15/20)^1.5 * 0.4` (approximately 0.206)
- **AND** the conflict, decay, and compaction dimensions MUST each be 0.0

#### Scenario: Pressure deterministic for identical state
- **GIVEN** two calls to `computePressure(contextId)` with identical anchor state and event history
- **WHEN** both calls complete
- **THEN** both MUST return identical `PressureScore` values

#### Scenario: All dimensions contribute to total
- **GIVEN** a context with non-zero values across all four dimensions
- **WHEN** `computePressure(contextId)` is called
- **THEN** the total MUST equal the sum of all four weighted dimension contributions

### REQ-DIMENSIONS: Four pressure dimensions

Each pressure dimension MUST be independently computable and reportable within the `PressureScore` record.

**BUDGET**: Measures anchor count utilization against the configured budget cap.

```
budget_pressure = (activeCount / budgetCap) ^ exponent
```

The exponent MUST default to 1.5 and MUST be configurable. This non-linear formula is inspired by the Sleeping LLM edit pressure computation (Guo et al., 2025) which uses the same exponent to create a convex curve -- low pressure when under-utilized, sharply rising as capacity is approached.

**CONFLICT**: Measures the recent conflict detection rate over a configurable sliding window.

```
conflict_pressure = conflictsInWindow / windowSize
```

The window size MUST default to 5 turns. The conflict count MUST be incremented on each `ConflictDetected` event and decremented as events age out of the window. The value MUST be clamped to [0.0, 1.0].

**DECAY**: Measures the recent authority demotion rate over the same sliding window.

```
decay_pressure = demotionsInWindow / windowSize
```

The demotion count MUST be incremented on each `AuthorityChanged` event where the direction is DEMOTED. The value MUST be clamped to [0.0, 1.0].

**COMPACTION**: Measures the recent compaction frequency over the same sliding window.

```
compaction_pressure = compactionsInWindow / windowSize
```

The compaction count MUST be incremented when a compaction event is recorded. The value MUST be clamped to [0.0, 1.0].

#### Scenario: Budget pressure uses non-linear exponent
- **GIVEN** a context with 18 active anchors and a budget cap of 20
- **AND** the exponent is 1.5
- **WHEN** budget pressure is computed
- **THEN** the raw budget pressure MUST equal `(18/20)^1.5` (approximately 0.859)

#### Scenario: Budget pressure at zero utilization
- **GIVEN** a context with 0 active anchors
- **WHEN** budget pressure is computed
- **THEN** the raw budget pressure MUST be 0.0

#### Scenario: Budget pressure at full capacity
- **GIVEN** a context with 20 active anchors and a budget cap of 20
- **WHEN** budget pressure is computed
- **THEN** the raw budget pressure MUST be 1.0

#### Scenario: Conflict pressure tracks sliding window
- **GIVEN** a conflict window size of 5 turns
- **AND** 3 conflicts detected in the last 5 turns
- **WHEN** conflict pressure is computed
- **THEN** the raw conflict pressure MUST be 0.6

#### Scenario: Dimension values clamped independently
- **GIVEN** a dimension computation that would exceed 1.0 (e.g., 6 conflicts in a 5-turn window)
- **WHEN** the dimension pressure is computed
- **THEN** the dimension value MUST be clamped to 1.0

### REQ-NONLINEAR: Configurable budget pressure exponent

The budget pressure exponent MUST be configurable via `DiceAnchorsProperties` and MUST default to 1.5. The exponent controls the convexity of the budget pressure curve:
- Exponent 1.0 produces a linear curve.
- Exponent 1.5 (default) produces a moderately convex curve where pressure accelerates near capacity.
- Exponent 2.0 produces a sharply convex curve.

#### Scenario: Custom exponent changes pressure curve
- **GIVEN** a configured exponent of 2.0
- **AND** a context with 15 active anchors and a budget cap of 20
- **WHEN** budget pressure is computed
- **THEN** the raw budget pressure MUST equal `(15/20)^2.0` (0.5625)
- **AND** this MUST differ from the default exponent result of `(15/20)^1.5` (approximately 0.515)

### REQ-THRESHOLD: Threshold breach events

`MemoryPressureGauge` MUST emit a `PressureThresholdBreached` event when the computed pressure score crosses a configured threshold. Two thresholds MUST be supported:

1. **LIGHT_SWEEP**: Default 0.4. Signals that lightweight maintenance MAY be beneficial.
2. **FULL_SWEEP**: Default 0.8. Signals that aggressive maintenance SHOULD be performed.

The event MUST include the threshold type, the pressure score, the per-dimension breakdown, and the context ID. Events MUST be published via Spring's `ApplicationEventPublisher`.

A threshold breach MUST be detected when the pressure score transitions from below the threshold to at or above the threshold. Re-publishing MUST NOT occur while the score remains above the threshold without first dropping below it.

#### Scenario: Light sweep threshold crossed
- **GIVEN** a context with previous pressure 0.35
- **AND** a light sweep threshold of 0.4
- **WHEN** the newly computed pressure is 0.42
- **THEN** a `PressureThresholdBreached` event MUST be published with threshold type LIGHT_SWEEP

#### Scenario: Full sweep threshold crossed
- **GIVEN** a context with previous pressure 0.75
- **AND** a full sweep threshold of 0.8
- **WHEN** the newly computed pressure is 0.83
- **THEN** a `PressureThresholdBreached` event MUST be published with threshold type FULL_SWEEP

#### Scenario: No re-publish while above threshold
- **GIVEN** a context already above the light sweep threshold (pressure 0.45)
- **WHEN** pressure is recomputed at 0.50 (still above 0.4)
- **THEN** no `PressureThresholdBreached` event MUST be published for LIGHT_SWEEP

#### Scenario: Re-publish after dropping below
- **GIVEN** a context that was above light sweep threshold (0.45), then dropped below (0.35)
- **WHEN** pressure rises again to 0.42
- **THEN** a `PressureThresholdBreached` event MUST be published for LIGHT_SWEEP

### REQ-CONFIG: Configurable weights, thresholds, and exponents

All pressure parameters MUST be configurable via `DiceAnchorsProperties`. The configuration MUST include:

1. **Dimension weights**: budget (default 0.4), conflict (default 0.3), decay (default 0.2), compaction (default 0.1).
2. **Thresholds**: lightSweep (default 0.4), fullSweep (default 0.8).
3. **Budget exponent**: default 1.5.
4. **Conflict window size**: default 5 turns.

Weights MUST sum to 1.0. A validation constraint MUST enforce this with a tolerance of 0.001.

#### Scenario: Default weights sum to 1.0
- **GIVEN** no custom pressure configuration
- **WHEN** the application starts
- **THEN** the effective weights MUST be budget=0.4, conflict=0.3, decay=0.2, compaction=0.1
- **AND** the sum MUST be 1.0

#### Scenario: Custom weights validated
- **GIVEN** custom weights budget=0.5, conflict=0.3, decay=0.1, compaction=0.2
- **WHEN** the application starts
- **THEN** validation MUST pass (sum = 1.1 would fail; sum = 1.0 passes)

#### Scenario: Full sweep threshold greater than light sweep
- **GIVEN** a configured light sweep threshold of 0.4 and full sweep threshold of 0.8
- **WHEN** validation runs
- **THEN** validation MUST pass
- **AND** a configuration with lightSweep=0.8 and fullSweep=0.4 MUST fail validation

### REQ-HISTORY: Per-turn pressure snapshots

`MemoryPressureGauge` MUST maintain an ordered list of `PressureScore` snapshots per context. Each call to `computePressure()` MUST append its result to the history. The history MUST be retrievable via `getHistory(contextId)`.

History MUST be retained for the context's lifetime (in-memory). No size limit is REQUIRED for the initial implementation; context lifetimes are bounded (simulation runs are finite, chat sessions have bounded turns).

History MUST be cleared when the context is cleaned up (via `clearContext(contextId)` or equivalent).

#### Scenario: History accumulates per turn
- **GIVEN** a context that has been evaluated 3 times
- **WHEN** `getHistory(contextId)` is called
- **THEN** the returned list MUST contain exactly 3 `PressureScore` entries in chronological order

#### Scenario: History isolated per context
- **GIVEN** two contexts (ctx-A and ctx-B) each evaluated once
- **WHEN** `getHistory("ctx-A")` is called
- **THEN** the result MUST contain only ctx-A's snapshot, not ctx-B's

#### Scenario: History cleared on context cleanup
- **GIVEN** a context with 5 pressure snapshots
- **WHEN** `clearContext(contextId)` is called
- **THEN** `getHistory(contextId)` MUST return an empty list

### REQ-EVENT: Event-driven pressure updates

`MemoryPressureGauge` MUST subscribe to `AnchorLifecycleEvent` emissions to update its per-context event counters. The following events MUST increment the corresponding dimension counters:

| Event Type | Counter Affected |
|---|---|
| `ConflictDetected` | conflict counter |
| `AuthorityChanged` (direction=DEMOTED) | decay counter |
| `Promoted`, `Archived`, `Evicted` | (trigger recount of active anchors for budget dimension) |

Compaction events MUST be trackable via an explicit `recordCompaction(contextId)` method, since compaction is not currently an `AnchorLifecycleEvent` subtype.

The gauge MUST NOT subscribe to its own `PressureThresholdBreached` events to avoid infinite loops.

#### Scenario: Conflict event increments counter
- **GIVEN** a context with 0 conflicts in the current window
- **WHEN** a `ConflictDetected` event fires for the context
- **THEN** the conflict counter for the context MUST increment to 1

#### Scenario: Demotion event increments decay counter
- **GIVEN** a context with 0 demotions in the current window
- **WHEN** an `AuthorityChanged` event fires with direction DEMOTED for the context
- **THEN** the decay counter for the context MUST increment to 1

#### Scenario: Promotion events do not affect authority counter
- **GIVEN** a context with 0 demotions in the current window
- **WHEN** an `AuthorityChanged` event fires with direction PROMOTED for the context
- **THEN** the decay counter MUST remain 0

### REQ-PERF: Performance constraints

Pressure computation MUST NOT require LLM calls and MUST complete within millisecond-level latency. The gauge MUST NOT introduce blocking I/O or network calls. All inputs MUST be sourced from in-memory state (event counters, anchor counts from the repository, and configuration values).

`MemoryPressureGauge` MUST NOT have any dependency on `ChatModel`, `LlmCallService`, or any LLM-related service.

#### Scenario: No LLM dependency
- **WHEN** the `MemoryPressureGauge` class is inspected
- **THEN** it MUST NOT import or inject any LLM-related class (`ChatModel`, `LlmCallService`, etc.)

## Invariants

- **I1**: Pressure score MUST always be in the range [0.0, 1.0].
- **I2**: Each individual dimension contribution MUST always be in the range [0.0, 1.0] before weighting.
- **I3**: Dimension weights MUST sum to 1.0 (tolerance 0.001).
- **I4**: `fullSweep` threshold MUST be strictly greater than `lightSweep` threshold.
- **I5**: Pressure computation MUST be deterministic for the same inputs.
- **I6**: The pressure gauge MUST NOT modify anchor state. It is a read-only observer.
- **I7**: `PressureThresholdBreached` events MUST NOT re-fire while the score remains above the threshold.
- **I8**: All anchor invariants (A1-A4 per Article V of the constitution) MUST be preserved.
