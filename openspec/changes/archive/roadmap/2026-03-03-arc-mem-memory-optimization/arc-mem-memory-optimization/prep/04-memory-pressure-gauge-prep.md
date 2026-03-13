# Prep: Memory Pressure Gauge

**Feature**: F04 — `memory-pressure-gauge`
**Wave**: 2
**Priority**: MUST
**Depends on**: F02 (unified maintenance strategy)

## RFC 2119 Compliance

All normative statements use RFC 2119 keywords.

## Locked Decisions

1. **Composite score [0.0, 1.0]**: Single scalar pressure value computed from four weighted dimensions. Clamped to [0.0, 1.0].
2. **Four dimensions**: Budget pressure (context unit count vs. cap), conflict pressure (recent conflict rate), decay pressure (recent demotion rate), compaction pressure (compaction frequency).
3. **Event-driven updates**: Pressure recalculated on `ContextUnitLifecycleEvent` emissions (Promoted, Archived, RankDecayed, AuthorityChanged). No polling.
4. **Configurable thresholds**: `lightSweep` (default 0.4) and `fullSweep` (default 0.8) thresholds trigger `PressureThresholdBreached` events. Mirrors sleeping-llm nap/sleep thresholds.
5. **No LLM calls**: Pressure computation is purely arithmetic over counters and ratios. Millisecond-level latency.
6. **PressureScore record**: Immutable record containing total pressure + per-dimension breakdown. One snapshot per turn, stored in an in-memory list for the context's lifetime.
7. **Micrometer for metrics instrumentation**: `Gauge`, `Counter`, and `DistributionSummary` from Micrometer (already a transitive dependency with 60+ usages). No new library dependency REQUIRED. `spring-boot-starter-actuator` MAY be added for `/actuator/metrics` endpoint but is OPTIONAL.
8. **AttentionWindow for sliding-window rates**: Conflict pressure and decay pressure dimensions SHOULD reuse the existing `AttentionWindow` pattern for sliding-window event rate computation with burst factor.

## Open Questions

| # | Question | Options | Leaning | Resolution Trigger |
|---|----------|---------|---------|-------------------|
| 1 | **Exact weight defaults** | (a) Budget 0.4, conflict 0.3, decay 0.2, compaction 0.1 (emphasis on count and conflicts). (b) Budget 0.6, conflict 0.3, decay 0.1, compaction 0.0 (sleeping-llm edit-pressure-heavy). (c) Equal weights 0.25 each (unbiased). | (a) Budget 0.4, conflict 0.3, decay 0.2, compaction 0.1. Budget and conflict are the strongest degradation signals. Sleeping-llm's 0.6 edit-pressure weight is too heavy for context units where conflict rate matters more than raw count. | Calibration via simulation runs post-implementation. Defaults are overridable. |
| 2 | **Non-linear exponent for budget pressure** | (a) 1.5 (sleeping-llm's edit_pressure exponent). (b) 2.0 (quadratic -- sharper cliff near capacity). (c) Configurable exponent. | (c) Configurable, default 1.5. Matches sleeping-llm research. Higher exponents can be tested via properties. | Calibration via simulation. |
| 3 | **History retention policy** | (a) Retain all per-turn snapshots for context lifetime (in-memory). (b) Sliding window of last N turns (bounded memory). (c) Retain all but compress older snapshots to summary statistics. | (a) Retain all. Context lifetimes are bounded (simulation runs are finite). At 20 turns per context, 20 `PressureScore` records is negligible memory. | Revisit if contexts become long-lived (e.g., chat sessions with 100+ turns). |
| 4 | **Conflict pressure sliding window size** | (a) Last 5 turns. (b) Last 10 turns. (c) Configurable. | (c) Configurable, default 5 turns. Shorter windows are more responsive; longer windows are more stable. | Calibration via simulation. |
| 5 | **Threshold hysteresis** | (a) Simple threshold: breach on cross, no hysteresis. (b) Sustained pressure: breach requires N consecutive turns above threshold. (c) Hysteresis band: breach at threshold, clear at threshold - delta. | (a) Simple threshold for initial implementation. Hysteresis is a refinement if threshold noise becomes a problem in simulation testing. | Post-implementation if needed. |

## Visibility Contract

| Surface | What | When | Format |
|---------|------|------|--------|
| SimulationView | Pressure score per turn | Each turn | Numeric [0.0, 1.0] + color: green/yellow/red |
| RunInspectorView | Per-dimension breakdown | Context trace per turn | Table: dimension, value, weight, contribution |
| Structured logs | Pressure score | Per turn | INFO: `pressure.score=0.45 budget=0.30 conflict=0.10 decay=0.05 compaction=0.00` |
| Structured logs | Threshold breach | When threshold crossed | WARN: `pressure.threshold.breached=LIGHT_SWEEP score=0.42` |
| Lifecycle events | `PressureThresholdBreached` | When threshold crossed | Event with threshold type (LIGHT/FULL), score, context ID |

## Acceptance Gates

| Gate | Verification | Command |
|------|-------------|---------|
| Pressure computable per-context | Unit test: given 15 context units with budget 20, known conflict rate, known decay count -> deterministic pressure score. | `./mvnw test -pl . -Dtest=MemoryPressureGaugeTest` |
| Threshold breach triggers events | Unit test: pressure crosses 0.4 -> `PressureThresholdBreached(LIGHT_SWEEP)` event emitted. | `./mvnw test -pl . -Dtest=MemoryPressureGaugeTest` |
| No LLM calls | Code review: `MemoryPressureGauge` has no `ChatModel` or `LlmCallService` dependency. | Code review (structural verification) |
| Per-dimension breakdown available | Unit test: `PressureScore` record exposes budget, conflict, decay, compaction components. | `./mvnw test -pl . -Dtest=MemoryPressureGaugeTest` |

## Small-Model Constraints

- **Max 3 files per task** (gauge service + records + tests)
- **Verification**: `./mvnw test` MUST pass after each task
- **No LLM dependencies**: Purely computational
- **Scope boundary**: `context unit/` package for gauge and records; `sim/` integration is a separate task

## Task Sketch

| # | Scope | Files | Gate |
|---|-------|-------|------|
| T1 | `PressureScore` record + `PressureDimension` enum + `PressureThreshold` record | `PressureScore.java`, `PressureDimension.java`, `PressureThreshold.java` | Records compile, dimension enum has 4 values |
| T2 | `MemoryPressureGauge` service with composite computation + event subscription | `MemoryPressureGauge.java`, `MemoryPressureGaugeTest.java` | Deterministic pressure computation for known inputs |
| T3 | `ArcMemProperties` pressure config + `PressureThresholdBreached` event + history tracking | `ArcMemProperties.java` (update), `PressureThresholdBreached.java` | Threshold breach emits event; history snapshots accumulate per turn |

## Risks Requiring Design Attention

1. **Event subscription ordering**: `MemoryPressureGauge` subscribes to `ContextUnitLifecycleEvent`. If the gauge update fires before other event handlers (e.g., maintenance strategy), the pressure score may be stale for one turn. Ensure event ordering is documented or gauge is recalculated lazily on demand.
2. **Conflict pressure requires state**: Tracking conflict rate over a sliding window requires buffering recent conflict events per context. The buffer MUST be bounded and cleaned up with the context.
