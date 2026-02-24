## MODIFIED Requirements

### Requirement: Generate Report action in RESULTS state

The `ConditionComparisonPanel` SHALL include a "Generate Report" button visible when an `ExperimentReport` is displayed. Clicking the button SHALL trigger report generation via `ResilienceReportBuilder` and initiate a Markdown file download in the browser.

#### Scenario: Generate Report button visible in RESULTS state

- **GIVEN** the BenchmarkView is in RESULTS state displaying an experiment report
- **WHEN** the user views the comparison panel
- **THEN** a "Generate Report" button SHALL be visible above or below the metric comparison

#### Scenario: Generate Report triggers download

- **GIVEN** the user clicks "Generate Report"
- **WHEN** the report is built and rendered to Markdown
- **THEN** a file download SHALL be initiated with filename `resilience-report-{experimentName}-{date}.md`
- **AND** the file content SHALL be the Markdown output from `MarkdownReportRenderer.render()`

#### Scenario: Generate Report with cancelled experiment

- **GIVEN** a cancelled experiment is loaded in RESULTS state
- **WHEN** the user clicks "Generate Report"
- **THEN** the generated report SHALL include a cancellation warning and partial results

### Requirement: Report generation feedback

While the report is being generated (per-fact data loading may take a few seconds), the "Generate Report" button SHALL show a loading state (disabled with "Generating..." text). After the download initiates, the button SHALL return to its normal state.

#### Scenario: Loading state during generation

- **GIVEN** the user clicks "Generate Report"
- **WHEN** the report builder is loading per-fact survival data
- **THEN** the button SHALL be disabled and show "Generating..."
- **AND** after download initiates, the button SHALL re-enable with original text

## Invariants

- **BVR-R1**: The "Generate Report" button SHALL only be visible when an `ExperimentReport` is currently displayed (RESULTS state).
