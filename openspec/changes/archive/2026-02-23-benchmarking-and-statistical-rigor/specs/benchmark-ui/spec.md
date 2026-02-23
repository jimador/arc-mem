## ADDED Requirements

### Requirement: BenchmarkPanel Vaadin component

The system SHALL provide a `BenchmarkPanel` class in the `sim.views` package extending a Vaadin layout component. The panel SHALL be displayed as a tab labeled "Benchmark" in the `SimulationView` `TabSheet`, alongside the existing Conversation, Anchors, Drift, and Context tabs.

#### Scenario: Tab appears in SimulationView

- **GIVEN** the `SimulationView` is rendered
- **WHEN** the user views the tab bar
- **THEN** a "Benchmark" tab SHALL be visible after the existing tabs

### Requirement: Metric cards display

When a `BenchmarkReport` is available, `BenchmarkPanel` SHALL render a metric card for each entry in `metricStatistics`. Each card SHALL display: the metric name, `mean +/- stddev` formatted to 2 decimal places, the `median` value, the `p95` value, and the `sampleCount` displayed as `"n=<count>"`.

#### Scenario: Metric card content

- **GIVEN** a `BenchmarkReport` with `metricStatistics` containing `"factSurvivalRate"` with `mean=0.85, stddev=0.05, median=0.86, p95=0.93, sampleCount=10`
- **WHEN** the panel renders
- **THEN** a metric card SHALL display `"factSurvivalRate"`, `"0.85 +/- 0.05"`, `"median: 0.86"`, `"p95: 0.93"`, and `"n=10"`

#### Scenario: All six metric cards rendered

- **GIVEN** a `BenchmarkReport` with 6 entries in `metricStatistics`
- **WHEN** the panel renders
- **THEN** exactly 6 metric cards SHALL be displayed

### Requirement: Per-strategy effectiveness bars

`BenchmarkPanel` SHALL render a bar for each entry in `strategyStatistics` showing the strategy name, a horizontal bar representing the mean effectiveness, and confidence bounds displayed as `mean +/- 1 stddev`.

#### Scenario: Strategy bar with confidence bounds

- **GIVEN** `strategyStatistics` containing `"SUBTLE_REFRAME"` with `mean=0.70, stddev=0.10`
- **WHEN** the panel renders
- **THEN** a bar SHALL be displayed for `"SUBTLE_REFRAME"` showing `"0.70 +/- 0.10"` with the bar width proportional to the mean value

#### Scenario: Multiple strategy bars

- **GIVEN** `strategyStatistics` containing 3 strategies
- **WHEN** the panel renders
- **THEN** exactly 3 strategy bars SHALL be rendered, one for each strategy

### Requirement: Baseline comparison badges

When `BenchmarkReport.baselineDeltas()` is non-null, `BenchmarkPanel` SHALL display a comparison badge next to each metric card. The badge SHALL indicate `IMPROVED` (green) when the delta is favorable, `REGRESSED` (red) when the delta is unfavorable, or `UNCHANGED` (neutral) when the absolute delta is less than 0.01. The badge SHALL include the delta value formatted to 2 decimal places.

#### Scenario: Improved metric badge

- **GIVEN** `baselineDeltas` contains `"factSurvivalRate" -> 0.05` (positive delta is favorable for survival rate)
- **WHEN** the panel renders
- **THEN** the `"factSurvivalRate"` card SHALL display an `IMPROVED` badge with `"+0.05"`

#### Scenario: Regressed metric badge

- **GIVEN** `baselineDeltas` contains `"contradictionCount" -> 1.5` (positive delta is unfavorable for contradiction count)
- **WHEN** the panel renders
- **THEN** the `"contradictionCount"` card SHALL display a `REGRESSED` badge with `"+1.50"`

#### Scenario: Unchanged metric badge

- **GIVEN** `baselineDeltas` contains `"driftAbsorptionRate" -> 0.005`
- **WHEN** the panel renders
- **THEN** the `"driftAbsorptionRate"` card SHALL display an `UNCHANGED` badge

#### Scenario: No baseline available

- **GIVEN** `baselineDeltas` is `null`
- **WHEN** the panel renders
- **THEN** no comparison badges SHALL be displayed

### Requirement: High-variance warning

When a metric's `BenchmarkStatistics.coefficientOfVariation()` exceeds 0.5, `BenchmarkPanel` SHALL display a visible warning indicator on that metric's card with text indicating high variance (e.g., "High variance: CV=0.75").

#### Scenario: High variance flagged

- **GIVEN** `metricStatistics` for `"meanTurnsToFirstDrift"` has `mean=3.0, stddev=2.0` (CV = 0.67)
- **WHEN** the panel renders
- **THEN** the `"meanTurnsToFirstDrift"` card SHALL display a high-variance warning

