## ADDED Requirements

### Requirement: BenchmarkReport record structure

The system SHALL provide an immutable `BenchmarkReport` record in the `sim.benchmark` package with the following fields: `reportId` (String), `scenarioId` (String), `createdAt` (Instant), `runCount` (int), `totalDurationMs` (long), `metricStatistics` (Map<String, BenchmarkStatistics>), `strategyStatistics` (Map<String, BenchmarkStatistics>), `runIds` (List<String>), `baselineReportId` (@Nullable String), and `baselineDeltas` (@Nullable Map<String, Double>).

#### Scenario: Record construction with all fields

- **GIVEN** all required field values including a baseline reference
- **WHEN** a `BenchmarkReport` is constructed
- **THEN** all accessor methods SHALL return the provided values, and `baselineReportId()` and `baselineDeltas()` SHALL return the baseline data

#### Scenario: Record construction without baseline

- **GIVEN** all required field values with `baselineReportId = null` and `baselineDeltas = null`
- **WHEN** a `BenchmarkReport` is constructed
- **THEN** `baselineReportId()` SHALL return `null` and `baselineDeltas()` SHALL return `null`

### Requirement: Report ID generation

Each `BenchmarkReport` SHALL have a `reportId` generated as `"bench-" + UUID.randomUUID()`. The ID SHALL be assigned at creation time by `BenchmarkAggregator` and SHALL be unique across all reports.

#### Scenario: Report ID format

- **GIVEN** a newly created `BenchmarkReport`
- **WHEN** `reportId()` is accessed
- **THEN** the value SHALL match the pattern `bench-[0-9a-f-]{36}`

### Requirement: Run IDs traceability

`BenchmarkReport.runIds()` SHALL contain the simulation run ID from each completed run, in execution order. The list SHALL be immutable. Each run ID SHALL correspond to a `SimulationRunRecord` persisted by `RunHistoryStore`.

#### Scenario: Run IDs match completed runs

- **GIVEN** a benchmark with 5 completed runs
- **WHEN** the `BenchmarkReport` is created
- **THEN** `runIds()` SHALL contain exactly 5 entries, each corresponding to a persisted `SimulationRunRecord`

#### Scenario: Run IDs are immutable

- **GIVEN** a `BenchmarkReport` with `runIds = ["run-1", "run-2", "run-3"]`
- **WHEN** a caller attempts to modify the list via `report.runIds().add("run-4")`
- **THEN** an `UnsupportedOperationException` SHALL be thrown

### Requirement: Persistence via RunHistoryStore

`RunHistoryStore` SHALL be extended with the following methods for benchmark report persistence: `saveBenchmarkReport(BenchmarkReport report)`, `loadBenchmarkReport(String reportId)` returning `Optional<BenchmarkReport>`, and `listBenchmarkReports(String scenarioId)` returning `List<BenchmarkReport>` ordered by `createdAt` descending.

#### Scenario: Save and load round-trip

- **GIVEN** a `BenchmarkReport` with `reportId = "bench-abc123"`
- **WHEN** `saveBenchmarkReport(report)` is called followed by `loadBenchmarkReport("bench-abc123")`
- **THEN** the loaded report SHALL be equal to the saved report with all fields preserved

#### Scenario: Load non-existent report

- **GIVEN** no report with ID `"bench-nonexistent"` exists
- **WHEN** `loadBenchmarkReport("bench-nonexistent")` is called
- **THEN** the result SHALL be `Optional.empty()`

#### Scenario: List reports by scenario

- **GIVEN** 3 benchmark reports for scenario `"adversarial-drift"` and 2 for scenario `"baseline-stability"`
- **WHEN** `listBenchmarkReports("adversarial-drift")` is called
- **THEN** the result SHALL contain exactly 3 reports, ordered by `createdAt` descending (newest first)

### Requirement: Report JSON serialization

`BenchmarkReport` SHALL be serializable to JSON for Neo4j storage using the same JSON-in-node pattern as `SimulationRunRecord`. The serialized form SHALL preserve all fields including nested `BenchmarkStatistics` records and nullable baseline fields. Deserialization SHALL reconstruct an identical `BenchmarkReport`.

#### Scenario: Nested BenchmarkStatistics serialization

