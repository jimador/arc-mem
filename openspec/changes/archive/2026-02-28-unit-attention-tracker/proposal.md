## Why

The context unit subsystem publishes a rich stream of lifecycle events (Promoted, Reinforced, Archived, Evicted, ConflictDetected, ConflictResolved, AuthorityChanged, TierChanged, Superseded, InvariantViolation) but nothing aggregates these signals over time. Every event is fire-and-forget — consumed by individual listeners with no temporal memory.

This means the system has **zero capacity to detect patterns** across events:

- **No attention tracking**: There is no way to know which context units are "hot" right now — frequently reinforced, recently conflicted, actively referenced — versus which are cold and drifting toward dormancy.
- **No early warning for drift**: When a cluster of related context units begins decaying simultaneously or reinforcement patterns shift away from established facts, the system cannot detect the trend until contradictions have already surfaced.
- **No conflict pressure detection**: Conflicts arise naturally from conversation evolution (corrections, world changes, elaborations that contradict sparse earlier facts) as well as from adversarial testing. There is no mechanism to detect when specific context units are experiencing disproportionate conflict pressure, regardless of cause.
- **No observability surface**: Simulation runs and chat sessions produce thousands of events but there is no time-windowed aggregation to power heat maps, dashboards, or post-run analysis.

The current context unit mechanisms (injection, conflict detection, authority hierarchy) work at the individual-event level. A temporal aggregation layer would enable *pattern-level* awareness and observability — detecting trends like topic drift, narrative focus shifts, and fact instability before they manifest as contradictions.

## What Changes

Add an **Context Unit Attention Tracker** — a generic, event-driven subsystem that monitors patterns of context unit activity over sliding time windows. The tracker consumes existing `ContextUnitLifecycleEvent`s and produces `AttentionSignal`s that downstream consumers can react to.

### Core subsystem (context unit package)

- **`AttentionTracker`**: Spring-managed event listener that maintains per-context unit, per-context sliding window accumulators. Consumes all `ContextUnitLifecycleEvent` subtypes. Publishes `AttentionSignal` events when thresholds are crossed.
- **`AttentionWindow`**: Per-context unit time-windowed accumulator. Tracks event counts by type, timestamps, conflict frequency, reinforcement frequency, and derived metrics (heat score, pressure score).
- **`AttentionSignal`**: Spring application event published when an context unit's attention metrics cross configured thresholds. Carries the context unit ID, context ID, signal type, and snapshot of the attention window.
- **`AttentionSignalType`**: Enum of signal categories — `PRESSURE_SPIKE` (rapid conflict accumulation), `HEAT_PEAK` (high overall activity), `HEAT_DROP` (activity cliff suggesting drift), `CLUSTER_DRIFT` (multiple related context units decaying together).
- **`AttentionConfig`**: Configuration properties under `context units.attention.*` controlling window duration, threshold values, and enable/disable.

### Signal consumers (downstream packages)

The tracker itself does NOT implement any use-case-specific logic. Consumers subscribe to `AttentionSignal` events:

1. **Attention-aware context assembly** — Uses heat scores as an additional factor in `RelevanceScorer` or `CompactedContextProvider` to prefer hot context units over cold ones.
2. **Spotlight/focus tracking** — Listens for `HEAT_PEAK` signals. Ranks context units by current heat score for context assembly prioritization.
3. **Drift early warning** — Listens for `HEAT_DROP` and `CLUSTER_DRIFT` signals. Flags potential narrative drift before contradictions surface.
4. **Topic heat map observability** — Periodically snapshots all attention windows for dashboard visualization.
5. **Conflict pressure response** — Listens for `PRESSURE_SPIKE` signals. Could log warnings, harden targeted context units (pin, rank boost), or surface alerts. Useful for both natural conversation instability and adversarial testing scenarios.

These consumers are **out of scope for this change** — the tracker establishes the signal infrastructure; consumers will be separate changes.

### Future: Embabel Agent integration

The tracker's query API provides the integration surface for deeper Embabel Agent framework integration. These are **out of scope for this change** but shape the design:

