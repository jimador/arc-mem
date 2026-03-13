## ADDED Requirements

### Requirement: Horizontal injection state band

The `UnitTimelinePanel` SHALL render a horizontal band spanning all turns of the simulation. Each turn segment of the band SHALL be colored cyan (`--unit-accent-cyan`) when injection was enabled for that turn, and amber (`--unit-accent-amber`) when injection was disabled. The band SHALL update progressively as turns complete during a running simulation.

#### Scenario: All turns injection-enabled
- **WHEN** a simulation completes with injection enabled for every turn
- **THEN** the entire injection state band renders in cyan

#### Scenario: Mixed injection states
- **WHEN** injection is toggled off at turn 5 and back on at turn 8 in a 10-turn simulation
- **THEN** turns 1-4 render cyan, turns 5-7 render amber, turns 8-10 render cyan

#### Scenario: Progressive rendering during simulation
- **WHEN** turn N completes during a running simulation
- **THEN** the injection band extends to include turn N with the correct color

### Requirement: Drift markers by turn type

Each turn position on the timeline SHALL display a marker whose shape indicates the turn type. Diamond markers SHALL represent drift/attack turns, square markers SHALL represent establish turns, and dot (circle) markers SHALL represent normal/warm-up turns. Marker colors SHALL follow the verdict: green for CONFIRMED, amber for NOT_MENTIONED, magenta for CONTRADICTED. Turns with no verdict evaluation SHALL use a neutral gray marker.

#### Scenario: Attack turn with contradiction
- **WHEN** turn 7 is an ATTACK turn and the verdict is CONTRADICTED
- **THEN** a diamond-shaped marker in magenta appears at position 7

#### Scenario: Establish turn with confirmation
- **WHEN** turn 3 is an ESTABLISH turn and the verdict is CONFIRMED
- **THEN** a square-shaped marker in green appears at position 3

#### Scenario: Warm-up turn with no verdict
- **WHEN** turn 1 is a WARM_UP turn and no drift evaluation was performed
- **THEN** a dot marker in neutral gray appears at position 1

### Requirement: Per-context unit event rows

Below the main injection band, the timeline SHALL display one row per tracked context unit showing that context unit's lifecycle events across turns. Events include: creation (context unit promoted), reinforcement (rank increased), decay (rank decreased), archive (context unit removed). Each event SHALL be represented by an icon or marker at the appropriate turn position. The context unit text or ID SHALL label each row.

#### Scenario: Context Unit created at turn 2
- **WHEN** context unit "Saphira is a gold dragon" is promoted at turn 2
- **THEN** the context unit's row shows a creation marker at position 2

#### Scenario: Context Unit reinforced then archived
- **WHEN** an context unit is reinforced at turn 4 and archived at turn 9
- **THEN** the context unit's row shows a reinforcement marker at turn 4 and an archive marker at turn 9

### Requirement: Cross-panel turn selection

Clicking a turn position on the timeline SHALL dispatch a turn-selection event that updates other panels. At minimum, clicking a turn SHALL update the ContextInspectorPanel to show the context unit state and verdicts for that specific turn. The currently selected turn SHALL be visually highlighted on the timeline with a distinct border or background.

#### Scenario: Click turn updates inspector
- **WHEN** the user clicks turn 5 on the timeline
- **THEN** the ContextInspectorPanel updates to show the context units and verdicts from turn 5
- **AND** the turn 5 position on the timeline is visually highlighted

#### Scenario: Selected turn persists until changed
- **WHEN** the user clicks turn 5, then no further interaction occurs
- **THEN** turn 5 remains highlighted and the inspector continues displaying turn 5 data

### Requirement: Timeline scroll and zoom

The timeline SHALL support horizontal scrolling for simulations with many turns. When the simulation has more turns than can fit in the visible width, the timeline SHALL be scrollable. The most recent turn SHALL be auto-scrolled into view as new turns arrive during a running simulation.

#### Scenario: Auto-scroll on new turn
- **WHEN** a new turn completes during a running simulation with 20+ turns
- **THEN** the timeline scrolls to show the latest turn position
