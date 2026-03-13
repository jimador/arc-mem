# Prep: Proactive Maintenance Cycle

## Feature Reference

Feature ID: `F07`. Change slug: `proactive-maintenance-cycle`. Wave 3. Priority: MUST.
Feature doc: `openspec/roadmaps/unit-memory-optimization/features/07-proactive-maintenance-cycle.md`
Research: `openspec/research/llm-optimization-external-research.md` (rec A, K)

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Locked Decisions

These decisions are final and MUST NOT be revisited during implementation.

1. **5-step cycle**: The proactive maintenance cycle MUST execute audit, refresh, consolidate, prune, validate -- in that order, as a single logical unit.
2. **Transaction-wrapped**: The entire cycle MUST run inside an context unit transaction (F01). Validation failure in step 5 triggers automatic rollback of all changes from steps 2-4.
3. **Pressure-triggered**: Cycle activation MUST be driven by the memory pressure gauge (F04). Light sweep at pressure >= 0.4; full sweep at pressure >= 0.8.
4. **Sleeping-llm-inspired**: The cycle is adapted from sleeping-llm's 8-step sleep pipeline (audit, maintain, consolidate, validate). Our 5-step mapping: audit = audit, refresh = maintain, consolidate = LoRA consolidation analog, prune = capacity management, validate = PPL validation analog.
5. **MaintenanceStrategy implementation**: `ProactiveMaintenanceStrategy` MUST implement the `MaintenanceStrategy` interface from F02, coexisting alongside the reactive strategy.
6. **CANON/pinned immunity**: CANON context units and pinned context units MUST NOT be pruned during any cycle step (invariants A3b, A4).
7. **CanonizationGate routing**: Consolidation step MUST route CANON candidacy through the existing `CanonizationGate` -- no bypass, no auto-approval.
8. **Prolog as pre-filter, not replacement**: DICE Prolog projection (tuProlog/2p-kt) MUST be used as a fast, deterministic pre-filter to reduce LLM calls in audit and validate steps. Prolog MUST NOT replace LLM-based relevance evaluation -- semantic contradictions that are not logically representable as Prolog facts still require LLM assessment. Zero new dependencies (already on classpath via DICE 0.1.0-SNAPSHOT).
9. **A/B testability**: The Prolog pre-filter MUST be toggleable per-simulation via scenario YAML (e.g., `prologPreFilter: true/false`). The simulator MUST support running the same scenario with and without the Prolog pre-filter to measure LLM call reduction, audit accuracy delta, and cycle duration impact. The LLM-only audit path (no Prolog) is the standard counterpart.

## Open Questions

These questions MUST be resolved during design/implementation. Each question includes the decision space and recommended approach.

### Q1: Audit LLM Prompt Design

**Question**: What prompt structure yields reliable relevance scoring for a batch of context units against recent conversation context in a single LLM call?

**Decision space**:
- (a) Single-call batch: pass all active context units + recent N turns of conversation; ask for per-context unit relevance score [0.0, 1.0].
- (b) Two-phase: lightweight heuristic pre-filter (entity overlap, recency) followed by LLM call for borderline context units only.
- (c) Pure heuristic: no LLM call; score based on entity overlap with recent conversation, time since last reinforcement, and memory tier.
- (d) Prolog pre-filter + LLM: project active context units to Prolog facts via DICE's `PrologEngine`, run deterministic queries to identify contradiction chains and unsupported claims. Context Units flagged by Prolog are marked as logically inconsistent (scored 0.0) without LLM evaluation. Remaining context units proceed to a batched LLM relevance call.

**Recommendation**: Option (d) for full sweeps (pressure >= 0.8) -- Prolog pre-filter reduces the LLM batch size by eliminating logically inconsistent context units deterministically. Option (b) for light sweeps (pressure >= 0.4), augmented with the Prolog pre-filter where available. Pure heuristic (c) as fallback when both LLM and Prolog are unavailable.

**Constraints**: Audit MUST complete in a single batched call where LLM is used. The prompt MUST include context unit text, authority, and rank alongside conversation context. Prolog pre-filter SHOULD run before the LLM call to reduce batch size. Prolog projection uses DICE 0.1.0-SNAPSHOT's tuProlog (2p-kt) -- already on the classpath with zero new dependencies.

### Q2: Consolidation Candidacy Criteria

**Question**: What criteria identify RELIABLE context units ready for CANON nomination?

