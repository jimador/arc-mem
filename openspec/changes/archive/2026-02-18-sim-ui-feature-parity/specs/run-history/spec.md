## ADDED Requirements

### Requirement: SimulationRunStore

A `SimulationRunStore` SHALL provide in-memory storage for completed simulation runs using a 50-entry LRU (Least Recently Used) eviction policy. When the store reaches capacity and a new run is added, the least recently accessed entry SHALL be evicted. The store SHALL provide methods: `save(SimulationRunRecord)`, `get(String runId) -> Optional<SimulationRunRecord>`, `list() -> List<SimulationRunRecord>`, and `delete(String runId)`.

#### Scenario: Store run on completion
- **WHEN** a simulation completes
- **THEN** `SimulationService` saves a `SimulationRunRecord` to the store

#### Scenario: LRU eviction at capacity
- **WHEN** the store contains 50 entries and a new run is saved
- **THEN** the least recently accessed entry is evicted and the new entry is stored

#### Scenario: Delete run
- **WHEN** `delete(runId)` is called for an existing run
- **THEN** the run is removed from the store and subsequent `get(runId)` returns empty

### Requirement: SimulationRunRecord

A `SimulationRunRecord` record SHALL capture: `runId` (String, UUID), `scenarioId` (String), `startedAt` (Instant), `completedAt` (Instant), `turnSnapshots` (List of per-turn snapshot records containing turn number, anchor state, verdicts, context trace, intervention log), `resilienceReport` (aggregate metrics matching DriftSummaryPanel stats), `interventionCount` (int), `finalAnchorState` (List<Anchor>), and `injectionEnabled` (boolean). The record SHALL be serializable for potential future persistence.

#### Scenario: Record captures all turns
- **WHEN** a 10-turn simulation completes
- **THEN** the SimulationRunRecord contains 10 turn snapshots

#### Scenario: Record includes resilience report
- **WHEN** a simulation with adversarial turns completes
- **THEN** the record's resilienceReport contains survival rate, contradiction count, and other drift metrics

### Requirement: RunHistoryDialog

A `RunHistoryDialog` Vaadin dialog SHALL list all stored runs with columns: scenario name, date (formatted), turn count, resilience percentage, and intervention count. The dialog SHALL provide actions per row: Inspect (opens RunInspectorView), Delete (removes from store with confirmation), and Compare (checkbox-based selection of exactly 2 runs for side-by-side comparison). The dialog SHALL be accessible from the SimulationView via a "History" button.

#### Scenario: Dialog lists stored runs
- **WHEN** the user clicks the "History" button on SimulationView
- **THEN** the RunHistoryDialog opens showing all stored runs with their metadata

#### Scenario: Inspect navigates to inspector
- **WHEN** the user clicks "Inspect" on a run in the dialog
- **THEN** the browser navigates to `/run?id={runId}` opening the RunInspectorView

#### Scenario: Compare two runs
- **WHEN** the user selects exactly 2 runs via checkboxes and clicks "Compare"
- **THEN** the browser navigates to `/run?id={runId1}&compare={runId2}`

#### Scenario: Delete with confirmation
- **WHEN** the user clicks "Delete" on a run
- **THEN** a confirmation dialog appears
- **AND** upon confirmation, the run is removed from the store and the list refreshes

### Requirement: RunInspectorView

A `RunInspectorView` SHALL be registered at `@Route("run")` and accept a `runId` query parameter. The view SHALL display a sidebar listing all turns and a main content area with tabbed panels. Tabs SHALL include: Conversation (full conversation replay), Prompt (system and user prompts for the selected turn), Anchors (anchor state at the selected turn), Drift (verdicts and drift metrics), Attack (attack strategy and target fact details), and Compaction (compaction details if applicable). Selecting a turn in the sidebar SHALL update all tabs.

#### Scenario: View loads with run data
- **WHEN** the user navigates to `/run?id={runId}` for a valid run
- **THEN** the RunInspectorView loads and displays the run's sidebar with all turns

#### Scenario: Turn selection updates tabs
- **WHEN** the user clicks turn 5 in the sidebar
- **THEN** all tabs update to display data from turn 5 of the recorded run

#### Scenario: Invalid run ID shows error
- **WHEN** the user navigates to `/run?id=nonexistent`
- **THEN** the view displays an error message "Run not found"

### Requirement: In-run anchor diff

The RunInspectorView SHALL support comparing anchor state between two turns within the same run. The user SHALL be able to select two turns and see a diff view showing: added anchors, removed anchors, rank changes, authority changes, and pin status changes. The diff SHALL be accessible from the Anchors tab.

#### Scenario: Anchor diff between turns
- **WHEN** the user selects turns 3 and 8 for anchor comparison
- **THEN** the diff view shows anchors added between turns 3-8, removed anchors, and anchors with rank/authority changes

### Requirement: Cross-run comparison

The RunInspectorView SHALL support a `compare` query parameter. When `?compare={runId2}` is provided alongside the primary `id`, the view SHALL display a side-by-side comparison layout. The comparison SHALL align turns by number and highlight differences in verdicts, anchor counts, and drift events between the two runs.

#### Scenario: Cross-run comparison layout
- **WHEN** the user navigates to `/run?id={run1}&compare={run2}`
- **THEN** the view displays both runs side by side with turns aligned

#### Scenario: Difference highlighting
- **WHEN** run1 turn 5 has CONFIRMED verdicts and run2 turn 5 has CONTRADICTED verdicts
- **THEN** the comparison view highlights the verdict difference for turn 5
