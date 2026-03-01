# Anchor Attention Tracker — Architecture

## Section 1: Full Design Overview

### Package: `dev.dunnam.diceanchors.anchor.attention`

| Class | Role |
|-------|------|
| `AttentionTracker` | Spring `@Component`. Listens to `AnchorLifecycleEvent`s, maintains per-anchor sliding windows, evaluates thresholds, publishes `AttentionSignal`s. Also exposes query API for downstream consumers. |
| `AttentionWindow` | Immutable record. Per-anchor sliding window state: conflict count, reinforcement count, total event count, timestamp list. Computes `heatScore`, `pressureScore`, `burstFactor`. All mutation returns a new record. |
| `AttentionKey` | Package-private record. Composite key `(anchorId, contextId)` used as `ConcurrentHashMap` key. |
| `AttentionSignal` | `ApplicationEvent` subclass. Published when a threshold is crossed. Carries `AttentionSignalType`, `AttentionSnapshot`, and optional `anchorId` (null for cluster-level signals). |
| `AttentionSignalType` | Enum. Four signal types with severity: `PRESSURE_SPIKE` (HIGH), `HEAT_PEAK` (MEDIUM), `HEAT_DROP` (MEDIUM), `CLUSTER_DRIFT` (HIGH). |
| `AttentionSnapshot` | Immutable record. Frozen metrics at signal-publish time. Constructed from a single `AttentionWindow` (`of()`) or aggregated from multiple (`ofCluster()`). |

### Data Flow

```
AnchorLifecycleEvent (Spring ApplicationEvent)
         │
         ▼
AttentionTracker.onLifecycleEvent()
         │
         ├─── Archived / Evicted ──────────────────────► removeWindow() → ConcurrentHashMap.remove()
         │
         └─── All other event types
                    │
                    ▼
           extractEntries()  ← categorizes to CONFLICT / REINFORCEMENT / GENERAL
                    │
                    ▼ (per entry)
           updateWindow()
                    │
                    ▼
           ConcurrentHashMap<AttentionKey, AttentionWindow>.compute()
           ┌─────────────────────────────────────────┐
           │  existing == null → AttentionWindow.     │
           │    initial() / initialConflict() /       │
           │    initialReinforcement()                │
           │                                         │
           │  existing != null → existing.withEvent() │
           │    / withConflict() / withReinforcement() │
           └─────────────────────────────────────────┘
                    │
                    ▼
           evaluateThresholds(key, updatedWindow)
                    │
           ConcurrentHashMap<AttentionKey, EnumSet<AttentionSignalType>>
           (hysteresis tracking — "already active" signals)
                    │
                    ├── pressureScore >= pressureThreshold
                    │   AND conflictCount >= minConflictsForPressure
                    │   → publish PRESSURE_SPIKE (if not already active)
                    │
                    ├── heatScore >= heatPeakThreshold
                    │   → publish HEAT_PEAK (if not already active)
                    │
                    └── was HEAT_PEAK active AND heatScore < heatDropThreshold
                        → publish HEAT_DROP (if not already active)
                    │
                    ▼
           evaluateClusterDrift(contextId)
           ┌─────────────────────────────────────────┐
           │  Scan all windows in context for         │
           │  HEAT_DROP in activeSignals              │
           │                                         │
           │  count >= clusterDriftMinAnchors →       │
           │    publish CLUSTER_DRIFT on              │
           │    synthetic key ("__cluster__", ctxId) │
           └─────────────────────────────────────────┘
                    │
                    ▼
           publishSignal()
                    │
                    ▼
           AttentionSnapshot.of() / ofCluster()
                    │
                    ▼
           new AttentionSignal(source, anchorId, contextId, type, snapshot)
                    │
                    ▼
           ApplicationEventPublisher.publishEvent()
                    │
                    ▼
           Downstream @EventListener consumers
           (currently: none registered; query API available for pull-based use)
```

### Component Responsibilities

**`AttentionTracker`**

- Owns both maps: `windows` (state) and `activeSignals` (hysteresis).
- Single entry point for event ingestion: `onLifecycleEvent()`.
- Threshold evaluation runs after every window update. Cluster drift runs once per event after all per-anchor updates for that event complete.
- Exposes pull API: `getWindow()`, `getHottestAnchors()`, `getAllWindows()`, `snapshot()`.
- `cleanupContext()` purges all state for a context (called after sim run cleanup).

**`AttentionWindow`**

