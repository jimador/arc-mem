## ADDED Requirements

### Requirement: BenchmarkRunner service

The system SHALL provide a `BenchmarkRunner` service in the `sim.benchmark` package annotated with `@Service`. The service SHALL depend on `SimulationService` and `BenchmarkAggregator` via constructor injection.

#### Scenario: Service instantiation

- **GIVEN** the Spring application context
- **WHEN** the `sim.benchmark` package is scanned
- **THEN** a single `BenchmarkRunner` bean SHALL be available for injection

### Requirement: runBenchmark method signature

`BenchmarkRunner` SHALL expose a method `runBenchmark(ScenarioDefinition scenario, int runCount, boolean injectionEnabled, int tokenBudget, Consumer<BenchmarkProgress> onProgress)` returning a `BenchmarkReport`. The `runCount` parameter MUST be >= 2. The method SHALL throw `IllegalArgumentException` if `runCount < 2`.

#### Scenario: Valid invocation

- **GIVEN** a loaded `ScenarioDefinition` and `runCount = 5`
- **WHEN** `runBenchmark(scenario, 5, true, 4000, callback)` is called
- **THEN** the method SHALL return a `BenchmarkReport` with `runCount = 5`

#### Scenario: Invalid run count rejected

- **GIVEN** `runCount = 1`
- **WHEN** `runBenchmark(scenario, 1, true, 4000, callback)` is called
- **THEN** the method SHALL throw `IllegalArgumentException` with a message indicating the minimum run count is 2

### Requirement: Sequential execution of N runs

`BenchmarkRunner` SHALL invoke `SimulationService.runSimulation()` exactly N times in sequence, where N equals the `runCount` parameter. Each run SHALL use a unique `contextId` (via the existing `sim-{uuid}` isolation pattern). The runner SHALL collect the `ScoringResult` from each run's final `SimulationProgress`.

#### Scenario: Five sequential runs

- **GIVEN** `runCount = 5`
- **WHEN** `runBenchmark` executes
- **THEN** `SimulationService.runSimulation()` SHALL be called exactly 5 times, each invocation completing before the next begins

#### Scenario: Each run uses isolated context

- **GIVEN** `runCount = 3`
- **WHEN** `runBenchmark` executes
- **THEN** each of the 3 runs SHALL use a distinct `contextId` matching pattern `sim-{uuid}`

### Requirement: Per-run progress callback

After each individual run completes, `BenchmarkRunner` SHALL invoke the `onProgress` callback with a `BenchmarkProgress` record containing: the completed run index (1-based), the total `runCount`, and the `ScoringResult` from that run.

#### Scenario: Progress reported after each run

- **GIVEN** `runCount = 3` and a progress callback
- **WHEN** `runBenchmark` executes all 3 runs
- **THEN** the callback SHALL be invoked 3 times with `(completedRun=1, total=3, result1)`, `(completedRun=2, total=3, result2)`, and `(completedRun=3, total=3, result3)` respectively

#### Scenario: Progress callback receives current ScoringResult

- **GIVEN** a progress callback
- **WHEN** run 2 of 5 completes with `factSurvivalRate = 0.85`
- **THEN** the callback SHALL receive a `BenchmarkProgress` where `scoringResult().factSurvivalRate() == 0.85`

### Requirement: Cancellation support

`BenchmarkRunner` SHALL support cancellation of an in-progress benchmark. When cancellation is requested, the runner SHALL complete the currently executing run but SHALL NOT start any subsequent runs. The method SHALL return a `BenchmarkReport` aggregated from whichever runs completed before cancellation, with `runCount` reflecting the actual number of completed runs.

#### Scenario: Cancel after 3 of 10 runs

- **GIVEN** `runCount = 10` and a benchmark in progress
- **WHEN** cancellation is requested while run 3 is executing
- **THEN** run 3 SHALL complete, runs 4-10 SHALL NOT start, and the returned `BenchmarkReport` SHALL have `runCount = 3`

#### Scenario: Cancel before any run completes

- **GIVEN** `runCount = 5` and a benchmark in progress
- **WHEN** cancellation is requested while run 1 is executing
- **THEN** run 1 SHALL complete, and the returned `BenchmarkReport` SHALL have `runCount = 1`

### Requirement: Benchmark report assembly

After all runs complete (or after cancellation), `BenchmarkRunner` SHALL pass the collected `List<ScoringResult>` to `BenchmarkAggregator.aggregate()` and return the resulting `BenchmarkReport`. The report's `totalDurationMs` SHALL reflect wall-clock time from the start of the first run to the end of the last completed run. The report's `runIds` list SHALL contain the run ID from each completed run.

#### Scenario: Report contains all run IDs

- **GIVEN** 5 completed runs with IDs `["run-a", "run-b", "run-c", "run-d", "run-e"]`
- **WHEN** `BenchmarkRunner` assembles the report
- **THEN** `report.runIds()` SHALL equal `["run-a", "run-b", "run-c", "run-d", "run-e"]`

#### Scenario: Duration reflects wall-clock time

- **GIVEN** a benchmark that starts at T=0 and the last run completes at T=60000ms
- **WHEN** the report is assembled
- **THEN** `report.totalDurationMs()` SHALL be approximately 60000

### Requirement: OTEL observability span

`BenchmarkRunner.runBenchmark()` SHALL be annotated with `@Observed(name = "benchmark.run")`. The span SHALL include the following attributes: `benchmark.scenario_id` (the scenario ID), `benchmark.run_count` (the requested run count), `benchmark.duration_ms` (total wall-clock duration), and `benchmark.mean_survival_rate` (the mean `factSurvivalRate` from the report).

#### Scenario: OTEL span emitted on benchmark completion

- **GIVEN** a benchmark run for scenario `"adversarial-drift"` with `runCount = 5`
- **WHEN** the benchmark completes in 45000ms with mean `factSurvivalRate = 0.82`
- **THEN** an OTEL span named `benchmark.run` SHALL be emitted with attributes `benchmark.scenario_id = "adversarial-drift"`, `benchmark.run_count = 5`, `benchmark.duration_ms = 45000`, and `benchmark.mean_survival_rate = 0.82`

## Invariants

- **BR1**: Each benchmark run SHALL use a unique, isolated `contextId`. No two runs within the same benchmark SHALL share a context.
- **BR2**: The `BenchmarkRunner` SHALL NOT modify `SimulationService` state or require changes to the `SimulationService` interface.
- **BR3**: Progress callbacks SHALL be invoked on the calling thread in run-completion order. No out-of-order delivery.
