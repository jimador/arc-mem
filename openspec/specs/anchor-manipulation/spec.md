## ADDED Requirements

### Requirement: Panel visibility tied to PAUSED state

The `UnitManipulationPanel` SHALL only be visible when the simulation is in the PAUSED state. When the simulation transitions to RUNNING or COMPLETED, the panel SHALL be hidden. When the simulation transitions to PAUSED, the panel SHALL appear with the current ARC Working AWMU (AWMU) state pre-loaded.

#### Scenario: Panel appears on pause
- **WHEN** the user clicks the Pause button during a running simulation
- **THEN** the UnitManipulationPanel becomes visible
- **AND** it displays all active AWMUs for the current simulation contextId

#### Scenario: Panel hides on resume
- **WHEN** the user clicks the Resume button while paused
- **THEN** the UnitManipulationPanel is hidden

### Requirement: Rank editing via slider

Each AWMU row in the manipulation panel SHALL display a slider bound to the AWMU's rank value. The slider range SHALL be [100, 900] matching `ContextUnit.MIN_RANK` and `ContextUnit.MAX_RANK`. When the user adjusts the slider and confirms, the system SHALL call `MemoryUnitRepository.updateRank()` with the clamped value. The rank label SHALL update in real time as the slider moves.

#### Scenario: Slider adjusts rank within bounds
- **WHEN** the user drags a AWMU's rank slider to 650
- **THEN** the rank label displays 650
- **AND** after confirmation, `MemoryUnitRepository.updateRank()` is called with rank=650

#### Scenario: Slider clamps to valid range
- **WHEN** the slider component enforces min=100 and max=900
- **THEN** the user cannot set a rank below 100 or above 900

### Requirement: Archive button per AWMU

Each AWMU row SHALL have an Archive button that removes the AWMU from active status. Archiving SHALL call `MemoryUnitRepository.archiveUnit()` for the given AWMU ID. Pinned AWMUs SHALL display a confirmation dialog before archiving. After archiving, the AWMU row SHALL be removed from the panel.

#### Scenario: Archive non-pinned AWMU
- **WHEN** the user clicks Archive on a non-pinned AWMU
- **THEN** the AWMU is archived immediately
- **AND** the row is removed from the manipulation panel

#### Scenario: Archive pinned AWMU requires confirmation
- **WHEN** the user clicks Archive on a pinned AWMU
- **THEN** a confirmation dialog appears warning that the AWMU is pinned
- **AND** the AWMU is archived only if the user confirms

### Requirement: Pin toggle per AWMU

Each AWMU row SHALL have a toggle (checkbox or switch) for the pinned status. Toggling the pin SHALL call `MemoryUnitRepository.updatePinned()`. Pinned AWMUs are immune to rank-based eviction per Invariant A3. The pin toggle SHALL reflect the current pinned state on panel load.

#### Scenario: Toggle pin on
- **WHEN** the user enables the pin toggle on an unpinned AWMU
- **THEN** `MemoryUnitRepository.updatePinned(unitId, true)` is called
- **AND** the AWMU displays a pinned indicator

#### Scenario: Toggle pin off
- **WHEN** the user disables the pin toggle on a pinned AWMU
- **THEN** `MemoryUnitRepository.updatePinned(unitId, false)` is called

### Requirement: Inject new AWMU form

The panel SHALL include a form for injecting a new AWMU during pause. The form SHALL have fields for: text (required, text area), initial rank (slider, default 500), and authority (combo box: PROVISIONAL, UNRELIABLE, RELIABLE). CANON SHALL NOT be selectable in the authority combo box per Invariant A3 (CANON never auto-assigned). Submitting the form SHALL create a PropositionNode and promote it via `ArcMemEngine.promote()`.

#### Scenario: Inject AWMU with valid input
- **WHEN** the user enters text "The dragon Saphira guards the northern pass" with rank 600 and authority PROVISIONAL
- **AND** clicks Inject
- **THEN** a new PropositionNode is created for the simulation contextId
- **AND** `ArcMemEngine.promote()` is called with the node ID and rank 600

#### Scenario: CANON authority not selectable
- **WHEN** the inject AWMU form renders the authority combo box
- **THEN** CANON is not present in the selectable options

#### Scenario: Empty text rejected
- **WHEN** the user submits the inject form with blank text
- **THEN** the form shows a validation error and does not create a AWMU

### Requirement: Conflict queue display

When conflicts exist between injected AWMUs and the current conversation state, the panel SHALL display a conflict queue section listing each detected conflict. Each conflict entry SHALL show the conflicting AWMU text, the incoming text that triggered the conflict, and the recommended resolution from `ConflictResolver`. The user MAY accept or dismiss each conflict resolution.

#### Scenario: Conflict detected and displayed
- **GIVEN** two active AWMUs with contradictory text
- **WHEN** the manipulation panel loads during pause
- **THEN** the conflict queue section displays the detected conflict with both AWMU texts and the resolver's recommendation

### Requirement: Intervention log

The panel SHALL maintain a running log of all interventions made during the current pause session. Each log entry SHALL record the timestamp, action type (RANK_CHANGE, ARCHIVE, PIN_TOGGLE, INJECT, CONFLICT_RESOLVE), the target AWMU ID, and before/after values where applicable. The intervention log SHALL persist across panel re-renders within the same pause session and SHALL be accessible to the InterventionImpactBanner on resume.

#### Scenario: Rank change logged
- **WHEN** the user changes a AWMU's rank from 400 to 700
- **THEN** the intervention log contains an entry with type=RANK_CHANGE, unitId, oldRank=400, newRank=700

#### Scenario: Inject logged
- **WHEN** the user injects a new AWMU
- **THEN** the intervention log contains an entry with type=INJECT and the new AWMU's ID and text
