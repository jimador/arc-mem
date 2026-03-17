# Feature: Export & Aggregation Pipeline

## Feature ID

`F05`

## Summary

Add JSON and CSV export formats for experiment reports, per-turn fact survival trace export (for generating figures), and a cross-experiment aggregation capability that combines multiple experiment runs into a unified dataset suitable for whitepaper tables and figures.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: The experiment runner only outputs Markdown reports. Whitepaper figures (fact survival over turns, erosion curves) require structured data. Statistical analysis in R/Python requires CSV or JSON. Cross-run aggregation for confidence intervals requires combining data from multiple experiment executions.
2. Value delivered: One-step path from experiment run to analysis-ready data. No manual data extraction from Markdown.
3. Why now: This is the pipeline that turns experiment runs into whitepaper artifacts. Without it, data gathering is manual.

## Scope

### In Scope

1. JSON export: complete ExperimentReport serialized as structured JSON with all metrics, effect sizes, per-cell data, and per-turn traces
2. CSV export: tabular format with one row per cell (condition × scenario), columns for all metrics + CI bounds
3. Per-turn fact survival trace export: JSON array with one entry per turn per fact, including activation score, verdict, authority, and trust score
4. Cross-experiment aggregation: load multiple ExperimentReport records from Neo4j and merge into a unified dataset
5. Export triggers: UI download button (Vaadin StreamResource) + programmatic API
6. File naming convention: `{experiment-name}_{timestamp}.{format}`

### Out of Scope

1. Figure generation (plotting is done externally in Python/R)
2. HTML report format
3. Real-time streaming export
4. Cloud storage integration

## Dependencies

1. Feature dependencies: F04 (secondary metrics should be included in export from the start)
2. Technical prerequisites: Jackson serialization for JSON; existing BenchmarkReport/ExperimentReport records
3. Parent objectives: Whitepaper Tables 7, Figures 7-8

## Impacted Areas

1. Packages/components: New `report/export/` package with `JsonExporter`, `CsvExporter`, `TraceExporter`, `CrossExperimentAggregator`
2. Data/persistence: Reads from Neo4j `ExperimentReport` and `SimulationRunRecord` nodes
3. Domain-specific subsystem impacts: None — pure read + transform

## Visibility Requirements

### UI Visibility

1. User-facing surface: BenchmarkView download dropdown with JSON/CSV/Markdown options
2. What is shown: Format selector, download button, file size estimate
3. Success signal: Downloaded JSON file opens in Python and contains all expected fields

### Observability Visibility

1. Logs/events/metrics: Export events logged with format, record count, file size
2. Trace/audit payload: Export metadata (source experiment IDs, timestamp, format)
3. How to verify: Export JSON, load in Python, verify DataFrame shape matches expected conditions × scenarios × metrics

## Acceptance Criteria

1. JSON export MUST include all ExperimentReport fields: cell reports, effect sizes, CIs, strategy deltas
2. CSV export MUST have one row per cell with columns for condition, scenario, and all metric mean/stddev/CI values
3. Per-turn trace export MUST include turn number, fact ID, activation score, verdict, authority, trust score
4. Cross-experiment aggregation MUST combine ≥2 experiment reports into a single merged dataset
5. All exports MUST be downloadable from BenchmarkView UI
6. JSON/CSV formats MUST be machine-parseable without manual cleanup

## Risks and Mitigations

1. Risk: Large experiments produce very large JSON files (many turns × many facts × many runs)
2. Mitigation: Trace export can be per-scenario to limit file size; summary-level export separate from trace-level

## Proposal Seed

### Suggested OpenSpec Change Slug

`export-aggregation-pipeline`

### Proposal Starter Inputs

1. Problem statement: Experiment data is locked in Markdown reports and Neo4j. Whitepaper figures and statistical analysis require structured data in JSON/CSV. No export path exists.
2. Why now: Every experiment run before this feature produces data that requires manual extraction. This is the automation bottleneck.
3. Constraints: Pure read + transform — no mutations. Export formats MUST be stable across experiment runner changes.
4. Outcomes: One-click export from UI; programmatic export for automated pipelines; cross-experiment aggregation for meta-analysis.

### Suggested Capability Areas

1. JSON serialization of experiment reports
2. CSV flattening of hierarchical metric data
3. Per-turn trace extraction
4. Cross-experiment merge logic

### Candidate Requirement Blocks

1. Requirement: CSV export MUST be loadable in pandas with `pd.read_csv()` without additional parsing
2. Scenario: User exports CSV, opens in Jupyter notebook, generates fact survival plot with 3 lines of code

## Validation Plan

1. Unit tests: JSON/CSV exporters produce valid, parseable output from mock ExperimentReport
2. Integration: Run a real experiment, export JSON/CSV, verify data integrity
3. End-to-end: Load exported CSV in Python script, verify column names and row counts match expectations

## Known Limitations

1. No figure generation — plotting delegated to external tools
2. Cross-experiment aggregation assumes compatible condition/scenario sets
3. Large trace exports may require pagination or streaming for very long simulations

## Suggested Command

`/opsx:new export-aggregation-pipeline`
