## MODIFIED Requirements

### Requirement: Propositions tab excludes promoted anchors
The Propositions tab SHALL only display propositions that have NOT been promoted to anchors (rank == 0). Propositions with rank > 0 SHALL appear exclusively in the Anchors tab.

#### Scenario: Promoted proposition moves to Anchors tab
- **GIVEN** a proposition "The tavern has a secret basement" is displayed in the Propositions tab
- **WHEN** the proposition is promoted to an anchor (either manually or via auto-promotion)
- **THEN** it disappears from the Propositions tab on next refresh
- **AND** it appears in the Anchors tab

### Requirement: Extraction triggers after 2 turns
The DICE extraction trigger interval SHALL be configured to 2 for the chat context, ensuring propositions appear after the second chat exchange rather than the sixth.

#### Scenario: Propositions appear after second turn
- **GIVEN** the user has completed 2 chat turns
- **WHEN** the sidebar refreshes
- **THEN** extracted propositions are visible in the Propositions tab