- **GIVEN** a `BenchmarkReport` with `metricStatistics` containing a `"factSurvivalRate"` entry with `mean = 0.85`
- **WHEN** the report is serialized to JSON and deserialized back
- **THEN** `report.metricStatistics().get("factSurvivalRate").mean()` SHALL equal `0.85`

#### Scenario: Null baseline fields serialized

- **GIVEN** a `BenchmarkReport` with `baselineReportId = null`
- **WHEN** the report is serialized to JSON and deserialized back
- **THEN** `baselineReportId()` SHALL be `null`

### Requirement: Baseline management - save as baseline

`RunHistoryStore` SHALL provide a method `saveAsBaseline(String reportId, String scenarioId)` that marks a specific `BenchmarkReport` as the baseline for a given scenario. Only one baseline SHALL exist per scenario at any time. Saving a new baseline SHALL replace the previous baseline for that scenario.

#### Scenario: Save baseline for scenario

- **GIVEN** a `BenchmarkReport` with `reportId = "bench-abc"` for scenario `"adversarial-drift"`
- **WHEN** `saveAsBaseline("bench-abc", "adversarial-drift")` is called
- **THEN** subsequent calls to `loadBaseline("adversarial-drift")` SHALL return the report with `reportId = "bench-abc"`

#### Scenario: Replace existing baseline

- **GIVEN** scenario `"adversarial-drift"` has baseline `"bench-old"`
- **WHEN** `saveAsBaseline("bench-new", "adversarial-drift")` is called
- **THEN** `loadBaseline("adversarial-drift")` SHALL return the report with `reportId = "bench-new"`, not `"bench-old"`

### Requirement: Baseline management - load baseline

`RunHistoryStore` SHALL provide a method `loadBaseline(String scenarioId)` returning `Optional<BenchmarkReport>`. If no baseline has been saved for the given scenario, the method SHALL return `Optional.empty()`.

#### Scenario: Load existing baseline

- **GIVEN** a baseline has been saved for scenario `"adversarial-drift"`
- **WHEN** `loadBaseline("adversarial-drift")` is called
- **THEN** the result SHALL be `Optional.of(baselineReport)`

#### Scenario: Load baseline for scenario with no baseline

- **GIVEN** no baseline has been saved for scenario `"baseline-stability"`
- **WHEN** `loadBaseline("baseline-stability")` is called
- **THEN** the result SHALL be `Optional.empty()`

### Requirement: Neo4j node label

Persisted `BenchmarkReport` nodes SHALL use the Neo4j label `BenchmarkReport`, distinct from the existing `SimulationRun` label used for individual run records.

#### Scenario: Correct node label

- **GIVEN** a `BenchmarkReport` is saved via `saveBenchmarkReport()`
- **WHEN** the Neo4j database is queried
- **THEN** the node SHALL have label `BenchmarkReport` and property `reportId` matching the report's ID

## Invariants

- **RP1**: `BenchmarkReport.runIds().size()` SHALL always equal `BenchmarkReport.runCount()`.
- **RP2**: `BenchmarkReport.metricStatistics()` SHALL be an immutable map. Callers SHALL NOT be able to modify it.
- **RP3**: `BenchmarkReport.strategyStatistics()` SHALL be an immutable map.
- **RP4**: At most one baseline SHALL exist per scenario at any time.
- **RP5**: `BenchmarkReport.reportId()` SHALL be non-null and non-empty.
- **BMR1**: Benchmark reports SHALL surface model identifier and degraded-run counts so evidence quality is assessable.

## Added Requirements (initial-community-review-readiness)

### Requirement: BenchmarkReport record structure (evidence quality metadata)

`BenchmarkReport` SHALL include model identifier and degraded-run counts so reviewers can assess evidence quality at a glance.

#### Scenario: Report contains model identifier and degraded-run counts
- **GIVEN** a completed benchmark run cohort
- **WHEN** the benchmark report is assembled
- **THEN** the report SHALL include the model identifier used and the count of runs where conflict evaluation was degraded

#### Scenario: Non-zero degraded-run count is visible in rendered output
- **GIVEN** a benchmark report with one or more degraded runs
- **WHEN** the report is rendered
- **THEN** the degraded-run count SHALL appear in the rendered output so reviewers notice it
