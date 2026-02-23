## 1. Ablation Condition Model

- [x] 1.1 Create `AblationCondition` record in `sim.benchmark` with fields: `name`, `injectionEnabled`, `authorityOverride` (@Nullable Authority), `rankOverride` (@Nullable Integer), `rankMutationEnabled`, `authorityPromotionEnabled`. Add static final constants for FULL_ANCHORS, NO_ANCHORS, FLAT_AUTHORITY, NO_RANK_DIFFERENTIATION per spec values. (ablation-conditions spec: declarative model, condition configuration fields, built-in conditions)
- [x] 1.2 Add `applySeedAnchors(List<SeedAnchor>)` method to `AblationCondition` that returns a new list with authority/rank overrides applied. Clamp rank overrides via `Anchor.clampRank()`. (ablation-conditions spec: condition application timing, respects anchor invariants)
- [x] 1.3 Add compact record constructor validation: `rankOverride` is clamped if present; `name` must be non-null/non-blank. (ablation-conditions spec: AC1 immutability, AC3 invariant compliance)
- [x] 1.4 Write `AblationConditionTest` — verify all 4 built-in conditions have correct field values, verify `applySeedAnchors` transforms correctly, verify rank clamping for out-of-range overrides, verify authority override applies regardless of direction, verify idempotency (AC2). (ablation-conditions spec: all scenarios)

## 2. Experiment Definition

- [x] 2.1 Create `ExperimentDefinition` record in `sim.benchmark` with fields: `name` (String), `conditions` (List<AblationCondition>), `scenarioIds` (List<String>), `repetitionsPerCell` (int), `evaluatorModel` (Optional<String>). Add compact constructor validation: conditions non-empty, scenarioIds non-empty, repetitionsPerCell >= 2. (experiment-execution spec: ExperimentDefinition record)
- [x] 2.2 Write `ExperimentDefinitionTest` — verify valid construction, verify IllegalArgumentException for empty conditions, empty scenarios, and repetitionsPerCell < 2. (experiment-execution spec: ExperimentDefinition scenarios)

## 3. Effect Size Calculator

