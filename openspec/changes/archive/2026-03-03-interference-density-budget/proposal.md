## Why

Budget enforcement is purely count-based: 20 anchors max regardless of content overlap. The sleeping-llm research (Paper 3) found a sharp phase transition at 13-14 facts where recall crashes from 0.92 to 0.57 when representational capacity is exceeded. For closely-related facts in the same domain, this threshold is lower due to interference between semantically overlapping representations. The current system treats 20 diverse anchors across unrelated topics identically to 20 anchors clustered in one domain.

A pluggable budget strategy that accounts for semantic density reduces effective budget when anchor overlap is high and selects eviction targets to reduce the densest cluster rather than blindly removing the globally lowest-ranked anchor. This prevents prompt-level interference that causes silent recall degradation before the count limit is reached.

## What Changes

- Define `BudgetStrategy` interface with `computeEffectiveBudget` and `selectForEviction` methods (L1)
- Extract current budget logic into `CountBasedBudgetStrategy` as the backward-compatible default (L2)
- Implement `InterferenceDensityBudgetStrategy` that reduces effective budget under high semantic overlap (L3)
- Implement `InterferenceDensityCalculator` using F05 conflict index edge density, with pluggable cluster algorithms (L5, L7)
- Add `AnchorCluster` record for cluster-aware eviction targeting largest interference clusters (L4)
- Add per-context strategy selection via scenario YAML and `DiceAnchorsProperties` (L6)
- Add phase transition detection with configurable thresholds (O2: 0.6 warning / 0.8 reduction)

## Capabilities

### New Capabilities
- `budget-strategy`: Pluggable budget enforcement via `BudgetStrategy` interface with count-based and density-based implementations
- `interference-density-calculation`: Density scoring from precomputed F05 conflict index edges with pluggable cluster algorithms
- `cluster-aware-eviction`: Eviction logic that targets the densest cluster rather than global lowest-rank
- `phase-transition-detection`: Warning system when interference density approaches configurable thresholds

### Modified Capabilities
- `anchor-budget-enforcement`: `AnchorEngine` delegates budget enforcement to injected `BudgetStrategy` instead of inline count logic

## Impact

- **Files (new)**: `anchor/BudgetStrategy.java`, `anchor/CountBasedBudgetStrategy.java`, `anchor/InterferenceDensityBudgetStrategy.java`, `anchor/InterferenceDensityCalculator.java`, `anchor/AnchorCluster.java`
- **Files (modified)**: `anchor/AnchorEngine.java`, `DiceAnchorsProperties.java`, `application.yml`, `sim/engine/SimulationScenario.java`, `sim/engine/ScenarioLoader.java`
- **APIs**: `BudgetStrategy` interface replaces inline budget logic in `AnchorEngine`; existing public API unchanged
- **Config**: New `dice-anchors.anchor.budget-strategy` property (default: COUNT). Density thresholds under `dice-anchors.anchor.density.*`
- **Affected**: Anchor promotion flow (eviction path), simulation scenario configuration
- **Performance**: No per-check LLM calls (L5). Density computation uses precomputed F05 conflict index data

## Constitutional Alignment

- **Article I** (RFC 2119): All requirements use RFC 2119 keywords
- **Article V** (Anchor Invariants): A1 budget enforcement preserved via `CountBasedBudgetStrategy` default. Rank clamping (A2) unaffected. Pinned and CANON immunity maintained (A3, A4)
- **Article III** (Constructor Injection): All new classes use constructor injection
- **Article IV** (Records): `AnchorCluster` is a record. `BudgetStrategy` is an interface (not a data carrier)
- **Article VII** (Test-First): Unit tests for critical business logic (eviction decisions, density calculation, cluster identification)

## Specification Overrides

### Article V, Clause A1 (Budget Enforcement)

- **Clause being relaxed**: A1 states "lowest-ranked non-pinned anchor MUST be evicted" when budget is exceeded
- **Justification**: `InterferenceDensityBudgetStrategy` evicts the lowest-ranked anchor in the largest cluster instead of the globally lowest-ranked anchor. This is a refinement of eviction targeting, not a relaxation of budget enforcement. The budget invariant (active count never exceeds budget) is preserved; only eviction selection changes.
- **Scope**: Only active when `budgetStrategy` is `INTERFERENCE_DENSITY`. `CountBasedBudgetStrategy` (default) preserves exact current behavior.
- **Expiration**: Permanent. Cluster-aware eviction is a strict improvement over global lowest-rank eviction for interference reduction.
