## ADDED Requirements

### Requirement: Benchmark report persistence

`RunHistoryStore` SHALL provide `saveBenchmarkReport(BenchmarkReport)` to persist a benchmark report as a Neo4j JSON node with label `BenchmarkReport`. The serialization pattern SHALL match the existing `SimulationRunRecord` JSON-in-Neo4j approach. The report SHALL be retrievable immediately after saving without requiring a separate transaction commit.

#### Scenario: Save and retrieve a benchmark report
- **WHEN** `saveBenchmarkReport(report)` is called with a valid `BenchmarkReport`
- **THEN** the report is persisted as a `BenchmarkReport`-labeled Neo4j node with JSON content
- **AND** `loadBenchmarkReport(report.reportId())` returns the same report

#### Scenario: Save overwrites existing report with same ID
- **WHEN** `saveBenchmarkReport(report)` is called with a `reportId` that already exists
- **THEN** the existing node is replaced with the new report data

### Requirement: Benchmark report retrieval by ID

`RunHistoryStore` SHALL provide `loadBenchmarkReport(String reportId)` returning `Optional<BenchmarkReport>`. The method SHALL return `Optional.empty()` when no report with the given ID exists.

#### Scenario: Load existing report
- **WHEN** `loadBenchmarkReport(reportId)` is called for a persisted report
- **THEN** the method returns `Optional.of(report)` with all fields deserialized

#### Scenario: Load nonexistent report
- **WHEN** `loadBenchmarkReport("nonexistent-id")` is called
- **THEN** the method returns `Optional.empty()`

### Requirement: Benchmark report listing

`RunHistoryStore` SHALL provide `listBenchmarkReports()` returning `List<BenchmarkReport>` ordered by `createdAt` descending (newest first). The method SHALL return an empty list when no reports exist.

#### Scenario: List returns reports in descending chronological order
- **WHEN** three benchmark reports exist with `createdAt` values T1, T2, T3 where T1 < T2 < T3
- **THEN** `listBenchmarkReports()` returns them in order [T3, T2, T1]

#### Scenario: List returns empty when no reports exist
- **WHEN** no benchmark reports have been persisted
- **THEN** `listBenchmarkReports()` returns an empty list

### Requirement: Benchmark report listing by scenario

`RunHistoryStore` SHALL provide `listBenchmarkReportsByScenario(String scenarioId)` returning `List<BenchmarkReport>` filtered to the given scenario and ordered by `createdAt` descending. The method SHALL return an empty list when no reports exist for the given scenario.

#### Scenario: List reports for a specific scenario
- **WHEN** reports exist for scenarios "scenario-A" and "scenario-B"
- **AND** `listBenchmarkReportsByScenario("scenario-A")` is called
- **THEN** only reports with `scenarioId` equal to "scenario-A" are returned, ordered by `createdAt` descending

#### Scenario: List returns empty for unknown scenario
- **WHEN** `listBenchmarkReportsByScenario("nonexistent-scenario")` is called
- **THEN** the method returns an empty list

### Requirement: Baseline management

`RunHistoryStore` SHALL provide `saveAsBaseline(String reportId, String scenarioId)` to designate one benchmark report as the baseline for a given scenario. Only one baseline SHALL exist per scenario; calling `saveAsBaseline` with a new `reportId` for the same `scenarioId` SHALL overwrite the previous baseline reference. The `reportId` MUST reference an existing benchmark report; if it does not, the method SHALL throw `IllegalArgumentException`.

#### Scenario: Set baseline for a scenario
- **WHEN** `saveAsBaseline(reportId, scenarioId)` is called with a valid report ID
- **THEN** `loadBaseline(scenarioId)` returns that report

#### Scenario: Overwrite previous baseline
- **WHEN** a baseline already exists for "scenario-A" and `saveAsBaseline(newReportId, "scenario-A")` is called
- **THEN** `loadBaseline("scenario-A")` returns the new report
- **AND** the previous baseline reference is removed

#### Scenario: Reject nonexistent report as baseline
- **WHEN** `saveAsBaseline("nonexistent-id", "scenario-A")` is called
- **THEN** the method throws `IllegalArgumentException`

### Requirement: Baseline retrieval

`RunHistoryStore` SHALL provide `loadBaseline(String scenarioId)` returning `Optional<BenchmarkReport>`. The method SHALL return `Optional.empty()` when no baseline has been set for the given scenario.

#### Scenario: Load existing baseline
- **WHEN** a baseline has been set for "scenario-A"
- **THEN** `loadBaseline("scenario-A")` returns `Optional.of(baselineReport)`

#### Scenario: Load baseline for scenario with no baseline
- **WHEN** no baseline has been set for "scenario-B"
- **THEN** `loadBaseline("scenario-B")` returns `Optional.empty()`

### Requirement: Benchmark report deletion with cascade

