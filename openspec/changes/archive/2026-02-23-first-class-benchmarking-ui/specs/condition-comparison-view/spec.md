## ADDED Requirements

### Requirement: Side-by-side metric cards per condition

The `ConditionComparisonPanel` SHALL render one metric card per condition per metric. Each card SHALL display the condition name, the metric name, the mean formatted to 2 decimal places, the standard deviation formatted to 2 decimal places, and the sample count formatted as `"n=<count>"`. Cards for the same metric SHALL be arranged side by side horizontally for direct visual comparison.

#### Scenario: Metric card content for a single condition

- **GIVEN** an `ExperimentReport` containing a cell for `FULL_ANCHORS` with `factSurvivalRate` mean=0.85, stddev=0.07, sampleCount=10
- **WHEN** the `ConditionComparisonPanel` renders
- **THEN** a card for `FULL_ANCHORS / factSurvivalRate` SHALL display `"0.85"`, `"+/- 0.07"`, and `"n=10"`

#### Scenario: Cards for all conditions appear side by side

- **GIVEN** an `ExperimentReport` with four conditions and one metric
- **WHEN** the panel renders
- **THEN** four cards SHALL be displayed in a single row, one per condition

#### Scenario: All metrics produce card rows

- **GIVEN** an `ExperimentReport` with two conditions and six metrics
- **WHEN** the panel renders
- **THEN** six rows of two cards each SHALL be displayed (one row per metric)

### Requirement: Delta badges between condition pairs

The `ConditionComparisonPanel` SHALL display a delta badge between each adjacent condition pair for each metric. The badge SHALL show the signed difference in mean (e.g., `"+12.3%"` or `"-8.5%"`). Delta badges SHALL use the metric's polarity to determine visual treatment: a positive delta is displayed as favorable (green) for survival-oriented metrics (`factSurvivalRate`, `driftAbsorptionRate`) and as unfavorable (red) for count-based metrics (`contradictionCount`, `majorContradictionCount`).

#### Scenario: Positive delta on survival metric shown as favorable

- **GIVEN** `FULL_ANCHORS` has `factSurvivalRate` mean=0.85 and `NO_ANCHORS` has mean=0.65
- **WHEN** the delta badge between them renders
- **THEN** the badge SHALL show `"+0.20"` (or `"+20.0%"`) with a favorable (green) visual style

#### Scenario: Positive delta on contradiction metric shown as unfavorable

- **GIVEN** `NO_ANCHORS` has `contradictionCount` mean=3.5 and `FULL_ANCHORS` has mean=1.2
- **WHEN** the delta badge renders
- **THEN** the badge SHALL show `"+2.3"` (or similar) with an unfavorable (red) visual style

#### Scenario: Near-zero delta shown as neutral

- **GIVEN** two conditions differ in a metric mean by less than 0.01
- **WHEN** the delta badge renders
- **THEN** the badge SHALL use a neutral visual style

### Requirement: Cohen's d effect size with interpretive labels

The `ConditionComparisonPanel` SHOULD display Cohen's d effect size for each condition pair and each metric. The effect size SHALL be sourced from `ExperimentReport.effectSizes()`. Each effect size SHALL be displayed with an interpretive label: `negligible` (|d| < 0.2), `small` (0.2 <= |d| < 0.5), `medium` (0.5 <= |d| < 0.8), `large` (|d| >= 0.8). A low-confidence warning SHOULD be shown when the sample count is below 10.

#### Scenario: Large effect size labeled correctly

- **GIVEN** a metric pair with Cohen's d = 1.2
- **WHEN** the `ConditionComparisonPanel` renders the effect size
- **THEN** the label `"large"` SHALL be displayed alongside the numeric value

#### Scenario: Negligible effect size labeled correctly

- **GIVEN** a metric pair with Cohen's d = 0.1
- **WHEN** the `ConditionComparisonPanel` renders the effect size
- **THEN** the label `"negligible"` SHALL be displayed

#### Scenario: Low-confidence warning shown for small samples

- **GIVEN** a metric pair with sampleCount = 4 (below threshold of 10)
- **WHEN** the effect size renders
- **THEN** a low-confidence warning SHALL be displayed alongside the effect size label

#### Scenario: No low-confidence warning for adequate samples

- **GIVEN** a metric pair with sampleCount = 15
- **WHEN** the effect size renders
- **THEN** no low-confidence warning SHALL be displayed

### Requirement: Condition-metric heatmap matrix

The `ConditionComparisonPanel` SHOULD display a heatmap matrix with conditions as rows and metrics as columns. Each cell SHALL display the mean value for that condition-metric pair. Cell background color SHALL be metric-aware: for `factSurvivalRate` and `driftAbsorptionRate`, higher values MUST map to green; for `contradictionCount` and `majorContradictionCount`, higher values MUST map to red. Every cell SHALL include a text label in addition to the color, so that the matrix is interpretable without relying on color alone.

#### Scenario: Heatmap renders correct dimensions

- **GIVEN** an `ExperimentReport` with 4 conditions and 6 metrics
- **WHEN** the heatmap matrix renders
- **THEN** it SHALL display a 4-row by 6-column grid

#### Scenario: Survival metric cell colored green for high value

- **GIVEN** `FULL_ANCHORS / factSurvivalRate` has mean=0.90 (highest across conditions)
- **WHEN** the heatmap cell renders
- **THEN** the cell SHALL have a green background (highest intensity in its column)

#### Scenario: Contradiction metric cell colored red for high value

- **GIVEN** `NO_ANCHORS / contradictionCount` has mean=4.5 (highest across conditions)
- **WHEN** the heatmap cell renders
- **THEN** the cell SHALL have a red background (highest intensity in its column)

#### Scenario: Text label present in every heatmap cell

- **GIVEN** any heatmap cell
- **WHEN** the cell renders
- **THEN** the numeric mean value SHALL be displayed as text within the cell, independent of color

### Requirement: Per-strategy effectiveness comparison table

The `ConditionComparisonPanel` SHOULD render a per-strategy effectiveness comparison table showing each strategy's mean effectiveness across all conditions. The table SHALL have strategies as rows and conditions as columns. Each cell SHALL display the mean effectiveness for that strategy-condition pair.

#### Scenario: Strategy table renders all strategies

- **GIVEN** an `ExperimentReport` with strategy statistics for `SUBTLE_REFRAME`, `DIRECT_CONTRADICTION`, and `AUTHORITY_APPEAL` across two conditions
- **WHEN** the strategy table renders
- **THEN** all three strategies SHALL appear as rows with a column for each condition

#### Scenario: Strategy with no data for a condition shows placeholder

- **GIVEN** a strategy not present in one condition's results
- **WHEN** the strategy table renders
- **THEN** the cell SHALL display `"—"` or `"n/a"` rather than blank or an error

## Invariants

- **CCV1**: Cell background color in the heatmap MUST be metric-aware. Applying green to high `contradictionCount` values is a defect. The metric polarity mapping (`factSurvivalRate` -> higher=green, `contradictionCount` -> higher=red, `driftAbsorptionRate` -> higher=green, `majorContradictionCount` -> higher=red) MUST be applied consistently.
- **CCV2**: Color MUST NOT be the sole indicator of value in any heatmap cell. Every heatmap cell MUST include a visible text label so that colorblind users can interpret the matrix without relying on color differentiation.
