# Feature: Memory Pressure Gauge

## Feature ID

`F04`

## Summary

Implement a composite memory pressure metric that quantifies anchor system health and triggers maintenance actions. Combines anchor count pressure, conflict detection rate, decay-triggered demotions, and compaction frequency into a single pressure score in the range [0.0, 1.0]. Pressure thresholds trigger maintenance events consumable by `MaintenanceStrategy` (F02).

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: dice-anchors has no quantitative measure of anchor system health. Budget enforcement is binary (over/under), decay is reactive, and there is no early warning for degraded states. A maintenance sweep triggered too late finds irrecoverable damage; one triggered too early wastes resources.
2. **Value delivered**: A continuous pressure signal that quantifies system health across four dimensions. Threshold breaches trigger proactive maintenance (F07), enabling the system to intervene before quality degrades rather than after.
3. **Why now**: Wave 2. Depends on F02 (maintenance strategy interface) for the `shouldRunSweep()` integration point. F07 (proactive maintenance cycle) depends on this feature for trigger signals.

## Scope

### In Scope

1. `MemoryPressureGauge` service: computes composite pressure score [0.0, 1.0] for a context.
2. Four pressure dimensions: budget pressure, conflict pressure, decay pressure, compaction pressure.
3. Configurable weights per dimension (via `DiceAnchorsProperties`).
4. Non-linear budget pressure computation (exponent 1.5, matching sleeping-llm edit pressure).
5. Configurable thresholds: `lightSweep` (default 0.4), `fullSweep` (default 0.8).
6. Event-driven updates: pressure recalculated on `AnchorLifecycleEvent` emissions.
7. Per-turn pressure snapshots for trend analysis.
8. Integration with `MaintenanceStrategy.shouldRunSweep()` from F02.

### Out of Scope

1. Pressure-triggered maintenance actions (F07 implements the response to pressure).
2. Pressure-driven UI dashboards beyond the numeric display (rich visualization is a future enhancement).
3. Cross-context pressure aggregation (each context has independent pressure).
4. LLM-based pressure assessment (pressure is purely computational).

## Dependencies

1. Feature dependencies: F02 (unified maintenance strategy -- provides `shouldRunSweep()` integration point).
2. Priority: MUST.
3. OpenSpec change slug: `memory-pressure-gauge`.
4. Research rec: K (memory pressure as budget signal).

### Library Dependencies

| Dependency | Artifact | Version | Status | License |
|-----------|----------|---------|--------|---------|
| **Micrometer** | `io.micrometer:micrometer-core` | Managed by Spring Boot BOM | Already present — transitive dependency with 60+ usages (`@Observed`, `ObservationRegistry`). Provides `Gauge`, `Counter`, `DistributionSummary`. | Apache 2.0 |
| **spring-boot-starter-actuator** | `org.springframework.boot:spring-boot-starter-actuator` | Managed by Spring Boot BOM | OPTIONAL — exposes metrics at `/actuator/metrics` for debugging. Not required for core pressure computation. | Apache 2.0 |

### Codebase Dependencies

| Dependency | Location | Usage |
|-----------|----------|-------|
| **AttentionWindow** | Existing `anchor/` package | Sliding-window event rate computation with burst factor. SHOULD be reused for conflict pressure and decay pressure dimensions. |

No new dependencies are REQUIRED for core functionality. Micrometer is already available. Actuator MAY be added for operational visibility but is not a prerequisite.

## Research Requirements

None. The sleeping-llm drowsiness signal provides the design pattern. Exact weight defaults and threshold values SHOULD be calibrated empirically through simulation runs, but the interface design does not require research.

## Impacted Areas

1. **`anchor/` package (primary)**: New types -- `MemoryPressureGauge` (service), `PressureScore` (record with total + per-dimension breakdown), `PressureThreshold` (record with light/full sweep levels), `PressureDimension` (enum: BUDGET, CONFLICT, DECAY, COMPACTION).
2. **`anchor/event/` package**: New event types -- `PressureThresholdBreached` extending `AnchorLifecycleEvent`.
3. **`DiceAnchorsProperties`**: New `pressure` config section with weights, thresholds, and exponents.
4. **`sim/engine/` package (consumer)**: `SimulationTurnExecutor` includes pressure score in context traces. `ContextTrace` MAY include pressure snapshot.

## Visibility Requirements

### UI Visibility

1. Pressure score MUST be displayable in SimulationView per turn (numeric value, [0.0, 1.0]).
2. Pressure score SHOULD be color-coded: green (< lightSweep), yellow (lightSweep-fullSweep), red (> fullSweep).
3. Per-dimension breakdown SHOULD be visible in RunInspectorView context traces.

### Observability Visibility

1. Pressure score MUST be logged at INFO level per turn: `pressure.score=0.45, pressure.budget=0.3, pressure.conflict=0.1, pressure.decay=0.05, pressure.compaction=0.0`.
2. Threshold breach events MUST be logged at WARN level: `pressure.threshold.breached=LIGHT_SWEEP, pressure.score=0.42`.
3. Pressure history SHOULD be available as an ordered list of per-turn snapshots for a context.

## Acceptance Criteria