`RunHistoryStore` SHALL provide `deleteBenchmarkReport(String reportId)` which removes the benchmark report node from Neo4j. If the deleted report is currently designated as a baseline for any scenario, the baseline reference SHALL also be removed. Deleting a nonexistent report SHALL be a no-op (no exception thrown).

#### Scenario: Delete a non-baseline report
- **WHEN** `deleteBenchmarkReport(reportId)` is called for a report that is not a baseline
- **THEN** the report node is removed from Neo4j
- **AND** `loadBenchmarkReport(reportId)` returns `Optional.empty()`

#### Scenario: Delete a baseline report cascades baseline removal
- **WHEN** report "R1" is the baseline for "scenario-A" and `deleteBenchmarkReport("R1")` is called
- **THEN** the report node is removed
- **AND** `loadBaseline("scenario-A")` returns `Optional.empty()`

#### Scenario: Delete nonexistent report is a no-op
- **WHEN** `deleteBenchmarkReport("nonexistent-id")` is called
- **THEN** no exception is thrown and no data is modified

### Requirement: Experiment report persistence

`RunHistoryStore` SHALL provide `saveExperimentReport(ExperimentReport)` to persist an experiment report as a Neo4j JSON node with label `ExperimentReport`. The serialization pattern SHALL match the existing `BenchmarkReport` JSON-in-Neo4j approach. The report SHALL be retrievable immediately after saving without requiring a separate transaction commit.

#### Scenario: Save and retrieve an experiment report

- **WHEN** `saveExperimentReport(report)` is called with a valid `ExperimentReport`
- **THEN** the report is persisted as an `ExperimentReport`-labeled Neo4j node with JSON content
- **AND** `loadExperimentReport(report.reportId())` returns the same report

#### Scenario: Save overwrites existing report with same ID

- **WHEN** `saveExperimentReport(report)` is called with a `reportId` that already exists
- **THEN** the existing node is replaced with the new report data

### Requirement: Experiment report retrieval by ID

`RunHistoryStore` SHALL provide `loadExperimentReport(String reportId)` returning `Optional<ExperimentReport>`. The method SHALL return `Optional.empty()` when no report with the given ID exists.

#### Scenario: Load existing experiment report

- **WHEN** `loadExperimentReport(reportId)` is called for a persisted report
- **THEN** the method returns `Optional.of(report)` with all fields deserialized including nested BenchmarkReport and BenchmarkStatistics records

#### Scenario: Load nonexistent experiment report

- **WHEN** `loadExperimentReport("nonexistent-id")` is called
- **THEN** the method returns `Optional.empty()`

### Requirement: Experiment report listing

`RunHistoryStore` SHALL provide `listExperimentReports()` returning `List<ExperimentReport>` ordered by `createdAt` descending (newest first). The method SHALL return an empty list when no experiment reports exist.

#### Scenario: List returns reports in descending chronological order

- **WHEN** three experiment reports exist with `createdAt` values T1, T2, T3 where T1 < T2 < T3
- **THEN** `listExperimentReports()` returns them in order [T3, T2, T1]

#### Scenario: List returns empty when no reports exist

- **WHEN** no experiment reports have been persisted
- **THEN** `listExperimentReports()` returns an empty list

### Requirement: Experiment report deletion

`RunHistoryStore` SHALL provide `deleteExperimentReport(String reportId)` which removes the experiment report node from Neo4j. Deleting a nonexistent report SHALL be a no-op (no exception thrown).

#### Scenario: Delete an existing experiment report

- **GIVEN** a persisted experiment report with reportId "exp-001"
- **WHEN** `deleteExperimentReport("exp-001")` is called
- **THEN** the report node is removed from Neo4j
- **AND** `loadExperimentReport("exp-001")` returns `Optional.empty()`

#### Scenario: Delete nonexistent report is a no-op

- **WHEN** `deleteExperimentReport("nonexistent-id")` is called
- **THEN** no exception is thrown and no data is modified

### Requirement: Experiment reports coexist with benchmark reports

The new experiment report methods SHALL NOT interfere with existing benchmark report methods. `listBenchmarkReports()` SHALL NOT return experiment reports, and `listExperimentReports()` SHALL NOT return benchmark reports. The two report types SHALL use distinct Neo4j node labels (`BenchmarkReport` and `ExperimentReport` respectively).

#### Scenario: Listing benchmark reports excludes experiment reports

- **GIVEN** both benchmark reports and experiment reports exist in the store
- **WHEN** `listBenchmarkReports()` is called
- **THEN** only `BenchmarkReport` records SHALL be returned

#### Scenario: Listing experiment reports excludes benchmark reports

- **GIVEN** both benchmark reports and experiment reports exist in the store
- **WHEN** `listExperimentReports()` is called
- **THEN** only `ExperimentReport` records SHALL be returned
