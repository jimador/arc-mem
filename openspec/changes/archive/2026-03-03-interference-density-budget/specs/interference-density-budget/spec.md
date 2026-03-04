# Interference-Density Budget Specification

## ADDED Requirements

### Requirement: Pluggable budget strategy interface

The system SHALL support pluggable budget strategies via a `BudgetStrategy` interface. The interface MUST define two methods:
- `computeEffectiveBudget(List<Anchor> activeAnchors, int rawBudget) -> int`: Returns the effective anchor budget after strategy-specific adjustments
- `selectForEviction(List<Anchor> activeAnchors, int excess) -> List<Anchor>`: Returns anchors to evict when active count exceeds effective budget

All budget enforcement in `AnchorEngine` MUST delegate to the active `BudgetStrategy`. The engine MUST NOT contain inline budget logic after this change.

#### Scenario: Strategy interface defines contract
- **WHEN** a `BudgetStrategy` implementation is provided
- **THEN** `AnchorEngine` delegates `computeEffectiveBudget` and `selectForEviction` to it
- **AND** the engine does not perform budget calculations independently

#### Scenario: Strategy is injectable
- **WHEN** the application starts with a configured budget strategy
- **THEN** the appropriate `BudgetStrategy` bean is injected into `AnchorEngine` via constructor injection
- **AND** no `@Autowired` field injection is used

### Requirement: Count-based budget strategy as backward-compatible default

`CountBasedBudgetStrategy` SHALL be the default budget strategy. It MUST reproduce the current `AnchorEngine` budget enforcement behavior exactly:
- `computeEffectiveBudget` returns the raw budget unchanged
- `selectForEviction` returns the lowest-ranked non-pinned anchors up to the excess count
- CANON-authority anchors MUST NOT be selected for eviction
- Pinned anchors MUST NOT be selected for eviction

#### Scenario: Count-based reproduces current eviction behavior
- **WHEN** `CountBasedBudgetStrategy` is active with 22 anchors and budget 20
- **AND** 2 anchors are pinned with the lowest ranks
- **THEN** `selectForEviction` returns the 2 lowest-ranked non-pinned anchors
- **AND** pinned anchors are excluded from eviction candidates

#### Scenario: Count-based effective budget equals raw budget
- **WHEN** `CountBasedBudgetStrategy.computeEffectiveBudget` is called with any anchor list
- **THEN** the returned value equals the raw budget exactly

#### Scenario: CANON immunity in count-based strategy
- **WHEN** `CountBasedBudgetStrategy` selects eviction candidates
- **AND** the globally lowest-ranked anchor has CANON authority
- **THEN** that anchor is skipped
- **AND** the next lowest-ranked non-CANON, non-pinned anchor is selected

#### Scenario: Default strategy is COUNT
- **WHEN** no `budgetStrategy` is configured in properties or scenario YAML
- **THEN** `CountBasedBudgetStrategy` is used
- **AND** all existing behavior is preserved without modification

### Requirement: Interference-density budget strategy reduces budget under overlap

`InterferenceDensityBudgetStrategy` MUST compute a reduced effective budget when anchor semantic overlap is high. The effective budget MUST be less than or equal to the raw budget; it MUST NOT increase the raw budget (L3).

The effective budget formula SHALL use linear reduction:
```
effectiveBudget = max(1, floor(rawBudget * (1.0 - densityScore * reductionFactor)))
```

Where:
- `densityScore` is computed by `InterferenceDensityCalculator` from F05 conflict index data
- `reductionFactor` is configurable (default: 0.5)
- The effective budget MUST be at least 1 (never zero)

#### Scenario: Low density preserves raw budget
- **WHEN** interference density score is 0.0 (no conflict edges between anchors)
- **THEN** effective budget equals raw budget
- **AND** no budget reduction occurs

