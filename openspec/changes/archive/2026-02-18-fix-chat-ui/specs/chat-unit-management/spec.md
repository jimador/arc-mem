## ADDED Requirements

### Requirement: View active context units in chat sidebar
The chat sidebar SHALL display all active context units for the `"chat"` context, showing text, rank, authority level, reinforcement count, and pinned status. The list SHALL be sorted by rank descending and refresh after each chat turn.

#### Scenario: Context Units displayed after turn
- **GIVEN** the chat context has 3 active context units
- **WHEN** a chat turn completes
- **THEN** the sidebar displays all 3 context units with their current rank, authority badge, and reinforcement count

### Requirement: Create context units manually
The chat sidebar SHALL provide a form to create new context units in the `"chat"` context. The form SHALL include a text field, a rank slider constrained to [100, 900], and an authority dropdown (PROVISIONAL, UNRELIABLE, RELIABLE). CANON SHALL NOT be available in the dropdown.

#### Scenario: User creates an context unit
- **WHEN** a user fills in context unit text "Baron Krell is dead", sets rank to 700, selects RELIABLE authority, and clicks Create
- **THEN** the system creates a PropositionNode with the given text, promotes it to context unit with rank 700 and RELIABLE authority in the `"chat"` context, and the context unit appears in the sidebar

#### Scenario: Budget enforcement on manual creation
- **GIVEN** the chat context already has 20 active context units (budget full)
- **WHEN** a user creates an context unit with rank 800
- **THEN** the system evicts the lowest-ranked non-pinned unit and promotes the new one, matching existing `ArcMemEngine.promote()` behavior

### Requirement: Edit activation score and authority
Each context unit in the sidebar SHALL have inline controls to modify rank and authority level. Rank changes SHALL be clamped to [100, 900]. Authority changes SHALL only allow upgrades (never downgrades).

#### Scenario: User adjusts activation score
- **WHEN** a user drags an context unit's rank slider from 500 to 750
- **THEN** the system updates the context unit's rank to 750 via `ContextUnitRepository` and the sidebar reflects the new rank

#### Scenario: User upgrades authority
- **GIVEN** an context unit with PROVISIONAL authority
- **WHEN** a user selects RELIABLE from the authority dropdown
- **THEN** the system upgrades the context unit's authority to RELIABLE

#### Scenario: Authority downgrade prevented
- **GIVEN** an context unit with RELIABLE authority
- **WHEN** the authority dropdown is displayed
- **THEN** only RELIABLE and higher options (CANON excluded) are available; PROVISIONAL and UNRELIABLE are disabled

### Requirement: Evict context units manually
Each context unit in the sidebar SHALL have an evict button. Eviction SHALL set the context unit's rank to 0, removing it from the active pool.

#### Scenario: User evicts an context unit
- **WHEN** a user clicks the evict button on an context unit
- **THEN** the system sets the context unit's rank to 0 and the context unit disappears from the active context units list
