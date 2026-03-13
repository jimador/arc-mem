## MODIFIED Requirements

### Requirement: View active context units in chat sidebar
The chat sidebar SHALL display all active context units for the current conversation's `conversationId` context, showing text, rank, authority level, reinforcement count, and pinned status. The list SHALL be sorted by rank descending and refresh after each chat turn.

#### Scenario: Context Units displayed after turn
- **GIVEN** the current conversation has 3 active context units
- **WHEN** a chat turn completes
- **THEN** the sidebar displays all 3 context units with their current rank, authority badge, and reinforcement count

### Requirement: Create context units manually
The chat sidebar SHALL provide a form to create new context units in the current conversation's context. The form SHALL include a text field, a rank slider constrained to [100, 900], and an authority dropdown (PROVISIONAL, UNRELIABLE, RELIABLE). CANON SHALL NOT be available in the dropdown.

#### Scenario: User creates an context unit
- **WHEN** a user fills in context unit text "Baron Krell is dead", sets rank to 700, selects RELIABLE authority, and clicks Create
- **THEN** the system creates a PropositionNode with the given text, promotes it to context unit with rank 700 and RELIABLE authority in the current conversation's context, and the context unit appears in the sidebar

#### Scenario: Budget enforcement on manual creation
- **GIVEN** the current conversation already has 20 active context units (budget full)
- **WHEN** a user creates an context unit with rank 800
- **THEN** the system evicts the lowest-ranked non-pinned unit and promotes the new one, matching existing `ArcMemEngine.promote()` behavior

### Requirement: Promote propositions to context units from Propositions tab
The Propositions tab SHALL include a "Promote" button on each proposition card. Clicking Promote SHALL call `ArcMemEngine.promote()` with the proposition's ID and the configured initial rank (default 500), promoting it to an context unit with PROVISIONAL authority. The sidebar SHALL refresh after promotion.

#### Scenario: User promotes a proposition
- **GIVEN** the Propositions tab shows an extracted proposition "The dragon is sleeping"
- **WHEN** the user clicks the Promote button on that proposition
- **THEN** the proposition becomes an context unit with rank 500, PROVISIONAL authority, and appears in the Context Units tab
- **AND** the proposition no longer appears in the Propositions tab