#### Scenario: High density reduces budget
- **WHEN** interference density score is 0.8 and raw budget is 20 and reduction factor is 0.5
- **THEN** effective budget is `floor(20 * (1.0 - 0.8 * 0.5))` = `floor(20 * 0.6)` = 12
- **AND** up to 8 anchors beyond the effective budget are evicted

#### Scenario: Maximum density with full reduction
- **WHEN** interference density score is 1.0 and reduction factor is 0.5
- **THEN** effective budget is `floor(rawBudget * 0.5)` = 10 for budget 20
- **AND** the budget is halved but never reduced below 1

#### Scenario: Effective budget never exceeds raw budget
- **WHEN** density score is negative (invalid)
- **THEN** density score is clamped to 0.0
- **AND** effective budget equals raw budget

### Requirement: Cluster-aware eviction

When the interference density strategy triggers eviction, it SHOULD prefer evicting the lowest-ranked anchor in the largest interference cluster rather than the globally lowest-ranked anchor (L4). This reduces the densest region of interference first.

Pinned anchors and CANON-authority anchors MUST remain immune to eviction regardless of strategy (L4).

#### Scenario: Largest cluster targeted first
- **WHEN** cluster A has 5 anchors and cluster B has 3 anchors
- **AND** eviction of 1 anchor is required
- **THEN** the lowest-ranked non-pinned, non-CANON anchor from cluster A is evicted
- **AND** cluster B is not affected

#### Scenario: Pinned anchor in largest cluster is skipped
- **WHEN** the largest cluster contains 4 anchors, all pinned
- **THEN** the strategy falls back to the next largest cluster
- **AND** pinned anchors are never evicted

#### Scenario: CANON anchor in largest cluster is skipped
- **WHEN** the lowest-ranked anchor in the largest cluster has CANON authority
- **THEN** that anchor is skipped
- **AND** the next lowest-ranked non-CANON anchor in the cluster is selected

#### Scenario: No evictable anchors in any cluster
- **WHEN** all anchors in all clusters are pinned or CANON
- **THEN** no eviction occurs
- **AND** a warning is logged

#### Scenario: Fallback to global lowest-rank when no clusters exist
- **WHEN** density calculator finds no clusters (all anchors are isolated)
- **THEN** eviction falls back to global lowest-ranked selection (equivalent to count-based)

### Requirement: Interference density calculation from conflict index

`InterferenceDensityCalculator` SHALL compute density from F05 conflict index data. Density calculation MUST NOT require per-check LLM calls (L5). The calculator MUST be pluggable to support A/B testing of cluster algorithms (L7).

The calculator interface SHALL define:
- `computeDensity(List<Anchor> anchors) -> double`: Returns density score in [0.0, 1.0]
- `identifyClusters(List<Anchor> anchors) -> List<AnchorCluster>`: Returns interference clusters

Density score is computed as edge density in the conflict index graph:
```
densityScore = actualConflictEdges / maxPossibleEdges
maxPossibleEdges = n * (n - 1) / 2  (where n = anchor count)
```

#### Scenario: Zero-conflict graph has zero density
- **WHEN** no conflict index edges exist between any anchor pair
- **THEN** density score is 0.0
- **AND** no clusters are identified

#### Scenario: Fully-connected conflict graph has density 1.0
- **WHEN** every anchor pair has a conflict index edge
- **THEN** density score is 1.0

#### Scenario: Single anchor has zero density
- **WHEN** only one anchor exists
- **THEN** density score is 0.0 (no pairs possible)

#### Scenario: Empty anchor list has zero density
- **WHEN** no anchors are provided
- **THEN** density score is 0.0

### Requirement: Cluster detection via connected components

The initial cluster algorithm SHALL use connected components in the conflict index graph (O3). Anchors connected by conflict index edges (directly or transitively) form a cluster.

`AnchorCluster` SHALL be a record: `(Set<String> anchorIds, double internalDensity)`.

#### Scenario: Two separate components form two clusters
- **WHEN** anchors A-B-C are connected and anchors D-E are connected but no edges between the groups
- **THEN** two clusters are returned: {A, B, C} and {D, E}

