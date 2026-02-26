## MODIFIED Requirements

### Requirement: DriftSummaryPanel placement
DriftSummaryPanel SHALL be rendered as the content of the "Results" tab in the right panel TabSheet. It SHALL NOT be rendered as a child of the left column below the ConversationPanel.

#### Scenario: Panel rendered in Results tab
- **WHEN** a simulation completes
- **THEN** the DriftSummaryPanel is visible within the Results tab of the right panel

#### Scenario: Panel not in left column
- **WHEN** a simulation completes
- **THEN** no DriftSummaryPanel appears below or alongside the ConversationPanel in the left column
