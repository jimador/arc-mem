## ADDED Requirements

### Requirement: Panel visibility tied to PAUSED state

The `UnitManipulationPanel` SHALL only be visible when the simulation is in the PAUSED state. When the simulation transitions to RUNNING or COMPLETED, the panel SHALL be hidden. When the simulation transitions to PAUSED, the panel SHALL appear with the current memory unit state pre-loaded.

#### Scenario: Panel appears on pause
- **WHEN** the user clicks the Pause button during a running simulation
- **THEN** the UnitManipulationPanel becomes visible
- **AND** it displays all active memory units for the current simulation contextId

#### Scenario: Panel hides on resume
- **WHEN** the user clicks the Resume button while paused
- **THEN** the UnitManipulationPanel is hidden

### Requirement: Rank editing via slider

Each memory unit row in the manipulation panel SHALL display a slider bound to the memory unit's rank value. The slider range SHALL be [100, 900] matching `ContextUnit.MIN_RANK` and `ContextUnit.MAX_RANK`. When the user adjusts the slider and confirms, the system SHALL call `MemoryUnitRepository.updateRank()` with the clamped value. The rank label SHALL update in real time as the slider moves.

#### Scenario: Slider adjusts rank within bounds
- **WHEN** the user drags a memory unit's rank slider to 650
- **THEN** the rank label displays 650
- **AND** after confirmation, `MemoryUnitRepository.updateRank()` is called with rank=650

#### Scenario: Slider clamps to valid range
- **WHEN** the slider component enforces min=100 and max=900
- **THEN** the user cannot set a rank below 100 or above 900

### Requirement: Archive button per memory unit

Each memory unit row SHALL have an Archive button that removes the memory unit from active status. Archiving SHALL call `MemoryUnitRepository.archiveUnit()` for the given memory unit ID. Pinned memory units SHALL display a confirmation dialog before archiving. After archiving, the memory unit row SHALL be removed from the panel.

#### Scenario: Archive non-pinned memory unit
- **WHEN** the user clicks Archive on a non-pinned memory unit
- **THEN** the memory unit is archived immediately
- **AND** the row is removed from the manipulation panel

#### Scenario: Archive pinned memory unit requires confirmation
- **WHEN** the user clicks Archive on a pinned memory unit
- **THEN** a confirmation dialog appears warning that the memory unit is pinned
- **AND** the memory unit is archived only if the user confirms

### Requirement: Pin toggle per memory unit

Each memory unit row SHALL have a toggle (checkbox or switch) for the pinned status. Toggling the pin SHALL call `MemoryUnitRepository.updatePinned()`. Pinned memory units are immune to rank-based eviction per Invariant A3. The pin toggle SHALL reflect the current pinned state on panel load.

#### Scenario: Toggle pin on
- **WHEN** the user enables the pin toggle on an unpinned memory unit
- **THEN** `MemoryUnitRepository.updatePinned(unitId, true)` is called
- **AND** the memory unit displays a pinned indicator

#### Scenario: Toggle pin off
- **WHEN** the user disables the pin toggle on a pinned memory unit
- **THEN** `MemoryUnitRepository.updatePinned(unitId, false)` is called

### Requirement: Inject new memory unit form

The panel SHALL include a form for injecting a new memory unit during pause. The form SHALL have fields for: text (required, text area), initial rank (slider, default 500), and authority (combo box: PROVISIONAL, UNRELIABLE, RELIABLE). CANON SHALL NOT be selectable in the authority combo box per Invariant A3 (CANON never auto-assigned). Submitting the form SHALL create a PropositionNode and promote it via `ArcMemEngine.promote()`.

#### Scenario: Inject memory unit with valid input
- **WHEN** the user enters text "The dragon Saphira guards the northern pass" with rank 600 and authority PROVISIONAL
- **AND** clicks Inject
- **THEN** a new PropositionNode is created for the simulation contextId
- **AND** `ArcMemEngine.promote()` is called with the node ID and rank 600

#### Scenario: CANON authority not selectable
- **WHEN** the inject memory unit form renders the authority combo box
- **THEN** CANON is not present in the selectable options

#### Scenario: Empty text rejected
- **WHEN** the user submits the inject form with blank text
- **THEN** the form shows a validation error and does not create a memory unit

### Requirement: Conflict queue display

When conflicts exist between injected memory units and the current conversation state, the panel SHALL display a conflict queue section listing each detected conflict. Each conflict entry SHALL show the conflicting memory unit text, the incoming text that triggered the conflict, and the recommended resolution from `ConflictResolver`. The user MAY accept or dismiss each conflict resolution.

#### Scenario: Conflict detected and displayed
- **GIVEN** two active memory units with contradictory text
- **WHEN** the manipulation panel loads during pause
- **THEN** the conflict queue section displays the detected conflict with both memory unit texts and the resolver's recommendation

### Requirement: Intervention log

The panel SHALL maintain a running log of all interventions made during the current pause session. Each log entry SHALL record the timestamp, action type (RANK_CHANGE, ARCHIVE, PIN_TOGGLE, INJECT, CONFLICT_RESOLVE), the target memory unit ID, and before/after values where applicable. The intervention log SHALL persist across panel re-renders within the same pause session and SHALL be accessible to the InterventionImpactBanner on resume.

#### Scenario: Rank change logged
- **WHEN** the user changes a memory unit's rank from 400 to 700
- **THEN** the intervention log contains an entry with type=RANK_CHANGE, unitId, oldRank=400, newRank=700

#### Scenario: Inject logged
- **WHEN** the user injects a new memory unit
- **THEN** the intervention log contains an entry with type=INJECT and the new memory unit's ID and text
