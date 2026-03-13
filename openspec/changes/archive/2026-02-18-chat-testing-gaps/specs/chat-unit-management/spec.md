## MODIFIED Requirements

### Requirement: Promote propositions to context units from Propositions tab
The Propositions tab SHALL include a "Promote" button on each proposition card. Clicking Promote SHALL call `ArcMemEngine.promote()` with the proposition's ID and the configured initial rank (default 500), promoting it to an context unit with PROVISIONAL authority. The sidebar SHALL refresh after promotion.

#### Scenario: User promotes a proposition
- **GIVEN** the Propositions tab shows an extracted proposition "The dragon is sleeping"
- **WHEN** the user clicks the Promote button on that proposition
- **THEN** the proposition becomes an context unit with rank 500, PROVISIONAL authority, and appears in the Context Units tab
- **AND** the proposition no longer appears in the Propositions tab
