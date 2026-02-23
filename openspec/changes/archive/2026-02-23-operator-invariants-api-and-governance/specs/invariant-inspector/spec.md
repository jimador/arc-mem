## ADDED Requirements

### Requirement: Invariant inspector tab in Context Inspector panel

The system SHALL add an "Invariants" tab to the `ContextInspectorPanel` Vaadin component. The tab SHALL display all active operator invariants applicable to the current simulation context, including globally-scoped invariants.

The tab SHALL be implemented as a `VerticalLayout` within the existing `TabSheet` in `ContextInspectorPanel`, consistent with the existing tabs (Anchors, Context Trace, Entity Mentions, etc.).

#### Scenario: Invariants tab visible in Context Inspector

- **GIVEN** a simulation is running with invariants configured
- **WHEN** the user opens the Context Inspector panel
- **THEN** an "Invariants" tab SHALL be visible alongside the existing tabs

#### Scenario: No invariants configured shows empty state

- **GIVEN** no invariants are configured for the current simulation
- **WHEN** the user selects the "Invariants" tab
- **THEN** the tab SHALL display a message: "No invariants configured for this context"

### Requirement: Invariant status display

Each invariant in the inspector SHALL display:

| Field | Display |
|-------|---------|
| Rule ID | Text label (e.g., `"protect-cursed-blade"`) |
| Type | Badge with invariant type name (e.g., `ANCHOR_PROTECTED`) |
| Strength | Color-coded badge: red for `MUST`, amber for `SHOULD` |
| Scope | Text: `GLOBAL` or `CONTEXT (ctx-id)` |
| Status | Icon: green checkmark for satisfied, red X for violated |
| Description | Truncated text with tooltip for full description |

The status SHALL be computed by calling `InvariantEvaluator` to check the current state against each invariant's constraints without performing any lifecycle action.

#### Scenario: Satisfied invariant shows green status

- **GIVEN** an `ANCHOR_PROTECTED` invariant for anchor "A1" and anchor "A1" is active
- **WHEN** the Invariants tab is rendered
- **THEN** the invariant row SHALL show a green checkmark status icon

#### Scenario: Violated invariant shows red status

- **GIVEN** a `MINIMUM_COUNT` invariant requiring 3 RELIABLE anchors and only 1 RELIABLE anchor exists
- **WHEN** the Invariants tab is rendered
- **THEN** the invariant row SHALL show a red X status icon

### Requirement: Violation history display

The Invariants tab SHALL include a "Violation History" section below the active invariants list. The section SHALL display all `InvariantViolation` events recorded during the current simulation, ordered by timestamp (newest first).

Each violation entry SHALL display:

| Field | Display |
|-------|---------|
| Timestamp | Formatted time (e.g., `"Turn 5, 14:32:01"`) |
| Rule ID | The violated invariant's rule ID |
| Action | The lifecycle action that was attempted (e.g., `EVICT`) |
| Anchor ID | The anchor targeted by the attempted action |
| Blocked | Badge: "BLOCKED" (red) or "WARNED" (amber) |

#### Scenario: Violation history populated after blocked eviction

- **GIVEN** a MUST-strength invariant blocked an eviction on turn 5
- **WHEN** the Invariants tab is viewed after turn 5
- **THEN** the Violation History section SHALL contain an entry with the rule ID, action `EVICT`, and status `BLOCKED`

#### Scenario: Empty violation history

- **GIVEN** no invariant violations have occurred during the simulation
- **WHEN** the Invariants tab is viewed
- **THEN** the Violation History section SHALL display "No violations recorded"

### Requirement: Real-time invariant status updates

The invariant inspector SHALL refresh invariant status and violation history after each simulation turn completes. The refresh SHALL be triggered by the same mechanism used by other Context Inspector tabs (polling or push from `SimulationTurnExecutor`).

The status check SHALL NOT block the simulation turn execution -- it SHALL be performed asynchronously after the turn result is available.

#### Scenario: Status updates after turn execution

- **GIVEN** a `MINIMUM_COUNT` invariant requiring 3 RELIABLE anchors
- **AND** 3 RELIABLE anchors exist before turn 7
- **WHEN** turn 7 evicts one RELIABLE anchor (dropping to 2)
- **THEN** the invariant status SHALL update from satisfied (green) to violated (red) after turn 7 completes

#### Scenario: Violation history grows across turns

- **GIVEN** invariant violations occur on turns 3, 5, and 8
- **WHEN** the Invariants tab is viewed after turn 8
- **THEN** the Violation History SHALL contain 3 entries ordered by timestamp (turn 8 first, turn 3 last)
