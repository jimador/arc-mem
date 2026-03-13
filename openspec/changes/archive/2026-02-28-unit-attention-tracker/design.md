## Context

The context unit subsystem publishes 10 lifecycle event types via Spring's `ApplicationEventPublisher`, but nothing aggregates them over time. The `ContextUnitLifecycleListener` logs individual events — no temporal memory, no pattern detection. This change adds an in-memory sliding-window tracker that observes these events and produces higher-level attention signals.

## Goals / Non-Goals

**Goals:**
- Event-driven sliding-window aggregation of context unit lifecycle events
- Per-context unit, per-context attention metrics (heat, pressure, burst)
- Threshold-based signal publishing for downstream consumers
- Query API for on-demand attention state inspection
- Zero-overhead disable path

**Non-Goals:**
- Implementing downstream consumers (adversarial hardening, spotlight tracking, etc.) — separate changes
- Persisting attention state to Neo4j
- Modifying any existing context unit behavior (rank, authority, eviction)
- UI components for attention visualization

## Decisions

### D1: Separate package `context unit/attention/` within the context unit module

Place all attention-tracking code in `dev.dunnam.arcmem.context unit.attention`. This is an unit-level concern (observing context unit lifecycle events), not a simulation or assembly concern. The package depends on `context unit/event/` types but nothing in `context unit/` depends on `attention/`.

**Alternative**: Put in `assembly/` since it could inform context assembly. Rejected — the tracker is a generic event observer, not assembly-specific. Assembly consumers would import from `attention/`, not the other way around.

### D2: `AttentionSignal` as separate `ApplicationEvent`, not in sealed hierarchy

`AttentionSignal` extends `ApplicationEvent` directly, NOT `ContextUnitLifecycleEvent`. The sealed hierarchy represents state mutations to context units. Attention signals are derived observations — they don't change context unit state. Mixing them into the sealed hierarchy would create a circular dependency (lifecycle events → tracker → attention signals → lifecycle hierarchy).

**Alternative**: Make `AttentionSignal` a POJO and have the tracker call consumers directly. Rejected — Spring events provide loose coupling, letting consumers register without the tracker knowing about them.

### D3: `ConcurrentHashMap<AttentionKey, AttentionWindow>` for thread-safe state

The tracker uses `ConcurrentHashMap` keyed by `AttentionKey(unitId, contextId)` record. `AttentionWindow` is an immutable record — updates replace the entry via `compute()`. This avoids synchronization beyond what `ConcurrentHashMap` provides.

**Alternative**: Synchronized `HashMap`. Rejected — `ConcurrentHashMap.compute()` gives per-key atomicity without global locks, which matters when multiple event listeners fire concurrently.

### D4: Hysteresis via `EnumSet` tracking per key

Each `AttentionKey` has an associated `EnumSet<AttentionSignalType>` tracking which signals are currently "active" (threshold crossed but not yet reset). A signal is published only on the rising edge (not active → threshold crossed). It resets when the metric drops below threshold.

Stored as `ConcurrentHashMap<AttentionKey, EnumSet<AttentionSignalType>>` alongside the window map.

### D5: Configuration via nested record in `ArcMemProperties`

Add `AttentionConfig` as a `@NestedConfigurationProperty` record within `ArcMemProperties`, following the existing pattern for `UnitConfig`, `MemoryConfig`, etc. Properties bind under `context units.attention.*`.

**Alternative**: Separate top-level `@ConfigurationProperties` class. Rejected — all context units config is centralized in `ArcMemProperties`.

### D6: Event-to-context unit mapping via pattern matching

The tracker extracts context unit IDs from events using a switch expression with pattern matching on the sealed hierarchy. `ConflictDetected` maps to multiple context unit IDs; all others map to a single ID. `InvariantViolation` with null `unitId` is skipped.

```java
private List<String> extractUnitIds(ContextUnitLifecycleEvent event) {
    return switch (event) {
        case ConflictDetected e -> e.getConflictingUnitIds();
        case Promoted e -> List.of(e.getUnitId());
        case Reinforced e -> List.of(e.getUnitId());
        case Archived e -> List.of(e.getUnitId());
        case Evicted e -> List.of(e.getUnitId());
        case ConflictResolved e -> List.of(e.getExistingUnitId());
        case AuthorityChanged e -> List.of(e.getUnitId());
        case TierChanged e -> List.of(e.getUnitId());
        case Superseded e -> List.of(e.getPredecessorId());
        case InvariantViolation e -> e.getUnitId() != null ? List.of(e.getUnitId()) : List.of();
    };
}
```

