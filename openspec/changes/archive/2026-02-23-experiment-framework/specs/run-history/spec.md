## ADDED Requirements

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
