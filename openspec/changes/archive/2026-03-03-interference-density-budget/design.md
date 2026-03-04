## Context

`AnchorEngine` enforces a hard budget of 20 active anchors per context. When a promotion pushes the count over budget, the engine evicts the lowest-ranked non-pinned anchors. This count-based enforcement ignores content: 20 unrelated anchors cause no interference, but 20 closely-related anchors in the same domain degrade LLM recall before the count limit is reached.

The sleeping-llm research found a sharp phase transition at 13-14 facts for an 8B weight-edited model -- recall crashes from 0.92 to 0.57 when representational capacity is exceeded. While dice-anchors uses prompt injection (not weight editing), the interference principle applies: semantically overlapping anchors compete for the LLM's attention budget more aggressively than diverse anchors.

The current eviction policy (global lowest-rank) is also suboptimal under interference: removing a low-rank anchor from a sparse topic while leaving a dense cluster intact does not reduce the interference causing recall degradation.

## Goals / Non-Goals

**Goals:**
- Define a pluggable `BudgetStrategy` interface for budget enforcement
- Implement `CountBasedBudgetStrategy` that exactly reproduces current behavior
- Implement `InterferenceDensityBudgetStrategy` that reduces effective budget under high overlap
- Implement cluster-aware eviction that targets the densest cluster
- Compute density from F05 conflict index edges (no per-check LLM calls)
- Make strategy selectable per simulation scenario for A/B testing
- Detect phase transitions when density approaches critical thresholds

