## Requirements

### Requirement: Per-turn duration captured in SimulationProgress

`SimulationProgress` SHALL include a `turnDurationMs` field (type `long`) representing
the wall-clock milliseconds elapsed from the start of `executeTurnFull()` to the
moment the result is handed back to the caller. For non-turn phases (SETUP, EVALUATE,
COMPLETE, CANCELLED) the field SHALL be `0`.

#### Scenario: Turn duration populated for ATTACK turn
- **WHEN** an ATTACK turn completes
- **THEN** `SimulationProgress.turnDurationMs` is a positive value equal to the
  elapsed wall-clock time for that turn (including LLM call, drift eval, extraction)

#### Scenario: Duration is zero for non-turn phases
- **WHEN** `SimulationProgress` is emitted for the COMPLETE phase
- **THEN** `turnDurationMs` equals `0`

#### Scenario: Duration reflects parallel speedup
- **WHEN** `parallelPostResponse=true` and an ATTACK turn completes
- **THEN** `turnDurationMs` is strictly less than the sum of individual LLM call
  durations (confirming parallel execution is measured correctly)

---

### Requirement: Turn duration badge on conversation bubbles

Each DM response bubble in the conversation panel SHALL display a small timing badge
showing the turn duration in human-readable form. Durations under 10 seconds SHALL
display as `Xs` (e.g., `3s`), durations 10–99 seconds as `XXs`, and durations of 60
seconds or more as `Xm Ys`. The badge SHALL use `--anchor-accent-amber` for durations
over 30 seconds and `--lumo-secondary-text-color` otherwise, signaling slow turns.

#### Scenario: Fast turn badge
- **WHEN** a turn completes in 2400 ms
- **THEN** the DM bubble shows a badge with text `2s` in secondary text color

#### Scenario: Slow turn badge color
- **WHEN** a turn completes in 45 000 ms
- **THEN** the DM bubble shows a badge with text `45s` in amber color

#### Scenario: No badge on player bubbles
- **WHEN** a player message bubble renders
- **THEN** no timing badge appears on that bubble

---

### Requirement: Turn duration tooltip on timeline drift markers

Each drift-marker cell in `AnchorTimelinePanel` SHALL display a tooltip on hover that
includes the turn duration. The tooltip text SHALL follow the pattern:
`T{N} | {TurnType} | {duration}`.

#### Scenario: Timeline cell tooltip
- **WHEN** the user hovers over the drift marker for turn 4
- **THEN** a tooltip appears reading `T4 | ATTACK | 3s`
