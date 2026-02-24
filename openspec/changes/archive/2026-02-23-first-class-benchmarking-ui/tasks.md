## 1. Shared Rendering Utilities

- [x] 1.1 Create `BenchmarkRenderUtils` in `sim.views` with static methods extracted from `BenchmarkPanel`: `metricCard(String label, BenchmarkStatistics stats, String health)` returning `Div`, `deltaBadge(double delta, boolean higherIsBetter)` returning `Span`, `strategyBar(String name, BenchmarkStatistics stats)` returning `HorizontalLayout`, `determineHealth(String metricName, BenchmarkStatistics stats)` returning `String`, and `HIGHER_IS_BETTER` map. (design D4, condition-comparison-view spec: side-by-side metric cards)
- [x] 1.2 Refactor `BenchmarkPanel` to delegate to `BenchmarkRenderUtils` for metric card rendering, delta badge rendering, strategy bar rendering, and health determination. Verify no behavioral change — existing BenchmarkPanel output is identical. (design D4)

## 2. BenchmarkView Shell and State Machine

- [x] 2.1 Create `BenchmarkViewState` enum in `sim.views` with values `CONFIG`, `RUNNING`, `RESULTS`. Add `canTransitionTo(BenchmarkViewState target)` method encoding valid transitions: CONFIG→RUNNING, RUNNING→RESULTS, RESULTS→CONFIG, RESULTS→RESULTS, CONFIG→RESULTS. (design D2, benchmark-view-routing spec: layout and lifecycle state machine)
- [x] 2.2 Create `BenchmarkView` class in `sim.views` with `@Route("benchmark")` and `@PageTitle("Anchor Benchmarks")`. Constructor-inject `ExperimentRunner`, `ScenarioLoader`, `RunHistoryStore`. Build header row with title "Anchor Benchmarks" and `RouterLink` navigation to `SimulationView` ("Simulator") and `ChatView` ("Chat"). Add `transitionTo(BenchmarkViewState)` method controlling panel visibility per design D2 state→panel mapping. (benchmark-view-routing spec: dedicated route, navigation from BenchmarkView, layout structure)
- [x] 2.3 Add `RouterLink` to `SimulationView` header row pointing to `BenchmarkView` ("Benchmark"). Add `RouterLink` to `ChatView` header area pointing to `BenchmarkView` ("Benchmark"). (benchmark-view-routing spec: navigation from SimulationView, navigation from ChatView)

## 3. Experiment Configuration Panel

