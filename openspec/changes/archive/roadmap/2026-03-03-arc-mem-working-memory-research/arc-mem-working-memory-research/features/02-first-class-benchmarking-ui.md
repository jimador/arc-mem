# Feature: First-Class Benchmarking UI

## Feature ID

`F02`

## Summary

Deliver a dedicated `/benchmark` Vaadin route that provides experiment configuration, real-time execution monitoring, cross-condition comparison views, per-fact drill-down, and experiment history. This view elevates benchmarking from a tab within SimulationView to a first-class surface purpose-built for designing, running, and interpreting ablation experiments.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: The current BenchmarkPanel is a tab within SimulationView (`/`). It supports single-scenario, single-condition benchmarking with metric cards, strategy bars, and baseline comparison. It cannot configure multi-condition experiments, display cross-condition comparisons, drill into per-fact verdicts across conditions, or manage experiment history. The experiment framework (F01) produces rich cross-condition data that has no visualization surface.
2. **Value delivered**: Researchers and operators get a purpose-built interface for designing ablation experiments, monitoring execution in real time, comparing results across conditions, and drilling into fact-level outcomes. This accelerates the experiment-iterate-interpret cycle critical for the tech report (F03).
3. **Why now**: Wave 2. F01 (experiment framework) produces ExperimentReports. Without a UI to configure experiments, monitor progress, and visualize results, the experiment framework requires manual API calls or test harnesses. The benchmarking UI is the interaction layer that makes F01 usable.

## Scope

### In Scope

