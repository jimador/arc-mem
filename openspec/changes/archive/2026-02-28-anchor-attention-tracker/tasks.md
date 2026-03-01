## 1. Configuration

- [x] 1.1 Add `AttentionConfig` nested record to `DiceAnchorsProperties` with all properties (enabled, windowDuration, pressureThreshold, minConflictsForPressure, heatPeakThreshold, heatDropThreshold, clusterDriftMinAnchors, maxExpectedEventsPerWindow)
- [x] 1.2 Add `dice-anchors.attention.*` defaults to `application.yml`
- [x] 1.3 Test: config binding with defaults, config binding with overrides

## 2. Core Value Objects

- [x] 2.1 Create `AttentionKey` record in `anchor/attention/` — `(anchorId, contextId)`
- [x] 2.2 Create `AttentionWindow` record with all fields and computed methods (`heatScore`, `pressureScore`, `burstFactor`)
- [x] 2.3 Create `AttentionWindow.withEvent()` factory that produces a new window with incremented counts and pruned timestamps
- [x] 2.4 Create `AttentionWindow.withConflict()` factory that increments both `conflictCount` and `totalEventCount`
- [x] 2.5 Test: `heatScore` normalization and clamping at 1.0
- [x] 2.6 Test: `pressureScore` returns 0.0 on empty window
- [x] 2.7 Test: `burstFactor` with uniform vs clustered timestamps
- [x] 2.8 Test: `withEvent` prunes timestamps outside window duration

## 3. Signal Types

- [x] 3.1 Create `AttentionSignalType` enum with `PRESSURE_SPIKE`, `HEAT_PEAK`, `HEAT_DROP`, `CLUSTER_DRIFT` and `severity()` method
- [x] 3.2 Create `AttentionSnapshot` record with `of(AttentionWindow)` and `ofCluster(List<AttentionWindow>)` factories
- [x] 3.3 Create `AttentionSignal` class extending `ApplicationEvent`
- [x] 3.4 Test: snapshot from single window captures correct metrics
- [x] 3.5 Test: snapshot from cluster aggregates means

## 4. AttentionTracker

- [x] 4.1 Create `AttentionTracker` bean with constructor injection of `ApplicationEventPublisher` and `AttentionConfig`
- [x] 4.2 Implement `@EventListener` method with anchor ID extraction via pattern matching switch
- [x] 4.3 Implement window update logic using `ConcurrentHashMap.compute()`
- [x] 4.4 Implement threshold evaluation after each window update (PRESSURE_SPIKE, HEAT_PEAK, HEAT_DROP)
- [x] 4.5 Implement hysteresis tracking via `EnumSet<AttentionSignalType>` per key
- [x] 4.6 Implement CLUSTER_DRIFT evaluation scoped to context
- [x] 4.7 Implement window removal on Archived/Evicted events
- [x] 4.8 Test: event processing creates new window
- [x] 4.9 Test: ConflictDetected updates windows for all conflicting anchor IDs
- [x] 4.10 Test: threshold crossing publishes signal exactly once (hysteresis)
- [x] 4.11 Test: hysteresis resets after metric drops below threshold
- [x] 4.12 Test: Archived event removes window
- [x] 4.13 Test: disabled config short-circuits all processing

## 5. Query API

- [x] 5.1 Implement `getWindow(anchorId, contextId)` returning `Optional<AttentionWindow>`
- [x] 5.2 Implement `getHottestAnchors(contextId, limit)` returning sorted list
- [x] 5.3 Implement `getAllWindows(contextId)` returning defensive copy
- [x] 5.4 Implement `snapshot(contextId)` returning frozen `Map<String, AttentionSnapshot>`
- [x] 5.5 Implement `cleanupContext(contextId)` removing all windows and hysteresis state
- [x] 5.6 Test: `getHottestAnchors` returns correct order and limit
- [x] 5.7 Test: `cleanupContext` removes all state for context
- [x] 5.8 Test: query on empty context returns empty collections

## 6. Architecture & Future Enhancements Documentation

- [x] 6.1 Create `docs/attention-tracker-architecture.md` with full design overview: package structure, data flow diagram, component responsibilities, thread safety model, and configuration reference
- [x] 6.2 Document future enhancement roadmap: Embabel integration surfaces (A: tool enrichment, B: @Condition gates, C: GOAP goal value), attention-aware context assembly consumer, drift early warning consumer, topic heat map observability consumer
- [x] 6.3 Document design decisions and trade-offs: why `anchor/attention/` package placement, why ApplicationEvent (not sealed hierarchy), why ConcurrentHashMap over synchronized, heatScore static normalization (with note to revisit adaptive normalization), CLUSTER_DRIFT per-event evaluation (with note to revisit periodic scheduled evaluation)

## 7. Verification

- [x] 7.1 All existing tests pass (`./mvnw test`)
- [x] 7.2 New tests pass with correct assertions
- [x] 7.3 Compile clean with no warnings
