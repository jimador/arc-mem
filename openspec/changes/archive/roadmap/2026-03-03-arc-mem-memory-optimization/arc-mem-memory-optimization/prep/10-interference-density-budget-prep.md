# Prep: Interference-Density Budget

**Feature**: F10 (`interference-density-budget`)
**Wave**: 4
**Priority**: MAY
**Depends on**: F05 (precomputed conflict index)
**Research rec**: C

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

---

## Locked Decisions

These decisions are final and MUST NOT be revisited during implementation.

### L1: BudgetStrategy Interface

Budget enforcement MUST be pluggable via a `BudgetStrategy` interface with two methods:
- `computeEffectiveBudget(List<Context Unit> activeUnits) -> int`
- `selectForEviction(List<Context Unit>, int excess) -> List<Context Unit>`

All budget enforcement in `ArcMemEngine` MUST delegate to the active `BudgetStrategy`.

### L2: Count-Based as Default

`CountBasedBudgetStrategy` MUST be the default strategy. It MUST reproduce current `ArcMemEngine` budget enforcement behavior exactly. No behavioral change without explicit opt-in.

### L3: Density Reduces Effective Budget

`InterferenceDensityBudgetStrategy` MUST compute a reduced effective budget when context unit semantic overlap is high. The effective budget is always less than or equal to the raw budget (never increases it).

### L4: Cluster-Aware Eviction

When the density strategy triggers eviction, it SHOULD prefer evicting the lowest-ranked context unit in the largest interference cluster, rather than the globally lowest-ranked context unit. Pinned context units and CANON-authority context units remain immune to eviction regardless of strategy.

### L5: No Per-Check LLM Calls

Density calculation MUST NOT require per-check LLM calls. It MUST use precomputed data (F05 conflict index edges, pre-calculated embeddings, or similar). Density computation is a graph/math operation, not an inference operation.

### L6: Per-Context Strategy Selection

Budget strategy MUST be selectable per-context. Simulation scenarios MUST be able to specify budget strategy in scenario YAML. Global default is configured via `ArcMemProperties`.

### L7: A/B Testable Cluster Algorithms

`InterferenceDensityCalculator` MUST be pluggable. All clustering implementations (Prolog transitive closure, connected components, DBSCAN) MUST implement the same calculator interface. The simulator MUST support selecting the cluster algorithm per-scenario for direct comparison of cluster quality and eviction decisions.

---

## Open Questions

These decisions require further investigation or prototyping before implementation.

### O1: Density Calculation Method

**Question**: How is interference density computed?

**Candidates**:
- **Conflict index edge density (F05)**: Count edges in the precomputed conflict adjacency graph per context unit. Context Units with many conflict/near-conflict edges are in dense regions. Pros: leverages existing F05 infrastructure, no additional data. Cons: conflict edges may not capture all forms of semantic overlap (two context units can be related without conflicting).
- **Embedding-based clustering**: Compute cosine similarity between context unit text embeddings. Cluster context units above a similarity threshold. Pros: captures semantic overlap beyond conflict. Cons: requires embedding infrastructure (model calls or pre-computed embeddings).
- **Hybrid**: Use conflict index edges as primary signal, augmented by text similarity (Jaccard, TF-IDF) as a lightweight secondary signal. Pros: balanced accuracy/cost. Cons: more complex implementation.

**Decision criteria**: Whichever method is chosen MUST NOT require per-check LLM calls (L5). Conflict index is the RECOMMENDED starting point because it leverages F05 with no additional infrastructure.

### O2: Phase Transition Threshold Values

**Question**: At what interference density score should the system warn or reduce budget?

**Context**: The sleeping-llm research found a phase transition at 13-14 facts for an 8B weight-edited model. Prompt-level interference thresholds are likely different and model-dependent.

**Approach**: Thresholds MUST be configurable. Initial values SHOULD be calibrated empirically by running simulation benchmarks with varying context unit counts and density levels. Suggested starting points:
- Warning threshold: density score > 0.6 (60% of context unit pairs have conflict-index edges)
- Budget reduction threshold: density score > 0.8
- These are placeholders pending empirical calibration.

### O3: Cluster Algorithm Choice

**Question**: How are context units grouped into interference clusters?

