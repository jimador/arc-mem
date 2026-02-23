## Why

Simulation scoring today produces point estimates — a single `factSurvivalRate`, `driftAbsorptionRate`, or `strategyEffectiveness` value per run. With no multi-run aggregation, confidence intervals, or variance analysis, operators cannot distinguish genuine resilience improvements from LLM non-determinism. A single run showing 100% survival could regress to 60% on the next identical run, and there is no way to detect this without manual re-runs and spreadsheet comparison. F01-F05 have built the anchor lifecycle, conflict detection, retrieval quality, temporal validity, and compaction recovery — F06 adds the statistical lens to evaluate whether those features actually work reliably.

## What Changes

- Add a **benchmark runner** that executes a scenario N times and collects per-run `ScoringResult` records.
- Add a **benchmark aggregator** that computes descriptive statistics (mean, stddev, min, max, median, p95) across collected runs for each scoring metric.
- Add a **per-strategy statistical breakdown** so strategy effectiveness is reported with variance, not just a single ratio.
- Add a **benchmark report** record that captures aggregated results, run metadata, and scenario configuration for reproducibility.
- Extend the **run history store** with queries to retrieve all runs for a given scenario (already partially supported via `listByScenario`).
- Add a **benchmark results UI panel** that displays aggregated metrics with confidence intervals and distribution summaries alongside single-run results.
- Add **baseline persistence** so operators can save a benchmark report as a golden baseline and compare future runs against it.
- Add **OTEL metrics** for benchmark execution (run count, aggregate duration, metric distributions).

## Capabilities

### New Capabilities

- `benchmark-runner`: Automated multi-run execution of a scenario with configurable repetition count, collecting per-run scoring results into a benchmark report.
- `benchmark-statistics`: Descriptive statistics computation (mean, stddev, min, max, median, p95) across scoring metrics from multiple runs, including per-strategy breakdowns with variance.
- `benchmark-report`: Structured record capturing aggregated statistics, individual run references, scenario configuration, and baseline comparison deltas. Serializable for persistence and export.
- `benchmark-ui`: UI panel displaying aggregated metrics with confidence intervals, distribution summaries, baseline comparison badges, and per-strategy statistical breakdowns.

### Modified Capabilities

- `drift-evaluation`: `ScoringResult` gains an optional `benchmarkContext` field linking single-run results to their parent benchmark report when run as part of a benchmark.
- `run-history`: `RunHistoryStore` adds aggregation queries (`listByScenario` already exists; new: `aggregateByScenario` returning grouped run metadata for benchmark consumption).

## Impact

- **Packages**: New `sim.benchmark` package for `BenchmarkRunner`, `BenchmarkAggregator`, `BenchmarkStatistics`, `BenchmarkReport`. Modifications to `sim.engine.ScoringService`, `sim.engine.ScoringResult`, `sim.engine.RunHistoryStore`.
- **UI**: New `BenchmarkPanel` in `sim.views`. Modifications to `DriftSummaryPanel` to show baseline comparison when available.
- **Persistence**: Benchmark reports and baselines stored via `RunHistoryStore` (Neo4j JSON nodes, same pattern as `SimulationRunRecord`).
- **Observability**: OTEL span attributes for benchmark runs (`benchmark.run_count`, `benchmark.scenario_id`, `benchmark.duration_ms`). Micrometer metrics for aggregate scoring distributions.
- **Dependencies**: No new external dependencies. Statistics computed with standard library (`DoubleSummaryStatistics`, manual percentile calculation on sorted arrays).
- **Testing**: New unit tests for `BenchmarkAggregator` statistics accuracy. New deterministic benchmark tests using canned `ScoringResult` sets. Existing `ScoringServiceTest` and `DeterministicSimulationTest` unaffected.
