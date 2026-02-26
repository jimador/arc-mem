## MODIFIED Requirements

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