**Candidates**:
- **Connected components** in the conflict index graph: simple, deterministic, but may produce overly-large clusters if the graph is well-connected.
- **Louvain community detection**: identifies densely-connected subgroups within the conflict graph. Better granularity but more complex.
- **K-means on embeddings**: requires embedding infrastructure. Groups by semantic similarity regardless of conflict edges.
- **Simple threshold grouping**: any pair with conflict-index edge weight above threshold forms a cluster. Transitive closure for multi-context unit clusters.
- **DBSCAN via Apache Commons Math 3.6.1**: density-based spatial clustering. Adds an external dependency but provides well-understood clustering behavior with noise tolerance.
- **Prolog transitive closure via DICE Prolog projection**: tuProlog (2p-kt) is already on the classpath via DICE 0.1.0-SNAPSHOT — zero new dependencies. Prolog can compute transitive conflict chains and cluster membership natively. Example rules: `conflictCluster(X, Y) :- conflictsWith(X, Y). conflictCluster(X, Z) :- conflictsWith(X, Y), conflictCluster(Y, Z).` Prolog's backward chaining computes connected components and transitive closure declaratively, with the conflict index facts projected from Neo4j. This MAY eliminate the need for Apache Commons Math entirely.

**Decision criteria**: Initial implementation SHOULD use the simplest approach that produces meaningful clusters. Connected components or threshold grouping are RECOMMENDED starting points. Prolog transitive closure is a strong candidate because it requires zero new dependencies (tuProlog is already on classpath) and natively handles the recursive cluster membership computation. Apache Commons Math DBSCAN remains an alternative if density-based clustering with noise tolerance is preferred over graph-based transitive closure. More sophisticated algorithms MAY be introduced if empirical results show simple approaches are insufficient.

### O4: Effective Budget Formula

**Question**: How does density score map to effective budget reduction?

**Candidates**:
- **Linear reduction**: `effectiveBudget = rawBudget * (1 - densityScore * reductionFactor)`
- **Step function**: `effectiveBudget = rawBudget` when density < threshold; `effectiveBudget = rawBudget - N` when density >= threshold
- **Sigmoid**: smooth transition centered on the phase transition threshold

**Decision criteria**: Formula MUST be configurable. Linear reduction is the simplest starting point.

---

## Small-Model Task Constraints

All implementation tasks MUST adhere to these constraints to remain small-model friendly.

- **Max 4 files per task**: Each implementation task MUST touch at most 4 source files (excluding test files).
- **Verification**: Every task MUST be verified with `./mvnw test` before completion.
- **Incremental delivery**: Each task MUST leave the codebase in a compilable, test-passing state.
- **No speculative implementation**: Tasks implement what is specified, nothing more.

---

## Implementation Tasks

### Task 1: BudgetStrategy Interface and CountBasedBudgetStrategy

**Files** (3):
1. `src/main/java/dev/dunnam/arcmem/context unit/BudgetStrategy.java` -- new interface
2. `src/main/java/dev/dunnam/arcmem/context unit/CountBasedBudgetStrategy.java` -- new class extracting current budget logic
3. `src/test/java/dev/dunnam/arcmem/context unit/CountBasedBudgetStrategyTest.java` -- new test

**Work**:
1. Define `BudgetStrategy` interface with `computeEffectiveBudget` and `selectForEviction` methods.
2. Extract current budget enforcement logic from `ArcMemEngine` into `CountBasedBudgetStrategy`.
3. Unit tests MUST verify `CountBasedBudgetStrategy` produces identical eviction decisions to current inline logic.

**Verification**: `./mvnw test`

### Task 2: Wire BudgetStrategy into ArcMemEngine

**Files** (4):
1. `src/main/java/dev/dunnam/arcmem/context unit/ArcMemEngine.java` -- refactor to delegate to `BudgetStrategy`
2. `src/main/java/dev/dunnam/arcmem/ArcMemProperties.java` -- add budget strategy config
3. `src/main/resources/application.yml` -- add default budget strategy property
4. `src/test/java/dev/dunnam/arcmem/context unit/ArcMemEngineTest.java` -- update tests for delegation

**Work**:
1. Replace inline budget logic in `ArcMemEngine` with delegation to injected `BudgetStrategy`.
2. Add `budget.strategy` property to `ArcMemProperties` (default: COUNT).
3. Spring configuration wires the appropriate strategy bean based on property.
4. All existing `ArcMemEngine` tests MUST pass without modification (behavioral equivalence).

**Verification**: `./mvnw test`

