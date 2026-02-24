## ADDED Requirements

### Requirement: Cell-level progress display

The `ExperimentProgressPanel` SHALL display the current cell label formatted as `"CONDITION x scenario-id"` (e.g., `"FLAT_AUTHORITY x adversarial-contradictory"`) while an experiment is executing. The cell label SHALL update each time a new cell begins execution.

#### Scenario: Cell label shown during execution

- **GIVEN** an experiment is executing the cell for `FLAT_AUTHORITY` x `adversarial-contradictory`
- **WHEN** the `ExperimentProgressPanel` renders
- **THEN** the label `"FLAT_AUTHORITY x adversarial-contradictory"` SHALL be visible

#### Scenario: Cell label updates on cell transition

- **GIVEN** the cell for `FULL_ANCHORS x baseline-neutral` has completed
- **WHEN** execution advances to `NO_ANCHORS x baseline-neutral`
- **THEN** the cell label SHALL update to `"NO_ANCHORS x baseline-neutral"`

### Requirement: Run-within-cell progress display

The `ExperimentProgressPanel` SHALL display the current run number within the active cell, formatted as `"Run <current>/<total>"` (e.g., `"Run 3/5"`). This counter SHALL increment after each individual run within the cell completes.

#### Scenario: Run counter shown with correct format

- **GIVEN** an experiment cell with 5 repetitions is executing
- **WHEN** the third run completes
- **THEN** the display SHALL show `"Run 3/5"`

#### Scenario: Run counter resets at cell boundary

- **GIVEN** cell 1 completed with 5/5 runs
- **WHEN** cell 2 begins its first run
- **THEN** the display SHALL show `"Run 1/5"` for the new cell

### Requirement: Overall progress bar

The `ExperimentProgressPanel` SHALL display a progress bar reflecting the fraction of total cells completed. The progress bar value SHALL be computed as `completedCells / totalCells`. The bar SHALL update after each cell (not each run) completes.

#### Scenario: Progress bar reflects cell completion ratio

- **GIVEN** an experiment with 12 cells total and 3 cells completed
- **WHEN** the progress panel renders
- **THEN** the progress bar SHALL indicate 25% completion

#### Scenario: Progress bar reaches 100% on completion

- **GIVEN** all cells in an experiment have completed
- **WHEN** the progress panel renders
- **THEN** the progress bar SHALL display 100%

#### Scenario: Progress never regresses

- **GIVEN** the progress bar is at 50% (6/12 cells complete)
- **WHEN** the next cell begins execution (before completing)
- **THEN** the progress bar SHALL remain at 50% and SHALL NOT decrease

### Requirement: Estimated time remaining

The `ExperimentProgressPanel` SHALL display an estimated time remaining based on elapsed wall-clock time and the number of cells completed. The estimate SHALL be computed as `(elapsedTime / completedCells) * remainingCells` and SHALL be displayed in a human-readable format (e.g., `"~4m 30s remaining"`). The estimate SHOULD be hidden until at least one cell has completed.

#### Scenario: ETA hidden before first cell completes

- **GIVEN** an experiment has just started and zero cells have completed
- **WHEN** the user views the progress panel
- **THEN** no estimated time remaining SHALL be displayed

#### Scenario: ETA computed and displayed after first cell

- **GIVEN** one cell completed in 60 seconds and 11 cells remain
- **WHEN** the progress panel updates
- **THEN** the estimated time remaining SHALL be displayed as approximately `"~11m 0s remaining"`

#### Scenario: ETA updates as more cells complete

- **GIVEN** ETA was previously displayed
- **WHEN** another cell completes
- **THEN** the ETA SHALL be recomputed and the display SHALL update

### Requirement: Completed-cell log

The `ExperimentProgressPanel` SHOULD display a scrollable log of completed cells. Each log entry SHALL include the cell label (condition x scenario-id) and the `factSurvivalRate` mean for that cell, formatted as a percentage.

#### Scenario: Log entry added on cell completion

- **GIVEN** the cell `FULL_ANCHORS x adversarial-contradictory` completes with `factSurvivalRate` mean of 0.80
- **WHEN** the progress panel updates
- **THEN** the log SHALL contain an entry for `"FULL_ANCHORS x adversarial-contradictory"` showing `"80%"` (or `"0.80"`) for `factSurvivalRate`

#### Scenario: Log entries are ordered chronologically

- **GIVEN** cells complete in order: cell A, then cell B, then cell C
- **WHEN** the user views the completed-cell log
- **THEN** entries SHALL appear in the order A, B, C (oldest first)

#### Scenario: Log is scrollable when entries exceed visible area

- **GIVEN** more than 10 cells have completed
- **WHEN** the user views the completed-cell log
- **THEN** the log SHALL be scrollable and all entries SHALL be accessible

### Requirement: Cancel Experiment button

The `ExperimentProgressPanel` SHALL display a "Cancel Experiment" button while an experiment is executing. Clicking the button SHALL invoke `ExperimentRunner.cancel()`. Cancellation SHALL complete the current cell before halting. The button SHALL be hidden when no experiment is running.

#### Scenario: Cancel button visible during execution

- **GIVEN** an experiment is actively executing
- **WHEN** the user views the progress panel
- **THEN** a "Cancel Experiment" button SHALL be visible and enabled

#### Scenario: Cancel invokes ExperimentRunner.cancel()

- **GIVEN** an experiment is executing
- **WHEN** the user clicks "Cancel Experiment"
- **THEN** `ExperimentRunner.cancel()` SHALL be called

#### Scenario: Partial results displayed on cancellation

- **GIVEN** an experiment was cancelled after 4 of 12 cells completed
- **WHEN** the cancellation completes
- **THEN** the comparison view SHALL display results for the 4 completed cells and the progress panel SHALL be replaced by the comparison view

#### Scenario: Cancel button hidden when idle

- **GIVEN** no experiment is executing
- **WHEN** the user views the benchmark layout
- **THEN** no "Cancel Experiment" button SHALL be visible

### Requirement: Thread-safe UI updates via Vaadin push

All progress updates from `ExperimentRunner` callbacks SHALL be applied to the Vaadin UI via `UI.access()` to ensure thread safety. The `ExperimentProgressPanel` SHALL NOT block the Vaadin UI thread during experiment execution.

#### Scenario: Progress update dispatched via UI.access

- **GIVEN** an experiment run completion event arrives on a background thread
- **WHEN** the progress panel processes the event
- **THEN** the UI update SHALL be dispatched through `UI.access()` and SHALL NOT throw a threading exception

## Invariants

- **EPM1**: The overall progress bar value MUST NOT decrease during an experiment. Once a cell is counted as complete, the progress fraction SHALL only increase or stay the same.
- **EPM2**: Cancelling an experiment MUST produce a viewable partial result. The comparison view SHALL render with whatever cells completed before cancellation. An experiment with zero completed cells MAY display an empty comparison view with an informational message.