**Decision space**:
- (a) Reinforcement count threshold (e.g., >= 10 reinforcements while RELIABLE).
- (b) Audit score threshold (e.g., relevance >= 0.8 in consecutive cycles).
- (c) Combined: reinforcement count AND audit score AND minimum age (turns since promotion to RELIABLE).
- (d) Trust score threshold from `TrustPipeline` re-evaluation.

**Recommendation**: Option (c) -- combined criteria requiring all three signals. This prevents premature consolidation from any single signal.

**Constraints**: Candidacy criteria MUST be configurable via `ArcMemProperties`. CANON assignment still requires `CanonizationGate` approval.

### Q3: Prune Threshold Calibration

**Question**: What audit score threshold triggers pruning, and how to avoid the pruning death spiral (sleeping-llm Paper 5)?

**Decision space**:
- (a) Fixed threshold (e.g., audit score < 0.2 triggers pruning).
- (b) Relative threshold (e.g., bottom 20% of context units by audit score).
- (c) Adaptive threshold: start conservative (0.1), increase if budget pressure remains high after pruning.
- (d) Two-tier: hard floor (< 0.1, always prune) + soft floor (< 0.3, prune only if budget pressure > 0.6).

**Recommendation**: Option (d) -- two-tier prevents death spiral by only aggressively pruning context units that are genuinely irrelevant (hard floor), while the soft floor responds to budget pressure. The death spiral risk from sleeping-llm Paper 5 occurs when pruning itself triggers more degradation; the hard floor being very low (0.1) mitigates this.

**Constraints**: Thresholds MUST be configurable. Pruning MUST NOT remove context units above the hard floor when budget pressure is below 0.4. Pruning MUST respect CANON and pinned immunity.

### Q4: Prolog Pre-Filter Query Design

**Question**: What Prolog query patterns best identify logically inconsistent context units among projected proposition facts?

**Decision space**:
- (a) Contradiction chain detection: query for pairs of propositions where one negates the other (e.g., `contradicts(A, B)` derived from proposition polarity and subject matching).
- (b) Unsupported claim detection: query for propositions that reference entities or relationships not grounded by any other proposition in the active set.
- (c) Temporal consistency: query for propositions whose claimed states are logically incompatible given a timeline (e.g., "X is alive" and "X died" without resurrection).
- (d) All of the above, applied in sequence with short-circuit evaluation.

**Recommendation**: Option (d) -- layered queries applied in sequence. Contradiction chains (a) are the highest-value, lowest-complexity check and SHOULD be implemented first. Unsupported claims (b) and temporal consistency (c) MAY be added incrementally. Each query type SHOULD have an independent enable/disable flag.

**Constraints**: Prolog projection MUST use DICE's existing `PrologEngine.query()` / `queryAll()` / `findAll()` API. Query execution time MUST be sub-second for context unit sets up to budget maximum (default 20). Prolog pre-filter is a cost-reduction optimization -- context units NOT flagged by Prolog still require LLM evaluation.

### Q5: Cycle Frequency Defaults

**Question**: What default values for min turns between sweeps and max pressure before forced sweep?

**Decision space**:
- (a) Conservative: min 10 turns between sweeps, forced at pressure >= 0.9.
- (b) Moderate: min 5 turns, forced at pressure >= 0.8.
- (c) Aggressive: min 3 turns, forced at pressure >= 0.6.

**Recommendation**: Option (a) -- conservative defaults. Sleeping-llm's finding (100% recovery in 4 cycles) suggests cycles don't need to be frequent to be effective. Over-frequent sweeps waste LLM calls and add latency. Users can tune down for aggressive scenarios.

**Constraints**: Defaults MUST err on the side of under-sweeping rather than over-sweeping (roadmap known limitation 3). Forced sweep threshold MUST be higher than light sweep trigger (0.4).

## Small-Model Task Constraints

Each implementation task MUST touch at most **5 files** (excluding test files). Each task MUST be independently verifiable via `./mvnw test`.

### Suggested Task Breakdown

1. **Task 1: ProactiveMaintenanceStrategy skeleton** (3 files)
   - Create `ProactiveMaintenanceStrategy` implementing `MaintenanceStrategy` (F02 interface).
   - 5-step method skeleton with logging but no implementation bodies.
   - Wire as Spring `@Bean` alongside reactive strategy.
   - Files: `ProactiveMaintenanceStrategy.java`, Spring config bean, test.