### Task 3: InterferenceDensityCalculator

**Files** (3):
1. `src/main/java/dev/dunnam/arcmem/context unit/InterferenceDensityCalculator.java` -- new class
2. `src/main/java/dev/dunnam/arcmem/context unit/UnitCluster.java` -- new record
3. `src/test/java/dev/dunnam/arcmem/context unit/InterferenceDensityCalculatorTest.java` -- new test

**Work**:
1. Implement density calculation from conflict index data (F05 dependency -- may require mock/stub if F05 is not yet implemented).
2. Define `UnitCluster` record: `(Set<String> unitIds, double internalDensity)`.
3. Implement cluster detection per O3 decision. If Prolog transitive closure is selected, project conflict index edges as Prolog facts via DICE's `PrologEngine` and compute cluster membership with recursive rules. If Apache Commons Math DBSCAN is selected, add the dependency and use `DBSCANClusterer`. Both approaches MUST satisfy L5 (no per-check LLM calls).
4. Expose `computeDensity(List<Context Unit>) -> double` and `identifyClusters(List<Context Unit>) -> List<UnitCluster>`.
5. Unit tests with synthetic conflict data verifying density scores and cluster identification.

**Verification**: `./mvnw test`

### Task 4: InterferenceDensityBudgetStrategy

**Files** (4):
1. `src/main/java/dev/dunnam/arcmem/context unit/InterferenceDensityBudgetStrategy.java` -- new class
2. `src/test/java/dev/dunnam/arcmem/context unit/InterferenceDensityBudgetStrategyTest.java` -- new test
3. `src/main/java/dev/dunnam/arcmem/ArcMemProperties.java` -- add density config parameters
4. `src/main/resources/application.yml` -- add density threshold defaults

**Work**:
1. Implement `BudgetStrategy` using `InterferenceDensityCalculator` for effective budget computation.
2. Implement cluster-aware eviction: select lowest-ranked context unit from largest cluster.
3. Respect pinned and CANON immunity.
4. Add configurable density thresholds and reduction formula parameters to properties.
5. Unit tests verifying: budget reduction under high density, cluster-aware eviction, immunity rules, fallback to count-based when density is low.

**Verification**: `./mvnw test`

### Task 5: Scenario YAML Integration and Observability

**Files** (4):
1. `src/main/java/dev/dunnam/arcmem/sim/engine/ScenarioLoader.java` -- read `budgetStrategy` from YAML
2. `src/main/java/dev/dunnam/arcmem/sim/engine/SimulationTurnExecutor.java` -- apply per-scenario strategy
3. `src/main/resources/simulations/` -- update one scenario as example
4. `src/test/java/dev/dunnam/arcmem/sim/engine/ScenarioLoaderTest.java` -- test YAML parsing

**Work**:
1. Add `budgetStrategy` field to scenario YAML schema (optional, default: COUNT).
2. `ScenarioLoader` parses the field and passes to simulation context.
3. Add structured logging for density score, effective budget, cluster sizes, eviction reasoning.
4. Unit test verifying YAML parsing with and without budget strategy field.

**Verification**: `./mvnw test`

---

## Implementation Gates

Each gate MUST be satisfied before proceeding to subsequent tasks. Gates are verified by running `./mvnw test` and inspecting test results.

### Gate 1: BudgetStrategy Interface Defined (after Task 1)

- `BudgetStrategy` interface exists with both method signatures.
- `CountBasedBudgetStrategy` compiles and passes unit tests.
- Tests verify identical eviction behavior to current inline logic.

### Gate 2: Count-Based Strategy Reproduces Current Behavior (after Task 2)

- `ArcMemEngine` delegates to `BudgetStrategy`.
- ALL existing `ArcMemEngine` tests pass without modification.
- Property-based strategy selection works (default: COUNT).

### Gate 3: Density Strategy Reduces Budget Under Overlap (after Task 4)

- `InterferenceDensityBudgetStrategy` computes reduced effective budget for high-overlap context unit sets.
- Cluster-aware eviction selects from largest cluster.
- Pinned and CANON context units are immune.
- Budget is NOT reduced when density is below threshold (falls back to count-based behavior).

### Gate 4: Full Integration (after Task 5)

- Scenario YAML can specify budget strategy.
- Observability logging emits density score, effective budget, and eviction reasoning.
- All existing scenarios pass with default (COUNT) strategy.
