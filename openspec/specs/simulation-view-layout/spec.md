### Requirement: SimulationView header row
The header row SHALL display the title "Anchor Drift Simulator" left-aligned. Chat and Benchmark navigation links and the theme toggle button SHALL be right-aligned in the same row.

#### Scenario: Header displays navigation links
- **WHEN** the SimulationView loads
- **THEN** the header contains "Anchor Drift Simulator" on the left and "Chat", "Benchmark" links and the theme toggle on the right

### Requirement: Single-row controls layout
The controls area SHALL be a single row containing the Category Select (160px), Scenario ComboBox (240px), Token Budget field, Max Turns field, and contextual action buttons. The Anchor Injection checkbox SHALL be positioned at the bottom of the left column, beneath the conversation panel.

#### Scenario: Controls render in a single row
- **WHEN** the SimulationView loads
- **THEN** the Category Select, Scenario ComboBox, Token Budget field, Max Turns field, and action buttons appear in a single horizontal row

#### Scenario: Injection toggle positioned below conversation
- **WHEN** the SimulationView loads
- **THEN** the Anchor Injection checkbox appears at the bottom of the left column beneath the conversation panel, not in the controls row

### Requirement: Contextual button visibility
Action buttons SHALL be shown or hidden based on the current SimControlState. Buttons that do not apply to the current state SHALL NOT be rendered.

#### Scenario: IDLE state buttons
- **WHEN** the simulation state is IDLE
- **THEN** only Run and Run History buttons are visible

#### Scenario: RUNNING state buttons
- **WHEN** the simulation state is RUNNING
- **THEN** only Pause and Stop buttons are visible

#### Scenario: PAUSED state buttons
- **WHEN** the simulation state is PAUSED
- **THEN** only Resume, Stop, and Run History buttons are visible

#### Scenario: COMPLETED state buttons
- **WHEN** the simulation state is COMPLETED
- **THEN** only Run and Run History buttons are visible

### Requirement: Right panel tab organization
The right panel TabSheet SHALL contain the following tabs in order: Scenario Details, Context Inspector, Timeline, Knowledge Browser, Results, Manipulation. The Manipulation tab SHALL only be visible when the simulation is paused. A Benchmark tab SHALL NOT appear in the SimulationView.

#### Scenario: Tab order on page load
- **WHEN** the SimulationView loads
- **THEN** the right panel shows tabs: Scenario Details, Context Inspector, Timeline, Knowledge Browser, Results

#### Scenario: Benchmark tab removed
- **WHEN** the SimulationView loads
- **THEN** no Benchmark tab exists in the right panel TabSheet

### Requirement: Scenario Details tab
The right panel SHALL include a "Scenario Details" tab that displays the selected scenario's title, test objective, focus, highlights, and setting text. This tab SHALL be selected by default when no simulation is running.

#### Scenario: Scenario Details selected on load
- **WHEN** a scenario is selected and no simulation is running
- **THEN** the Scenario Details tab is active and displays the scenario information

#### Scenario: Scenario Details updates on selection
- **WHEN** the user selects a different scenario from the ComboBox
- **THEN** the Scenario Details tab content updates to reflect the new scenario

### Requirement: Results tab
The right panel SHALL include a "Results" tab that displays the DriftSummaryPanel content (metrics grid, strategy effectiveness, assertion results). This tab SHALL be auto-selected when a simulation completes.

#### Scenario: Results tab auto-selected on completion
- **WHEN** a simulation completes
- **THEN** the Results tab is automatically selected and displays drift summary metrics

#### Scenario: Conversation remains visible after completion
- **WHEN** a simulation completes and results are displayed
- **THEN** the conversation panel in the left column remains fully visible and scrollable

### Requirement: Tab auto-selection lifecycle
The right panel SHALL auto-select tabs based on simulation lifecycle events: Scenario Details on page load or scenario change, Context Inspector when a run starts, Results on completion, Manipulation on pause.

#### Scenario: Context Inspector selected on run start
- **WHEN** the user clicks Run
- **THEN** the Context Inspector tab is automatically selected

#### Scenario: Manipulation selected on pause
- **WHEN** the simulation is paused
- **THEN** the Manipulation tab becomes visible and is automatically selected

### Requirement: Conversation full height
The conversation panel SHALL occupy the full height of the left column. The only element below the conversation panel SHALL be the Anchor Injection toggle. No other panels SHALL be stacked below the conversation within the left column.

#### Scenario: No panels below conversation
- **WHEN** a simulation completes
- **THEN** the left column contains the conversation panel and the injection toggle, with no drift summary or other panels below