- [x] 3.1 Create `ExperimentConfigPanel` in `sim.views` extending `VerticalLayout`. Add `CheckboxGroup<AblationCondition>` with items for all 4 standard conditions (FULL_ANCHORS, NO_ANCHORS, FLAT_AUTHORITY, NO_RANK_DIFFERENTIATION), using condition `name()` as display label. (experiment-config-ux spec: condition checkbox selection)
- [x] 3.2 Add scenario multi-select component populated from `ScenarioLoader.listScenarios()`. Display each scenario as `id` with a category badge tag (scenario.category()). Allow multiple selection. (experiment-config-ux spec: scenario multi-select with category tags)
- [x] 3.3 Add `IntegerField` for repetition count with range [2, 20], default value 5, step 1. Add optional `TextField` for evaluator model override (placeholder "default: scenario model"). (experiment-config-ux spec: repetition slider, evaluator model override)
- [x] 3.4 Add "Run Experiment" button. Disable when fewer than 2 conditions selected OR 0 scenarios selected. Add value-change listeners on condition group and scenario select to re-evaluate button enabled state. Wire button click to a `Consumer<ExperimentDefinition>` callback that assembles the definition from current selections. (experiment-config-ux spec: run experiment button gating)
- [x] 3.5 Add optional experiment name `TextField` (placeholder "auto-generated if blank"). If blank on run, auto-generate name as "Experiment YYYY-MM-DD #N" where N is next sequence. (design open question #2)

## 4. Experiment Progress Panel

- [x] 4.1 Create `ExperimentProgressPanel` in `sim.views` extending `VerticalLayout`. Add `Span` for current cell label, `Span` for run-within-cell ("Run 3/5"), `ProgressBar` for overall progress, `Span` for ETA. Add `updateProgress(ExperimentProgress)` method that updates all components from the progress record. Compute ETA from elapsed time and fraction complete. (experiment-progress-monitor spec: cell-level progress, run counter, overall progress bar, estimated time remaining)
- [x] 4.2 Add `VerticalLayout` for completed-cell log. After each cell completes (detected by run counter resetting), append a `Span` with cell key and factSurvivalRate mean (if available from the most recent cell's progress). Style with `ar-bench-progress-label`. (experiment-progress-monitor spec: completed-cell log)
- [x] 4.3 Add "Cancel Experiment" button that calls a `Runnable` cancel callback. On cancel, display "Cancelling... completing current cell" message. (experiment-progress-monitor spec: cancel experiment button)

## 5. Condition Comparison Panel

- [x] 5.1 Create `ConditionComparisonPanel` in `sim.views` extending `VerticalLayout`. Add `showReport(ExperimentReport)` method. For each scenario in the report, create a section header. For each condition, render a column of metric cards using `BenchmarkRenderUtils.metricCard()` with stats from the cell's `BenchmarkReport.metricStatistics`. Layout conditions side-by-side in a CSS Grid or HorizontalLayout. (condition-comparison-view spec: side-by-side metric cards)
- [x] 5.2 Between adjacent condition columns, render delta badges for each metric. Compute delta as difference of means. Use `BenchmarkRenderUtils.deltaBadge()`. Below each delta, display the effect size from `ExperimentReport.effectSizeMatrix` as "d=X.XX LABEL" with the interpretive label (negligible/small/medium/large). Show low-confidence warning badge when `EffectSizeEntry.lowConfidence()` is true. (condition-comparison-view spec: delta badges, Cohen's d with labels, low-confidence warning)
- [x] 5.3 Add condition-metric heatmap matrix below the card comparison. Render as a CSS Grid of `Div` cells with `ar-heatmap-cell` class. Rows = conditions, columns = metrics. Cell text = mean value. Cell `data-health` attribute set by `BenchmarkRenderUtils.determineHealth()`. Add row/column headers. (condition-comparison-view spec: condition-metric heatmap)
- [x] 5.4 Add per-strategy effectiveness comparison table below the heatmap. For each strategy in `ExperimentReport.strategyDeltas`, render a row with the strategy name and one column per condition showing the mean effectiveness. Use `BenchmarkRenderUtils.strategyBar()` for each cell. (condition-comparison-view spec: per-strategy effectiveness comparison)

## 6. Fact-Level Drill-Down Panel

- [x] 6.1 Create `FactDrillDownPanel` in `sim.views` extending `VerticalLayout`. Accept an `ExperimentReport` and `RunHistoryStore` reference. For each metric in the comparison, add a Vaadin `Details` component (collapsed by default) labeled "Drill down: <metric>". (fact-drill-down spec: drill-down trigger)
- [x] 6.2 On `Details` expand, lazy-load per-fact data: iterate `cellReports`, for each cell load `SimulationRunRecord` via `RunHistoryStore.load(runId)` for each `runId` in the cell's `BenchmarkReport.runIds`. Extract per-fact verdicts from `turnSnapshots`. Aggregate to compute survival count per fact per condition. Cache results. Show loading indicator during load. (fact-drill-down spec: per-fact survival count, data source, design D7)
- [x] 6.3 Render per-fact table: columns = Fact Text, then one column per condition showing "X/N survived" (e.g., "5/5" or "2/5"). Color-code survival counts (all survived = green, partial = amber, none = red). Add first-drift turn column per condition showing the earliest turn where the fact was contradicted (or "—" if never). (fact-drill-down spec: per-fact survival display, first-drift turn)

## 7. Experiment History Panel

- [x] 7.1 Create `ExperimentHistoryPanel` in `sim.views` extending `VerticalLayout`. Add a `refresh()` method that calls `RunHistoryStore.listExperimentReports()` and renders a list of experiment summary rows. Each row shows: experiment name, date (formatted), condition count, scenario count, repetitions, and primary metric (factSurvivalRate grand mean across all cells). Sort by createdAt descending. (experiment-history-panel spec: experiment list, summary columns, sort order)
- [x] 7.2 Add "Load" button per row that fires a `Consumer<ExperimentReport>` callback to load the selected experiment into the comparison view. The callback triggers `BenchmarkView.transitionTo(RESULTS)` with the loaded report. (experiment-history-panel spec: load experiment)
- [x] 7.3 Add "Delete" button per row with a confirmation prompt. On confirm, call `RunHistoryStore.deleteExperimentReport(reportId)` and refresh the list. (experiment-history-panel spec: delete experiment)

## 8. Async Execution Wiring

- [x] 8.1 Wire `ExperimentConfigPanel`'s run callback in `BenchmarkView`. On run: transition to RUNNING, build `ExperimentDefinition` from config panel selections, launch `ExperimentRunner.runExperiment()` via `CompletableFuture.runAsync()`. Pass progress callback that invokes `ui.access(() -> progressPanel.updateProgress(progress))`. On completion: `ui.access(() -> { transitionTo(RESULTS); comparisonPanel.showReport(report); historyPanel.refresh(); })`. On error: `ui.access(() -> showError(ex))`. (design D8, experiment-progress-monitor spec: thread-safe UI updates)
- [x] 8.2 Wire `ExperimentProgressPanel`'s cancel callback to `ExperimentRunner.cancel()`. After cancellation completes, the experiment still returns a report with `cancelled=true`. Display it in the comparison view with a "Cancelled — partial results" banner. (experiment-progress-monitor spec: cancel experiment, design D2)
- [x] 8.3 Wire `ExperimentHistoryPanel`'s load callback in `BenchmarkView`. On load: call `RunHistoryStore.loadExperimentReport(id)`, transition to RESULTS, pass report to `comparisonPanel.showReport()`. (experiment-history-panel spec: load experiment)

## 9. CSS Styling

- [x] 9.1 Add new CSS classes to `frontend/themes/anchor-retro/styles.css` following `ar-*` naming convention: `ar-heatmap-grid` (CSS Grid for conditions × metrics), `ar-heatmap-cell` (individual cell with `[data-health]` coloring), `ar-heatmap-header` (row/column headers), `ar-effect-size` (effect size display), `ar-effect-size-label` (interpretive label badge), `ar-experiment-row` (history list row), `ar-experiment-summary` (summary text), `ar-condition-column` (side-by-side condition layout), `ar-delta-column` (delta badge column between conditions), `ar-cancelled-banner` (partial results warning). (benchmark-view-routing spec: anchor-retro CSS compliance, condition-comparison-view spec: heatmap, accessibility)
- [x] 9.2 Ensure all color-coded elements have text labels in addition to color. Heatmap cells MUST show numeric values. Delta badges MUST show "IMPROVED"/"REGRESSED" text. Effect size badges MUST show "negligible"/"small"/"medium"/"large" text. (condition-comparison-view spec: CCV2 accessibility invariant)

## 10. Verification

- [x] 10.1 Run `./mvnw.cmd clean compile -DskipTests` — verify clean compilation with all new types.
- [x] 10.2 Run `./mvnw.cmd test` — verify all existing tests still pass (no regressions from BenchmarkRenderUtils refactor or navigation link additions).
- [x] 10.3 Manual verification: start the app, navigate to `/benchmark`, verify CONFIG state shows config panel and history panel, verify navigation links work bidirectionally between all three views.
