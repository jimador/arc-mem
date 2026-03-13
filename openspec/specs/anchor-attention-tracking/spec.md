## ADDED Requirements

### Requirement: AttentionWindow per-unit accumulator

The system SHALL maintain an `AttentionWindow` record per (unitId, contextId) pair that accumulates lifecycle event counts within a sliding time window. `AttentionWindow` SHALL be an immutable record in `dev.arcmem.arcmem.attention`.

`AttentionWindow` SHALL contain the following fields:

| Field | Type | Description |
|-------|------|-------------|
| `unitId` | String | The memory unit being tracked |
| `contextId` | String | The context (chat session or sim run) |
| `conflictCount` | int | `ConflictDetected` events targeting this memory unit within the window |
| `reinforcementCount` | int | `Reinforced` events for this memory unit within the window |
| `totalEventCount` | int | All lifecycle events for this memory unit within the window |
| `lastEventAt` | Instant | Timestamp of the most recent event |
| `windowStart` | Instant | Start of the current sliding window |
| `eventTimestamps` | `List<Instant>` | Ordered timestamps of all events in the window, for burst detection |

`AttentionWindow` SHALL expose derived metrics via computed methods:

- `heatScore()` — overall activity intensity: `totalEventCount` normalized by window duration. Higher means more active. SHALL return a `double` in [0.0, 1.0] normalized against `AttentionConfig.maxExpectedEventsPerWindow`.
- `pressureScore()` — adversarial targeting intensity: `conflictCount / totalEventCount`. SHALL return `0.0` when `totalEventCount == 0`. Higher means more conflicts relative to total activity.
- `burstFactor()` — temporal clustering of events. SHALL compute the ratio of events in the most recent quarter of the window to the expected uniform rate (0.25). A value > 1.0 indicates events are clustered toward the present. SHALL return `1.0` when fewer than 2 events exist.

`AttentionWindow` SHALL be replaced (not mutated) when new events arrive. The tracker creates a new `AttentionWindow` with updated counts and pruned timestamps outside the window.

#### Scenario: Window accumulates conflict events

- **GIVEN** an `AttentionWindow` for memory unit "A1" with `conflictCount = 2` and `totalEventCount = 5`
- **WHEN** a `ConflictDetected` event targeting "A1" is processed
- **THEN** a new `AttentionWindow` SHALL be produced with `conflictCount = 3`, `totalEventCount = 6`, and `pressureScore()` returning `0.5`

#### Scenario: Window prunes expired events

- **GIVEN** an `AttentionWindow` with `windowStart = T0` and events at T1, T2, T3
- **AND** the window duration is 5 minutes
- **WHEN** a new event arrives at T0 + 6 minutes
- **THEN** events before `T0 + 1 minute` SHALL be pruned from `eventTimestamps` and counts SHALL be decremented accordingly

#### Scenario: Heat score normalization

- **GIVEN** `AttentionConfig.maxExpectedEventsPerWindow = 20`
- **AND** an `AttentionWindow` with `totalEventCount = 10`
- **WHEN** `heatScore()` is called
- **THEN** `0.5` SHALL be returned

#### Scenario: Heat score clamped at 1.0

- **GIVEN** `AttentionConfig.maxExpectedEventsPerWindow = 20`
- **AND** an `AttentionWindow` with `totalEventCount = 30`
- **WHEN** `heatScore()` is called
- **THEN** `1.0` SHALL be returned

#### Scenario: Burst factor with clustered events

- **GIVEN** a 4-minute window with 8 events, 6 of which occurred in the last minute
- **WHEN** `burstFactor()` is called
- **THEN** the result SHALL be `(6/8) / 0.25 = 3.0` — events are 3x more clustered than uniform

#### Scenario: Empty window returns zero metrics

- **GIVEN** a newly created `AttentionWindow` with no events
- **WHEN** `heatScore()`, `pressureScore()`, and `burstFactor()` are called
- **THEN** `heatScore()` SHALL return `0.0`, `pressureScore()` SHALL return `0.0`, and `burstFactor()` SHALL return `1.0`

---

### Requirement: AttentionSignalType enum

The system SHALL define an `AttentionSignalType` enum in `dev.arcmem.arcmem.attention` with the following values:

| Value | Description | Trigger condition |
|-------|-------------|-------------------|
| `PRESSURE_SPIKE` | Rapid accumulation of conflict events targeting a single memory unit | `pressureScore() >= config.pressureThreshold` AND `conflictCount >= config.minConflictsForPressure` |
| `HEAT_PEAK` | High overall activity for a memory unit | `heatScore() >= config.heatPeakThreshold` |
| `HEAT_DROP` | Sharp decline in activity after a period of high heat | Previous `heatScore() >= config.heatPeakThreshold` AND current `heatScore() < config.heatDropThreshold` |
| `CLUSTER_DRIFT` | Multiple memory units in the same context experiencing concurrent heat drops | Count of memory units with `HEAT_DROP` in the same evaluation cycle `>= config.clusterDriftMinUnits` |

