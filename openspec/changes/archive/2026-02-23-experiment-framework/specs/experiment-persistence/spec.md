## ADDED Requirements

### Requirement: ExperimentReport record

The system SHALL provide an `ExperimentReport` record in the `sim.benchmark` package containing: `reportId` (UUID string, non-null), `experimentName` (String, non-null), `createdAt` (Instant, non-null), `conditions` (List of condition name strings), `scenarioIds` (List of String), `repetitionsPerCell` (int), `totalDurationMs` (long), `cellReports` (Map of cell key string to `BenchmarkReport`), `effectSizeMatrix` (Map of condition-pair key to per-metric effect size values), `strategyDeltas` (Map of strategy name to per-condition effectiveness values), and `cancelled` (boolean).

#### Scenario: ExperimentReport contains all required fields

- **WHEN** an `ExperimentReport` is constructed
- **THEN** all fields SHALL be present and accessible: reportId, experimentName, createdAt, conditions, scenarioIds, repetitionsPerCell, totalDurationMs, cellReports, effectSizeMatrix, strategyDeltas, cancelled

#### Scenario: ExperimentReport with cancelled experiment

- **GIVEN** an experiment that was cancelled after 3 of 8 cells
- **WHEN** the `ExperimentReport` is constructed
- **THEN** `cancelled` SHALL be `true`
- **AND** `cellReports` SHALL contain exactly 3 entries

### Requirement: Save experiment report

`RunHistoryStore` SHALL provide `saveExperimentReport(ExperimentReport)` to persist an experiment report as a Neo4j JSON node with label `ExperimentReport`. The serialization pattern SHALL match the existing `BenchmarkReport` JSON-in-Neo4j approach. The report SHALL be retrievable immediately after saving without requiring a separate transaction commit. If a report with the same `reportId` already exists, it SHALL be overwritten.

#### Scenario: Save and retrieve an experiment report

- **WHEN** `saveExperimentReport(report)` is called with a valid `ExperimentReport`
- **THEN** the report is persisted as an `ExperimentReport`-labeled Neo4j node with JSON content
- **AND** `loadExperimentReport(report.reportId())` returns the same report with all fields intact

#### Scenario: Save overwrites existing report with same ID

- **GIVEN** an `ExperimentReport` with reportId "exp-001" already persisted
- **WHEN** `saveExperimentReport(updatedReport)` is called with the same reportId "exp-001"
- **THEN** the existing node SHALL be replaced with the updated report data

### Requirement: Load experiment report by ID

`RunHistoryStore` SHALL provide `loadExperimentReport(String reportId)` returning `Optional<ExperimentReport>`. The method SHALL return `Optional.empty()` when no report with the given ID exists. The deserialized report SHALL contain all nested structures (cellReports, effectSizeMatrix, strategyDeltas) fully restored.

#### Scenario: Load existing experiment report

- **GIVEN** a persisted experiment report with reportId "exp-001"
- **WHEN** `loadExperimentReport("exp-001")` is called
- **THEN** the method SHALL return `Optional.of(report)` with all fields deserialized including nested BenchmarkReport records

#### Scenario: Load nonexistent experiment report

- **WHEN** `loadExperimentReport("nonexistent-id")` is called
- **THEN** the method SHALL return `Optional.empty()`

### Requirement: List experiment reports

`RunHistoryStore` SHALL provide `listExperimentReports()` returning `List<ExperimentReport>` ordered by `createdAt` descending (newest first). The method SHALL return an empty list when no experiment reports exist.

#### Scenario: List returns reports in descending chronological order

- **GIVEN** three experiment reports with `createdAt` values T1, T2, T3 where T1 < T2 < T3
- **WHEN** `listExperimentReports()` is called
- **THEN** the reports SHALL be returned in order [T3, T2, T1]

#### Scenario: List returns empty when no reports exist

- **WHEN** no experiment reports have been persisted
- **THEN** `listExperimentReports()` SHALL return an empty list

### Requirement: Delete experiment report

`RunHistoryStore` SHALL provide `deleteExperimentReport(String reportId)` which removes the experiment report node from Neo4j. Deleting a nonexistent report SHALL be a no-op (no exception thrown).

#### Scenario: Delete an existing experiment report

- **GIVEN** a persisted experiment report with reportId "exp-001"
- **WHEN** `deleteExperimentReport("exp-001")` is called
- **THEN** the report node SHALL be removed from Neo4j
- **AND** `loadExperimentReport("exp-001")` SHALL return `Optional.empty()`

#### Scenario: Delete nonexistent report is a no-op

- **WHEN** `deleteExperimentReport("nonexistent-id")` is called
- **THEN** no exception SHALL be thrown and no data SHALL be modified

### Requirement: Neo4j storage pattern

`ExperimentReport` SHALL be stored as a Neo4j node with label `ExperimentReport` following the same JSON-in-Neo4j pattern used by `BenchmarkReport`. The node SHALL have properties: `reportId` (indexed for lookup), `experimentName`, `createdAt` (ISO-8601 string), and `data` (JSON string containing the full serialized report).

#### Scenario: ExperimentReport stored with correct label

- **WHEN** `saveExperimentReport(report)` is called
- **THEN** a Neo4j node with label `ExperimentReport` SHALL be created or updated

#### Scenario: Report ID is indexed for efficient lookup

- **GIVEN** multiple experiment reports stored in Neo4j
- **WHEN** `loadExperimentReport(reportId)` is called
- **THEN** the query SHALL use the `reportId` property for lookup (not a full scan)

### Requirement: JSON serialization

`ExperimentReport` SHALL be fully JSON-serializable using Jackson. All nested records including `BenchmarkReport`, `BenchmarkStatistics`, effect size entries, and strategy delta entries SHALL serialize and deserialize correctly. The `Instant` field (`createdAt`) SHALL serialize as an ISO-8601 string. The `Map` fields (`cellReports`, `effectSizeMatrix`, `strategyDeltas`) SHALL serialize as JSON objects.

#### Scenario: Round-trip serialization

- **GIVEN** a fully populated `ExperimentReport` with 4 cell reports, effect size matrix, and strategy deltas
- **WHEN** the report is serialized to JSON and then deserialized back
- **THEN** the deserialized report SHALL be equal to the original in all fields

#### Scenario: Nested BenchmarkReport serialization

- **GIVEN** an `ExperimentReport` containing `BenchmarkReport` records with `BenchmarkStatistics`
- **WHEN** the report is serialized and deserialized
- **THEN** the nested `BenchmarkStatistics` within each `BenchmarkReport` SHALL be correctly restored

#### Scenario: Instant serialization format

- **GIVEN** an `ExperimentReport` with `createdAt = 2026-02-23T15:30:00Z`
- **WHEN** the report is serialized to JSON
- **THEN** the `createdAt` field SHALL be represented as the ISO-8601 string "2026-02-23T15:30:00Z"

## Invariants

- **EP1**: `ExperimentReport` persistence SHALL follow the same transactional semantics as `BenchmarkReport` persistence.
- **EP2**: `ExperimentReport` and `BenchmarkReport` nodes SHALL coexist in the same Neo4j database without interference.
- **EP3**: JSON serialization SHALL be deterministic: serializing the same report twice SHALL produce identical JSON output.