2. **Task 2: Audit step -- Prolog pre-filter** (4 files)
   - Project active context units to Prolog facts via DICE `PrologEngine`.
   - Implement Prolog queries for contradiction chain detection and unsupported claim identification.
   - Context Units flagged by the pre-filter are scored 0.0 (logically inconsistent) and excluded from the LLM batch.
   - Files: `ContextUnitPrologPreFilter.java`, Prolog query definitions, `ProactiveMaintenanceStrategy.java` (pre-filter integration), test.

3. **Task 3: Audit step -- LLM relevance evaluation** (4 files)
   - Implement batched LLM relevance call for context units passing the Prolog pre-filter.
   - Design audit prompt template for batched LLM call.
   - Merge Prolog pre-filter results with LLM scores into unified per-context unit audit scores.
   - Files: `ProactiveMaintenanceStrategy.java` (audit method), audit prompt template, `LlmCallService` integration, test.

4. **Task 4: Refresh + consolidate steps** (4 files)
   - Refresh: re-rank context units based on audit scores, trigger trust re-evaluation for borderline.
   - Consolidate: identify RELIABLE candidacy context units, route to `CanonizationGate`.
   - Files: `ProactiveMaintenanceStrategy.java` (refresh + consolidate methods), `ArcMemProperties` (candidacy config), `CanonizationGate` integration, test.

5. **Task 5: Prune + validate steps** (5 files)
   - Prune: archive context units below threshold with two-tier logic.
   - Validate: run Prolog invariant queries as a fast deterministic pre-check (protected fact survival, logical consistency), then `CompactionValidator` + `InvariantEvaluator` for full validation. Rollback on failure via F01 transaction.
   - Files: `ProactiveMaintenanceStrategy.java` (prune + validate methods), `ArcMemProperties` (prune thresholds), Prolog validation queries, `CompactionValidator`/`InvariantEvaluator` integration, test.

6. **Task 6: Simulation integration** (4 files)
   - Hook proactive maintenance into `SimulationTurnExecutor` between turns.
   - Wire memory pressure gauge trigger.
   - Add cycle frequency configuration.
   - Files: `SimulationTurnExecutor.java`, `ArcMemProperties` (frequency config), `MemoryPressureGauge` integration, test.

7. **Task 7: Convergence metrics + observability** (4 files)
   - Track per-cycle audit score averages, pruning counts, Prolog pre-filter hit rates, convergence trajectory.
   - Emit structured log events at INFO for cycle summary, DEBUG for per-context unit details.
   - Expose cycle outcome in `SimulationRunContext` for UI display.
   - Files: convergence metrics record, `ProactiveMaintenanceStrategy.java` (metrics emission), `SimulationRunContext` extension, test.

## Gates

Implementation is complete when ALL of the following are satisfied:

1. **Cycle completeness**: The 5-step cycle (audit, refresh, consolidate, prune, validate) MUST execute end-to-end in a simulation run without errors.
2. **Rollback works**: A test scenario where validation fails (e.g., protected fact pruned) MUST trigger automatic rollback, restoring all context units to pre-cycle state.
3. **Measurable recall improvement**: Running proactive maintenance on a degraded context unit set in simulation MUST produce measurable improvement in context unit quality metrics (average audit score, budget utilization, or fact survival rate) compared to the same scenario with reactive-only maintenance.
4. **No regression**: Existing simulation scenarios MUST produce equivalent or better results with proactive maintenance enabled.
5. **Observability**: Cycle summary events MUST appear in logs; per-step timing MUST be traceable.

## Dependencies Map

```
F01 (unit-transaction-framework) ──┐
F02 (unified-maintenance-strategy) ──┼──► F07 (proactive-maintenance-cycle)
F04 (memory-pressure-gauge) ─────────┘
```

F07 consumes:
- `MaintenanceStrategy` interface from F02
- `ContextUnitTransaction` rollback from F01
- `MemoryPressureGauge` trigger signal from F04

F07 integrates with (no dependency):
- `CanonizationGate` (existing, for consolidation routing)
- `CompactionValidator` (existing, for validation step)
- `InvariantEvaluator` (existing, for validation step)
- `LlmCallService` (existing, for audit LLM call)
- DICE `PrologEngine` (existing via DICE 0.1.0-SNAPSHOT, for Prolog pre-filter in audit and validate steps -- tuProlog/2p-kt already on classpath, zero new dependencies)
