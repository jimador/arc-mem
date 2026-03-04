# Proactive Maintenance Cycle Specification

## Research Attribution

This feature is adapted from the Sleeping LLM project (Guo et al., 2025), which demonstrated that periodic "sleep cycles" for memory consolidation recover degraded knowledge from 40% initial recall to 100% within 4 cycles. The 5-step sweep contract (audit, refresh, consolidate, prune, validate) maps to the paper's 8-step sleep architecture: audit = audit, refresh = maintain, consolidate = LoRA consolidation analog, prune = capacity management, validate = PPL validation analog.

## ADDED Requirements

### Requirement: 5-step sweep execution

The proactive maintenance cycle SHALL execute five steps in strict order: audit, refresh, consolidate, prune, validate. Each step MUST complete before the next begins. The cycle MUST NOT reorder or skip steps.

#### Scenario: Full sweep executes all steps in order
- **GIVEN** a context with 15 active anchors and memory pressure >= 0.8
- **WHEN** `executeSweep()` is called
- **THEN** all five steps execute in order: audit, refresh, consolidate, prune, validate
- **AND** `SweepResult` reflects counts from each step

#### Scenario: Light sweep executes all steps with reduced LLM usage
- **GIVEN** a context with 10 active anchors and memory pressure between 0.4 and 0.8
- **WHEN** `executeSweep()` is called
- **THEN** all five steps execute in order
- **AND** the audit step uses heuristic scoring only (no LLM call)

### Requirement: Pressure-triggered activation

The cycle MUST be triggered by memory pressure from `MemoryPressureGauge` (F04). `shouldRunSweep()` SHALL return `true` when the pressure score meets the configured threshold AND the minimum turn interval since the last sweep has elapsed.

#### Scenario: Full sweep triggered at high pressure
- **GIVEN** memory pressure total >= 0.8 (configurable via `fullSweepThreshold`)
- **AND** at least `minTurnsBetweenSweeps` turns have elapsed since the last sweep
- **WHEN** `shouldRunSweep()` is called
- **THEN** it returns `true`

#### Scenario: Sweep suppressed when below minimum turn interval
- **GIVEN** memory pressure total >= 0.8
- **AND** fewer than `minTurnsBetweenSweeps` turns have elapsed since the last sweep
- **WHEN** `shouldRunSweep()` is called
- **THEN** it returns `false`

#### Scenario: No sweep at low pressure
- **GIVEN** memory pressure total < 0.4
- **WHEN** `shouldRunSweep()` is called
- **THEN** it returns `false`

### Requirement: Audit step -- heuristic relevance scoring

The audit step SHALL compute a relevance score in [0.0, 1.0] for each active anchor using heuristic signals: entity overlap with recent conversation context, time since last reinforcement (recency), and current rank position relative to budget.

#### Scenario: Recently reinforced anchor scores high
- **GIVEN** an anchor reinforced within the last 2 turns
- **WHEN** the audit step computes its relevance score
- **THEN** the recency component contributes a high score (>= 0.7)

#### Scenario: Stale anchor with no recent reinforcement scores low
- **GIVEN** an anchor not reinforced in 10+ turns
- **AND** no entity overlap with recent conversation
- **WHEN** the audit step computes its relevance score
- **THEN** the combined score is low (<= 0.3)

### Requirement: Audit step -- optional batched LLM evaluation

For full sweeps (pressure >= 0.8), the audit step MAY invoke a single batched LLM call to refine relevance scores for anchors in the borderline range (heuristic score between soft prune threshold and 0.7). The LLM call SHALL include anchor text, authority, rank, and recent conversation turns.

#### Scenario: Full sweep invokes LLM for borderline anchors
- **GIVEN** a full sweep with 5 anchors scoring between 0.3 and 0.7 heuristically
- **WHEN** the audit step executes
- **THEN** a single batched LLM call evaluates those 5 anchors
- **AND** LLM scores replace heuristic scores for those anchors

#### Scenario: Light sweep skips LLM call
- **GIVEN** a light sweep (pressure between 0.4 and 0.8)
- **WHEN** the audit step executes
- **THEN** no LLM call is made
- **AND** heuristic scores are used for all anchors

#### Scenario: LLM call failure falls back to heuristic scores
- **GIVEN** a full sweep where the batched LLM call times out or fails
- **WHEN** the audit step handles the error
- **THEN** heuristic scores are retained for all anchors
- **AND** the failure is logged at WARN level