- [x] 3.1 Create `EffectSizeEntry` record in `sim.benchmark` with fields: `cohensD` (double), `interpretation` (String: "negligible"/"small"/"medium"/"large"), `lowConfidence` (boolean). (cross-condition-statistics spec: effect size interpretation labels, high-variance warning)
- [x] 3.2 Create `ConfidenceInterval` record in `sim.benchmark` with fields: `lower` (double), `upper` (double). (cross-condition-statistics spec: 95% confidence intervals)
- [x] 3.3 Create `EffectSizeCalculator` @Service in `sim.benchmark`. Implement `computeEffectSizes(Map<String, BenchmarkReport> cellReports, List<AblationCondition> conditions)` returning the effect size matrix (Map<String, Map<String, EffectSizeEntry>>) keyed by alphabetically-ordered condition pairs. Derive sample stddev from population stddev: `sampleStddev = popStddev * sqrt(n / (n-1))`. Handle zero-variance (return 0.0) and NaN metrics (exclude). (cross-condition-statistics spec: Cohen's d, edge cases)
- [x] 3.4 Add `computeConfidenceIntervals(Map<String, BenchmarkReport> cellReports)` to `EffectSizeCalculator` returning Map<String, Map<String, ConfidenceInterval>> keyed by cell key and metric name. CI = mean ± 1.96 × sampleStddev / sqrt(n). (cross-condition-statistics spec: 95% confidence intervals)
- [x] 3.5 Add `computeStrategyDeltas(Map<String, BenchmarkReport> cellReports)` to `EffectSizeCalculator` returning Map<String, Map<String, Double>> keyed by strategy name and condition name. Extract strategy effectiveness means from each cell's `strategyStatistics`. (cross-condition-statistics spec: per-strategy effectiveness comparison)
- [x] 3.6 Write `EffectSizeCalculatorTest` — verify Cohen's d computation with known values, verify interpretation labels at thresholds (0.15→negligible, 0.35→small, 0.65→medium, 1.2→large, -0.75→medium), verify symmetry (|d(A,B)| == |d(B,A)|), verify zero-variance returns 0.0, verify NaN exclusion, verify CI computation, verify strategy deltas, verify matrix size = N*(N-1)/2, verify low-confidence flag when CV > 0.5. (cross-condition-statistics spec: all scenarios, CS1, CS2, CS3)

## 4. BenchmarkRunner Condition Support

- [x] 4.1 Add overloaded `runBenchmark(...)` method to `BenchmarkRunner` accepting an `AblationCondition` parameter. The original method delegates to the overloaded version with `AblationCondition.FULL_ANCHORS`. Apply condition via `applySeedAnchors()` on the scenario's seed anchors and override `injectionStateSupplier` when condition.injectionEnabled() is false. (benchmark-runner spec: method signature, condition application, condition interaction with injectionEnabled)
- [x] 4.2 Update existing `BenchmarkRunner` tests to verify backward compatibility — existing calls without condition parameter still work identically. Add tests for condition application: NO_ANCHORS disables injection, FLAT_AUTHORITY overrides authority, NO_RANK_DIFFERENTIATION flattens rank. Verify condition does not leak between runs (BR4). (benchmark-runner spec: all scenarios, BR1-BR4)

## 5. Experiment Progress

- [x] 5.1 Create `ExperimentProgress` record in `sim.benchmark` with fields: `currentCell` (int), `totalCells` (int), `conditionName` (String), `scenarioId` (String), `currentRun` (int), `totalRuns` (int). Add `message()` method returning formatted string per spec. (experiment-execution spec: progress reporting)

## 6. Experiment Runner

- [x] 6.1 Create `ExperimentRunner` @Service in `sim.benchmark`. Constructor-inject `BenchmarkRunner`, `EffectSizeCalculator`, `ScenarioLoader`, `RunHistoryStore`. Add `AtomicBoolean cancelRequested`, `cancel()`, and `isCancelRequested()` methods. (experiment-execution spec: matrix execution, experiment-level cancellation)
- [x] 6.2 Implement `runExperiment(ExperimentDefinition definition, Supplier<Boolean> injectionStateSupplier, Supplier<Integer> tokenBudgetSupplier, Consumer<ExperimentProgress> onProgress)` method. Iterate conditions × scenarios sequentially. For each cell, load scenario via ScenarioLoader, apply condition, call BenchmarkRunner.runBenchmark() with condition, collect BenchmarkReport. Check cancelRequested between cells. (experiment-execution spec: matrix execution, cell execution order, per-cell aggregation, condition application per cell)
- [x] 6.3 Add OTEL span `experiment.run` wrapping the full experiment with attributes: experiment.name, experiment.condition_count, experiment.scenario_count, experiment.total_cells, experiment.repetitions. Add child span `experiment.cell` for each cell with attributes: cell.condition, cell.scenario_id, cell.run_count, cell.index. (experiment-execution spec: OTEL experiment span, OTEL cell span)
- [x] 6.4 After all cells complete (or cancellation), call EffectSizeCalculator to compute effect sizes, CIs, and strategy deltas. Assemble ExperimentReport with all cell reports, effect size matrix, strategy deltas, total duration, and cancelled flag. Save via RunHistoryStore. Return to caller. (experiment-execution spec: ExperimentReport assembly)
- [x] 6.5 Write `ExperimentRunnerTest` — mock BenchmarkRunner and EffectSizeCalculator. Verify matrix execution count (2 conditions × 3 scenarios × 5 reps = 30 runs across 6 cells), verify sequential cell execution, verify cancellation produces partial report with cancelled=true, verify progress callback invoked with correct cell/run indices, verify each cell uses correct condition. (experiment-execution spec: all scenarios, EX1-EX4)

## 7. Experiment Report and Persistence

- [x] 7.1 Create `ExperimentReport` record in `sim.benchmark` with fields per spec: reportId, experimentName, createdAt, conditions, scenarioIds, repetitionsPerCell, totalDurationMs, cellReports, effectSizeMatrix, strategyDeltas, cancelled. Ensure all collection fields are immutable (List.of/Map.copyOf in compact constructor). (experiment-persistence spec: ExperimentReport record)
- [x] 7.2 Add `saveExperimentReport`, `loadExperimentReport`, `listExperimentReports`, `deleteExperimentReport` methods to `RunHistoryStore` interface. (run-history spec: all 5 requirements)
- [x] 7.3 Implement the 4 new methods in `Neo4jRunHistoryStore` using ConcurrentHashMap (matching the existing BenchmarkReport in-memory pattern). `listExperimentReports()` returns sorted by createdAt descending. (experiment-persistence spec: Neo4j storage pattern, JSON serialization)
- [x] 7.4 Write `ExperimentReportTest` — verify record construction, verify immutable collections, verify JSON round-trip serialization (Jackson ObjectMapper serialize/deserialize preserves all fields including nested BenchmarkReport/BenchmarkStatistics). (experiment-persistence spec: ExperimentReport record, JSON serialization, round-trip scenarios)
- [x] 7.5 Write persistence tests — verify save/load round-trip, verify load nonexistent returns Optional.empty(), verify list ordering, verify delete removes report, verify delete nonexistent is no-op, verify experiment reports don't appear in listBenchmarkReports and vice versa. (run-history spec: all scenarios)

## 8. Verification

- [x] 8.1 Run `./mvnw.cmd clean compile -DskipTests` — verify clean compilation with all new types
- [x] 8.2 Run `./mvnw.cmd test` — verify all existing tests still pass alongside new tests
