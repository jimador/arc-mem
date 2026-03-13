### Requirement: ConversationPanel extraction
`ConversationPanel` SHALL be a self-contained Vaadin `VerticalLayout` extracted from `SimulationView`. It SHALL expose `appendTurn(TurnSnapshot)` and `reset()` as its public API. The panel SHALL occupy the full height of the left column with no sibling panels stacked below it.

#### Scenario: Panel occupies full left column
- **WHEN** a simulation is running or completed
- **THEN** the ConversationPanel is the sole content of the left column with flex-grow filling available height

#### Scenario: Append turn
- **WHEN** `appendTurn(TurnSnapshot)` is called
- **THEN** a new conversation bubble is rendered and the panel scrolls to the latest entry

#### Scenario: Reset
- **WHEN** `reset()` is called
- **THEN** all conversation bubbles are cleared and the placeholder message is shown

### Requirement: Injection state tags per turn

Each turn bubble SHALL display a tag indicating the injection state for that turn. The tag SHALL read "INJ ON" with cyan styling (`--arc-accent-cyan`) when injection was enabled, or "INJ OFF" with amber styling (`--arc-accent-amber`) when injection was disabled. The tag SHALL appear inline with the turn number header (e.g., `[T5] Player INJ ON`).

#### Scenario: Injection enabled turn
- **WHEN** turn 3 completes with injection enabled
- **THEN** the turn header displays a cyan "INJ ON" tag

#### Scenario: Injection disabled turn
- **WHEN** turn 7 completes with injection disabled
- **THEN** the turn header displays an amber "INJ OFF" tag

### Requirement: Verdict-colored left borders

DM response bubbles SHALL have a left border color determined by the drift verdict for that turn. Green (`--arc-accent-green`) SHALL indicate all facts CONFIRMED, amber (`--arc-accent-amber`) SHALL indicate all facts NOT_MENTIONED (no evaluation or no contradictions but also no confirmations), and magenta (`--arc-accent-magenta`) SHALL indicate at least one CONTRADICTED verdict. Turns with no drift evaluation SHALL use a neutral border.

#### Scenario: DM response with contradicted verdict
- **WHEN** a DM response renders on a turn where at least one fact was CONTRADICTED
- **THEN** the bubble has a magenta left border

#### Scenario: DM response with all confirmed
- **WHEN** a DM response renders on a turn where all evaluated facts are CONFIRMED
- **THEN** the bubble has a green left border

#### Scenario: DM response with no evaluation
- **WHEN** a DM response renders on a WARM_UP turn with no drift evaluation
- **THEN** the bubble has a neutral (gray) left border

### Requirement: Turn type badges

Each turn bubble SHALL display a badge indicating the turn type. Badge text SHALL be the turn type name (WARM_UP, ESTABLISH, ATTACK, DISPLACEMENT, DRIFT, RECALL_PROBE). Attack-family types (ATTACK, DISPLACEMENT, DRIFT) SHALL use a distinct badge color (magenta or red background). ESTABLISH and WARM_UP SHALL use a neutral badge. RECALL_PROBE SHALL use a cyan badge.

#### Scenario: Attack turn badge
- **WHEN** a turn of type ATTACK renders
- **THEN** the turn bubble displays a badge reading "ATTACK" with a magenta/red background

#### Scenario: Establish turn badge
- **WHEN** a turn of type ESTABLISH renders
- **THEN** the turn bubble displays a badge reading "ESTABLISH" with a neutral background

### Requirement: Click-to-select turn listeners

Each turn bubble SHALL be clickable. Clicking a turn bubble SHALL dispatch a turn-selection event that updates the ContextInspectorPanel and UnitTimelinePanel to display data for the selected turn. The selected turn bubble SHALL be visually highlighted with a distinct border or background change.

#### Scenario: Click turn updates inspector
- **WHEN** the user clicks the turn 5 bubble in the conversation panel
- **THEN** the ContextInspectorPanel updates to show turn 5 memory unit state and verdicts

#### Scenario: Selected turn highlighted
- **WHEN** the user clicks a turn bubble
- **THEN** the clicked bubble receives a highlight border
- **AND** any previously highlighted bubble loses its highlight

### Requirement: System messages preserved

System messages (e.g., "Simulation complete!") SHALL continue to render as centered, italicized text between turn bubbles, consistent with the existing `appendSystemMessage()` behavior. System messages SHALL NOT be clickable and SHALL NOT display injection state tags or turn type badges.

#### Scenario: Completion message renders
- **WHEN** the simulation completes
- **THEN** a centered system message "Simulation complete!" appears at the bottom of the conversation panel
