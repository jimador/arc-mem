# Feature: Proactive Maintenance Cycle

## Feature ID

`F07`

## Summary

Implement a full proactive maintenance cycle -- the sleeping-llm-inspired 5-step anchor health audit: audit, refresh, consolidate, prune, validate. This is the `ProactiveMaintenanceStrategy` implementation of the `MaintenanceStrategy` interface (F02), triggered by memory pressure thresholds (F04). The sweep API SHOULD accept optional snapshot/restore callbacks to accommodate future transactional rollback (F01, deferred) without requiring rework.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: dice-anchors maintenance is purely reactive -- decay and reinforcement happen per-turn in response to events. Degraded anchors persist until they happen to be touched. There is no periodic health assessment. Anchors that are no longer relevant to the conversation linger in the budget, crowding out valuable new propositions.
2. Value delivered: Research (sleeping-llm, Paper 5) shows proactive sleep cycles recover degraded knowledge from 40% initial recall to 100% within 4 cycles. A periodic sweep that audits, refreshes, consolidates, prunes, and validates the anchor set directly improves long-horizon consistency.
3. Why now: Wave 3 -- foundational interfaces are in place (F02 strategy abstraction, F04 pressure triggers). This feature is the payoff -- the most impactful optimization from the sleeping-llm research.

## Scope

### In Scope

