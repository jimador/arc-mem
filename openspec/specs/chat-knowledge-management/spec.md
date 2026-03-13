## ADDED Requirements

### Requirement: Knowledge tab in ChatView sidebar

`ChatView` SHALL include a "Knowledge" tab in the sidebar `TabSheet`. The tab SHALL display all propositions for the current context that are not active memory units. Each proposition card SHALL show text, confidence score, and a "Promote" button.

#### Scenario: Knowledge tab shows propositions
- **WHEN** the Knowledge tab is selected
- **THEN** all non-memory-unit propositions for the chat context are displayed
- **AND** each card shows text and confidence

#### Scenario: Promote from Knowledge tab
- **WHEN** the user clicks "Promote" on a proposition card
- **THEN** `ArcMemEngine.promote()` is called with the proposition ID and default rank 500
- **AND** the sidebar refreshes to reflect the new memory unit

### Requirement: Add Knowledge form

The Knowledge tab SHALL include an "Add Knowledge" form with a text field and a confidence slider (0.0-1.0, default 0.8). Clicking "Create" SHALL persist a new `PropositionNode` with the given text and confidence, set `contextId` to the current context, and refresh the sidebar.

#### Scenario: Create knowledge entry
- **WHEN** the user enters text and clicks "Create"
- **THEN** a new `PropositionNode` is saved with the entered text and confidence
- **AND** the node appears in the Knowledge tab list
- **AND** the form fields are cleared

#### Scenario: Empty text rejected
- **WHEN** the user clicks "Create" with empty text
- **THEN** no proposition is created
