## ADDED Requirements

### Requirement: Tabbed sidebar layout
The chat sidebar SHALL use a tabbed layout with three tabs: Anchors, Propositions, and Session Info. The Anchors tab SHALL be selected by default.

#### Scenario: User switches between tabs
- **WHEN** a user clicks the Propositions tab
- **THEN** the sidebar displays the propositions list, hiding the anchors panel

### Requirement: View extracted propositions
The Propositions tab SHALL display all non-anchor propositions for the `"chat"` context, showing text, confidence score, and knowledge status. The list SHALL refresh after each chat turn.

#### Scenario: Propositions appear after extraction
- **GIVEN** a chat turn has completed and DICE extraction has processed the messages
- **WHEN** the sidebar refreshes
- **THEN** newly extracted propositions appear in the Propositions tab with their confidence scores

#### Scenario: Promoted propositions move to Anchors tab
- **GIVEN** a proposition has been promoted to anchor status (rank > 0)
- **WHEN** the sidebar refreshes
- **THEN** the proposition appears in the Anchors tab and no longer appears in the Propositions tab

### Requirement: Session info display
The Session Info tab SHALL display the current context ID, the total number of active anchors, the total number of propositions, and the number of chat turns completed in this session.

#### Scenario: Session info updates each turn
- **GIVEN** the user has completed 5 chat turns
- **WHEN** the Session Info tab is viewed
- **THEN** it displays contextId "chat", the current anchor count, proposition count, and "5 turns"