1. Pressure score MUST be computable for any context at any time via `MemoryPressureGauge.computePressure(contextId)`.
2. Each pressure dimension MUST be independently measurable and reportable in the `PressureScore` record.
3. Threshold breaches MUST trigger `PressureThresholdBreached` events consumable by `MaintenanceStrategy`.
4. Weights and thresholds MUST be configurable via `DiceAnchorsProperties`.
5. Pressure history MUST be available for trend analysis (at minimum per-turn snapshots stored in memory for the context's lifetime).
6. Pressure gauge MUST NOT introduce significant per-turn latency (no LLM calls -- purely computational).
7. Budget pressure MUST use non-linear computation (exponent 1.5): `(activeCount / budgetCap) ^ 1.5`.
8. Conflict pressure MUST reflect recent conflict detection rate over a configurable sliding window.
9. Default weights MUST sum to 1.0 and SHOULD approximate: budget 0.4, conflict 0.3, decay 0.2, compaction 0.1.
10. Pressure computation MUST be deterministic for the same inputs.

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Weight miscalibration** | Medium | Medium | Default weights are estimates. Simulation A/B testing (reactive vs. hybrid with different weight sets) SHOULD calibrate optimal values. Weights are configurable, not hardcoded. |
| **Threshold noise** | Medium | Low | Short-lived pressure spikes (single turn with many conflicts) could trigger unnecessary sweeps. Mitigation: require sustained pressure above threshold for N consecutive turns before triggering full sweep. `lightSweep` has lower hysteresis requirement than `fullSweep`. |
| **Missing dimension** | Low | Medium | Four dimensions may not capture all degradation signals. Mitigation: `PressureDimension` is an enum -- adding a fifth dimension is additive and non-breaking. |
| **Performance at scale** | Low | Low | Pressure computation is O(1) per dimension (counts and ratios). No concern at current anchor budget sizes (20). |

## Proposal Seed

### Change Slug

`memory-pressure-gauge`

### Proposal Starter Inputs

1. **Problem statement**: dice-anchors has no quantitative measure of anchor system health. Budget enforcement is binary (over/under), decay is reactive, and there is no early warning for degraded states. Research (sleeping-llm) shows that a composite drowsiness signal effectively triggers maintenance before quality degrades.
2. **Why now**: F07 (proactive maintenance) needs a trigger mechanism. Without a pressure gauge, proactive sweeps would run on a fixed schedule, which is either too frequent (wasting resources) or too infrequent (missing degradation).
3. **Constraints/non-goals**: MUST be purely computational -- no LLM calls. SHOULD reuse existing lifecycle events for input. No pressure-triggered actions (F07 consumes the signal).
4. **Visible outcomes**: Pressure score per turn in simulation traces. Color-coded gauge in SimulationView. Threshold breach events in logs.

### Suggested Capability Areas

1. **Composite pressure computation**: Four-dimension weighted score with configurable parameters.
2. **Event integration**: Pressure recalculated on lifecycle events, threshold breach events emitted.
3. **History tracking**: Per-turn pressure snapshots for trend analysis.
4. **Configuration**: Weights, thresholds, and exponents via properties.

### Candidate Requirement Blocks

1. **REQ-COMPUTE**: The system SHALL compute a composite memory pressure score in the range [0.0, 1.0] from four dimensions (budget, conflict, decay, compaction).
2. **REQ-THRESHOLD**: The system SHALL emit threshold breach events when pressure crosses configurable light-sweep and full-sweep thresholds.
3. **REQ-CONFIG**: Pressure weights, thresholds, and exponents SHALL be configurable via `DiceAnchorsProperties`.
4. **REQ-HISTORY**: The system SHALL maintain per-turn pressure snapshots for trend analysis within a context's lifetime.
5. **REQ-PERF**: Pressure computation SHALL NOT require LLM calls and SHALL complete within millisecond-level latency.

## Validation Plan

1. **Unit tests** MUST verify pressure computation against known inputs (e.g., 15 anchors with budget 20 at weight 0.4 = known budget pressure contribution).
2. **Unit tests** MUST verify non-linear budget pressure (exponent 1.5).
3. **Unit tests** MUST verify threshold breach event emission when pressure crosses light and full thresholds.
4. **Unit tests** MUST verify weight configuration respects property values.
5. **Unit tests** MUST verify pressure score clamping to [0.0, 1.0].
6. **Integration test** SHOULD verify pressure tracking across multiple simulation turns with increasing conflict rate.
7. **Observability validation** MUST confirm pressure scores appear in structured logs with per-dimension breakdown.

## Known Limitations

1. **Weight calibration is empirical.** Default weights are informed by sleeping-llm ratios but not validated for the anchor domain. Simulation-based calibration is expected post-implementation.
2. **No cross-context aggregation.** Each context has an independent pressure score. System-wide pressure (across all active contexts) is not computed.
3. **History is in-memory only.** Pressure snapshots are lost when the context is cleaned up. Persistent pressure history is a candidate extension.
4. **Sliding window for conflict pressure requires buffering.** The conflict rate dimension tracks events over a window, requiring a bounded event buffer per context.

## Suggested Command

```
/opsx:new memory-pressure-gauge
```