### Requirement: Refresh step -- re-rank based on audit scores

The refresh step SHALL adjust anchor ranks based on audit scores. Anchors with high audit scores (>= 0.7) SHALL receive a rank boost. Anchors with low audit scores (<= 0.3) SHALL receive a rank penalty. Anchors in the mid-range MUST NOT be modified.

#### Scenario: High-relevance anchor receives rank boost
- **GIVEN** an anchor with audit score 0.85 and current rank 400
- **WHEN** the refresh step executes
- **THEN** the anchor's rank increases (bounded by `clampRank()`)

#### Scenario: Low-relevance anchor receives rank penalty
- **GIVEN** an anchor with audit score 0.2 and current rank 500
- **WHEN** the refresh step executes
- **THEN** the anchor's rank decreases (bounded by `clampRank()`)

#### Scenario: Borderline anchor rank unchanged
- **GIVEN** an anchor with audit score 0.5
- **WHEN** the refresh step executes
- **THEN** the anchor's rank is not modified

### Requirement: Refresh step -- trust re-evaluation for borderline anchors

The refresh step SHOULD trigger trust re-evaluation via `TrustPipeline` for anchors whose audit score indicates potential degradation (score between 0.2 and 0.4). This MAY result in authority demotion through the existing trust re-evaluation path.

#### Scenario: Borderline anchor triggers trust re-evaluation
- **GIVEN** an anchor with audit score 0.3 and authority RELIABLE
- **WHEN** the refresh step executes
- **THEN** `TrustPipeline` re-evaluates the anchor's trust signals

### Requirement: Consolidate step -- CANON candidacy routing

The consolidate step SHALL identify RELIABLE anchors meeting all candidacy criteria and route them to `CanonizationGate.requestCanonization()`. Candidacy criteria MUST include: reinforcement count >= configured threshold, audit score >= configured threshold, AND turns since RELIABLE promotion >= configured minimum age.

#### Scenario: Anchor meeting all criteria is routed to CanonizationGate
- **GIVEN** a RELIABLE anchor with reinforcementCount >= 10, audit score >= 0.8, and age since RELIABLE >= 5 turns
- **WHEN** the consolidate step executes
- **THEN** `CanonizationGate.requestCanonization()` is called for that anchor

#### Scenario: Anchor missing one criterion is not consolidated
- **GIVEN** a RELIABLE anchor with reinforcementCount >= 10 and audit score >= 0.8 but age < 5 turns
- **WHEN** the consolidate step executes
- **THEN** no canonization request is created for that anchor

#### Scenario: Non-RELIABLE anchors are skipped
- **GIVEN** an anchor with authority PROVISIONAL or UNRELIABLE
- **WHEN** the consolidate step executes
- **THEN** no candidacy evaluation occurs for that anchor

### Requirement: Prune step -- two-tier threshold eviction

The prune step SHALL use a two-tier threshold model: a hard floor (default 0.1) below which anchors are always pruned, and a soft floor (default 0.3) below which anchors are pruned only when memory pressure exceeds the soft prune pressure threshold (default 0.6).

#### Scenario: Anchor below hard floor is always pruned
- **GIVEN** an anchor with audit score 0.05 (below hard floor of 0.1)
- **WHEN** the prune step executes
- **THEN** the anchor is archived via `AnchorEngine`

#### Scenario: Anchor below soft floor pruned under pressure
- **GIVEN** an anchor with audit score 0.2 (below soft floor of 0.3)
- **AND** current memory pressure >= 0.6
- **WHEN** the prune step executes
- **THEN** the anchor is archived

#### Scenario: Anchor below soft floor retained at low pressure
- **GIVEN** an anchor with audit score 0.2 (below soft floor of 0.3)
- **AND** current memory pressure < 0.6
- **WHEN** the prune step executes
- **THEN** the anchor is NOT archived

### Requirement: CANON and pinned immunity during pruning

CANON anchors MUST NOT be pruned during any cycle step (invariant A3b). Pinned anchors MUST NOT be pruned during any cycle step (invariant A3d). These anchors MUST be excluded from prune evaluation entirely.

#### Scenario: CANON anchor immune to pruning
- **GIVEN** a CANON anchor with audit score 0.0
- **WHEN** the prune step executes
- **THEN** the anchor is NOT archived