- Pure data + computation. No dependencies.
- Sliding window: `withEvent()` prunes timestamps older than `(eventTime - windowDuration)`, proportionally adjusts conflict/reinforcement counts for pruned events.
- `heatScore(maxEvents)` — normalized event density: `min(1.0, totalEventCount / maxEvents)`.
- `pressureScore()` — conflict ratio: `conflictCount / totalEventCount`.
- `burstFactor()` — recency concentration: ratio of events in last quarter of window span vs uniform distribution. Value `> 1.0` means events are clustered toward the end.

**`AttentionKey`**

- Package-private. Value type for map keying. Record equality on `(anchorId, contextId)`.
- The synthetic key `("__cluster__", contextId)` is used in `activeSignals` to track CLUSTER_DRIFT hysteresis. It never appears in `windows`.

**`AttentionSignal`**

- Extends `ApplicationEvent` (not `AnchorLifecycleEvent`) per invariant ATT3. This avoids circular dependency: lifecycle events are inputs; attention signals are outputs.
- `anchorId` is nullable. Null means cluster-level signal (CLUSTER_DRIFT).

**`AttentionSnapshot`**

- Frozen at publish time. Safe to hand off to async consumers.
- `ofCluster()` aggregates: averages heat/pressure/burst across windows, sums conflict/reinforcement/total event counts, takes max window duration, collects all anchor IDs.

### Thread Safety Model

`AttentionTracker` uses two `ConcurrentHashMap` instances:

1. `windows: ConcurrentHashMap<AttentionKey, AttentionWindow>` — state store
2. `activeSignals: ConcurrentHashMap<AttentionKey, EnumSet<AttentionSignalType>>` — hysteresis state

**Per-key atomicity for window updates:** `windows.compute(key, ...)` provides atomic read-modify-write for each key independently. `AttentionWindow` is an immutable record, so the lambda computes a new instance without shared mutable state.

