# Implementation Tasks

## 1. BudgetStrategy Interface and CountBasedBudgetStrategy

- [x] 1.1 Create `BudgetStrategy` sealed interface in `anchor/` package
  - [x] 1.1.1 Define `computeEffectiveBudget(List<Anchor> activeAnchors, int rawBudget) -> int`
  - [x] 1.1.2 Define `selectForEviction(List<Anchor> activeAnchors, int excess) -> List<Anchor>`
  - [x] 1.1.3 Seal to permit `CountBasedBudgetStrategy` and `InterferenceDensityBudgetStrategy`
- [x] 1.2 Create `BudgetStrategyType` enum: `COUNT`, `INTERFERENCE_DENSITY`
- [x] 1.3 Create `CountBasedBudgetStrategy` implementing `BudgetStrategy`
  - [x] 1.3.1 `computeEffectiveBudget` returns `rawBudget` unchanged
  - [x] 1.3.2 `selectForEviction` filters out pinned and CANON anchors, sorts by rank ascending, limits to `excess`
- [x] 1.4 Create `CountBasedBudgetStrategyTest`
  - [x] 1.4.1 Verify effective budget equals raw budget
  - [x] 1.4.2 Verify eviction selects lowest-ranked non-pinned, non-CANON
  - [x] 1.4.3 Verify pinned anchors excluded from eviction
  - [x] 1.4.4 Verify CANON anchors excluded from eviction
  - [x] 1.4.5 Verify empty list returns empty eviction list

**Files** (3 source, 1 test):
- `src/main/java/dev/dunnam/diceanchors/anchor/BudgetStrategy.java` (new)
- `src/main/java/dev/dunnam/diceanchors/anchor/BudgetStrategyType.java` (new)
- `src/main/java/dev/dunnam/diceanchors/anchor/CountBasedBudgetStrategy.java` (new)
- `src/test/java/dev/dunnam/diceanchors/anchor/CountBasedBudgetStrategyTest.java` (new)

**Verification**: `./mvnw test`

**Gate 1**: `BudgetStrategy` interface exists. `CountBasedBudgetStrategy` compiles and passes tests. Eviction behavior matches current inline logic.

---

## 2. Wire BudgetStrategy into AnchorEngine

- [x] 2.1 Add `BudgetConfig` record to `DiceAnchorsProperties`
  - [x] 2.1.1 `strategy` field with default `COUNT`
  - [x] 2.1.2 `densityWarningThreshold` (default 0.6)
  - [x] 2.1.3 `densityReductionThreshold` (default 0.8)
  - [x] 2.1.4 `densityReductionFactor` (default 0.5)
- [x] 2.2 Add `dice-anchors.anchor.budget` section to `application.yml`
- [x] 2.3 Refactor `AnchorEngine` to accept `BudgetStrategy` via constructor injection
  - [x] 2.3.1 Replace inline budget logic in `promote()` with strategy delegation
  - [x] 2.3.2 Replace `repository.evictLowestRanked()` path with `selectForEviction()` + `archiveAnchor()` loop
  - [x] 2.3.3 Keep invariant-protection filtering in the engine (post-strategy validation)
- [x] 2.4 Create Spring `@Configuration` to wire `BudgetStrategy` bean from properties
- [x] 2.5 Verify all existing `AnchorEngineTest` tests pass without modification

**Files** (4 source):
- `src/main/java/dev/dunnam/diceanchors/DiceAnchorsProperties.java` (modify: add `BudgetConfig`)
- `src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java` (modify: delegate to `BudgetStrategy`)
- `src/main/resources/application.yml` (modify: add `budget` section)
- `src/main/java/dev/dunnam/diceanchors/anchor/BudgetStrategyConfiguration.java` (new)

**Verification**: `./mvnw test` -- all existing tests MUST pass without modification.

**Gate 2**: `AnchorEngine` delegates to `BudgetStrategy`. Default COUNT strategy produces identical behavior. Property-based selection works.

---

## 3. InterferenceDensityCalculator

- [x] 3.1 Create `InterferenceDensityCalculator` interface in `anchor/` package
  - [x] 3.1.1 Define `computeDensity(List<Anchor>, ConflictIndex) -> double`
  - [x] 3.1.2 Define `identifyClusters(List<Anchor>, ConflictIndex) -> List<AnchorCluster>`
- [x] 3.2 Create `AnchorCluster` record: `(Set<String> anchorIds, double internalDensity)`
- [x] 3.3 Create `ConnectedComponentsCalculator` implementing `InterferenceDensityCalculator`
  - [x] 3.3.1 BFS/DFS connected components from conflict index edges
  - [x] 3.3.2 Edge density: `|E| / (n * (n-1) / 2)`, return 0.0 for n <= 1
  - [x] 3.3.3 Internal density per cluster: edges within cluster / max possible within cluster
  - [x] 3.3.4 Isolated anchors (no conflict edges) excluded from clusters
- [x] 3.4 Create `ConnectedComponentsCalculatorTest`
  - [x] 3.4.1 Zero anchors returns density 0.0 and empty clusters
  - [x] 3.4.2 Single anchor returns density 0.0
  - [x] 3.4.3 Two anchors with conflict edge returns density 1.0 and one cluster
  - [x] 3.4.4 Two separate components form two clusters
  - [x] 3.4.5 Fully connected graph returns density 1.0
  - [x] 3.4.6 Isolated anchor excluded from all clusters

