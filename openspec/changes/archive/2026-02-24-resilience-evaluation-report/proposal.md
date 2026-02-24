## Why

The experiment framework produces rich cross-condition data (effect sizes, per-fact survival, strategy effectiveness) but there is no structured report artifact that can be exported, shared with the DICE team, or submitted alongside a tech report. Results exist only inside the live Vaadin UI. To present anchor resilience findings externally — whether in a team review, an arXiv submission, or a GitHub discussion — we need a self-contained evaluation report that captures experiment configuration, statistical results, interpretive narrative, and positioning against related systems (MemGPT/Letta, Zep/Graphiti, ShardMemo, Core Anchor Memory).

## What Changes

- Introduce a `ResilienceReport` data model that captures a complete evaluation narrative: experiment metadata, per-condition metric summaries, cross-condition effect sizes, per-fact survival tables, strategy effectiveness breakdowns, and a positioning statement.
- Add a `ResilienceReportBuilder` service that transforms an `ExperimentReport` into a `ResilienceReport`, computing derived metrics (overall resilience score, drift resistance index) and generating section-level summaries.
- Add a Markdown export capability that renders the `ResilienceReport` as a self-contained Markdown document suitable for inclusion in a tech report, GitHub issue, or arXiv appendix.
- Add a "Generate Report" action to the BenchmarkView RESULTS state that produces and downloads the Markdown report.
- Add a `ResilienceScore` composite metric that distills multi-metric experiment results into a single headline number with breakdown, suitable for cross-experiment comparison.

## Capabilities

### New Capabilities
- `resilience-report`: Data model, builder, and Markdown export for self-contained evaluation reports from experiment results.
- `resilience-score`: Composite metric computing an overall resilience score from experiment metrics (survival rate, contradiction count, drift absorption, strategy resistance).

### Modified Capabilities
- `benchmark-view-routing`: RESULTS state gains a "Generate Report" button that triggers report generation and Markdown download.

## Impact

- **New package**: `sim.report` — `ResilienceReport`, `ResilienceReportBuilder`, `ResilienceScore`, `MarkdownReportRenderer`.
- **BenchmarkView** (`sim.views`): RESULTS state adds report generation button. `ConditionComparisonPanel` gains a "Generate Report" action.
- **No persistence changes**: Reports are generated on-demand from existing `ExperimentReport` data. Optionally cached in memory but not stored in Neo4j.
- **No API changes**: This is a view-layer and report-layer addition.
- **Dependencies**: Builds on `ExperimentReport`, `BenchmarkReport`, `BenchmarkStatistics`, `EffectSizeEntry` from `sim.benchmark`.

## Constitutional Alignment

- RFC 2119 keywords used throughout specs per constitution requirement.
- No new persistence layer (Neo4j-only constraint preserved).
- Anchor invariants (rank clamping, authority upgrade-only, budget enforcement) are not affected — this change is read-only over experiment results.
