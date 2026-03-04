# Feature: Interference-Density Budget

## Feature ID

`F10`

## Summary

Replace simple count-based anchor budget enforcement with interference-density-aware budgeting. The sleeping-llm research found a sharp phase transition at 13-14 facts (0.92 to 0.57 recall) -- not gradual decay. Budget SHOULD consider domain overlap, not just raw count. 20 unrelated facts may be fine, but 20 closely-related facts in the same domain cause prompt-level interference well before the count limit.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: Budget enforcement is purely count-based: 20 anchors max regardless of content overlap. Research (sleeping-llm Paper 3) found a sharp phase transition at 13-14 facts -- recall crashes from 0.92 to 0.57 when representational capacity is exceeded. For closely-related facts in the same domain, this threshold is even lower due to interference between semantically overlapping representations. The current system treats 20 diverse anchors across unrelated topics identically to 20 anchors clustered in one domain.
2. **Value delivered**: A pluggable budget strategy that accounts for semantic density, reducing effective budget when anchor overlap is high and selecting eviction targets to reduce the densest cluster rather than blindly removing the globally lowest-ranked anchor. This prevents the prompt-level interference that causes silent recall degradation before the count limit is reached.
3. **Why now**: Wave 4 feature. The `BudgetStrategy` interface SHOULD be defined when `AnchorEngine` is touched for F01/F02, but full implementation depends on F05 (conflict index) for density calculation. Defining the strategy now ensures the extension point exists.

## Scope

### In Scope

1. `BudgetStrategy` interface: `computeEffectiveBudget(List<Anchor> activeAnchors) -> int`, `selectForEviction(List<Anchor>, int excess) -> List<Anchor>`.
2. `CountBasedBudgetStrategy`: current behavior (backward compatible default).
3. `InterferenceDensityBudgetStrategy`: reduces effective budget when anchor overlap is high; cluster-aware eviction.
4. Interference density calculation using precomputed conflict index (F05) to approximate semantic overlap.
5. Cluster-aware eviction: in dense regions, prefer evicting the lowest-ranked member of the largest cluster.
6. Phase transition detection: warning signal when interference density approaches critical thresholds.
7. Capacity headroom signal: expose remaining effective capacity as a metric.
8. Configuration: strategy selection via `DiceAnchorsProperties` and per-scenario YAML override.
9. **A/B testability**: `InterferenceDensityCalculator` MUST be pluggable. The simulator MUST support comparing cluster algorithm implementations (Prolog transitive closure vs. connected components vs. DBSCAN) per-scenario for direct comparison of cluster quality and eviction decisions.

### Out of Scope

