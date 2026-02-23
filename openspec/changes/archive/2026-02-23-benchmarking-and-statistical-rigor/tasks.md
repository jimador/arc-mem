## 1. Data Model and Statistics Core

- [x] 1.1 Create `sim.benchmark` package and `BenchmarkStatistics` record (mean, stddev, min, max, median, p95, sampleCount) — ref: benchmark-statistics spec R1
- [x] 1.2 Create `BenchmarkReport` record (reportId, scenarioId, createdAt, runCount, totalDurationMs, metricStatistics, strategyStatistics, runIds, baselineReportId, baselineDeltas) — ref: benchmark-report spec R1-R3
- [x] 1.3 Create `BenchmarkProgress` record for per-run progress callbacks (completedRuns, totalRuns, latestScoringResult) — ref: benchmark-runner spec R3
- [x] 1.4 Verify: all three records compile, are JSON-serializable, and have Javadoc

## 2. BenchmarkAggregator Service

- [x] 2.1 Implement `BenchmarkAggregator.aggregate(List<ScoringResult>, String scenarioId, List<String> runIds, long durationMs)` returning `BenchmarkReport` — ref: benchmark-statistics spec R2
- [x] 2.2 Implement per-metric statistics extraction for all 7 ScoringResult fields (factSurvivalRate, contradictionCount, majorContradictionCount, driftAbsorptionRate, meanTurnsToFirstDrift, anchorAttributionCount) — ref: benchmark-statistics spec R3
- [x] 2.3 Implement median calculation (odd/even handling) and p95 with linear interpolation on sorted arrays — ref: benchmark-statistics spec R4-R5
- [x] 2.4 Implement per-strategy statistics aggregation from strategyEffectiveness maps — ref: benchmark-statistics spec R6
- [x] 2.5 Implement coefficient of variation calculation with CV > 0.5 flagging and zero-mean safety — ref: benchmark-statistics spec R8
- [x] 2.6 Implement `computeDeltas(BenchmarkReport current, BenchmarkReport baseline)` returning metric-name-to-delta map — ref: benchmark-statistics spec R11
- [x] 2.7 Handle edge cases: empty input (IllegalArgumentException), single run (stddev=0), NaN handling for meanTurnsToFirstDrift — ref: benchmark-statistics spec R9-R10
- [x] 2.8 Verify: unit tests for BenchmarkAggregator with canned ScoringResult sets covering all edge cases

## 3. BenchmarkRunner Service

- [x] 3.1 Implement `BenchmarkRunner` @Service with constructor injection of SimulationService, BenchmarkAggregator, RunHistoryStore — ref: benchmark-runner spec R1
- [x] 3.2 Implement `runBenchmark()` method: validate runCount >= 2, execute N sequential simulation runs, capture ScoringResult from each — ref: benchmark-runner spec R1-R2
- [x] 3.3 Implement per-run progress callback invocation with BenchmarkProgress — ref: benchmark-runner spec R3
- [x] 3.4 Implement cancellation support: check AtomicBoolean per-run, complete current run before stopping, return partial report — ref: benchmark-runner spec R4
- [x] 3.5 Add `@Observed(name = "benchmark.run")` with OTEL span attributes: benchmark.scenario_id, benchmark.run_count, benchmark.duration_ms, benchmark.mean_survival_rate — ref: benchmark-runner spec R6
- [x] 3.6 Assemble BenchmarkReport via BenchmarkAggregator and persist via RunHistoryStore — ref: benchmark-runner spec R5
- [x] 3.7 Verify: unit test with mocked SimulationService verifying sequential execution, progress callbacks, and report assembly

## 4. RunHistoryStore Extensions

- [x] 4.1 Add `saveBenchmarkReport(BenchmarkReport)`, `loadBenchmarkReport(String)`, `listBenchmarkReports()`, `listBenchmarkReportsByScenario(String)` to RunHistoryStore interface — ref: run-history delta spec R1-R4
- [x] 4.2 Add `saveAsBaseline(String reportId, String scenarioId)` and `loadBaseline(String scenarioId)` to RunHistoryStore — ref: run-history delta spec R5-R6
- [x] 4.3 Add `deleteBenchmarkReport(String reportId)` with baseline cascade to RunHistoryStore — ref: run-history delta spec R7
- [x] 4.4 Implement all new methods in Neo4jRunHistoryStore using JSON node pattern with `BenchmarkReport` label — ref: design D6
- [x] 4.5 Verify: unit tests for save/load/list/delete/baseline operations

## 5. BenchmarkPanel UI

- [x] 5.1 Create `BenchmarkPanel` Vaadin component with metric cards (mean ± stddev, median, p95, sample count) for each scoring metric — ref: benchmark-ui spec R1-R2
- [x] 5.2 Add per-strategy effectiveness bars with confidence bounds (mean ± 1 stddev) — ref: benchmark-ui spec R3
- [x] 5.3 Add baseline comparison badges (IMPROVED/REGRESSED/UNCHANGED) with delta values — ref: benchmark-ui spec R4
- [x] 5.4 Add high-variance warning display when CV > 0.5 — ref: benchmark-ui spec R5
- [x] 5.5 Add run benchmark controls: run count slider (default 5, range 2-20) and start button — ref: benchmark-ui spec R6
- [x] 5.6 Add progress indicator (Run X/N) and cancel button during benchmark execution — ref: benchmark-ui spec R7
- [x] 5.7 Wire BenchmarkPanel as a tab in SimulationView TabSheet — ref: benchmark-ui spec R1

## 6. Integration and Verification

- [x] 6.1 Add BenchmarkPanel tab to SimulationView alongside existing Conversation/Anchors/Drift/Context tabs
- [x] 6.2 Wire BenchmarkRunner to BenchmarkPanel start/cancel/progress flow with UI.access() thread safety
- [x] 6.3 Wire baseline save/load from BenchmarkPanel to RunHistoryStore
- [x] 6.4 Verify: full compile (`./mvnw.cmd clean compile -DskipTests`)
- [x] 6.5 Verify: all existing tests pass (`./mvnw.cmd test`) — no regressions
- [x] 6.6 Verify: new benchmark tests pass — BenchmarkAggregator, BenchmarkRunner, RunHistoryStore extensions
