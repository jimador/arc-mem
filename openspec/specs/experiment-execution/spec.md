## ADDED Requirements

### Requirement: ExperimentDefinition record

The system SHALL provide an `ExperimentDefinition` record in the `sim.benchmark` package containing: `name` (String, non-null), `conditions` (List of `AblationCondition`, non-empty), `scenarioIds` (List of String, non-empty), `repetitionsPerCell` (int, >= 2), and `evaluatorModel` (Optional String, for evaluator model override). The record SHALL throw `IllegalArgumentException` if `conditions` is empty, `scenarioIds` is empty, or `repetitionsPerCell < 2`.

#### Scenario: Valid ExperimentDefinition creation

- **WHEN** an `ExperimentDefinition` is created with name "ablation-test", 2 conditions, 3 scenario IDs, and `repetitionsPerCell = 5`
- **THEN** the record SHALL be successfully constructed with all fields accessible

#### Scenario: Empty conditions list rejected

- **WHEN** an `ExperimentDefinition` is created with an empty conditions list
- **THEN** the constructor SHALL throw `IllegalArgumentException`

#### Scenario: Repetitions below minimum rejected

- **WHEN** an `ExperimentDefinition` is created with `repetitionsPerCell = 1`
- **THEN** the constructor SHALL throw `IllegalArgumentException` with a message indicating the minimum is 2

#### Scenario: Evaluator model override is optional

- **WHEN** an `ExperimentDefinition` is created without specifying `evaluatorModel`
- **THEN** the `evaluatorModel` field SHALL be `Optional.empty()`

### Requirement: Matrix execution

The system SHALL provide an `ExperimentRunner` service in the `sim.benchmark` package. The service SHALL execute `conditions.size() x scenarios.size() x repetitionsPerCell` total simulation runs, organized into cells. One cell SHALL represent one condition paired with one scenario, executed for `repetitionsPerCell` runs. The total number of cells SHALL equal `conditions.size() x scenarios.size()`.

#### Scenario: 2 conditions x 3 scenarios x 5 repetitions

- **GIVEN** an `ExperimentDefinition` with 2 conditions, 3 scenarios, and `repetitionsPerCell = 5`
- **WHEN** the experiment executes to completion
- **THEN** exactly 6 cells SHALL be executed, each containing 5 runs, for a total of 30 simulation runs

#### Scenario: Each cell corresponds to one condition-scenario pair

- **GIVEN** conditions [FULL_ANCHORS, NO_ANCHORS] and scenarios ["drift-1", "drift-2"]
- **WHEN** the experiment executes
- **THEN** 4 cells SHALL be produced: (FULL_ANCHORS, "drift-1"), (FULL_ANCHORS, "drift-2"), (NO_ANCHORS, "drift-1"), (NO_ANCHORS, "drift-2")

### Requirement: Cell execution order

Cells SHALL execute sequentially (one at a time). Within a cell, individual runs SHALL execute sequentially, consistent with the existing `BenchmarkRunner` invariant BR3. The system SHALL NOT execute multiple cells or runs in parallel.

#### Scenario: Sequential cell execution

- **GIVEN** an experiment with 4 cells
- **WHEN** the experiment executes
- **THEN** cell 2 SHALL NOT begin until cell 1 has fully completed (all runs finished and report aggregated)

### Requirement: Per-cell aggregation

Each cell SHALL produce a `BenchmarkReport` via the existing `BenchmarkAggregator`. The cell's `BenchmarkReport` SHALL contain statistics aggregated from the `repetitionsPerCell` runs within that cell. The cell SHALL be identified by a key combining the condition name and scenario ID.

#### Scenario: Cell produces BenchmarkReport

- **GIVEN** a cell with condition `FLAT_AUTHORITY` and scenario "adversarial-drift" with 5 repetitions
- **WHEN** all 5 runs in the cell complete
- **THEN** a `BenchmarkReport` SHALL be produced with `runCount = 5` and statistics computed from those 5 runs

#### Scenario: Cell key identifies condition-scenario pair

- **GIVEN** a cell with condition `NO_ANCHORS` and scenario "baseline-stability"
- **WHEN** the cell report is stored in the experiment report
- **THEN** it SHALL be keyed by a combination of "NO_ANCHORS" and "baseline-stability"

### Requirement: Experiment-level cancellation

The system SHALL support cancellation of an in-progress experiment. When cancellation is requested, the `ExperimentRunner` SHALL complete the currently executing cell (including its current run) before stopping. The system SHALL NOT start any subsequent cells after cancellation is acknowledged. Partial results from all completed cells SHALL be included in the `ExperimentReport`. The report's `cancelled` field SHALL be `true`.

#### Scenario: Cancel mid-experiment preserves completed cells

- **GIVEN** an experiment with 6 cells, currently executing cell 3
- **WHEN** cancellation is requested during cell 3's execution
- **THEN** cell 3 SHALL complete fully (all remaining runs in the cell finish)
- **AND** cells 4, 5, 6 SHALL NOT execute
- **AND** the `ExperimentReport` SHALL contain results for cells 1, 2, and 3
- **AND** `report.cancelled()` SHALL be `true`

#### Scenario: Cancellation before first cell completes