### D7: CLUSTER_DRIFT evaluation on every event, scoped to context

After processing each event, the tracker evaluates cluster drift for the event's context. It counts how many context units in that context currently have a `HEAT_DROP` condition (previously hot, now below drop threshold). If the count meets `clusterDriftMinUnits`, a `CLUSTER_DRIFT` signal is published.

This is efficient because each event only triggers a scan of windows in one context (not all contexts).

## Data Flow

```mermaid
flowchart TD
    ALE[ContextUnitLifecycleEvent subtypes] -->|@EventListener| AT[AttentionTracker]
    AT -->|update| WM[Window Map<br>ConcurrentHashMap&lt;AttentionKey, AttentionWindow&gt;]
    AT -->|check thresholds| HM[Hysteresis Map<br>ConcurrentHashMap&lt;AttentionKey, EnumSet&gt;]
    AT -->|publish| AS[AttentionSignal]
    AS -->|@EventListener| C1[Consumer: Adversarial Hardener]
    AS -->|@EventListener| C2[Consumer: Spotlight Tracker]
    AS -->|@EventListener| C3[Consumer: Observability]
    AT -->|query API| Q[getHottestUnits / snapshot / getWindow]
    Q -->|read-only| WM
```

## Component Structure

```mermaid
classDiagram
    class AttentionTracker {
        -ConcurrentHashMap~AttentionKey,AttentionWindow~ windows
        -ConcurrentHashMap~AttentionKey,EnumSet~ activeSignals
        -ApplicationEventPublisher publisher
        -AttentionConfig config
        +onLifecycleEvent(ContextUnitLifecycleEvent)
        +getWindow(unitId, contextId) Optional~AttentionWindow~
        +getHottestUnits(contextId, limit) List~AttentionWindow~
        +getAllWindows(contextId) List~AttentionWindow~
        +snapshot(contextId) Map~String,AttentionSnapshot~
        +cleanupContext(contextId)
    }
    class AttentionWindow {
        <<record>>
        +unitId: String
        +contextId: String
        +conflictCount: int
        +reinforcementCount: int
        +totalEventCount: int
        +lastEventAt: Instant
        +windowStart: Instant
        +eventTimestamps: List~Instant~
        +heatScore() double
        +pressureScore() double
        +burstFactor() double
    }
    class AttentionSignal {
        +unitId: String
        +contextId: String
        +signalType: AttentionSignalType
        +snapshot: AttentionSnapshot
        +occurredAt: Instant
    }
    class AttentionSnapshot {
        <<record>>
        +of(AttentionWindow) AttentionSnapshot
        +ofCluster(List~AttentionWindow~) AttentionSnapshot
    }
    class AttentionSignalType {
        <<enum>>
        PRESSURE_SPIKE
        HEAT_PEAK
        HEAT_DROP
        CLUSTER_DRIFT
        +severity() Severity
    }
    class AttentionKey {
        <<record>>
        +unitId: String
        +contextId: String
    }
    class AttentionConfig {
        <<record>>
        +enabled: boolean
        +windowDuration: Duration
        +pressureThreshold: double
        +heatPeakThreshold: double
        +heatDropThreshold: double
        +clusterDriftMinUnits: int
        +minConflictsForPressure: int
        +maxExpectedEventsPerWindow: int
    }
    AttentionTracker --> AttentionWindow
    AttentionTracker --> AttentionSignal
    AttentionTracker --> AttentionConfig
    AttentionTracker --> AttentionKey
    AttentionSignal --> AttentionSignalType
    AttentionSignal --> AttentionSnapshot
```

## Risks / Trade-offs

- **Memory growth**: Each active context unit in each context gets a window with a list of timestamps. Mitigated by: (1) windows are pruned on every event, (2) Archived/Evicted context units have windows removed, (3) `cleanupContext()` for bulk teardown. For a budget of 20 context units, this is negligible.
- **Event storm during batch operations**: `ArcMemEngine.promote()` can trigger Promoted + Evicted + TierChanged in quick succession. The tracker handles each independently — this is by design, as rapid events are exactly what heat/burst metrics should capture.
- **CLUSTER_DRIFT scan cost**: Scanning all windows in a context on every event is O(budget). With default budget of 20, this is trivial. Would need optimization if budget scaled to thousands.
