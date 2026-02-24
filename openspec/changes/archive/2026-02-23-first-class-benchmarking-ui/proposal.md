## Why

The experiment framework (F01) produces rich cross-condition data — ExperimentReports with Cohen's d effect sizes, 95% confidence intervals, per-strategy effectiveness deltas, and per-cell BenchmarkReports — but there is no dedicated surface to configure, execute, or visualize experiments. The existing BenchmarkPanel is a tab within SimulationView (`/`) scoped to single-scenario, single-condition benchmarks. It cannot configure multi-condition ablation experiments, display cross-condition comparisons, drill into per-fact verdicts across conditions, or manage experiment history. Without a purpose-built UI, the experiment framework requires manual API calls or test harnesses, blocking the experiment-iterate-interpret cycle needed for the tech report (F03).

## What Changes

- **New `/benchmark` Vaadin route** — a dedicated BenchmarkView with its own layout, independent of SimulationView. Follows the same `@Route` + `VerticalLayout` pattern used by SimulationView (`/`) and ChatView (`/chat`).
- **Experiment configuration panel** — condition checkboxes (FULL_ANCHORS, NO_ANCHORS, FLAT_AUTHORITY, NO_RANK_DIFFERENTIATION), scenario multi-select populated from ScenarioLoader with category tags, repetition slider [2-20], optional evaluator model override, and a "Run Experiment" button gated on valid selection.
- **Real-time execution monitor** — cell-level progress display ("Cell 3/12: FLAT_AUTHORITY x adversarial-contradictory, Run 2/5"), overall progress bar, estimated time remaining, completed-cell log with key metrics, and cancellation control. Uses Vaadin push (same pattern as SimulationView turn updates).
- **Cross-condition comparison view** — side-by-side metric cards per condition with mean/stddev/n, delta badges between condition pairs, Cohen's d effect size with interpretive labels (negligible/small/medium/large), and a condition-metric heatmap matrix with color-coded cells (metric-aware: higher survival = green, higher contradictions = red). Per-strategy effectiveness breakdown across conditions.
- **Fact-level drill-down** — expand a metric to see per-fact survival verdicts across conditions and runs, with first-drift-turn indicators per condition.
- **Experiment history panel** — list past experiments (name, date, conditions, scenarios, primary metric summary), load into comparison view, delete experiments.
- **Cross-view navigation** — links between SimulationView (`/`), BenchmarkView (`/benchmark`), and ChatView (`/chat`).

## Capabilities

### New Capabilities

- `experiment-config-ux`: Experiment configuration panel — condition selection, scenario multi-select with category tags, repetition slider, evaluator model override, run-button gating.
- `experiment-progress-monitor`: Real-time execution monitoring — cell-level progress, overall progress bar, ETA, cancellation control, completed-cell log.
- `condition-comparison-view`: Cross-condition comparison visualization — side-by-side metric cards, delta badges, effect size labels, condition-metric heatmap, per-strategy effectiveness.
- `fact-drill-down`: Per-fact drill-down — survival verdicts across conditions and runs, first-drift-turn indicators.
- `experiment-history-panel`: Experiment history management — list, load, delete past experiments.
- `benchmark-view-routing`: Dedicated `/benchmark` route with layout and cross-view navigation.

### Modified Capabilities

_(none — existing BenchmarkPanel in SimulationView is unchanged)_

## Impact

- **`sim.views` package (primary)**: New `BenchmarkView.java` + 5 panel classes (ExperimentConfigPanel, ExperimentProgressPanel, ConditionComparisonPanel, FactDrillDownPanel, ExperimentHistoryPanel).
- **Vaadin routing**: New `@Route("benchmark")`. Navigation links added to header areas of SimulationView and ChatView.
- **`frontend/themes/anchor-retro/styles.css`**: New CSS classes for comparison cards, delta badges, heatmap cells, effect size indicators, drill-down tables. MUST follow existing `ar-bench-*` naming convention.
- **`sim.benchmark` package**: No data model changes. The UI consumes ExperimentReport, ExperimentDefinition, AblationCondition, BenchmarkReport, BenchmarkStatistics, EffectSizeEntry, ConfidenceInterval, and strategy delta maps as-is.
- **`sim.engine` package**: No changes. ScenarioLoader and RunHistoryStore are consumed read-only (scenario listing, experiment persistence).

## Constitutional Alignment

- **Neo4j-only persistence**: Experiment reports already persisted via RunHistoryStore (F01). No new persistence layer.
- **Constructor injection only**: All new view/panel classes use constructor injection for services.
- **Records for DTOs**: UI consumes existing records (ExperimentReport, BenchmarkReport, etc.) — no new DTOs needed.
- **Anchor invariants**: UI is read-only with respect to anchor state. No mutations.
- **RFC 2119**: All specs produced from this proposal use RFC 2119 keywords.