1. **Dedicated `/benchmark` Vaadin route** with its own layout and navigation.
2. **Experiment configuration panel**: Condition selection, scenario selection, repetition count, and optional evaluator model override.
3. **Real-time experiment execution monitor**: Cell-by-cell progress, cancellation controls, and estimated time remaining.
4. **Cross-condition comparison view**: Side-by-side metric cards with delta badges and effect size (Cohen's d) display.
5. **Per-fact drill-down**: Expand a metric to see per-fact survival/drift verdicts across conditions and runs.
6. **Experiment history**: List past experiments, load results, and compare across experiments.
7. **Navigation**: Links between SimulationView (`/`), BenchmarkView (`/benchmark`), and ChatView (`/chat`).

### Out of Scope

1. Modifications to the existing BenchmarkPanel in SimulationView (it continues to work for single-scenario benchmarks).
2. Export to PDF, CSV, or other formats (candidate extension).
3. Real-time streaming of individual turn results during a run (the UI receives progress at the run and cell level, not the turn level).
4. Custom scenario authoring from the UI.
5. Integration with external experiment tracking platforms (MLflow, Weights & Biases).

## Dependencies

1. **F01 (experiment-framework)**: REQUIRED. Provides ExperimentDefinition, AblationCondition, ExperimentRunner, ExperimentReport, and effect size computation. The UI is a presentation layer over F01's data model and execution engine.
2. **Vaadin 24.6.4**: The existing UI framework. The benchmarking view MUST use the same theme (`frontend/themes/arc-retro/styles.css`) and component patterns as SimulationView and ChatView.
3. **sim.engine.ScenarioLoader**: Provides the list of available scenarios for the multi-select configuration.
4. **sim.engine.RunHistoryStore**: Provides experiment report persistence for the history view.
5. **Priority**: MUST.
6. **OpenSpec change slug**: `first-class-benchmarking-ui`.

## Research Requirements (Optional)

No dedicated research tasks. UI design decisions SHOULD reference the existing BenchmarkPanel and SimulationView patterns for consistency. Heatmap/matrix visualization libraries available in Vaadin SHOULD be evaluated during design.

## Impacted Areas

1. **`sim.views` package (primary)**: New BenchmarkView, ExperimentConfigPanel, ExperimentProgressPanel, ConditionComparisonPanel, FactDrillDownPanel, ExperimentHistoryPanel.
2. **Vaadin routing**: New `@Route("benchmark")` registration. Navigation links added to SimulationView and ChatView.
3. **`frontend/themes/arc-retro/styles.css`**: New CSS classes for comparison views, delta badges, heatmap cells, and effect size indicators.
4. **`sim.benchmark` package (minor)**: No changes to data model. The UI consumes ExperimentReport, BenchmarkReport, BenchmarkStatistics, and effect size data as-is.

## Visibility Requirements

### UI Visibility

1. **Experiment Configuration Panel**:
   - Condition selection MUST use checkboxes for the four standard conditions (FULL_UNITS, NO_UNITS, FLAT_AUTHORITY, NO_RANK_DIFFERENTIATION). At least two conditions MUST be selected to run an experiment.
   - Scenario selection MUST use a multi-select component populated from ScenarioLoader. Scenarios SHOULD display their category (adversarial, baseline, feature-specific) as tags.
   - Repetition count MUST use a slider or number input with range [2, 20], defaulting to 5.
   - Evaluator model override SHOULD be an optional text field, defaulting to the scenario's configured model.
   - A "Run Experiment" button MUST be disabled until at least 2 conditions and 1 scenario are selected.

2. **Experiment Progress Panel**:
   - MUST display: experiment name, current cell label ("FLAT_AUTHORITY x adversarial-contradictory"), current run within cell ("Run 3/5"), overall progress bar, and estimated time remaining.
   - MUST provide a "Cancel Experiment" button that triggers experiment-level cancellation (completes current cell).
   - SHOULD display a running log of completed cells with their key metric (factSurvivalRate mean).

3. **Condition Comparison View**:
   - MUST display side-by-side metric cards for each condition. Each card MUST show: metric name, mean, stddev, and sample count.
   - MUST display delta badges between condition pairs (e.g., "+12.3% survival rate" between FULL_UNITS and NO_UNITS).
   - SHOULD display Cohen's d effect size for each metric pair, with interpretive labels (small: 0.2, medium: 0.5, large: 0.8).
   - SHOULD display a heatmap or matrix view: conditions (rows) x metrics (columns) with color-coded cells (green = good, red = concerning). Color coding MUST be metric-aware (higher factSurvivalRate = green, higher contradictionCount = red).
   - SHOULD display per-strategy effectiveness comparison: a grouped bar chart or table showing strategy success rates across conditions.

4. **Fact-Level Drill-Down**:
   - MUST allow expanding a metric (e.g., factSurvivalRate) to see per-fact verdicts across conditions.
   - Per-fact display MUST show: fact text, survival count per condition (e.g., "5/5 in FULL_UNITS, 2/5 in NO_UNITS"), and first-drift turn per condition.
   - SHOULD display a per-turn timeline visualization: horizontal bar per condition showing when drift first occurred for a selected fact.

5. **Experiment History**:
   - MUST list past experiments with: name, date, condition count, scenario count, and primary metric summary.
   - MUST support loading a past experiment to display its full comparison view.
   - SHOULD support selecting two experiments for cross-experiment comparison.

### Observability Visibility

1. UI interactions (experiment start, cancel, history load) SHOULD emit Micrometer metrics (`benchmark.ui.experiment.started`, `benchmark.ui.experiment.cancelled`, `benchmark.ui.history.loaded`).
2. The BenchmarkView SHOULD display the OTEL trace ID for the running experiment, enabling operators to correlate UI activity with backend traces.
3. Error states (experiment failure, partial results due to cancellation) MUST be displayed with clear error messages and recovery guidance.

## Acceptance Criteria

1. The BenchmarkView MUST be accessible at `/benchmark` as a dedicated Vaadin route.
2. Navigation links MUST exist between SimulationView (`/`), BenchmarkView (`/benchmark`), and ChatView (`/chat`).
3. The experiment configuration panel MUST allow selecting conditions (checkboxes), scenarios (multi-select), and repetitions (slider 2-20).
4. The experiment progress panel MUST display cell-level progress ("Cell 3/12: FLAT_AUTHORITY x adversarial-contradictory, Run 2/5") during execution.
5. The condition comparison view MUST display side-by-side metric cards for each condition with delta badges between pairs.
6. Per-fact drill-down MUST show per-fact survival counts across conditions (e.g., "Fact X survived in FULL_UNITS 5/5, drifted in NO_UNITS 2/5").
7. Experiment history MUST list past experiments and support loading them into the comparison view.
8. The cancel button MUST trigger experiment-level cancellation and display partial results.
9. The UI MUST use the existing `arc-retro` CSS theme for visual consistency.
10. Effect sizes (Cohen's d) SHOULD be displayed with interpretive labels (small/medium/large) in the comparison view.
11. Per-strategy effectiveness breakdown SHOULD be visible across conditions (table or grouped chart).
12. The BenchmarkView MUST NOT break or interfere with existing SimulationView or ChatView functionality.

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Vaadin component complexity** | Medium | Medium | The comparison view requires non-trivial layouts (side-by-side cards, heatmaps, drill-down). Use Vaadin's existing Grid, Details, and custom HTML components. Avoid external charting libraries unless absolutely necessary. |
| **Real-time progress updates** | Medium | Low | Vaadin push is already used in SimulationView for turn-by-turn updates. Experiment progress uses the same pattern with coarser granularity (per-run, not per-turn). |
| **Large experiment results** | Low | Medium | An experiment with 4 conditions x 5 scenarios x 5 reps = 100 BenchmarkReports per ExperimentReport. The comparison view SHOULD paginate or summarize when result sets are large. |
| **CSS theme consistency** | Low | Low | New CSS classes MUST follow the naming conventions in `arc-retro/styles.css`. Delta badges and heatmap cells SHOULD be reviewed against the existing theme before finalization. |
| **Accessibility** | Medium | Medium | Heatmap color coding MUST NOT rely solely on color. Cells SHOULD include text labels or patterns for colorblind accessibility. |

## Proposal Seed

### Change Slug

`first-class-benchmarking-ui`

### Proposal Starter Inputs

1. **Problem statement**: The experiment framework (F01) produces rich cross-condition data (ExperimentReports with effect sizes, per-strategy breakdowns, per-cell BenchmarkReports) that has no dedicated visualization surface. The existing BenchmarkPanel is scoped to single-scenario benchmarks and cannot display condition comparisons.
2. **Why now**: F01 delivers the data layer. Without a UI, experiment design and result interpretation require manual report inspection. The paper-writing process (F03) benefits directly from rapid visual iteration on experiment results.
3. **Constraints/non-goals**: No modifications to SimulationView's existing BenchmarkPanel. No external charting libraries unless Vaadin's built-in components are insufficient. No data export (candidate extension). Use existing `arc-retro` theme.
4. **Visible outcomes**: Operators MUST be able to configure an experiment, watch it execute, compare results across conditions with effect sizes, drill into per-fact outcomes, and browse experiment history -- all from a single dedicated view.

### Suggested Capability Areas

1. **Experiment configuration UX**: Condition checkboxes, scenario multi-select with category tags, repetition slider, evaluator model override.
2. **Real-time execution monitoring**: Cell-level progress, estimated time remaining, cancellation.
3. **Cross-condition comparison visualization**: Metric cards, delta badges, effect size labels, heatmap.
4. **Fact-level drill-down**: Per-fact survival across conditions, first-drift turn timeline.
5. **Experiment history management**: List, load, and compare past experiments.

### Candidate Requirement Blocks

1. **REQ-ROUTE**: The system SHALL provide a dedicated `/benchmark` Vaadin route with navigation to and from SimulationView and ChatView.
2. **REQ-CONFIG**: The system SHALL allow operators to configure experiments by selecting conditions, scenarios, repetitions, and optional evaluator model override.
3. **REQ-PROGRESS**: The system SHALL display real-time cell-level progress during experiment execution, including estimated time remaining and a cancellation control.
4. **REQ-COMPARE**: The system SHALL display side-by-side condition comparison with delta badges and effect size indicators.
5. **REQ-DRILL**: The system SHALL provide per-fact drill-down showing survival verdicts across conditions.
6. **REQ-HISTORY**: The system SHALL persist experiments and allow operators to browse and reload past experiment results.

## Validation Plan

1. **Manual UI testing** MUST verify the experiment configuration workflow: select conditions, scenarios, repetitions; start experiment; observe progress; view results.
2. **Manual UI testing** MUST verify the comparison view renders correctly with at least 2 conditions and displays delta badges and effect sizes.
3. **Manual UI testing** MUST verify per-fact drill-down shows per-condition survival counts.
4. **Manual UI testing** MUST verify experiment history lists past experiments and loads them correctly.
5. **Accessibility testing** SHOULD verify that heatmap cells are distinguishable without relying solely on color.
6. **Regression testing** MUST verify that SimulationView (`/`), ChatView (`/chat`), and the existing BenchmarkPanel continue to function correctly after the new route is added.
7. **Cross-browser testing** SHOULD verify the BenchmarkView renders correctly in Chrome, Firefox, and Safari.

## Known Limitations

1. **No data export**: Results are viewable in the UI and persisted in Neo4j, but cannot be exported to CSV, PDF, or LaTeX tables. This is a candidate extension for F03 (paper writing benefits from table export).
2. **No custom condition authoring from the UI**: The four standard conditions are hardcoded. Custom conditions (e.g., "CANON only" or "budget = 10") require code changes in the experiment framework.
3. **No collaborative features**: Only one experiment can run at a time (inherited from BenchmarkRunner's sequential execution constraint). There is no multi-user experiment queue.
4. **Heatmap resolution**: With 4 conditions x 6 metrics, the heatmap is a 4x6 grid. This is manageable. Scaling to many more conditions or metrics MAY require a different visualization approach.
5. **Per-turn timeline**: The fact-level drill-down shows first-drift turn but does not replay the full conversation. Full conversation replay is available in SimulationView for individual runs.

## Suggested Command

```
/opsx:ff first-class-benchmarking-ui
```
