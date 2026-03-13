## Requirements

### Requirement: Thinking indicator shown during turn execution

The conversation panel SHALL display a thinking indicator component immediately after
the player message bubble is added and before the DM response bubble arrives. The
indicator SHALL be removed as soon as the DM response is available. The indicator
SHALL be visible only while a turn is actively executing (not during PAUSED or IDLE
states).

#### Scenario: Indicator appears after player message
- **WHEN** a turn begins executing (player message submitted to the engine)
- **THEN** a thinking indicator appears below the player message bubble within 100 ms

#### Scenario: Indicator removed on DM response
- **WHEN** the DM response arrives
- **THEN** the thinking indicator is removed before the DM bubble is added

#### Scenario: Indicator absent when paused
- **WHEN** the simulation is PAUSED between turns
- **THEN** no thinking indicator is visible in the conversation panel

---

### Requirement: Thinking indicator content

The indicator SHALL consist of a pulsing spinner (Vaadin `ProgressBar` in
indeterminate mode or equivalent CSS animation) paired with a rotating status label.
The status label SHALL cycle through at least three messages on a timed interval
(≤ 3 seconds each):

1. "Generating response…"
2. "Evaluating drift…"
3. "Extracting propositions…"

The indicator container SHALL use the retro theme surface color and a subtle left
border in `--arc-accent-cyan`.

#### Scenario: Status label rotates
- **WHEN** the thinking indicator has been visible for 3 seconds without a DM response
- **THEN** the status label has changed to a different message

#### Scenario: Visual style
- **WHEN** the thinking indicator renders
- **THEN** it has a cyan left border and a spinner to the left of the label text
- **AND** no second thinking indicator appears if the previous one is still visible

---

### Requirement: Thread-safe UI update via Push

The thinking indicator MUST be added and removed via `UI.getCurrent().access()` to
ensure thread-safe Vaadin push updates from the simulation's virtual-thread executor.

#### Scenario: No concurrency error on indicator add
- **WHEN** the turn begins on a virtual thread
- **THEN** the UI.access() call successfully schedules the indicator addition without
  throwing a `UIDetachedException` or `ConcurrentModificationException`
