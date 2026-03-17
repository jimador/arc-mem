# Prep: Export & Aggregation Pipeline

## Feature

F05 — export-aggregation-pipeline

## Key Decisions

1. **JSON format**: Jackson serialization of ExperimentReport. Include all fields, no filtering.
2. **CSV format**: Flat table. One row per cell (condition × scenario). Columns: condition, scenario, then metric_mean, metric_stddev, metric_ci_lower, metric_ci_upper for each metric.
3. **Trace format**: JSON array. One entry per turn per fact: `{turnNumber, factId, activationScore, verdict, authority, trustScore}`.
4. **Cross-experiment aggregation**: Load multiple ExperimentReport records by ID, merge into a single dataset with experiment ID as a grouping column.
5. **File naming**: `{experiment-name}_{ISO-timestamp}.{format}` — no spaces, filesystem-safe.

## Open Questions

1. Should CSV include per-strategy metrics or just primary/secondary metrics?
2. Maximum trace file size for a large experiment? May need per-scenario splitting.
3. Should cross-experiment aggregation re-compute effect sizes or just concatenate raw data?

## Acceptance Gate

- JSON export parseable by Python `json.load()`
- CSV export loadable by `pd.read_csv()` without extra parsing
- Trace export usable for per-turn plotting
- Download available from BenchmarkView UI

## Research Dependencies

None

## Handoff Notes

JSON export is the easiest (Jackson already serializes everything). CSV is the most work (flattening hierarchical data). Trace export requires plumbing turn-level data from SimulationRunRecord. Start with JSON, then CSV, then traces.