1. Implement `ProactiveMaintenanceStrategy` as a `MaintenanceStrategy` (F02) with 5 sequential steps: audit, refresh, consolidate, prune, validate.
2. Wire memory pressure gauge (F04) as the cycle trigger -- light sweep at pressure 0.4, full sweep at 0.8.
3. Sweep method SHOULD accept optional snapshot/restore callbacks (no-op default) to accommodate future transactional rollback (F01, deferred).
4. Audit step: evaluate anchor relevance against recent conversation context. A Prolog pre-filter SHOULD identify logically inconsistent anchors (contradiction chains, unsupported claims) deterministically before the LLM relevance check, reducing the number of anchors requiring expensive LLM evaluation. Anchors passing the Prolog filter are evaluated via a batched LLM call.
5. Refresh step: re-rank anchors whose relevance has drifted; trigger trust re-evaluation for borderline anchors.
6. Consolidate step: promote consistently-reinforced RELIABLE anchors toward CANON candidacy via `CanonizationGate`.
7. Prune step: archive anchors below audit threshold regardless of budget headroom (quality-based eviction).
8. Validate step: run `CompactionValidator` + `InvariantEvaluator` to ensure protected facts survived. Prolog invariant queries SHOULD verify protected fact survival and logical consistency deterministically as a fast pre-check before the full validator pass. Log violations; invoke restore callback if provided.
9. Configurable cycle frequency via `DiceAnchorsProperties` (min turns between sweeps, max pressure before forced sweep).
10. Convergence metrics tracking (audit scores per cycle, analogous to sleeping-llm's 30-facts-to-100%-in-4-cycles finding).
11. **A/B testability**: The Prolog pre-filter in audit and validate steps MUST be toggleable per-simulation. The simulator MUST support running the same scenario with Prolog pre-filter enabled vs. disabled (LLM-only audit) for direct comparison of LLM call reduction, audit accuracy, and cycle duration.

### Out of Scope

1. Reactive per-turn maintenance (already implemented via `DecayPolicy`/`ReinforcementPolicy` -- this feature adds a complementary proactive model).
2. Replacing the existing reactive maintenance -- both models coexist as `MaintenanceStrategy` implementations selectable per context.
3. New LLM model integrations -- audit uses the existing LLM infrastructure (`LlmCallService` or equivalent).
4. UI redesign -- visibility uses existing SimulationView and RunInspectorView extension points.

## Dependencies

1. Feature dependencies: F02 (unified-maintenance-strategy), F04 (memory-pressure-gauge). F01 (anchor-transaction-framework) is deferred; sweep API SHOULD be designed for future transaction support via optional callbacks.
2. Technical prerequisites: `MaintenanceStrategy` interface (F02), `MemoryPressureGauge` (F04), `CompactionValidator`, `InvariantEvaluator`, `CanonizationGate`, DICE Prolog projection (tuProlog/2p-kt via DICE 0.1.0-SNAPSHOT -- already on classpath, zero new dependencies).
3. Priority: MUST.
4. OpenSpec change slug: `proactive-maintenance-cycle`.
5. Research rec: A (full implementation).

## Research Requirements

1. Open questions:
   - What audit prompt design yields reliable relevance scoring with a single batched LLM call?
   - What consolidation candidacy criteria best identify RELIABLE anchors ready for CANON nomination?
   - What prune threshold calibration prevents over-pruning without retaining degraded anchors?
   - What default cycle frequency balances maintenance overhead against anchor health?
   - What Prolog query patterns best identify contradiction chains and unsupported claims among projected anchor facts?
2. Required channels: `codebase`
3. Research completion gate: Audit prompt prototype SHOULD be validated against at least one adversarial simulation scenario before full implementation.

## Impacted Areas

1. Packages/components: `anchor/` (`ProactiveMaintenanceStrategy` implementation, audit logic, consolidation candidacy), `sim/engine/` (maintenance cycle execution between turns), `DiceAnchorsProperties` (cycle configuration).
2. Data/persistence: No schema changes. Audit scores are transient (computed per cycle, not persisted). Rank/authority changes flow through existing `AnchorRepository`.
3. Domain-specific subsystem impacts: Simulation turn executor gains a maintenance-between-turns hook. Chat flow MAY expose an explicit trigger for proactive sweeps.

## Visibility Requirements

### UI Visibility

1. User-facing surface: SimulationView SHOULD display a maintenance cycle indicator (when a sweep runs and its outcome). RunInspectorView SHOULD display audit results per anchor.
2. What is shown: Cycle step progression, per-anchor audit scores, refresh/consolidate/prune outcomes, validation pass/fail, rollback events.
3. Success signal: After a full sweep, anchor quality metrics (average audit score, budget utilization) improve measurably.

### Observability Visibility

1. Logs/events/metrics: Per-step timing (audit, refresh, consolidate, prune, validate). Counts for anchors audited, refreshed, consolidated, pruned. Validation pass/fail. Rollback events. Logger MUST emit cycle summary at INFO level.
2. Trace/audit payload: Full audit score per anchor, consolidation candidacy decisions, prune threshold comparisons, validation details.
3. How to verify: Log grep for `maintenance.cycle.complete` events; compare pre/post anchor quality metrics in simulation runs.

## Acceptance Criteria

1. Proactive maintenance cycle MUST execute the 5-step sequence (audit, refresh, consolidate, prune, validate) in order.
2. Cycle MUST be triggered by memory pressure threshold from F04 (light sweep at 0.4, full sweep at 0.8).
3. Cycle MUST run inside an anchor transaction (F01) with automatic rollback on validation failure.
4. Audit step MUST evaluate anchor relevance against recent conversation context. SHOULD use Prolog queries as a deterministic pre-filter to identify logically inconsistent anchors before the LLM relevance check. MAY use a single batched LLM call for remaining anchors.
5. Refresh step MUST re-rank anchors whose audit score indicates relevance drift. SHOULD trigger trust re-evaluation for borderline anchors.
6. Consolidate step MUST identify RELIABLE anchors meeting candidacy criteria and route them to `CanonizationGate`.
7. Prune step MUST evict anchors below audit threshold regardless of budget headroom.
8. Validate step MUST check invariants via `InvariantEvaluator` AND protected fact survival via `CompactionValidator`. SHOULD run Prolog invariant queries as a fast deterministic pre-check before the full validator pass.
9. Cycle metrics (anchors audited, refreshed, consolidated, pruned, elapsed time) MUST be traceable.
10. Simulator MUST support running proactive maintenance between turns with configurable frequency.
11. CANON anchors MUST NOT be pruned during the cycle (invariant A4).
12. Pinned anchors MUST NOT be pruned during the cycle.

## Risks and Mitigations

1. Risk: Proactive sweep runs too frequently, degrading per-turn latency.
   Mitigation: Conservative defaults (high pressure threshold for forced sweep, minimum turns between sweeps). Adaptive scheduling based on sweep outcome metrics.
2. Risk: Audit LLM call returns low-quality relevance scores, leading to incorrect pruning.
   Mitigation: Validate audit prompt against known scenarios before deployment. Transaction rollback catches validation failures.
3. Risk: Consolidation promotes anchors prematurely toward CANON.
   Mitigation: Consolidation routes through `CanonizationGate` which enforces HITL approval for CANON transitions.
4. Risk: Transaction rollback overhead is non-trivial for large anchor sets.
   Mitigation: Snapshot scope limited to anchors modified during the cycle, not the entire context.

## Proposal Seed

### Suggested OpenSpec Change Slug

`proactive-maintenance-cycle`

### Proposal Starter Inputs

1. Problem statement: dice-anchors maintenance is purely reactive -- decay and reinforcement happen per-turn in response to events. Degraded anchors persist until they happen to be touched. There is no periodic health assessment. Research (sleeping-llm) shows proactive sleep cycles recover degraded knowledge from 40% initial recall to 100% within 4 cycles. The sleeping-llm parallel: wake = reactive maintenance (per-turn), sleep = proactive maintenance (periodic sweep).
2. Why now: Foundational interfaces are in place (F01 transactions, F02 strategy abstraction, F04 pressure triggers). This feature is the payoff -- the most impactful optimization from the sleeping-llm research.
3. Constraints: MUST use existing LLM infrastructure (no new model integrations). Audit LLM call SHOULD be batched for efficiency. MUST NOT run during active user turns (between turns only in sim; on explicit trigger in chat). MUST preserve anchor invariants (A1-A4).
4. Visible outcomes: Maintenance cycle indicator in SimulationView; audit results in RunInspectorView; per-step timing and outcome metrics in logs.

### Suggested Capability Areas

1. 5-step maintenance cycle implementation (ProactiveMaintenanceStrategy).
2. Prolog pre-filter for audit (contradiction chain detection, unsupported claim identification via DICE Prolog projection).
3. Audit LLM prompt design and batched evaluation (for anchors passing the Prolog pre-filter).
4. Consolidation candidacy criteria and CanonizationGate routing.
5. Quality-based pruning (distinct from budget-overflow eviction).
6. Transaction-wrapped validation with rollback (Prolog invariant queries as fast pre-check).
7. Convergence metrics tracking.

### Candidate Requirement Blocks

1. Requirement: The proactive maintenance cycle SHALL execute 5 steps (audit, refresh, consolidate, prune, validate) as a single transactional unit.
2. Scenario: In a 20-turn simulation with adversarial contradiction turns, a proactive sweep triggered at pressure 0.8 SHALL audit all active anchors, prune those below relevance threshold, and validate that protected facts survived -- rolling back if validation fails.
3. Scenario: Sleeping-llm convergence parallel -- running 4 maintenance cycles on a degraded anchor set (40% of anchors below relevance threshold) SHALL recover anchor quality to measurable improvement, tracked via convergence metrics.

## Research Findings

| Task ID | Key Finding | Evidence Source | Confidence | Impact on Scope |
|---------|-------------|-----------------|------------|-----------------|
| Rec A | Sleeping-llm 8-step sleep cycle (audit, maintain, consolidate, validate) recovers 40% recall to 100% in 4 cycles. | `openspec/research/llm-optimization-external-research.md` sec 3.1 | High | Validates the 5-step cycle design. |
| Rec K | Memory pressure metric combining anchor count, conflict rate, demotion frequency triggers maintenance. | `openspec/research/llm-optimization-external-research.md` sec 3.3 | High | Confirms pressure-based triggering. |
| Paper 5 | MEMIT-only (no LoRA) shows wake capacity threshold and sleep convergence proof. Pruning death spiral risk. | sleeping-llm Paper 5 (Zenodo 18778768) | Medium | Prune threshold calibration must avoid death spiral (over-pruning triggers more pruning). |

## Validation Plan

1. Unit tests: Each step (audit, refresh, consolidate, prune, validate) tested independently with mock anchors and predictable audit scores.
2. Integration test: Full 5-step cycle against a simulation run with known degraded anchors; verify correct pruning and rollback on validation failure.
3. Observability validation: Cycle summary logged at INFO; per-step metrics traceable.
4. Simulation validation: Compare reactive-only vs. reactive+proactive maintenance on adversarial scenarios; measure anchor quality convergence.

## Known Limitations

1. Audit quality depends on the LLM's ability to assess anchor relevance in a single batched call. Cross-model generalization is a follow-up concern.
2. Convergence metrics are descriptive (not prescriptive) -- the system does not auto-tune cycle frequency based on convergence rate in this iteration.
3. The "Aria Effect" (sleeping-llm Paper 6) implies some anchors are inherently harder to maintain. Per-anchor difficulty tracking is not included in this feature.
4. Proactive maintenance adds scheduling complexity that reactive-only systems avoid. A badly-tuned cycle frequency could degrade performance.
5. Prolog pre-filter coverage depends on the quality of proposition-to-fact projection. Semantic contradictions that are not logically representable as Prolog facts still require LLM evaluation. Prolog is a cost-reduction optimization, not a completeness guarantee.

## Suggested Command

`/opsx:new proactive-maintenance-cycle`