**Non-Goals:**
- Embedding infrastructure for clustering (uses F05 conflict index as proxy)
- Dynamic budget adjustment mid-turn (computed at eviction time only)
- UI for strategy configuration (YAML/properties only)
- Per-domain budget partitioning
- Changes to the conflict index itself (that is F05's scope)

## Decisions

### 1. BudgetStrategy Interface

Define a sealed interface with two implementations:

```java
public sealed interface BudgetStrategy
        permits CountBasedBudgetStrategy, InterferenceDensityBudgetStrategy {

    int computeEffectiveBudget(List<Anchor> activeAnchors, int rawBudget);

    List<Anchor> selectForEviction(List<Anchor> activeAnchors, int excess);
}
```

Sealed because the set of strategies is fixed for this change. Additional strategies can unseal the interface if needed later.

The `rawBudget` parameter is passed explicitly rather than read from config. This keeps the strategy stateless (I8) and testable without Spring context.

**Why**: The current budget logic is inlined in `AnchorEngine.promote()` across two code paths (with and without invariant-protected anchors). Extracting it into a strategy separates the budget policy from the engine coordination, enabling alternative policies without modifying the engine.

**Alternative considered**: Adding a `densityAware` boolean flag to `AnchorEngine`. Rejected because it couples all future budget variations to the engine class and makes A/B testing difficult.

### 2. CountBasedBudgetStrategy

Extracts the current inline logic from `AnchorEngine.promote()`:

```java
public final class CountBasedBudgetStrategy implements BudgetStrategy {

    @Override
    public int computeEffectiveBudget(List<Anchor> activeAnchors, int rawBudget) {
        return rawBudget;
    }

    @Override
    public List<Anchor> selectForEviction(List<Anchor> activeAnchors, int excess) {
        return activeAnchors.stream()
                .filter(a -> !a.pinned() && a.authority() != Authority.CANON)
                .sorted(Comparator.comparingInt(Anchor::rank))
                .limit(excess)
                .toList();
    }
}
```

This is a mechanical extraction. The eviction predicate (not pinned, not CANON) and sort order (ascending rank) match the existing `AnchorEngine` code exactly. No behavioral change.

**Why**: L2 requires the default strategy to reproduce current behavior exactly. By extracting rather than rewriting, we ensure behavioral equivalence.

### 3. InterferenceDensityCalculator Interface and ConnectedComponentsCalculator

The density calculator is a pluggable interface (L7) to support A/B testing of cluster algorithms:

```java
public interface InterferenceDensityCalculator {
    double computeDensity(List<Anchor> anchors, ConflictIndex conflictIndex);
    List<AnchorCluster> identifyClusters(List<Anchor> anchors, ConflictIndex conflictIndex);
}
```

The initial implementation uses connected components (O3 decision: simplest approach for a demo repo):

```java
public class ConnectedComponentsCalculator implements InterferenceDensityCalculator {
    // BFS/DFS from each unvisited anchor, following conflict index edges
    // Density = actual edges / max possible edges (n*(n-1)/2)
}
```

The calculator takes a `ConflictIndex` parameter rather than reading from a repository directly. This keeps it pure (input -> output) and testable with synthetic conflict data.

**Density formula**: Edge density = `|E| / (n * (n-1) / 2)` where `|E|` is the count of conflict index edges and `n` is the number of anchors. This gives a value in [0.0, 1.0]. Single anchors and empty lists return 0.0.

**Cluster detection**: Standard BFS connected components. Each connected component in the conflict graph becomes an `AnchorCluster`. Isolated anchors (no conflict edges) are not members of any cluster.

**Why connected components**: O3 recommends the simplest approach. Connected components are deterministic, require no parameters, and produce meaningful clusters from the conflict graph. More sophisticated algorithms (Louvain, DBSCAN, Prolog transitive closure) can be swapped in via the `InterferenceDensityCalculator` interface (L7).

**Why not Prolog**: The prep doc mentions tuProlog for transitive closure. Connected components via BFS is equivalent to transitive closure for cluster membership and avoids the Prolog integration overhead for this initial implementation. The pluggable interface preserves the option.

### 4. InterferenceDensityBudgetStrategy

Composes the calculator with the budget formula:

```java
public final class InterferenceDensityBudgetStrategy implements BudgetStrategy {

    private final InterferenceDensityCalculator calculator;
    private final ConflictIndex conflictIndex;
    private final double warningThreshold;     // default 0.6
    private final double reductionThreshold;   // default 0.8
    private final double reductionFactor;       // default 0.5

    @Override
    public int computeEffectiveBudget(List<Anchor> activeAnchors, int rawBudget) {
        double density = calculator.computeDensity(activeAnchors, conflictIndex);
        logDensityMetrics(density, rawBudget);
        if (density <= reductionThreshold) {
            return rawBudget;
        }
        return Math.max(1, (int) Math.floor(rawBudget * (1.0 - density * reductionFactor)));
    }

    @Override
    public List<Anchor> selectForEviction(List<Anchor> activeAnchors, int excess) {
        var clusters = calculator.identifyClusters(activeAnchors, conflictIndex);
        if (clusters.isEmpty()) {
            // Fallback: global lowest-rank (same as count-based)
            return countBasedFallback(activeAnchors, excess);
        }
        return clusterAwareEviction(activeAnchors, clusters, excess);
    }
}
```

**Budget reduction formula** (O4 decision: linear reduction):
```
effectiveBudget = max(1, floor(rawBudget * (1.0 - densityScore * reductionFactor)))
```

Budget reduction only activates when density exceeds the reduction threshold (default 0.8). Below the threshold, the raw budget is returned unchanged. This provides a "safe zone" where normal density levels have no effect, matching the sleeping-llm finding that phase transition is sharp, not gradual.

**Cluster-aware eviction algorithm**:
1. Sort clusters by size descending (largest first)
2. For the largest cluster, find the lowest-ranked non-pinned, non-CANON anchor
3. If found, add to eviction list; repeat with next largest cluster if more evictions needed
4. If a cluster has no evictable anchors, skip to the next cluster
5. If all clusters exhausted and more evictions needed, fall back to global lowest-rank

**Phase transition detection**: When density exceeds `warningThreshold` (0.6), emit a WARN log. When density exceeds `reductionThreshold` (0.8), emit a WARN log indicating phase transition risk. These thresholds reference the sleeping-llm finding that the transition is sharp, suggesting the system should alert operators before the cliff.

**Why linear reduction**: O4 recommends linear as the simplest starting point. Step functions are too coarse; sigmoid requires more parameter tuning. Linear provides smooth proportional reduction with a single `reductionFactor` parameter.

### 5. AnchorEngine Integration

Refactor `AnchorEngine.promote()` to delegate budget enforcement:

**Before** (current inline logic):
```java
if (protectedIds.isEmpty()) {
    var evicted = repository.evictLowestRanked(contextId, config.budget());
    // ...
} else {
    if (allAnchors.size() > config.budget()) {
        int toEvict = allAnchors.size() - config.budget();
        var evictable = allAnchors.stream()
                .filter(a -> !a.pinned() && !protectedIds.contains(a.id()))
                .sorted(Comparator.comparingInt(Anchor::rank))
                .limit(toEvict)
                .toList();
        // ...
    }
}
```

**After** (strategy delegation):
```java
int effectiveBudget = budgetStrategy.computeEffectiveBudget(allAnchors, config.budget());
if (allAnchors.size() > effectiveBudget) {
    int excess = allAnchors.size() - effectiveBudget;
    var evictCandidates = budgetStrategy.selectForEviction(allAnchors, excess);
    // Filter out invariant-protected, then archive and publish events
}
```

The invariant-protection logic (checking `InvariantEvaluator` before eviction) stays in `AnchorEngine` because it is engine-level coordination, not budget policy. The strategy proposes eviction candidates; the engine validates them against invariants before executing.

The `repository.evictLowestRanked()` call is replaced by the strategy's `selectForEviction()` followed by `repository.archiveAnchor()` for each candidate. This eliminates the split between repository-level and engine-level eviction paths.

**Why**: The engine's dual eviction path (one via repository, one via engine) is a source of complexity. Consolidating through the strategy simplifies the code and ensures both paths respect the same eviction policy.

### 6. Configuration

Add to `DiceAnchorsProperties`:

```java
public record BudgetConfig(
        @DefaultValue("COUNT") BudgetStrategyType strategy,
        @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.6") double densityWarningThreshold,
        @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.8") double densityReductionThreshold,
        @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.5") double densityReductionFactor
) {}
```

`BudgetStrategyType` is an enum: `COUNT`, `INTERFERENCE_DENSITY`.

Scenario YAML adds an optional `budgetStrategy` field:
```yaml
budgetStrategy: INTERFERENCE_DENSITY  # or COUNT (default)
```

**Why separate config record**: Groups density-related parameters under a single config record. The `strategy` field selects the implementation; threshold/factor fields only apply when `INTERFERENCE_DENSITY` is selected.

### 7. ConflictIndex Dependency (F05)

The `InterferenceDensityCalculator` takes a `ConflictIndex` parameter. Since F05 (precomputed conflict index) may not be implemented yet, the design uses interface-level decoupling:

- `ConflictIndex` is an interface with `getConflicts(String anchorId) -> Set<ConflictEntry>`
- If F05 is not yet available, a `NoOpConflictIndex` returns empty sets (density always 0.0, no clusters, density strategy behaves like count-based)
- When F05 is implemented, the real `Neo4jConflictIndex` is injected

This ensures the interference-density budget feature can be wired and tested independently of F05 completion.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **F05 not yet implemented** | NoOp conflict index falls back to count-based behavior. Feature is testable with mock conflict data. |
| **Phase transition thresholds miscalibrated** | Thresholds are configurable. Empirical calibration via simulation benchmarks. Default 0.6/0.8 are conservative starting points. |
| **Cluster-aware eviction removes wrong anchors** | Eviction still targets lowest-ranked within cluster. Pinned and CANON immune. Invariant evaluator provides additional safety net. |
| **Connected components too coarse** | Pluggable calculator interface (L7) allows swapping to Louvain, DBSCAN, or Prolog without changing the strategy. |
| **Performance of density calculation** | Bounded by anchor count (max ~20-50). Graph traversal is O(V+E). No LLM calls. |
| **Backward compatibility regression** | CountBasedBudgetStrategy is default. All existing tests must pass unchanged. Density is opt-in only. |

## Migration Plan

1. Create `BudgetStrategy` interface and `CountBasedBudgetStrategy` (Task 1)
2. Wire `BudgetStrategy` into `AnchorEngine`, replacing inline budget logic (Task 2)
3. Implement `InterferenceDensityCalculator` with connected components (Task 3)
4. Implement `InterferenceDensityBudgetStrategy` with density formula and cluster eviction (Task 4)
5. Add scenario YAML integration and observability logging (Task 5)

Each task leaves the codebase compilable and test-passing. Tasks 1-2 are pure refactoring (no behavior change). Tasks 3-5 add new capability.

Rollback: Set `budgetStrategy: COUNT` (or remove the property) to restore original behavior.

## Open Questions

None remaining. All prep doc open questions (O1-O4) are resolved:
- **O1**: Conflict index edge density from F05 (resolved in Decision 3)
- **O2**: Configurable thresholds, 0.6 warning / 0.8 reduction (resolved in Decision 4)
- **O3**: Connected components for initial cluster algorithm (resolved in Decision 3)
- **O4**: Linear reduction formula (resolved in Decision 4)