#### Scenario: Isolated anchor is not in any cluster
- **WHEN** anchor F has no conflict edges to any other anchor
- **THEN** anchor F does not appear in any cluster

#### Scenario: Internal density measures within-cluster connectivity
- **WHEN** cluster {A, B, C} has edges A-B and B-C but not A-C
- **THEN** internal density is 2/3 (2 edges out of 3 possible)

### Requirement: Phase transition detection

The system SHOULD detect when interference density approaches critical thresholds and emit warnings. Thresholds MUST be configurable (O2).

Default thresholds:
- Warning threshold: density score > 0.6
- Reduction threshold: density score > 0.8

#### Scenario: Density exceeds warning threshold
- **WHEN** density score is 0.65 and warning threshold is 0.6
- **THEN** a structured WARN log event is emitted with density score and anchor count
- **AND** `budget.density.warning` is logged

#### Scenario: Density exceeds reduction threshold
- **WHEN** density score is 0.85 and reduction threshold is 0.8
- **THEN** budget reduction is applied
- **AND** a structured WARN log event is emitted indicating phase transition risk

#### Scenario: Density below warning threshold
- **WHEN** density score is 0.4
- **THEN** no warning is emitted
- **AND** budget reduction is zero

### Requirement: Per-context strategy selection

Budget strategy MUST be selectable per-context (L6). Simulation scenarios MUST support specifying `budgetStrategy` in scenario YAML. The global default is configured via `DiceAnchorsProperties`.

#### Scenario: Scenario YAML specifies density strategy
- **WHEN** scenario YAML contains `budgetStrategy: INTERFERENCE_DENSITY`
- **THEN** the simulation run uses `InterferenceDensityBudgetStrategy`
- **AND** the global default is not used for that run

#### Scenario: Scenario YAML omits budget strategy
- **WHEN** scenario YAML does not contain `budgetStrategy`
- **THEN** the global default (COUNT) is used

#### Scenario: Invalid strategy in YAML
- **WHEN** scenario YAML contains `budgetStrategy: INVALID_VALUE`
- **THEN** scenario loading logs a warning
- **AND** falls back to the global default strategy

### Requirement: Observability

Interference density metrics MUST be observable via structured logging.

Required log fields:
- `budget.density.score`: Density score per eviction evaluation
- `budget.effective`: Effective budget after density adjustment
- `budget.raw`: Raw budget before adjustment
- `budget.cluster.count`: Number of clusters when density strategy is active
- `budget.cluster.maxSize`: Size of the largest cluster
- `budget.eviction.reason`: `largest-cluster-lowest-rank` or `global-lowest-rank`
- `budget.headroom`: Effective budget minus current active anchor count

#### Scenario: Density strategy logs all metrics
- **WHEN** `InterferenceDensityBudgetStrategy` evaluates budget
- **THEN** density score, effective budget, raw budget, cluster count, max cluster size, and headroom are logged

#### Scenario: Count-based strategy logs basic metrics
- **WHEN** `CountBasedBudgetStrategy` evaluates budget
- **THEN** raw budget and headroom are logged
- **AND** density-specific fields are not logged

## Invariants

- **I1**: Effective budget MUST be less than or equal to raw budget (density never increases budget)
- **I2**: Effective budget MUST be at least 1 (never zero)
- **I3**: Pinned anchors MUST NOT be selected for eviction by any strategy
- **I4**: CANON-authority anchors MUST NOT be selected for eviction by any strategy
- **I5**: Density calculation MUST NOT invoke LLM calls (uses precomputed F05 conflict index)
- **I6**: `CountBasedBudgetStrategy` MUST produce identical eviction decisions to the current inline logic in `AnchorEngine`
- **I7**: All existing tests MUST pass without modification when using the default COUNT strategy
- **I8**: `BudgetStrategy` implementations MUST be stateless (no per-context state held in the strategy)
