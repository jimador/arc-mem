## Why

dice-anchors maintenance is purely reactive -- decay and reinforcement happen per-turn in response to events. Degraded anchors persist until they happen to be touched. There is no periodic health assessment, so anchors that are no longer relevant to the conversation linger in the budget, crowding out valuable new propositions.

Research from Sleeping LLM (Guo et al., 2025) demonstrates that proactive "sleep cycles" recover degraded knowledge from 40% initial recall to 100% within 4 cycles. The sleeping-llm parallel: wake = reactive maintenance (per-turn), sleep = proactive maintenance (periodic sweep). The foundational interfaces for this are already in place: `MaintenanceStrategy` sealed interface (F02), `MemoryPressureGauge` pressure-triggered activation (F04). This feature is the payoff -- implementing the actual 5-step sweep cycle that the stub `ProactiveMaintenanceStrategy` was designed for.

## What Changes

- Replace the no-op `ProactiveMaintenanceStrategy` stub with a full 5-step sweep implementation: audit, refresh, consolidate, prune, validate
- Audit step: heuristic relevance scoring (entity overlap, recency, rank decay rate) with an optional batched LLM call for full sweeps only
- Refresh step: re-rank anchors whose audit score indicates relevance drift; trigger trust re-evaluation for borderline cases
- Consolidate step: identify RELIABLE anchors meeting candidacy criteria; route to `CanonizationGate` for CANON nomination
- Prune step: two-tier threshold eviction -- hard floor (0.1, always prune) + soft floor (0.3, prune only under pressure)
- Validate step: run `CompactionValidator` + `InvariantEvaluator` to confirm protected facts survived; log violations (no rollback -- F01 deferred)
- Wire `MemoryPressureGauge` as the cycle trigger: light sweep at pressure >= 0.4, full sweep at pressure >= 0.8
- Add proactive maintenance configuration to `DiceAnchorsProperties` (cycle frequency, thresholds, candidacy criteria)
- Track convergence metrics per cycle (audit scores, pruning counts, refresh counts)

## Capabilities

### New Capabilities
- `proactive-maintenance-cycle`: 5-step anchor health audit sweep (audit, refresh, consolidate, prune, validate) triggered by memory pressure thresholds

### Modified Capabilities
- `MaintenanceStrategy`: `ProactiveMaintenanceStrategy` transitions from stub to full implementation
- `DiceAnchorsProperties`: New `ProactiveConfig` nested record for sweep configuration
- `AnchorConfiguration`: Bean creation wires new dependencies into `ProactiveMaintenanceStrategy`

## Impact

- **Files**: Modified `ProactiveMaintenanceStrategy.java` (full implementation), `DiceAnchorsProperties.java` (ProactiveConfig), `AnchorConfiguration.java` (bean wiring). New: `AuditScore.java` (record), `SweepType.java` (enum), `CycleMetrics.java` (record), audit prompt template
- **APIs**: No new external APIs. Internal: `ProactiveMaintenanceStrategy` gains working `shouldRunSweep()` and `executeSweep()` methods
- **Config**: New `dice-anchors.maintenance.proactive.*` properties (min-turns-between-sweeps, hard-prune-threshold, soft-prune-threshold, candidacy criteria)
- **Dependencies**: No new dependencies. Uses existing `MemoryPressureGauge`, `AnchorEngine`, `CanonizationGate`, `CompactionValidator`, `InvariantEvaluator`, `LlmCallService`
- **Value**: Periodic health maintenance recovers anchor quality that reactive-only maintenance misses; reduces context pollution from stale anchors

## Constitutional Alignment

- **Article I (RFC 2119)**: All requirements use RFC 2119 keywords in spec
- **Article II (Neo4j sole store)**: Audit scores are transient (not persisted). Rank/authority changes flow through existing `AnchorRepository`
- **Article III (Constructor injection)**: All new dependencies injected via constructor
- **Article IV (Records)**: `AuditScore`, `CycleMetrics`, `SweepType` use records/enums
- **Article V (Anchor invariants)**: CANON anchors (A3b) and pinned anchors (A3d) immune to pruning. Rank clamped via `clampRank()` (A2). Budget enforcement preserved (A1). No auto-CANON assignment (A4)
- **Article VI (Sim isolation)**: Sweep operates within existing contextId isolation
- **Article VII (Test-first)**: Unit tests for each step, sweep trigger logic, and configuration validation

## Specification Overrides

### F01 Transaction Framework (Deferred)

The feature doc specifies that the cycle MUST run inside an anchor transaction (F01) with automatic rollback on validation failure. F01 is deferred, so this implementation logs validation failures but does not roll back. The validate step is designed so that adding rollback later requires only wrapping the cycle in a transaction boundary -- no restructuring.

**Override scope**: Acceptance criteria 3 ("Cycle MUST run inside an anchor transaction with automatic rollback") is relaxed to "Cycle SHOULD log validation failures; rollback support deferred to F01."

**Expiration**: When F01 (anchor-transaction-framework) is implemented.

### Prolog Pre-Filter (Omitted)

The feature doc specifies Prolog pre-filters for audit and validate steps. This is a demo repo; the Prolog integration adds complexity disproportionate to its demo value. The value of this feature is the 5-step cycle itself, not the Prolog optimization layer.

**Override scope**: All Prolog-related requirements (audit pre-filter, validate pre-check, A/B testability of Prolog toggle) are omitted. Audit uses heuristic + optional LLM only.

**Expiration**: If DICE Prolog integration matures and Prolog pre-filtering becomes a demo priority.