#### Scenario: Pinned anchor immune to pruning
- **GIVEN** a pinned anchor with audit score 0.0
- **WHEN** the prune step executes
- **THEN** the anchor is NOT archived

### Requirement: Validate step -- invariant and compaction checks

The validate step SHALL run `InvariantEvaluator.evaluate()` for each anchor modified during the cycle (refreshed, consolidated, or pruned). It SHALL also run `CompactionValidator.validate()` against the remaining anchor set to detect protected fact loss. Violations MUST be logged at WARN level.

#### Scenario: Invariant violation detected during validation
- **GIVEN** a prune action that would violate an invariant rule
- **WHEN** the validate step evaluates the action
- **THEN** the violation is logged at WARN level
- **AND** the violation count is included in `SweepResult`

#### Scenario: All validations pass
- **GIVEN** no invariant violations and no compaction losses
- **WHEN** the validate step completes
- **THEN** the summary indicates a clean validation pass

### Requirement: Cycle metrics tracking

The sweep MUST track and return metrics in `SweepResult`: anchors audited, anchors refreshed (rank changed), anchors consolidated (routed to CanonizationGate), anchors pruned, anchors validated, and total cycle duration. A cycle summary MUST be logged at INFO level.

#### Scenario: Metrics recorded for complete sweep
- **GIVEN** a sweep that audits 15 anchors, refreshes 4, consolidates 1, prunes 3, validates 12
- **WHEN** the sweep completes
- **THEN** `SweepResult` contains matching counts
- **AND** an INFO-level log message summarizes the cycle

### Requirement: Configurable sweep parameters

Sweep parameters MUST be configurable via `DiceAnchorsProperties`. The following properties SHALL be supported with their defaults:
- `minTurnsBetweenSweeps`: minimum turns between sweep executions (default: 10)
- `hardPruneThreshold`: audit score below which anchors are always pruned (default: 0.1)
- `softPruneThreshold`: audit score below which anchors are pruned under pressure (default: 0.3)
- `softPrunePressureThreshold`: pressure level required for soft-floor pruning (default: 0.6)
- `candidacyMinReinforcements`: minimum reinforcement count for CANON candidacy (default: 10)
- `candidacyMinAuditScore`: minimum audit score for CANON candidacy (default: 0.8)
- `candidacyMinAge`: minimum turns since RELIABLE promotion for CANON candidacy (default: 5)
- `rankBoostAmount`: rank increase for high-relevance anchors (default: 50)
- `rankPenaltyAmount`: rank decrease for low-relevance anchors (default: 50)

#### Scenario: Custom thresholds applied
- **GIVEN** `hardPruneThreshold` configured as 0.15
- **WHEN** an anchor has audit score 0.12
- **THEN** the anchor is pruned (below custom hard floor)

### Requirement: Error resilience

The entire sweep cycle MUST NOT throw exceptions. If any step fails, the strategy MUST log the error, skip the failed step, and continue with remaining steps. `executeSweep()` MUST return a valid `SweepResult` even on partial failure.

#### Scenario: Audit step failure does not prevent subsequent steps
- **GIVEN** the audit step throws an unexpected exception
- **WHEN** `executeSweep()` handles the error
- **THEN** refresh, consolidate, prune, and validate steps are skipped (no audit data)
- **AND** `SweepResult` is returned with zero counts and an error summary

#### Scenario: Prune step failure does not prevent validation
- **GIVEN** the prune step throws an unexpected exception
- **WHEN** `executeSweep()` handles the error
- **THEN** the validate step still executes
- **AND** the error is logged at ERROR level

## Invariants

- **I1**: The 5-step order (audit, refresh, consolidate, prune, validate) MUST NOT be changed
- **I2**: CANON anchors MUST NOT be pruned or have their rank penalized (invariant A3b)
- **I3**: Pinned anchors MUST NOT be pruned or have their rank penalized (invariant A3d)
- **I4**: All rank modifications MUST go through `Anchor.clampRank()` (invariant A2)
- **I5**: CANON candidacy MUST route through `CanonizationGate` -- no direct CANON assignment (invariant A4)
- **I6**: Sweep MUST NOT throw exceptions -- all errors caught, logged, and reported via `SweepResult`
- **I7**: Sweep operates within the existing contextId isolation (Article VI)
- **I8**: `SweepResult.anchorsAudited` >= all other counters (auditing is a prerequisite)
