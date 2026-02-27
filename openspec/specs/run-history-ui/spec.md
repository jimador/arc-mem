## Requirements

### Requirement: Run history panel in SimulationView

`SimulationView` SHALL include a collapsible run history panel that displays all persisted simulation runs. The panel SHALL load runs from `RunHistoryStore.list()` on view entry and support manual refresh.

#### Scenario: Panel displays historical runs
- **WHEN** a user navigates to SimulationView
- **THEN** the run history panel displays all persisted runs sorted by `completedAt` descending

#### Scenario: Panel refreshes on demand
- **WHEN** a user clicks the refresh button on the run history panel
- **THEN** the panel reloads from `RunHistoryStore.list()` and displays current data

### Requirement: Run history filtering

The run history panel SHALL support filtering by scenario ID and injection-enabled status. Filters SHALL be applied client-side on the loaded dataset. The scenario filter SHALL be a dropdown populated from distinct scenario IDs in the loaded runs.

#### Scenario: Filter by scenario
- **WHEN** a user selects "adversarial-contradictory" from the scenario filter
- **THEN** only runs with `scenarioId` equal to "adversarial-contradictory" are displayed

#### Scenario: Filter by injection status
- **WHEN** a user toggles the "injection enabled" filter to ON
- **THEN** only runs with `injectionEnabled == true` are displayed

#### Scenario: Combined filters
- **WHEN** a user selects a scenario AND an injection status
- **THEN** both filters are applied conjunctively

### Requirement: Run history grid columns

The run history grid SHALL display: scenario ID, completion date (formatted), turn count, resilience rate (percentage of turns without contradictions), model ID, and injection status. Each column SHALL be sortable.

#### Scenario: Grid displays run metadata
- **GIVEN** a persisted run with scenarioId "anchor-drift", 10 turns, 80% resilience, model "gpt-4.1-mini", injection ON
- **WHEN** the run appears in the grid
- **THEN** all metadata columns are populated with the correct values

### Requirement: Navigate to run inspector

Clicking a run in the history panel SHALL navigate to `RunInspectorView` with the selected run's `runId` as a query parameter.

#### Scenario: Single run inspection
- **WHEN** a user clicks a run row in the history panel
- **THEN** the browser navigates to `/run?runId={selectedRunId}`

### Requirement: Compare two runs

The history panel SHALL support selecting two runs for comparison. The comparison action SHALL navigate to `RunInspectorView` with both run IDs as query parameters.

#### Scenario: Select two runs for comparison
- **WHEN** a user selects two runs via checkboxes and clicks "Compare"
- **THEN** the browser navigates to `/run?runId={firstRunId}&compare={secondRunId}`

#### Scenario: Compare button disabled with wrong selection count
- **WHEN** fewer than 2 or more than 2 runs are selected
- **THEN** the "Compare" button is disabled

### Requirement: Delete run from history

The history panel SHALL provide a delete action per run. Deletion SHALL call `RunHistoryStore.delete(runId)` and remove the row from the grid without full reload.

#### Scenario: Delete a run
- **WHEN** a user clicks delete on a run and confirms
- **THEN** the run is removed from the store and disappears from the grid

#### Scenario: Delete requires confirmation
- **WHEN** a user clicks delete on a run
- **THEN** a confirmation dialog appears before the delete executes