- **GIVEN** an experiment with 4 cells, currently executing cell 1
- **WHEN** cancellation is requested during cell 1
- **THEN** cell 1 SHALL complete
- **AND** the `ExperimentReport` SHALL contain results for cell 1 only

### Requirement: Progress reporting

The `ExperimentRunner` SHALL report progress at both cell and run granularity via a callback. Progress reports SHALL include: current cell index (1-based), total cell count, condition name, scenario ID, current run index within the cell (1-based), and total repetitions per cell. The progress format SHALL be: "Cell {n}/{total}: {conditionName} x {scenarioId}, Run {r}/{reps}".

#### Scenario: Progress reported at run granularity

- **GIVEN** an experiment with 2 conditions, 2 scenarios, and 3 repetitions (4 cells, 12 total runs)
- **WHEN** cell 2, run 2 completes
- **THEN** the progress callback SHALL be invoked with cell index 2, total cells 4, and run index 2 of 3

#### Scenario: Progress format matches specification

- **GIVEN** cell 3 of 6 with condition "FLAT_AUTHORITY" and scenario "adversarial-drift", run 2 of 5
- **WHEN** the progress is reported
- **THEN** the progress message SHALL be "Cell 3/6: FLAT_AUTHORITY x adversarial-drift, Run 2/5"

### Requirement: OTEL experiment span

Experiment execution SHALL emit an OTEL span named `experiment.run` with the following attributes: `experiment.name` (experiment name), `experiment.condition_count` (number of conditions), `experiment.scenario_count` (number of scenarios), `experiment.total_cells` (total number of cells), and `experiment.repetitions` (repetitions per cell).

#### Scenario: Experiment span emitted on completion

- **GIVEN** an experiment named "ablation-v1" with 4 conditions, 3 scenarios, and 5 repetitions
- **WHEN** the experiment completes
- **THEN** an OTEL span named `experiment.run` SHALL be emitted with attributes `experiment.name = "ablation-v1"`, `experiment.condition_count = 4`, `experiment.scenario_count = 3`, `experiment.total_cells = 12`, `experiment.repetitions = 5`

### Requirement: OTEL cell span

Each cell execution SHALL emit an OTEL span named `experiment.cell` as a child of the `experiment.run` span. The cell span SHALL include attributes: `cell.condition` (condition name), `cell.scenario_id` (scenario ID), `cell.run_count` (repetitions), and `cell.index` (1-based cell index).

#### Scenario: Cell span emitted for each cell

- **GIVEN** an experiment with 4 cells
- **WHEN** cell 2 (condition "NO_ANCHORS", scenario "drift-1") completes
- **THEN** an OTEL span named `experiment.cell` SHALL be emitted as a child of the `experiment.run` span with attributes `cell.condition = "NO_ANCHORS"`, `cell.scenario_id = "drift-1"`, `cell.run_count = 5`, `cell.index = 2`

### Requirement: Condition application per cell

Before each cell begins execution, the `ExperimentRunner` SHALL apply the cell's `AblationCondition` to configure the simulation for that condition. The condition SHALL be passed to `BenchmarkRunner` for application before each run in the cell. When the cell completes, the condition context SHALL be cleared to avoid leaking into subsequent cells.

#### Scenario: Different cells use different conditions

- **GIVEN** two consecutive cells: cell 1 with `FULL_ANCHORS` and cell 2 with `NO_ANCHORS`
- **WHEN** cell 1 completes and cell 2 begins
- **THEN** the `NO_ANCHORS` condition SHALL be applied before cell 2's runs start
- **AND** no configuration from `FULL_ANCHORS` SHALL leak into cell 2

### Requirement: ExperimentReport assembly

After all cells complete (or upon cancellation), the `ExperimentRunner` SHALL assemble an `ExperimentReport` containing: all cell-level `BenchmarkReport` records keyed by condition-scenario pair, the cross-condition effect size matrix (computed by `EffectSizeCalculator`), per-strategy effectiveness deltas, total duration, and a `cancelled` flag. The report SHALL be returned to the caller.

#### Scenario: Complete experiment produces full report

- **GIVEN** an experiment with 4 conditions, 2 scenarios, and 5 repetitions that completes without cancellation
- **WHEN** the experiment finishes
- **THEN** the `ExperimentReport` SHALL contain 8 cell-level `BenchmarkReport` records, an effect size matrix, strategy deltas, and `cancelled = false`

#### Scenario: Cancelled experiment produces partial report

- **GIVEN** an experiment cancelled after 3 of 8 cells
- **WHEN** the report is assembled
- **THEN** the `ExperimentReport` SHALL contain 3 cell-level `BenchmarkReport` records
- **AND** the effect size matrix SHALL be computed from the 3 completed cells only
- **AND** `cancelled` SHALL be `true`

## Invariants

- **EX1**: Each run within the experiment matrix SHALL use a unique, isolated `contextId` following the `sim-{uuid}` pattern. No two runs SHALL share a context.
- **EX2**: Cell execution SHALL be strictly sequential. No two cells SHALL execute concurrently.
- **EX3**: The `ExperimentRunner` SHALL NOT modify `SimulationService` state beyond what `BenchmarkRunner` already does. All condition application flows through `BenchmarkRunner`.
- **EX4**: Cancellation SHALL always produce a valid `ExperimentReport` with at least one completed cell.
