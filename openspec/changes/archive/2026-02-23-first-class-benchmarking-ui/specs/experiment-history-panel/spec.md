## ADDED Requirements

### Requirement: Experiment list from RunHistoryStore

The `ExperimentHistoryPanel` SHALL display a list of past experiments retrieved from `RunHistoryStore.listExperimentReports()`. The list SHALL be sorted by `createdAt` descending (newest first) at all times.

#### Scenario: List rendered on panel open

- **GIVEN** `RunHistoryStore` contains three past experiments
- **WHEN** the `ExperimentHistoryPanel` renders
- **THEN** all three experiments SHALL appear as list entries

#### Scenario: Newest experiment appears first

- **GIVEN** experiments created at T1 < T2 < T3
- **WHEN** the panel renders the list
- **THEN** the experiment from T3 SHALL appear first, T2 second, T1 third

#### Scenario: Empty history shows informational message

- **GIVEN** `RunHistoryStore` contains no experiments
- **WHEN** the panel renders
- **THEN** an informational message (e.g., `"No experiments recorded yet"`) SHALL be displayed rather than an empty list

### Requirement: Experiment summary columns

Each entry in the `ExperimentHistoryPanel` list SHALL display: the experiment name, the creation date formatted as a human-readable date-time, the number of conditions, the number of scenarios, the repetition count, and the primary metric summary (`factSurvivalRate` mean across all cells, formatted as a percentage).

#### Scenario: All summary fields present per entry

- **GIVEN** a past experiment with name `"ablation-2026-02-23"`, created at `2026-02-23T14:30:00`, 4 conditions, 3 scenarios, 5 repetitions, overall factSurvivalRate mean of 0.72
- **WHEN** the panel renders the list entry
- **THEN** the entry SHALL display all six fields: name, date, `"4 conditions"`, `"3 scenarios"`, `"5 reps"`, and `"72%"` (or `"0.72"`) for factSurvivalRate

#### Scenario: Primary metric summary derived from all cells

- **GIVEN** an experiment where cells have factSurvivalRate means of 0.80, 0.60, 0.70, 0.50
- **WHEN** the panel renders the primary metric summary
- **THEN** the displayed value SHALL reflect the aggregate (e.g., mean across cells = 0.65)

### Requirement: Load experiment into comparison view

The `ExperimentHistoryPanel` SHALL provide a "Load" control for each list entry. Activating the "Load" control for an experiment SHALL populate the `ConditionComparisonPanel` with the data from that experiment's `ExperimentReport` and navigate the layout to the comparison view.

#### Scenario: Load control present per entry

- **GIVEN** the experiment history list contains at least one entry
- **WHEN** the user views an entry
- **THEN** a "Load" button or equivalent control SHALL be visible

#### Scenario: Loading an experiment populates comparison view

- **GIVEN** the user clicks "Load" on a past experiment
- **WHEN** the load completes
- **THEN** the `ConditionComparisonPanel` SHALL render the conditions, metrics, and effect sizes from that experiment's `ExperimentReport`

#### Scenario: Loading replaces any previously displayed comparison

- **GIVEN** a comparison view is already showing results from a previous load
- **WHEN** the user loads a different experiment
- **THEN** the comparison view SHALL be fully replaced with the newly loaded experiment's data

### Requirement: Delete experiment

The `ExperimentHistoryPanel` SHALL provide a "Delete" control for each list entry. Activating the "Delete" control SHALL invoke `RunHistoryStore.deleteExperimentReport()` with the experiment's identifier and SHALL remove the entry from the list. Deletion SHALL be idempotent: deleting an experiment that no longer exists (e.g., already deleted via another session) SHALL NOT produce an error visible to the user.

#### Scenario: Delete control present per entry

- **GIVEN** the experiment history list contains at least one entry
- **WHEN** the user views an entry
- **THEN** a "Delete" button or equivalent control SHALL be visible

#### Scenario: Deleting an experiment removes it from the list

- **GIVEN** the list contains experiments A, B, and C
- **WHEN** the user deletes experiment B
- **THEN** experiment B SHALL be removed from the list and experiments A and C SHALL remain

#### Scenario: Deleting already-absent experiment shows no error

- **GIVEN** an experiment has been deleted externally since the list was last loaded
- **WHEN** the user activates "Delete" for that experiment
- **THEN** no error message SHALL be shown to the user and the entry SHALL be removed from the list view

#### Scenario: Delete invokes RunHistoryStore.deleteExperimentReport

- **GIVEN** the user confirms deletion of experiment with id `"exp-abc-123"`
- **WHEN** the delete action executes
- **THEN** `RunHistoryStore.deleteExperimentReport("exp-abc-123")` SHALL be called

### Requirement: List refresh after mutation

After a delete operation, and after a new experiment completes, the `ExperimentHistoryPanel` list SHALL reflect the current state from `RunHistoryStore`. The list SHALL refresh automatically after any mutation rather than requiring a manual page reload.

#### Scenario: List updates after deletion

- **GIVEN** the list shows 3 experiments and the user deletes one
- **WHEN** the deletion completes
- **THEN** the list SHALL show the 2 remaining experiments without requiring a page reload

#### Scenario: List updates after new experiment completes

- **GIVEN** an experiment finishes executing
- **WHEN** the user navigates to the history panel
- **THEN** the newly completed experiment SHALL appear at the top of the list

## Invariants

- **EHP1**: The experiment list MUST always be sorted by `createdAt` descending. Entries MUST NOT appear in insertion order, alphabetical order, or any other order. This sort MUST be enforced on every render and every refresh.
- **EHP2**: Deletion MUST be idempotent. Calling `RunHistoryStore.deleteExperimentReport()` for an experiment identifier that does not exist in the store SHALL succeed silently. The UI MUST NOT surface an error to the user when this occurs.