Each signal type SHALL carry a `severity()` method returning `LOW`, `MEDIUM`, or `HIGH`:
- `PRESSURE_SPIKE` → `HIGH`
- `HEAT_PEAK` → `MEDIUM`
- `HEAT_DROP` → `MEDIUM`
- `CLUSTER_DRIFT` → `HIGH`

#### Scenario: PRESSURE_SPIKE threshold met

- **GIVEN** `config.pressureThreshold = 0.6` and `config.minConflictsForPressure = 3`
- **AND** a memory unit with `pressureScore() = 0.7` and `conflictCount = 4`
- **WHEN** the tracker evaluates attention signals
- **THEN** a `PRESSURE_SPIKE` signal SHALL be emitted

#### Scenario: PRESSURE_SPIKE not emitted below minimum conflicts

- **GIVEN** `config.pressureThreshold = 0.6` and `config.minConflictsForPressure = 3`
- **AND** a memory unit with `pressureScore() = 0.8` but `conflictCount = 2`
- **WHEN** the tracker evaluates attention signals
- **THEN** no `PRESSURE_SPIKE` signal SHALL be emitted (below minimum count)

---

### Requirement: AttentionSignal application event

The system SHALL define an `AttentionSignal` class extending `ApplicationEvent` in `dev.arcmem.arcmem.attention`. `AttentionSignal` SHALL be published via Spring's `ApplicationEventPublisher` when attention thresholds are crossed.

`AttentionSignal` SHALL contain:

| Field | Type | Description |
|-------|------|-------------|
| `unitId` | String | The memory unit that triggered the signal (null for `CLUSTER_DRIFT`) |
| `contextId` | String | The context in which the signal was detected |
| `signalType` | `AttentionSignalType` | The category of attention signal |
| `snapshot` | `AttentionSnapshot` | Frozen metrics at the time the signal was emitted |
| `occurredAt` | Instant | When the signal was detected |

`AttentionSignal` SHALL NOT be part of the `UnitLifecycleEvent` sealed hierarchy. It is a separate event type — lifecycle events are inputs to the tracker; attention signals are outputs.

#### Scenario: Signal carries frozen snapshot

- **GIVEN** a memory unit with `heatScore = 0.8`, `pressureScore = 0.3`, `burstFactor = 2.1`
- **WHEN** a `HEAT_PEAK` signal is published
- **THEN** the `AttentionSignal.snapshot` SHALL contain `heatScore = 0.8`, `pressureScore = 0.3`, `burstFactor = 2.1` at the time of emission, regardless of subsequent window changes

#### Scenario: CLUSTER_DRIFT signal has null unitId

- **GIVEN** a cluster drift condition involving memory units "A1", "A2", "A3"
- **WHEN** a `CLUSTER_DRIFT` signal is published
- **THEN** `unitId` SHALL be null and the `snapshot` SHALL contain aggregate metrics for the drifting cluster

---

### Requirement: AttentionSnapshot value object

The system SHALL define an `AttentionSnapshot` record in `dev.arcmem.arcmem.attention` that captures a frozen point-in-time view of a memory unit's attention metrics.

`AttentionSnapshot` SHALL contain:

| Field | Type | Description |
|-------|------|-------------|
| `heatScore` | double | Heat score at snapshot time |
| `pressureScore` | double | Pressure score at snapshot time |
| `burstFactor` | double | Burst factor at snapshot time |
| `conflictCount` | int | Conflict count at snapshot time |
| `reinforcementCount` | int | Reinforcement count at snapshot time |
| `totalEventCount` | int | Total event count at snapshot time |
| `windowDuration` | Duration | The window duration at snapshot time |
| `unitIds` | `List<String>` | Memory unit IDs included (single memory unit for per-unit signals, multiple for cluster signals) |

`AttentionSnapshot` SHALL be constructable from a single `AttentionWindow` (wrapping its current metrics) or from multiple windows (for cluster aggregation).

#### Scenario: Snapshot from single window

- **GIVEN** an `AttentionWindow` with `heatScore = 0.6`, `pressureScore = 0.4`, `burstFactor = 1.5`
- **WHEN** `AttentionSnapshot.of(window)` is called
- **THEN** the snapshot SHALL contain matching values and `unitIds` containing the single memory unit ID

#### Scenario: Snapshot from cluster

- **GIVEN** three `AttentionWindow`s for memory units "A1", "A2", "A3" with heat scores 0.2, 0.1, 0.15
- **WHEN** `AttentionSnapshot.ofCluster(windows)` is called
- **THEN** `unitIds` SHALL contain ["A1", "A2", "A3"] and metric fields SHALL contain aggregated values (mean of individual scores)