1. Embedding infrastructure for anchor clustering (MAY use F05 conflict index as a proxy instead).
2. Dynamic budget adjustment mid-turn (budget is computed at eviction time, not continuously).
3. UI for budget strategy configuration (configuration is via YAML/properties only).
4. Per-domain budget partitioning (all anchors share one budget pool; density adjusts the pool size).
5. Changes to the conflict index itself (that is F05's scope).

## Dependencies

1. Feature dependencies: F05 (precomputed conflict index for density calculation).
2. Priority: MAY.
3. Wave: 4.
4. OpenSpec change slug: `interference-density-budget`.
5. Research rec: C (interference-density-aware budget enforcement).

## Research Requirements

1. **Density calculation method**: The optimal approach for computing interference density is an open question. Candidates: conflict index edge density (F05), embedding-based cosine similarity clustering, or a hybrid. Initial implementation SHOULD use F05 conflict index edges as the density proxy.
2. **Phase transition thresholds**: The sleeping-llm finding of 13-14 facts as a phase transition was for a specific model (8B). The threshold for prompt-level interference in dice-anchors (which uses context injection, not weight editing) requires empirical calibration via simulation benchmarks.
3. **Cluster algorithm**: The method for grouping anchors into interference clusters (connected components in conflict graph, k-means on embeddings, density-based clustering, or Prolog transitive closure) is an open design decision. DICE Prolog projection (tuProlog/2p-kt, already on classpath via DICE 0.1.0-SNAPSHOT) MAY compute transitive conflict chains and cluster membership natively via rules like `conflictCluster(X, Y) :- conflictsWith(X, Y). conflictCluster(X, Z) :- conflictsWith(X, Y), conflictCluster(Y, Z).` This could eliminate the need for an external clustering library (e.g., Apache Commons Math DBSCAN).

## Impacted Areas

1. **`anchor/` package (primary)**: New types -- `BudgetStrategy` (interface), `CountBasedBudgetStrategy`, `InterferenceDensityBudgetStrategy`, `InterferenceDensityCalculator`, `AnchorCluster` (record).
2. **`anchor/` package (refactor)**: `AnchorEngine` delegates budget enforcement through `BudgetStrategy` instead of inline count-based logic. `CountBasedBudgetStrategy` reproduces current behavior exactly.
3. **`persistence/` package**: New repository queries for conflict index edge density (depends on F05 schema).
4. **`DiceAnchorsProperties`**: New `budget` config section with `strategy` (default: COUNT), density thresholds, and cluster parameters.
5. **`sim/engine/` package**: `ScenarioLoader` reads `budgetStrategy` from scenario YAML. Scenarios can specify budget strategy for A/B comparison.

## Visibility Requirements

### UI Visibility

1. SimulationView SHOULD display effective budget alongside raw budget for each run.
2. RunInspectorView SHOULD display cluster visualization showing anchor groupings and density scores.
3. Eviction decisions SHOULD display reasoning (cluster-aware vs. global lowest-rank) in turn trace.

### Observability Visibility

1. Interference density score MUST be logged per eviction evaluation: `budget.density.score`, `budget.effective`, `budget.raw`.
2. Cluster sizes MUST be logged when density strategy is active: `budget.cluster.count`, `budget.cluster.maxSize`.
3. Eviction decisions MUST include reasoning: `budget.eviction.reason` (lowest-rank-global vs. largest-cluster-lowest-rank).
4. Phase transition warnings MUST emit structured log events at WARN level when density approaches critical thresholds.
5. Capacity headroom SHOULD be exposed as a metric: `budget.headroom` (effective budget minus current count).

## Acceptance Criteria

1. Budget enforcement MUST support pluggable strategies via `BudgetStrategy` interface.
2. `CountBasedBudgetStrategy` MUST reproduce current behavior exactly (default strategy).
3. `InterferenceDensityBudgetStrategy` MUST reduce effective budget when anchor overlap is high.
4. Eviction in density mode SHOULD prefer reducing the largest cluster over global lowest-rank eviction.
5. Effective budget MUST be observable alongside raw budget in simulation traces.
6. Phase transition detection SHOULD trigger warnings when density approaches critical thresholds.
7. Strategy MUST be selectable per-context (simulation scenarios can specify budget strategy in YAML).
8. Default strategy (COUNT) MUST be the default -- no behavior change without explicit opt-in.
9. Density calculation MUST NOT require per-check LLM calls (MUST use precomputed data from F05).
10. Capacity headroom signal MUST be computable from effective budget and current anchor count.

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **F05 conflict index not rich enough for density calculation** | Medium | High | Design `InterferenceDensityCalculator` as a pluggable component. If conflict index edges are insufficient, swap in embedding-based density without changing the `BudgetStrategy` interface. |
| **Phase transition thresholds wrong for prompt injection** | High | Medium | The 13-14 fact threshold is from weight-editing research, not prompt injection. Run calibration benchmarks in simulation harness with varying anchor counts and density levels. Thresholds MUST be configurable. |
| **Cluster-aware eviction removes wrong anchors** | Medium | Medium | Cluster eviction SHOULD prefer lowest-ranked within the cluster. Pinned anchors remain immune. Authority-based floor (CANON/RELIABLE never evicted by density alone) provides safety net. |
| **Performance overhead of density calculation** | Low | Medium | Density is computed from precomputed F05 index, not per-turn LLM calls. Computation is graph traversal or Prolog query, bounded by anchor count (max ~20-50). |
| **Backward compatibility regression** | Low | High | `CountBasedBudgetStrategy` MUST be the default. All existing tests MUST pass without modification. Density strategy is opt-in only. |

## Proposal Seed

### Change Slug

`interference-density-budget`

### Proposal Starter Inputs

1. **Problem statement**: Budget enforcement is purely count-based: 20 anchors max regardless of content. Research (sleeping-llm Paper 3) found a sharp phase transition at 13-14 facts -- recall crashes from 0.92 to 0.57 when capacity is exceeded. For closely-related facts, this threshold is even lower due to representational interference. 20 diverse anchors across different topics may be fine; 20 anchors about the same domain can degrade quality well before the count limit.
2. **Why now**: Wave 4 feature -- the interface (`BudgetStrategy`) should be defined when AnchorEngine is touched for F01/F02, but full implementation depends on F05 (conflict index) for density calculation.
3. **Constraints/non-goals**: Default strategy MUST be count-based (no behavior change without opt-in). Density calculation MUST NOT require per-check LLM calls. SHOULD leverage F05 conflict index for overlap estimation. No per-domain budget partitioning.
4. **Visible outcomes**: Effective budget displayed alongside raw budget in simulation runs. Cluster-aware eviction reasoning in turn traces. Phase transition warnings when density is critical.

### Suggested Capability Areas

1. **Strategy interface**: `BudgetStrategy` with pluggable implementations for count-based and density-based enforcement.
2. **Density calculation**: `InterferenceDensityCalculator` using F05 conflict index edges to measure semantic overlap.
3. **Cluster-aware eviction**: Eviction logic that targets the densest cluster rather than global lowest-rank.
4. **Phase transition detection**: Warning system when interference density approaches empirically-calibrated thresholds.

### Candidate Requirement Blocks

1. **REQ-STRATEGY**: The system SHALL support pluggable budget strategies via the `BudgetStrategy` interface.
2. **REQ-COUNT-DEFAULT**: The `CountBasedBudgetStrategy` SHALL reproduce current budget enforcement behavior exactly.
3. **REQ-DENSITY**: The `InterferenceDensityBudgetStrategy` SHALL reduce effective budget when anchor semantic overlap is high.
4. **REQ-CLUSTER-EVICT**: Eviction under the density strategy SHOULD target the lowest-ranked anchor in the largest interference cluster.
5. **REQ-OBSERVABLE**: Effective budget, density score, and cluster metrics SHALL be observable via structured logging and simulation traces.

## Validation Plan

1. **Unit tests** MUST verify `CountBasedBudgetStrategy` produces identical eviction decisions to current `AnchorEngine` inline logic for the same inputs.
2. **Unit tests** MUST verify `InterferenceDensityBudgetStrategy` reduces effective budget when given a set of highly-overlapping anchors (simulated via mock conflict index).
3. **Unit tests** MUST verify cluster-aware eviction selects from the largest cluster, not globally lowest-rank.
4. **Unit tests** SHOULD verify phase transition detection triggers at configurable density thresholds.
5. **Unit tests** MUST verify pinned anchors and CANON anchors are immune to density-based eviction.
6. **Integration test** SHOULD verify a simulation scenario with `budgetStrategy: INTERFERENCE_DENSITY` produces different eviction behavior than the default count-based strategy.
7. **Regression**: All existing simulation scenarios MUST pass without modification when using default (COUNT) strategy.

## Known Limitations

1. **Density threshold calibration**: The sleeping-llm phase transition (13-14 facts) was measured for weight-editing, not prompt injection. Prompt-level interference thresholds require separate empirical calibration that has not yet been performed.
2. **Conflict index dependency**: Full density calculation requires F05 (precomputed conflict index). Without F05, the density strategy falls back to a simplified heuristic (e.g., text similarity scoring) that is less accurate.
3. **No per-domain partitioning**: All anchors share a single budget pool. Domains with naturally high overlap (e.g., geography, character relationships) may be penalized relative to diverse domains. Per-domain budgets are a candidate extension.
4. **Cluster algorithm not finalized**: The optimal clustering approach (connected components, k-means, DBSCAN, or Prolog transitive closure) is an open design decision that depends on F05's conflict index structure. DICE Prolog projection MAY replace Apache Commons Math for DBSCAN clustering — see Research Requirements for details.

## Suggested Command

```
/opsx:new interference-density-budget
```