#### Scenario: Normal variance not flagged

- **GIVEN** `metricStatistics` for `"factSurvivalRate"` has `mean=0.85, stddev=0.05` (CV = 0.059)
- **WHEN** the panel renders
- **THEN** the `"factSurvivalRate"` card SHALL NOT display a high-variance warning

### Requirement: Run benchmark button with run count slider

`BenchmarkPanel` SHALL display a "Run Benchmark" button and a run count slider. The slider SHALL have a default value of 5, a minimum of 2, and a maximum of 20. Clicking the button SHALL invoke `BenchmarkRunner.runBenchmark()` with the selected run count and the currently loaded scenario.

#### Scenario: Default slider value

- **GIVEN** the `BenchmarkPanel` is rendered without prior interaction
- **WHEN** the user views the run count slider
- **THEN** the slider SHALL display a value of 5

#### Scenario: Slider range enforced

- **GIVEN** the run count slider
- **WHEN** the user attempts to set a value outside `[2, 20]`
- **THEN** the slider SHALL clamp the value to the nearest boundary

#### Scenario: Button triggers benchmark

- **GIVEN** a loaded scenario and slider value of 10
- **WHEN** the user clicks "Run Benchmark"
- **THEN** `BenchmarkRunner.runBenchmark()` SHALL be called with `runCount = 10`

### Requirement: Progress indicator during execution

While a benchmark is executing, `BenchmarkPanel` SHALL display a progress indicator showing the current run number and total run count (e.g., "Run 3/10"). The progress indicator SHALL update after each run completes via the `BenchmarkProgress` callback. The "Run Benchmark" button SHALL be disabled during execution.

#### Scenario: Progress displayed during benchmark

- **GIVEN** a benchmark is in progress with `runCount = 10`
- **WHEN** run 3 completes
- **THEN** the progress indicator SHALL display "Run 3/10"

#### Scenario: Button disabled during execution

- **GIVEN** a benchmark is in progress
- **WHEN** the user views the "Run Benchmark" button
- **THEN** the button SHALL be disabled and not clickable

#### Scenario: Progress cleared on completion

- **GIVEN** a benchmark has completed
- **WHEN** the panel updates with the final report
- **THEN** the progress indicator SHALL be hidden and the "Run Benchmark" button SHALL be re-enabled

### Requirement: Cancel button during execution

While a benchmark is executing, `BenchmarkPanel` SHALL display a "Cancel" button. Clicking the button SHALL request cancellation via `BenchmarkRunner`. The cancel button SHALL be hidden when no benchmark is running.

#### Scenario: Cancel button visible during execution

- **GIVEN** a benchmark is in progress
- **WHEN** the user views the panel
- **THEN** a "Cancel" button SHALL be visible

#### Scenario: Cancel stops benchmark

- **GIVEN** a benchmark is in progress at run 3 of 10
- **WHEN** the user clicks "Cancel"
- **THEN** the benchmark SHALL be cancelled, the panel SHALL display results from completed runs, and the progress indicator SHALL be hidden

#### Scenario: Cancel button hidden when idle

- **GIVEN** no benchmark is in progress
- **WHEN** the user views the panel
- **THEN** no "Cancel" button SHALL be visible

### Requirement: Sample count display

Every metric card and strategy bar in `BenchmarkPanel` SHALL display the sample count from the corresponding `BenchmarkStatistics.sampleCount()`, formatted as `"n=<count>"`.

#### Scenario: Sample count on metric card

- **GIVEN** a metric with `sampleCount = 10`
- **WHEN** the panel renders the metric card
- **THEN** the card SHALL include the text `"n=10"`

#### Scenario: Strategy bar sample count differs from metric

- **GIVEN** a strategy `"B"` with `sampleCount = 2` (missing from some runs) while metrics have `sampleCount = 5`
- **WHEN** the panel renders
- **THEN** the strategy bar for `"B"` SHALL show `"n=2"` and metric cards SHALL show `"n=5"`

## Invariants

- **UI1**: `BenchmarkPanel` SHALL NOT block the Vaadin UI thread during benchmark execution. All `BenchmarkRunner` calls SHALL be dispatched asynchronously.
- **UI2**: Progress updates SHALL be pushed to the UI via Vaadin's `UI.access()` to ensure thread-safe updates.
- **UI3**: The panel SHALL gracefully handle a `null` or absent `BenchmarkReport` by showing the benchmark configuration controls (button + slider) without metric cards.
- **UI4**: Badge direction (IMPROVED vs. REGRESSED) SHALL account for metric polarity: higher `factSurvivalRate` and `driftAbsorptionRate` are improvements, while higher `contradictionCount` and `majorContradictionCount` are regressions.
