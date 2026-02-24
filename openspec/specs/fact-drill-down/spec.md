## ADDED Requirements

### Requirement: Drill-down trigger from metric card

The `FactDrillDownPanel` SHALL be accessible from any metric card in the `ConditionComparisonPanel`. Each metric card SHALL provide a UI affordance (e.g., an expand button or a clickable row) that, when activated, opens the drill-down view for that metric. The drill-down view SHALL replace or augment the metric card area with per-fact detail.

#### Scenario: Expand control visible on every metric card

- **GIVEN** the `ConditionComparisonPanel` is rendered with at least one metric
- **WHEN** the user views any metric card
- **THEN** an expand or drill-down control SHALL be visible on the card

#### Scenario: Activating expand opens per-fact view

- **GIVEN** the user clicks the expand control on the `factSurvivalRate` metric card
- **WHEN** the `FactDrillDownPanel` renders
- **THEN** per-fact verdict rows SHALL be displayed for the `factSurvivalRate` metric

#### Scenario: Drill-down for non-survival metric is accessible

- **GIVEN** the user clicks the expand control on the `contradictionCount` metric card
- **WHEN** the `FactDrillDownPanel` renders
- **THEN** per-fact rows for `contradictionCount` SHALL be displayed

### Requirement: Per-fact survival count display

For the expanded metric, the `FactDrillDownPanel` SHALL display one row per fact. Each row SHALL include the fact text and a survival count per condition formatted as `"<survived>/<total> in <CONDITION>"`. The survival count SHALL be derived from the per-run `ScoringResult` verdict details within the relevant cell `BenchmarkReport`s.

#### Scenario: Survival count format per condition

- **GIVEN** fact `"The capital of France is Paris"` survived 5 of 5 runs in `FULL_ANCHORS` and 2 of 5 runs in `NO_ANCHORS`
- **WHEN** the `FactDrillDownPanel` renders the row for this fact
- **THEN** the row SHALL display `"5/5 in FULL_ANCHORS"` and `"2/5 in NO_ANCHORS"`

#### Scenario: All facts appear as rows

- **GIVEN** a metric with verdicts recorded for 8 distinct facts
- **WHEN** the drill-down view renders
- **THEN** 8 rows SHALL be displayed, one per fact

#### Scenario: Facts with zero survival shown correctly

- **GIVEN** a fact that was never scored as surviving in any run within a condition
- **WHEN** the drill-down view renders
- **THEN** the row SHALL show `"0/<total> in <CONDITION>"` rather than blank or an error

### Requirement: First-drift turn per condition

Each per-fact row in the `FactDrillDownPanel` SHALL display the first turn at which drift was detected for that fact within each condition. The value SHALL be sourced from the per-run `ScoringResult` details in the cell `BenchmarkReport`. When no drift occurred across all runs for a condition, the cell SHALL display `"no drift"`.

#### Scenario: First-drift turn displayed per condition

- **GIVEN** fact X drifted first at turn 4 in `NO_ANCHORS` (earliest across runs) and had no drift in `FULL_ANCHORS`
- **WHEN** the drill-down row renders
- **THEN** the `NO_ANCHORS` column SHALL show `"turn 4"` and the `FULL_ANCHORS` column SHALL show `"no drift"`

#### Scenario: Earliest drift turn shown when multiple runs drifted

- **GIVEN** fact X drifted at turn 6 in run 1 and turn 3 in run 2 within `FLAT_AUTHORITY`
- **WHEN** the drill-down row renders
- **THEN** the `FLAT_AUTHORITY` first-drift column SHALL display `"turn 3"` (the minimum across runs)

#### Scenario: No drift across all conditions shown correctly

- **GIVEN** a fact that was never scored as drifted across any run in any condition
- **WHEN** the drill-down row renders
- **THEN** every condition's first-drift cell SHALL display `"no drift"`

### Requirement: Data sourced from per-run ScoringResult verdict details

The `FactDrillDownPanel` SHALL source all per-fact data exclusively from the `ScoringResult` strategy and verdict details within the `BenchmarkReport`s stored in the `ExperimentReport` cells. The panel SHALL NOT recompute survival or drift data independently.

#### Scenario: Drill-down reflects stored BenchmarkReport data

- **GIVEN** an `ExperimentReport` loaded from `RunHistoryStore`
- **WHEN** the drill-down view renders for a selected metric
- **THEN** the per-fact survival counts SHALL match the `ScoringResult` verdict details stored in the corresponding `BenchmarkReport` cells

#### Scenario: Drill-down available for loaded historical experiment

- **GIVEN** the user has loaded a past experiment from the experiment history panel
- **WHEN** the user activates the drill-down on any metric card in the comparison view
- **THEN** per-fact rows SHALL render from the stored data without requiring a re-run

### Requirement: Collapse drill-down to return to summary

The `FactDrillDownPanel` SHALL provide a control to collapse the per-fact view and return to the metric card summary. When collapsed, the metric card SHALL return to its normal state.

#### Scenario: Collapse control visible in drill-down view

- **GIVEN** the drill-down view is open for a metric
- **WHEN** the user views the expanded area
- **THEN** a collapse or close control SHALL be visible

#### Scenario: Collapsing restores metric card

- **GIVEN** the drill-down view is open for `factSurvivalRate`
- **WHEN** the user clicks the collapse control
- **THEN** the per-fact rows SHALL be hidden and the metric card summary SHALL be visible again

## Invariants

- **FDD1**: Drill-down MUST be available for every metric displayed in the `ConditionComparisonPanel`. There SHALL be no metric card that lacks a drill-down control. If per-fact data is unavailable for a metric, the drill-down view SHALL display an informational message rather than being suppressed.