---

### Requirement: AttentionTracker event listener

The system SHALL provide an `AttentionTracker` Spring bean in `dev.arcmem.arcmem.attention` that listens for `UnitLifecycleEvent` subtypes and maintains `AttentionWindow` state per (unitId, contextId).

`AttentionTracker` SHALL:

1. Listen for all `UnitLifecycleEvent` subtypes via `@EventListener`
2. Extract the memory unit ID from each event (using the event-type-specific accessor — `getUnitId()`, `getPropositionId()`, etc.)
3. For `ConflictDetected` events, update windows for ALL memory unit IDs in `getConflictingUnitIds()`
4. Update the corresponding `AttentionWindow` — create a new window if none exists, or produce a replacement with incremented counts and pruned timestamps
5. After each window update, evaluate whether any `AttentionSignalType` threshold has been newly crossed
6. Publish `AttentionSignal` via `ApplicationEventPublisher` for each newly-crossed threshold
7. Periodically evaluate `CLUSTER_DRIFT` across all windows in a context

`AttentionTracker` SHALL use `ConcurrentHashMap<AttentionKey, AttentionWindow>` for thread-safe window storage, where `AttentionKey` is a record of `(unitId, contextId)`.

`AttentionTracker` SHALL NOT publish duplicate signals for the same threshold crossing. Once a signal is emitted for a given (unitId, contextId, signalType), it SHALL NOT be re-emitted until the metric drops below the threshold and crosses it again (hysteresis).

#### Scenario: Reinforced event updates window

- **GIVEN** an active `AttentionTracker` with no prior events for memory unit "A1" in context "ctx-1"
- **WHEN** a `Reinforced` event for "A1" in "ctx-1" is received
- **THEN** a new `AttentionWindow` SHALL be created with `reinforcementCount = 1`, `totalEventCount = 1`

#### Scenario: ConflictDetected updates multiple windows

- **GIVEN** a `ConflictDetected` event in context "ctx-1" with `conflictingUnitIds = ["A1", "A2", "A3"]`
- **WHEN** the tracker processes the event
- **THEN** the `AttentionWindow` for each of "A1", "A2", "A3" SHALL be updated with `conflictCount` incremented

#### Scenario: Threshold crossing publishes signal

- **GIVEN** `config.pressureThreshold = 0.5` and `config.minConflictsForPressure = 3`
- **AND** memory unit "A1" has `conflictCount = 2`, `totalEventCount = 3` (pressure = 0.67, below min count)
- **WHEN** a third `ConflictDetected` targeting "A1" arrives, making `conflictCount = 3`, `totalEventCount = 4`
- **THEN** `pressureScore() = 0.75 >= 0.5` AND `conflictCount = 3 >= 3`, so a `PRESSURE_SPIKE` signal SHALL be published

#### Scenario: Hysteresis prevents duplicate signals

- **GIVEN** a `PRESSURE_SPIKE` was already emitted for memory unit "A1"
- **AND** the pressure score remains above threshold
- **WHEN** another `ConflictDetected` event arrives for "A1"
- **THEN** no additional `PRESSURE_SPIKE` signal SHALL be published

#### Scenario: Hysteresis resets after metric drops below threshold

- **GIVEN** a `PRESSURE_SPIKE` was emitted for memory unit "A1"
- **AND** subsequent non-conflict events reduce `pressureScore()` below `config.pressureThreshold`
- **WHEN** new conflict events later push `pressureScore()` back above threshold
- **THEN** a new `PRESSURE_SPIKE` signal SHALL be published

#### Scenario: Context cleanup on Archived/Evicted events

- **GIVEN** an `AttentionWindow` exists for memory unit "A1" in context "ctx-1"
- **WHEN** an `Archived` or `Evicted` event for "A1" is received
- **THEN** the window SHALL be removed from the tracker's state (the memory unit is no longer active)

---

### Requirement: AttentionConfig configuration properties

The system SHALL define `AttentionConfig` as a `@ConfigurationProperties` record bound to `arc-mem.attention`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Master enable/disable for the attention tracker |
| `windowDuration` | Duration | `PT5M` | Sliding window duration |
| `pressureThreshold` | double | `0.5` | Minimum `pressureScore()` to trigger `PRESSURE_SPIKE` |
| `minConflictsForPressure` | int | `3` | Minimum `conflictCount` required for `PRESSURE_SPIKE` (prevents false positives on low-event windows) |
| `heatPeakThreshold` | double | `0.7` | Minimum `heatScore()` to trigger `HEAT_PEAK` |
| `heatDropThreshold` | double | `0.2` | Maximum `heatScore()` to trigger `HEAT_DROP` (after previously being above `heatPeakThreshold`) |
| `clusterDriftMinUnits` | int | `3` | Minimum memory units experiencing `HEAT_DROP` to trigger `CLUSTER_DRIFT` |
| `maxExpectedEventsPerWindow` | int | `20` | Normalization ceiling for `heatScore()` calculation |

