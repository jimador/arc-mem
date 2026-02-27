## MODIFIED Requirements

### Requirement: Store selection via configuration

The active `RunHistoryStore` implementation SHALL be selected via `dice-anchors.run-history.store` configuration property. Valid values SHALL be `MEMORY` (SimulationRunStore) and `NEO4J` (Neo4jRunHistoryStore). The default SHALL be `NEO4J`.

#### Scenario: Neo4j store is default
- **WHEN** no `dice-anchors.run-history.store` property is set
- **THEN** the `Neo4jRunHistoryStore` is used

#### Scenario: Memory store selected explicitly
- **WHEN** `dice-anchors.run-history.store=MEMORY` is configured
- **THEN** the `SimulationRunStore` is used

## ADDED Requirements

### Requirement: Neo4j benchmark report persistence

`Neo4jRunHistoryStore` SHALL persist `BenchmarkReport` as Neo4j nodes with label `BenchmarkReport`. Each node SHALL have first-class properties `reportId` (indexed), `scenarioId` (indexed), `createdAt`, and `modelId`, plus a `payload` property containing the full JSON serialization. The serialization pattern SHALL match the existing `SimulationRun` JSON-in-Neo4j approach.

#### Scenario: Save and load benchmark report round-trip
- **WHEN** `saveBenchmarkReport(report)` is called
- **THEN** `loadBenchmarkReport(report.reportId())` returns the same report with all fields preserved

#### Scenario: List benchmark reports ordered by date
- **WHEN** three benchmark reports exist with `createdAt` values T1 < T2 < T3
- **THEN** `listBenchmarkReports()` returns them in order [T3, T2, T1]

#### Scenario: List benchmark reports by scenario
- **WHEN** reports exist for scenarios "A" and "B"
- **THEN** `listBenchmarkReportsByScenario("A")` returns only scenario "A" reports

### Requirement: Neo4j experiment report persistence

`Neo4jRunHistoryStore` SHALL persist `ExperimentReport` as Neo4j nodes with label `ExperimentReport`. Each node SHALL have first-class properties `reportId` (indexed) and `createdAt`, plus a `payload` property containing the full JSON serialization.

#### Scenario: Save and load experiment report round-trip
- **WHEN** `saveExperimentReport(report)` is called
- **THEN** `loadExperimentReport(report.reportId())` returns the same report with all nested records preserved

#### Scenario: List experiment reports ordered by date
- **WHEN** three experiment reports exist
- **THEN** `listExperimentReports()` returns them ordered by `createdAt` descending

### Requirement: Neo4j baseline persistence

`Neo4jRunHistoryStore` SHALL persist baseline references as `Baseline` nodes with a `BASELINE_FOR` relationship to the designated `BenchmarkReport` node. Each `Baseline` node SHALL have a `scenarioId` property. Only one baseline SHALL exist per scenario; `saveAsBaseline` SHALL use MERGE on `scenarioId` to enforce uniqueness.

#### Scenario: Save and load baseline
- **WHEN** `saveAsBaseline(reportId, scenarioId)` is called with a valid report
- **THEN** `loadBaseline(scenarioId)` returns that report

#### Scenario: Overwrite previous baseline
- **WHEN** a baseline exists for "scenario-A" and `saveAsBaseline(newReportId, "scenario-A")` is called
- **THEN** `loadBaseline("scenario-A")` returns the new report

#### Scenario: Delete report cascades baseline removal
- **WHEN** report "R1" is the baseline for "scenario-A" and `deleteBenchmarkReport("R1")` is called
- **THEN** `loadBaseline("scenario-A")` returns `Optional.empty()`

### Requirement: Report type isolation in Neo4j

`BenchmarkReport` and `ExperimentReport` nodes SHALL use distinct Neo4j labels. `listBenchmarkReports()` SHALL NOT return experiment reports. `listExperimentReports()` SHALL NOT return benchmark reports.

#### Scenario: Listing benchmark reports excludes experiment reports
- **GIVEN** both benchmark and experiment reports exist
- **WHEN** `listBenchmarkReports()` is called
- **THEN** only `BenchmarkReport` records are returned

#### Scenario: Listing experiment reports excludes benchmark reports
- **GIVEN** both benchmark and experiment reports exist
- **WHEN** `listExperimentReports()` is called
- **THEN** only `ExperimentReport` records are returned