1. **Attention-enriched tool responses (Surface A)** — `ContextUnitQueryTools` could include attention metadata (heat score, pressure score) in `UnitSummary` responses, giving the LLM richer context for self-directed behavior. Requires no Embabel API changes — just enriching existing tool return types.
2. **Attention as @Condition gates (Surface B)** — Embabel's `@Condition` methods could gate action execution based on attention state (e.g., "no context units under active pressure", "context is stable"). Becomes relevant when the chat flow evolves from a single-action model to multi-action GOAP planning.
3. **Goal value functions (Surface C)** — Embabel's `Goal.value` cost computation could factor in attention signals, enabling the GOAP planner to autonomously prioritize context unit defense or drift correction. Requires context units to evolve to a multi-goal agent architecture.

The query API (not the event stream) is the natural Embabel integration point, since Embabel's action execution model is synchronous request-response. Convenience methods like `getMaxPressure(contextId)` and `isContextStable(contextId)` are trivially derivable from the existing query API and can be added when consumers need them.

## Capabilities

### New Capabilities
- `unit-attention-tracking`: Event-driven sliding-window aggregation of context unit lifecycle events, producing attention signals when thresholds are crossed.

### Modified Capabilities
- None — this is purely additive. No existing behavior changes.

## Impact

### New files
- `src/main/java/dev/dunnam/arcmem/context unit/attention/AttentionTracker.java`
- `src/main/java/dev/dunnam/arcmem/context unit/attention/AttentionWindow.java`
- `src/main/java/dev/dunnam/arcmem/context unit/attention/AttentionSignal.java`
- `src/main/java/dev/dunnam/arcmem/context unit/attention/AttentionSignalType.java`
- `src/main/java/dev/dunnam/arcmem/context unit/attention/AttentionConfig.java`
- `src/main/java/dev/dunnam/arcmem/context unit/attention/AttentionSnapshot.java`
- Test files for all of the above

### Modified files
- `src/main/resources/application.yml` — add `context units.attention.*` defaults

### Constitutional alignment
- **Article II (Neo4j only)**: Compliant. The tracker is entirely in-memory; no new persistence.
- **Article III (Constructor injection)**: Compliant. All beans use constructor injection.
- **Article IV** (if applicable): No new dependencies.
- Uses RFC 2119 keywords per Article I.

## Use Cases

### UC1: Attention-Aware Context Assembly
The `RelevanceScorer` currently uses rank and authority. With attention signals, it could also factor in recency-of-activity: a rank-500 context unit that was just reinforced twice may be more relevant than a rank-700 context unit that hasn't been touched in 10 turns. This is the most broadly useful consumer — every conversation benefits from attention-informed context assembly.

### UC2: Spotlight / Focus Tracking
During a D&D session, the party spends several turns interacting with a specific NPC. The NPC's context unit gets repeatedly reinforced (mentioned in context assembly, referenced by extraction). Its heat score climbs. A `HEAT_PEAK` signal fires, telling context assembly to prioritize this context unit even if its base rank isn't the highest.

### UC3: Drift Early Warning
Three context units related to the same location (tavern name, tavern owner, tavern location) all stop being reinforced over several turns. Their heat scores drop. The tracker detects the cluster pattern and publishes a `CLUSTER_DRIFT` signal before any of them individually decay enough to trigger archival.

### UC4: Topic Heat Map
A simulation run executes 25 turns. After each turn, an observability consumer snapshots all attention windows and records them. The UI renders a heat map showing which context units were hottest at each turn — useful for understanding how narrative focus shifted over time.

### UC5: Conflict Pressure Detection
An context unit experiences disproportionate conflict events — whether from natural conversation evolution (a player correcting earlier assumptions, world details being elaborated in ways that conflict with sparse initial facts) or from deliberate adversarial pressure in stress-test scenarios. The `AttentionTracker` accumulates these in the context unit's window. When the conflict ratio within the window exceeds the `pressureThreshold`, a `PRESSURE_SPIKE` signal is published. A downstream consumer could pin the context unit, boost its rank, or surface it for operator review.