When `enabled = false`, the `AttentionTracker` SHALL NOT process events and SHALL NOT publish signals. The `@EventListener` methods SHALL short-circuit immediately.

#### Scenario: Disabled tracker ignores events

- **GIVEN** `arc-mem.attention.enabled = false`
- **WHEN** any `UnitLifecycleEvent` is published
- **THEN** the `AttentionTracker` SHALL not update any windows or publish any signals

#### Scenario: Custom window duration

- **GIVEN** `arc-mem.attention.window-duration = PT10M`
- **WHEN** events are processed
- **THEN** the sliding window SHALL span the last 10 minutes

#### Scenario: Custom pressure thresholds

- **GIVEN** `arc-mem.attention.pressure-threshold = 0.8` and `arc-mem.attention.min-conflicts-for-pressure = 5`
- **WHEN** a memory unit has `pressureScore() = 0.7` and `conflictCount = 4`
- **THEN** no `PRESSURE_SPIKE` signal SHALL be emitted (neither threshold met)

---

### Requirement: AttentionTracker query API

`AttentionTracker` SHALL expose a read-only query API for downstream consumers to inspect current attention state without waiting for threshold-based signals:

| Method | Return type | Description |
|--------|-------------|-------------|
| `getWindow(String unitId, String contextId)` | `Optional<AttentionWindow>` | Current window for a specific memory unit |
| `getHottestUnits(String contextId, int limit)` | `List<AttentionWindow>` | Top N memory units by `heatScore()` descending |
| `getAllWindows(String contextId)` | `List<AttentionWindow>` | All active windows for a context |
| `snapshot(String contextId)` | `Map<String, AttentionSnapshot>` | Frozen snapshots of all windows, keyed by memory unit ID |

All query methods SHALL be thread-safe and SHALL return immutable data. `getHottestUnits` and `getAllWindows` SHALL return defensive copies.

#### Scenario: Query hottest memory units

- **GIVEN** context "ctx-1" has 10 memory units with varying heat scores
- **WHEN** `getHottestUnits("ctx-1", 3)` is called
- **THEN** a list of 3 `AttentionWindow`s SHALL be returned, ordered by `heatScore()` descending

#### Scenario: Query empty context

- **GIVEN** no events have been processed for context "ctx-99"
- **WHEN** `getAllWindows("ctx-99")` is called
- **THEN** an empty list SHALL be returned

#### Scenario: Snapshot returns frozen state

- **GIVEN** context "ctx-1" has active windows for memory units "A1" and "A2"
- **WHEN** `snapshot("ctx-1")` is called
- **THEN** a map SHALL be returned with keys "A1" and "A2", each containing an `AttentionSnapshot` reflecting the current metrics

---

### Requirement: Context lifecycle cleanup

The `AttentionTracker` SHALL clean up all windows for a context when the context is terminated. This SHALL be triggered by:

1. A `cleanupContext(String contextId)` method callable by `SimulationService` or `ChatActions` during context teardown
2. Automatic cleanup when all memory units in a context have been archived or evicted (detected when the last `AttentionWindow` for a context is removed)

After cleanup, `getAllWindows(contextId)` SHALL return an empty list and `snapshot(contextId)` SHALL return an empty map.

#### Scenario: Explicit context cleanup

- **GIVEN** context "sim-abc" has 5 active attention windows
- **WHEN** `cleanupContext("sim-abc")` is called
- **THEN** all 5 windows SHALL be removed and no signals SHALL be emitted for this context

#### Scenario: Automatic cleanup on last memory unit removal

- **GIVEN** context "ctx-1" has exactly one remaining attention window for memory unit "A1"
- **WHEN** an `Archived` event for "A1" in "ctx-1" is received
- **THEN** the window SHALL be removed and the context SHALL be fully cleaned up

## Invariants

- **ATT1**: `AttentionWindow` instances SHALL be immutable. All updates produce new instances.
- **ATT2**: The `AttentionTracker` SHALL NOT publish duplicate signals for the same threshold crossing without an intervening reset (hysteresis).
- **ATT3**: `AttentionSignal` SHALL NOT be part of the `UnitLifecycleEvent` sealed hierarchy. Lifecycle events are inputs; attention signals are outputs.
- **ATT4**: The `AttentionTracker` SHALL NOT modify memory unit state (rank, authority, pinned status). It is a passive observer that produces signals for downstream consumers to act on.
- **ATT5**: All `AttentionTracker` state SHALL be in-memory only. No persistence to Neo4j.
- **ATT6**: When `AttentionConfig.enabled = false`, the tracker SHALL have zero runtime overhead beyond the short-circuit check.
