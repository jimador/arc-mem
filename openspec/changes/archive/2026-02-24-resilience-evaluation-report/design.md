## Context

The experiment framework (F01/F06) produces `ExperimentReport` with cross-condition statistics, per-cell `BenchmarkReport` records, effect size matrices, and strategy deltas. The BenchmarkView UI displays these results interactively. `FactDrillDownPanel` already loads per-fact survival data lazily from `RunHistoryStore`.

This design adds a report generation layer that transforms experiment results into a self-contained Markdown document for external sharing.

### Current State

- **ExperimentReport**: Contains `cellReports` (Map<cellKey, BenchmarkReport>), `effectSizeMatrix`, `strategyDeltas`, `conditions`, `scenarioIds`, `cancelled`.
- **BenchmarkReport**: Per-cell `metricStatistics`, `strategyStatistics`, `runIds`.
- **FactDrillDownPanel**: Loads `SimulationRunRecord` per `runId`, extracts per-fact contradiction verdicts from `turnSnapshots`. Uses `ScenarioLoader` for ground truth text.
- **BenchmarkView**: State machine (CONFIG/RUNNING/RESULTS) with `ConditionComparisonPanel` in RESULTS state.

## Goals / Non-Goals

**Goals:**

1. Self-contained Markdown evaluation report from any `ExperimentReport`.
2. Composite resilience score for headline comparison.
3. Per-fact survival tables in the report (reusing FactDrillDownPanel's data loading logic).
4. Positioning statement referencing MemGPT/Letta, Zep/Graphiti, ShardMemo, Core Anchor Memory.
5. One-click download from BenchmarkView.

**Non-Goals:**

1. PDF generation (Markdown is sufficient; PDF can be rendered downstream).
2. Interactive/HTML report format.
3. Persisting generated reports to Neo4j.
4. Automatic report generation (always user-triggered).

## Decisions

### D1: New `sim.report` Package

**Decision**: Create all report types in a new `sim.report` package, separate from `sim.benchmark` (data) and `sim.views` (UI).

**Rationale**: Report generation is a presentation concern, distinct from experiment execution and UI rendering. Clean separation of concerns.

### D2: Reuse FactDrillDownPanel Data Loading Logic via Extraction

**Decision**: Extract the per-fact survival loading logic from `FactDrillDownPanel` into a shared utility (`FactSurvivalLoader` in `sim.report` or `sim.engine`) that both `FactDrillDownPanel` and `ResilienceReportBuilder` can use.

**Alternatives considered**:
- *Duplicate the logic*: Fast but creates maintenance burden.
- *Have builder depend on FactDrillDownPanel*: Wrong dependency direction (report layer depending on view layer).

**Rationale**: The core logic (load run records, iterate snapshots, extract contradiction verdicts, compute survival counts) is ~50 lines. Extracting to a shared loader keeps both consumers clean.

### D3: Synchronous Report Generation with UI Feedback

**Decision**: Generate the report synchronously on a background thread (via `CompletableFuture`), with the button showing a loading state. The per-fact data loading is the expensive part (O(runs × cells) record loads).

**Alternatives considered**:
- *Generate lazily section by section*: Overengineered for a report that takes a few seconds.
- *Pre-compute on experiment completion*: Wastes work if user never generates a report.

**Rationale**: Follows the same `CompletableFuture` + `ui.access()` pattern used throughout the codebase.

### D4: Vaadin StreamResource for Download

**Decision**: Use Vaadin's `StreamResource` + `Anchor` component for the Markdown file download. The anchor is programmatically clicked after report generation completes.

**Rationale**: Vaadin's standard approach for file downloads. No external dependencies needed.

### D5: Positioning Statement as Static Template

**Decision**: The positioning statement is a static String constant in `ResilienceReportBuilder`, not dynamically generated. It references the related systems and explains what this system focuses on.

**Rationale**: The positioning is a fixed narrative choice, not derived from experiment data. Keeping it as a constant avoids unnecessary complexity. Can be made configurable later if needed.

### D6: ResilienceScore Weights as Constants

**Decision**: The component weights (survival=0.40, drift=0.25, contradiction=0.20, strategy=0.15) are defined as constants in `ResilienceScoreCalculator`. Not configurable via properties.

**Rationale**: These weights encode the system's evaluation philosophy. Making them configurable invites confusion without clear benefit. If calibration is needed, the constants are easy to update.

## Data Flow

```
ExperimentReport
       │
       ├──► ResilienceScoreCalculator.compute()  ──► ResilienceScore
       │
       ├──► FactSurvivalLoader.load()            ──► Map<scenarioId, List<FactSurvivalRow>>
       │         (uses RunHistoryStore + ScenarioLoader)
       │
       └──► ResilienceReportBuilder.build()       ──► ResilienceReport
                    │
                    └──► MarkdownReportRenderer.render()  ──► String (Markdown)
                              │
                              └──► StreamResource ──► Browser download
```

## New Types

| Type | Package | Purpose |
|------|---------|---------|
| `ResilienceReport` | `sim.report` | Top-level report record |
| `ScenarioSection` | `sim.report` | Per-scenario data section |
| `ConditionSummary` | `sim.report` | Per-condition metric summary |
| `EffectSizeSummary` | `sim.report` | Effect size between two conditions |
| `FactSurvivalRow` | `sim.report` | Per-fact survival across conditions |
| `FactConditionResult` | `sim.report` | Survival count + first drift for one fact in one condition |
| `StrategySection` | `sim.report` | Strategy effectiveness breakdown |
| `ResilienceScore` | `sim.report` | Composite resilience metric |
| `ResilienceScoreCalculator` | `sim.report` | Computes ResilienceScore from ExperimentReport |
| `ResilienceReportBuilder` | `sim.report` | Assembles ResilienceReport from ExperimentReport |
| `MarkdownReportRenderer` | `sim.report` | Renders ResilienceReport as Markdown |
| `FactSurvivalLoader` | `sim.report` | Shared per-fact survival data loading (extracted from FactDrillDownPanel) |

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|------|--------|------------|
| Per-fact data loading is slow for large experiments | Report generation takes 5–15 seconds for 4 conditions × 3 scenarios × 5 reps | Loading indicator on button. Data is cached in the loader. |
| FactSurvivalLoader extraction changes FactDrillDownPanel | Refactor risk | Extract as a new class that FactDrillDownPanel delegates to. No behavioral change. |
| Markdown table formatting edge cases | Long fact text breaks table alignment | Truncate fact text to 60 chars in tables, full text in footnotes or tooltip-style. |
