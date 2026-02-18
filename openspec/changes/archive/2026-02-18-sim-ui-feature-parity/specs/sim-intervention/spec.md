## ADDED Requirements

### Requirement: InterventionImpactBanner after resume

When the simulation resumes from a PAUSED state in which the user made at least one intervention, an `InterventionImpactBanner` SHALL appear at the top of the SimulationView. The banner SHALL display the total number of interventions made during the pause session and the net change in active anchor count (e.g., "+2 anchors" or "-1 anchor"). The banner SHALL be styled distinctly from turn content (e.g., a bordered, colored bar).

#### Scenario: Banner appears after interventions
- **WHEN** the user makes 3 interventions during pause (rank change, inject, archive) and clicks Resume
- **THEN** the InterventionImpactBanner appears showing "3 interventions" and the anchor count delta

#### Scenario: Banner not shown when no interventions
- **WHEN** the user pauses and resumes without making any changes
- **THEN** no InterventionImpactBanner appears

### Requirement: Intervention count tracking

The system SHALL track the number of interventions made during each pause session. Intervention types include: RANK_CHANGE, ARCHIVE, PIN_TOGGLE, INJECT, and CONFLICT_RESOLVE. The count SHALL reset when the simulation resumes and a new pause session begins. The count SHALL be sourced from the AnchorManipulationPanel's intervention log.

#### Scenario: Count reflects all intervention types
- **WHEN** the user changes 1 rank, archives 1 anchor, and injects 1 new anchor during pause
- **THEN** the intervention count is 3

#### Scenario: Count resets on new pause session
- **WHEN** the user resumes, then pauses again
- **THEN** the intervention count for the new pause session starts at 0

### Requirement: Anchor count delta display

The banner SHALL compute and display the difference in active anchor count between the start and end of the pause session. The delta SHALL be computed as `(anchors at resume) - (anchors at pause)`. Positive deltas SHALL be prefixed with "+" and use green styling. Negative deltas SHALL use magenta styling. Zero delta SHALL display "no change" in neutral styling.

#### Scenario: Net increase in anchors
- **WHEN** there were 12 active anchors at pause and 14 at resume
- **THEN** the banner displays "+2 anchors" in green

#### Scenario: Net decrease in anchors
- **WHEN** there were 15 active anchors at pause and 13 at resume
- **THEN** the banner displays "-2 anchors" in magenta

### Requirement: Dismissible banner

The InterventionImpactBanner SHALL include a close/dismiss button. Clicking the dismiss button SHALL remove the banner from the UI. The banner SHALL also auto-dismiss after a configurable duration (default: 10 seconds) or when the next turn completes, whichever comes first.

#### Scenario: Manual dismiss
- **WHEN** the user clicks the dismiss button on the banner
- **THEN** the banner is immediately removed from the UI

#### Scenario: Auto-dismiss on next turn
- **WHEN** the next simulation turn completes after resume
- **THEN** the banner is automatically removed