**Files** (3 source, 1 test):
- `src/main/java/dev/dunnam/diceanchors/anchor/InterferenceDensityCalculator.java` (new)
- `src/main/java/dev/dunnam/diceanchors/anchor/AnchorCluster.java` (new)
- `src/main/java/dev/dunnam/diceanchors/anchor/ConnectedComponentsCalculator.java` (new)
- `src/test/java/dev/dunnam/diceanchors/anchor/ConnectedComponentsCalculatorTest.java` (new)

**Note**: Tests use a mock/stub `ConflictIndex` with synthetic conflict data. If F05 is not yet implemented, define a minimal `ConflictIndex` interface locally or use the F05 interface if available.

**Verification**: `./mvnw test`

---

## 4. InterferenceDensityBudgetStrategy

- [x] 4.1 Create `InterferenceDensityBudgetStrategy` implementing `BudgetStrategy`
  - [x] 4.1.1 `computeEffectiveBudget`: compute density, apply linear reduction formula when above reduction threshold
  - [x] 4.1.2 `selectForEviction`: cluster-aware eviction from largest cluster, fallback to global lowest-rank
  - [x] 4.1.3 Phase transition detection: warn at warning threshold, log at reduction threshold
  - [x] 4.1.4 Respect pinned and CANON immunity in cluster eviction
  - [x] 4.1.5 Clamp effective budget to `max(1, computed)` and `min(rawBudget, computed)`
- [x] 4.2 Add observability logging
  - [x] 4.2.1 Log `budget.density.score`, `budget.effective`, `budget.raw` per evaluation
  - [x] 4.2.2 Log `budget.cluster.count`, `budget.cluster.maxSize` when density strategy active
  - [x] 4.2.3 Log `budget.eviction.reason` for each eviction decision
  - [x] 4.2.4 Log `budget.headroom` (effective - current count)
- [x] 4.3 Create `InterferenceDensityBudgetStrategyTest`
  - [x] 4.3.1 Low density (0.0) returns raw budget unchanged
  - [x] 4.3.2 High density (0.8+) reduces effective budget per formula
  - [x] 4.3.3 Density below reduction threshold returns raw budget (no reduction)
  - [x] 4.3.4 Cluster-aware eviction selects from largest cluster
  - [x] 4.3.5 Pinned anchors immune to cluster eviction
  - [x] 4.3.6 CANON anchors immune to cluster eviction
  - [x] 4.3.7 No clusters falls back to global lowest-rank
  - [x] 4.3.8 Effective budget never exceeds raw budget
  - [x] 4.3.9 Effective budget always at least 1

**Files** (2 source, 1 test):
- `src/main/java/dev/dunnam/diceanchors/anchor/InterferenceDensityBudgetStrategy.java` (new)
- `src/main/java/dev/dunnam/diceanchors/DiceAnchorsProperties.java` (modify: thresholds already added in Task 2)
- `src/test/java/dev/dunnam/diceanchors/anchor/InterferenceDensityBudgetStrategyTest.java` (new)

**Verification**: `./mvnw test`

**Gate 3**: Density strategy reduces budget under high overlap. Cluster-aware eviction targets largest cluster. Pinned and CANON immune. Falls back to count-based when density is low.

---

## 5. Scenario YAML Integration and Observability

- [x] 5.1 Add `budgetStrategy` field to `SimulationScenario` record
  - [x] 5.1.1 Optional `@Nullable String budgetStrategy` field
  - [x] 5.1.2 `effectiveBudgetStrategy()` method returning `BudgetStrategyType` with COUNT default
- [x] 5.2 Update `SimulationTurnExecutor` to apply per-scenario budget strategy
  - [x] 5.2.1 Read strategy from scenario, resolve to `BudgetStrategy` bean
  - [x] 5.2.2 Pass strategy to engine context for the simulation run
- [x] 5.3 Update one scenario YAML as example
  - [x] 5.3.1 Add `budgetStrategy: INTERFERENCE_DENSITY` to `budget-starvation-interference.yml`
- [x] 5.4 Add structured logging for phase transition warnings
  - [x] 5.4.1 WARN when density > warning threshold
  - [x] 5.4.2 WARN when density > reduction threshold (phase transition risk)
- [x] 5.5 Verify all existing scenarios pass with default COUNT strategy

**Files** (4 source):
- `src/main/java/dev/dunnam/diceanchors/sim/engine/SimulationScenario.java` (modify: add `budgetStrategy`)
- `src/main/java/dev/dunnam/diceanchors/sim/engine/SimulationTurnExecutor.java` (modify: apply strategy)
- `src/main/resources/simulations/budget-starvation-interference.yml` (modify: add `budgetStrategy`)
- `src/main/java/dev/dunnam/diceanchors/anchor/BudgetStrategyConfiguration.java` (modify: factory method for per-context strategy)

**Verification**: `./mvnw test`

**Gate 4**: Scenario YAML can specify budget strategy. All existing scenarios pass with COUNT. Observability logging emits density metrics.

## Definition of Done

- All tests pass (`./mvnw test`)
- `CountBasedBudgetStrategy` produces identical behavior to current inline logic
- `InterferenceDensityBudgetStrategy` reduces budget under high density
- Cluster-aware eviction targets densest cluster
- Pinned and CANON immunity preserved in all strategies
- No per-check LLM calls for density calculation
- Strategy selectable per-context via scenario YAML
- Structured observability logging for density metrics
- Default strategy is COUNT -- no behavior change without opt-in
