## ADDED Requirements

### Requirement: Panel visibility tied to PAUSED state

The `UnitManipulationPanel` SHALL only be visible when the simulation is in the PAUSED state. When the simulation transitions to RUNNING or COMPLETED, the panel SHALL be hidden. When the simulation transitions to PAUSED, the panel SHALL appear with the current context unit state pre-loaded.

#### Scenario: Panel appears on pause
- **WHEN** the user clicks the Pause button during a running simulation
- **THEN** the UnitManipulationPanel becomes visible
- **AND** it displays all active context units for the current simulation contextId

#### Scenario: Panel hides on resume
- **WHEN** the user clicks the Resume button while paused
- **THEN** the UnitManipulationPanel is hidden

### Requirement: Rank editing via slider

Each context unit row in the manipulation panel SHALL display a slider bound to the context unit's rank value. The slider range SHALL be [100, 900] matching `Context Unit.MIN_RANK` and `Context Unit.MAX_RANK`. When the user adjusts the slider and confirms, the system SHALL call `ContextUnitRepository.updateRank()` with the clamped value. The rank label SHALL update in real time as the slider moves.

#### Scenario: Slider adjusts rank within bounds
- **WHEN** the user drags an context unit's rank slider to 650
- **THEN** the rank label displays 650
- **AND** after confirmation, `ContextUnitRepository.updateRank()` is called with rank=650

#### Scenario: Slider clamps to valid range
- **WHEN** the slider component enforces min=100 and max=900
- **THEN** the user cannot set a rank below 100 or above 900

### Requirement: Archive button per context unit

Each context unit row SHALL have an Archive button that removes the context unit from active status. Archiving SHALL call `ContextUnitRepository.archiveUnit()` for the given context unit ID. Pinned context units SHALL display a confirmation dialog before archiving. After archiving, the context unit row SHALL be removed from the panel.

#### Scenario: Archive non-pinned unit
- **WHEN** the user clicks Archive on a non-pinned unit
- **THEN** the context unit is archived immediately
- **AND** the row is removed from the manipulation panel

#### Scenario: Archive pinned unit requires confirmation
- **WHEN** the user clicks Archive on a pinned unit
- **THEN** a confirmation dialog appears warning that the context unit is pinned
- **AND** the context unit is archived only if the user confirms

### Requirement: Pin toggle per context unit

Each context unit row SHALL have a toggle (checkbox or switch) for the pinned status. Toggling the pin SHALL call `ContextUnitRepository.updatePinned()`. Pinned context units are immune to rank-based eviction per Invariant A3. The pin toggle SHALL reflect the current pinned state on panel load.

#### Scenario: Toggle pin on
- **WHEN** the user enables the pin toggle on an unpinned context unit
- **THEN** `ContextUnitRepository.updatePinned(unitId, true)` is called
- **AND** the context unit displays a pinned indicator

#### Scenario: Toggle pin off
- **WHEN** the user disables the pin toggle on a pinned unit
- **THEN** `ContextUnitRepository.updatePinned(unitId, false)` is called

### Requirement: Inject new context unit form

The panel SHALL include a form for injecting a new context unit during pause. The form SHALL have fields for: text (required, text area), initial rank (slider, default 500), and authority (combo box: PROVISIONAL, UNRELIABLE, RELIABLE). CANON SHALL NOT be selectable in the authority combo box per Invariant A3 (CANON never auto-assigned). Submitting the form SHALL create a PropositionNode and promote it via `ArcMemEngine.promote()`.

#### Scenario: Inject context unit with valid input
- **WHEN** the user enters text "The dragon Saphira guards the northern pass" with rank 600 and authority PROVISIONAL
- **AND** clicks Inject
- **THEN** a new PropositionNode is created for the simulation contextId
- **AND** `ArcMemEngine.promote()` is called with the node ID and rank 600

#### Scenario: CANON authority not selectable
- **WHEN** the inject context unit form renders the authority combo box
- **THEN** CANON is not present in the selectable options

#### Scenario: Empty text rejected
- **WHEN** the user submits the inject form with blank text
- **THEN** the form shows a validation error and does not create an context unit

### Requirement: Conflict queue display

When conflicts exist between injected context units and the current conversation state, the panel SHALL display a conflict queue section listing each detected conflict. Each conflict entry SHALL show the conflicting context unit text, the incoming text that triggered the conflict, and the recommended resolution from `ConflictResolver`. The user MAY accept or dismiss each conflict resolution.

#### Scenario: Conflict detected and displayed
- **GIVEN** two active context units with contradictory text
- **WHEN** the manipulation panel loads during pause
- **THEN** the conflict queue section displays the detected conflict with both context unit texts and the resolver's recommendation

### Requirement: Intervention log

The panel SHALL maintain a running log of all interventions made during the current pause session. Each log entry SHALL record the timestamp, action type (RANK_CHANGE, ARCHIVE, PIN_TOGGLE, INJECT, CONFLICT_RESOLVE), the target context unit ID, and before/after values where applicable. The intervention log SHALL persist across panel re-renders within the same pause session and SHALL be accessible to the InterventionImpactBanner on resume.

#### Scenario: Rank change logged
- **WHEN** the user changes an context unit's rank from 400 to 700
- **THEN** the intervention log contains an entry with type=RANK_CHANGE, unitId, oldRank=400, newRank=700

#### Scenario: Inject logged
- **WHEN** the user injects a new context unit
- **THEN** the intervention log contains an entry with type=INJECT and the new context unit's ID and text