**Hysteresis tracking:** `activeSignals.computeIfAbsent(key, ...)` ensures the `EnumSet` is created once. The `EnumSet` itself is then mutated inside `evaluateThresholds()`. This is safe because:
- The default Spring `@EventListener` dispatch is single-threaded (synchronous, caller's thread).
- If `@Async` is used, two events for the same anchor could race on the same `EnumSet`. The race window is narrow (add/contains/remove on a non-thread-safe set) and the consequence is a duplicate or missed signal publication, not data corruption. For the current synchronous use case this is not an issue.

**CLUSTER_DRIFT evaluation:** `evaluateClusterDrift()` reads `activeSignals` entries via stream (snapshot semantics with ConcurrentHashMap iteration). The synthetic `__cluster__` key is updated with `computeIfAbsent` + mutation. Same caveat as above applies for async listeners.

**Conclusion:** The design is correct for single-threaded event delivery. If async delivery is introduced, the `EnumSet` operations should be replaced with `ConcurrentSkipListSet` or the threshold logic should move into the `compute()` lambda.

### Configuration Reference

All properties are under the `dice-anchors.attention` prefix.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Master switch. When false, `onLifecycleEvent` and all query methods are no-ops. |
| `window-duration` | `Duration` | `PT5M` (5 minutes) | Sliding window length. Events older than `(now - windowDuration)` are pruned on next update. |
| `pressure-threshold` | `double` [0,1] | `0.5` | Minimum `pressureScore` (conflict ratio) required to fire `PRESSURE_SPIKE`. |
| `min-conflicts-for-pressure` | `int` >= 1 | `3` | Minimum absolute conflict count required alongside `pressureThreshold` for `PRESSURE_SPIKE`. Prevents false positives on sparse windows. |
| `heat-peak-threshold` | `double` [0,1] | `0.7` | Minimum `heatScore` required to fire `HEAT_PEAK`. |
| `heat-drop-threshold` | `double` [0,1] | `0.2` | `heatScore` must fall below this (from a prior `HEAT_PEAK` state) to fire `HEAT_DROP`. |
| `cluster-drift-min-anchors` | `int` >= 2 | `3` | Minimum number of anchors with active `HEAT_DROP` signals in the same context required to fire `CLUSTER_DRIFT`. |
| `max-expected-events-per-window` | `int` >= 1 | `20` | Normalization ceiling for `heatScore`. `heatScore = min(1.0, totalEventCount / maxExpectedEventsPerWindow)`. |

**Fallback:** If `dice-anchors.attention` is absent from configuration entirely, `AttentionTracker` constructs a default `AttentionConfig` with values matching the defaults above, except `enabled=false`.

---

## Section 2: Future Enhancement Roadmap

### Embabel Integration Surfaces

The attention subsystem is currently a passive observer — it maintains state and publishes signals, but no Embabel-aware component consumes it yet. The natural integration surfaces are described below, ordered by implementation complexity.

#### Surface A: Attention-Enriched Tool Responses (Near-term)

`AnchorQueryTools` currently returns `AnchorSummary` records with rank and authority. Enriching these with attention metadata requires no Embabel API changes.

```
AnchorQueryTools.findRelevantAnchors(query, contextId)
    │
    ├── existing: AnchorRepository.findByContextId(...)
    └── new:      AttentionTracker.getWindow(anchorId, contextId)
                  → inject heatScore, pressureScore into AnchorSummary
```

The LLM sees, for each anchor: rank, authority, AND attention state. It can self-direct: defend facts under pressure, prioritize hot topics, treat low-heat facts as less salient.

Integration point: `AttentionTracker.getWindow(anchorId, contextId)` called from `AnchorQueryTools`.

#### Surface B: Attention as `@Condition` Gates (Medium-term)

Embabel's `@Condition` annotation methods can gate whether an agent action executes. Attention state maps naturally to stability conditions.

Examples:
- `"no anchors under active pressure"` — gate that blocks a summarization action while conflicts are unresolved
- `"context is stable"` — shorthand for no active `PRESSURE_SPIKE` or `CLUSTER_DRIFT` in the context

This becomes relevant when chat flow evolves from the current single-action architecture to multi-action GOAP planning with precondition evaluation.

Integration point: `AttentionTracker.getAllWindows(contextId)` or a convenience method such as `isContextStable(contextId)` that returns false if any active `HIGH` severity signals exist.

#### Surface C: Goal Value Functions (Long-term)

Embabel's `Goal.value` cost computation influences which goal the GOAP planner pursues. Attention signals encode urgency that the planner could exploit autonomously.

Examples:
- An anchor under `PRESSURE_SPIKE` increases the cost of goal paths that ignore it
- `CLUSTER_DRIFT` triggers a "stabilize context" goal with high priority

This requires dice-anchors to evolve to a multi-goal agent architecture and the planner to have visibility into attention metrics.

Integration point: convenience methods on `AttentionTracker` such as `getMaxPressure(contextId)` or `hasActiveSignalOfSeverity(contextId, Severity.HIGH)`.

### Planned Consumers

The following consumers are anticipated but not yet implemented. They register as `@EventListener` for `AttentionSignal` or call the query API.

**1. Attention-Aware Context Assembly**

`RelevanceScorer` currently weights anchors by authority, tier, and confidence. A fourth factor — heat score — would boost recently-active anchors in the assembled context, ensuring the LLM receives the most contested facts in its working memory.

Mechanism: `RelevanceScorer` calls `AttentionTracker.getWindow(anchorId, contextId)` during scoring. Heat score is added as a weighted factor alongside `authorityWeight`, `tierWeight`, `confidenceWeight`.

**2. Drift Early Warning**

An `@EventListener` for `AttentionSignal` that watches for `HEAT_DROP` and `CLUSTER_DRIFT`. When triggered, it could flag the current context as potentially drifting and surface a warning in the `RunInspectorView` or log a structured drift event for post-hoc analysis.

**3. Topic Heat Map Observability**

Periodic snapshot recording via `AttentionTracker.snapshot(contextId)`. A scheduled task (or a turn-completion hook in `SimulationTurnExecutor`) records the full heat map after each turn, enabling dashboard visualization of attention evolution over a sim run.

**4. Conflict Pressure Response**

An `@EventListener` for `AttentionSignal` filtered to `PRESSURE_SPIKE`. When an anchor accumulates enough conflict events to cross the pressure threshold, the listener could proactively harden the anchor (increase rank, require higher conflict confidence to override) or trigger a re-evaluation of the conflict resolution strategy for that anchor.

### Query API vs Event Stream

The query API (`getWindow`, `getHottestAnchors`, `getAllWindows`, `snapshot`) is the natural Embabel integration surface. Embabel's action execution model is synchronous request-response: an action method runs, calls tools, returns a result. The pull-based query API fits this model cleanly.

The event stream (`AttentionSignal` via `ApplicationEventPublisher`) is appropriate for reactive side effects that happen outside the main action execution path: logging, observability, early-warning hooks.

---

## Section 3: Design Decisions and Trade-offs

### Decision 1: Package Placement — `anchor/attention/`

Attention tracking is placed in `anchor/attention/`, not `assembly/` or `sim/`.

**Rationale:** The tracker is a pure observer of anchor lifecycle events. It has no dependency on assembly, sim, or chat. Its inputs are `AnchorLifecycleEvent` (from `anchor/event/`); its outputs are `AttentionSignal` (a new event type) and a query API. Consumers live in `assembly/`, `sim/`, and `chat/`, but the tracker itself has no knowledge of them.

Placing it in `anchor/attention/` keeps the dependency graph acyclic:

```
anchor/event/ → anchor/attention/ → (consumed by) assembly/, sim/, chat/
```

**Alternative rejected: `assembly/`** — `assembly/` is where context is assembled for LLM calls. Attention tracking is not assembly-specific; it observes the full anchor lifecycle regardless of whether assembly ever runs. Future consumers in `anchor/` itself (e.g., attention-aware decay rates) would need to import from `assembly/`, creating an unacceptable cycle.

### Decision 2: `ApplicationEvent` Rather Than Sealed Hierarchy Extension

`AttentionSignal` extends `ApplicationEvent`, not `AnchorLifecycleEvent`.

**Rationale:** `AnchorLifecycleEvent` is a sealed hierarchy of primary domain events. Adding `AttentionSignal` to that hierarchy would create a circular dependency: `AnchorLifecycleEvent` would need to import from `anchor/attention/`, and `AttentionTracker` listens to `AnchorLifecycleEvent`. The cycle would be:

```
anchor/event/AnchorLifecycleEvent → anchor/attention/AttentionSignal
anchor/attention/AttentionTracker → anchor/event/AnchorLifecycleEvent
```

Keeping `AttentionSignal` as a separate `ApplicationEvent` subclass avoids this entirely. Lifecycle events are inputs; attention signals are derived outputs — a semantic distinction that maps naturally to the class hierarchy separation. Invariant ATT3 codifies this.

### Decision 3: `ConcurrentHashMap.compute()` Over Synchronized Blocks

**Rationale:** `compute()` provides per-key atomic read-modify-write without a global lock. Events for different anchors within the same context can be processed concurrently without contention. A single `synchronized` block on `this` would serialize all event processing regardless of which anchor is involved.

**Trade-off:** The `EnumSet<AttentionSignalType>` stored in `activeSignals` is mutated outside any `compute()` call, inside `evaluateThresholds()`. This is safe for synchronous `@EventListener` dispatch (single-threaded), but introduces a race if `@Async` is added. The narrow window (contains + add/remove on a non-synchronized set) could produce a duplicate signal publication. Consequence is observable noise, not data corruption. For the current use case this is acceptable.

If async delivery is required, the fix is to move threshold evaluation inside the `windows.compute()` lambda and handle `activeSignals` with `compute()` as well, or replace `EnumSet` with `ConcurrentSkipListSet`.

### Decision 4: Static Normalization Ceiling for `heatScore`

**Current:** `heatScore = min(1.0, totalEventCount / maxExpectedEventsPerWindow)` where `maxExpectedEventsPerWindow` is a static config value (default 20).

**Rationale:** Simple, predictable, and easy to tune per deployment. A sim turn generating 5–10 events maps to heatScore 0.25–0.5 with the default ceiling. A chat session generating 1–2 events maps to 0.05–0.1.

**Trade-off:** The ceiling is workload-dependent. A sim run with aggressive adversary turns may saturate `heatScore` to 1.0 for most anchors, making the score uniformly uninformative. A low-throughput chat session may never exceed 0.3, keeping all anchors below the `heatPeakThreshold` of 0.7.

**Future consideration:** Adaptive normalization based on observed event rates per context. `AttentionTracker` could track a running exponential moving average of events per window per context and normalize against that, producing a heat score relative to the context's own baseline activity rather than a static absolute ceiling.

### Decision 5: Per-Event CLUSTER_DRIFT Evaluation

**Current:** `evaluateClusterDrift(contextId)` runs after every event processed. It scans all `AttentionWindow` entries for the context and counts those with an active `HEAT_DROP` signal.

**Rationale:** Keeps the logic simple and self-contained. No separate scheduler or periodic task. With a budget of 20 anchors, the scan is O(20) — negligible.

**Trade-off:** Cluster drift is an emergent pattern that develops over multiple turns, not individual events. Evaluating it after every event adds no latency but does mean the check runs tens of times per turn during high-activity periods. The hysteresis check (`!active.contains(CLUSTER_DRIFT)`) prevents redundant signal publications.

**Future consideration:** If evaluation is moved to a per-turn hook (e.g., called at the end of `SimulationTurnExecutor.executeTurn()`), it becomes easier to reason about cluster state at turn boundaries and avoids mid-turn partial evaluations. This would also allow detecting drift patterns that only become visible when comparing start-of-turn vs end-of-turn heat maps.
