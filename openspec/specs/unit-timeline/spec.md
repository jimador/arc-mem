## Requirements

### Requirement: Two-band header layout

The `MemoryUnitTimelinePanel` header MUST consist of two visually distinct horizontal
bands stacked vertically:

1. **Injection strip** (4 px height): A thin color strip, cyan for injection ON and
   amber for injection OFF, spanning all turns. No text or symbols.
2. **Drift marker track** (≥ 20 px height): One marker per turn position, using
   Unicode symbols to indicate turn type and colored by verdict severity.

The previous single-band design (28 × 20 px cells with turn numbers inside) is
**REMOVED**. The new design MUST render within the existing panel width at ≥ 1280 px
viewport without horizontal overflow on the header bands alone. The band SHALL update
progressively as turns complete during a running simulation.

#### Scenario: Injection strip renders thin and color-coded
- **WHEN** turns 1–3 have injection ON and turns 4–5 have injection OFF
- **THEN** a 4 px strip shows cyan for positions 1–3 and amber for positions 4–5

#### Scenario: All turns injection-enabled
- **WHEN** a simulation completes with injection enabled for every turn
- **THEN** the entire injection state band renders in cyan

#### Scenario: Mixed injection states
- **WHEN** injection is toggled off at turn 5 and back on at turn 8 in a 10-turn simulation
- **THEN** turns 1-4 render cyan, turns 5-7 render amber, turns 8-10 render cyan

#### Scenario: Progressive rendering during simulation
- **WHEN** turn N completes during a running simulation
- **THEN** the injection band extends to include turn N with the correct color

#### Scenario: Drift markers use symbols
- **WHEN** turn 2 is ESTABLISH and turn 5 is ATTACK with verdict CONTRADICTED
- **THEN** position 2 shows `■` in green and position 5 shows `◇` in magenta

---

### Requirement: Drift marker symbols and color mapping

| Turn type      | Symbol | Fallback |
|----------------|--------|----------|
| ESTABLISH      | `■`    | `S`      |
| ATTACK         | `◇`    | `A`      |
| WARM_UP        | `·`    | `W`      |
| Other / none   | `●`    | `O`      |

Verdict severity color mapping:

| Severity      | Color                         |
|---------------|-------------------------------|
| CONFIRMED     | `--arc-accent-green`       |
| NOT_MENTIONED | `--lumo-secondary-text-color` |
| CONTRADICTED  | `--arc-accent-magenta`     |
| None / no eval| neutral gray                  |

The symbol is always a single character rendered at `1.1em` in the theme monospace font.

#### Scenario: Attack turn with contradiction
- **WHEN** turn 7 is an ATTACK turn and the verdict is CONTRADICTED
- **THEN** a diamond-shaped marker (`◇`) in magenta appears at position 7

#### Scenario: Establish turn with confirmation
- **WHEN** turn 3 is an ESTABLISH turn and the verdict is CONFIRMED
- **THEN** a square-shaped marker (`■`) in green appears at position 3

#### Scenario: Warm-up turn with no verdict
- **WHEN** turn 1 is a WARM_UP turn and no drift evaluation was performed
- **THEN** a dot marker (`·`) in neutral gray appears at position 1

---

### Requirement: Per-unit event rows use flex-wrap badge cells

Each per-unit row MUST use a flex-wrap layout (`display: flex; flex-wrap: wrap;
gap: 4px`) with one badge per event occurrence. Badges are **not** aligned to fixed
turn-position columns. Each badge SHALL show the event-type initial letter (`C`, `R`,
`D`, `A`, `E`) in a pill-shaped element colored by event type.

The previous fixed-width 28 × 16 px cell grid is **REMOVED**.

Row header MUST include:
- Authority badge (colored by authority level using existing authority-badge CSS class)
- Truncated memory unit text (max 20 chars, monospace, 0.75em)
- Injection-state dot: `●` in cyan when currently injected, `○` in steel-gray otherwise

Events include: creation (memory unit promoted), reinforcement (activation score increased), decay
(activation score decreased), archive (memory unit removed). The memory unit text or ID SHALL label each row.

#### Scenario: Memory unit row with three events
- **WHEN** memory unit "The king lives" has events: Created at T2, Reinforced at T5, Decayed at T8
- **THEN** its row shows three badge pills: `C` (green), `R` (cyan), `D` (amber)
- **AND** the badges wrap to a new line if the row overflows

#### Scenario: Memory unit created at turn 2
- **WHEN** memory unit "Saphira is a gold dragon" is promoted at turn 2
- **THEN** the memory unit's row shows a creation marker at position 2

#### Scenario: Memory unit reinforced then archived
- **WHEN** a memory unit is reinforced at turn 4 and archived at turn 9
- **THEN** the memory unit's row shows a reinforcement marker at turn 4 and an archive marker at turn 9

---

### Requirement: Cell readability at default zoom

**I1**: All drift marker symbols and memory unit event badges MUST be legible at 100 %
browser zoom on a 1280 × 800 viewport without requiring the user to scroll
horizontally to read the per-unit rows. Badge font-size MUST be ≥ 0.7em.

---

### Requirement: Cross-panel turn selection

Clicking a turn position on the timeline SHALL dispatch a turn-selection event that
updates other panels. At minimum, clicking a turn SHALL update the
`ContextInspectorPanel` to show the memory unit state and verdicts for that specific turn.
The currently selected turn SHALL be visually highlighted on the timeline with a
distinct border or background.

#### Scenario: Click turn updates inspector
- **WHEN** the user clicks turn 5 on the timeline
- **THEN** the ContextInspectorPanel updates to show the memory units and verdicts from turn 5
- **AND** the turn 5 position on the timeline is visually highlighted

#### Scenario: Selected turn persists until changed
- **WHEN** the user clicks turn 5, then no further interaction occurs
- **THEN** turn 5 remains highlighted and the inspector continues displaying turn 5 data

---

### Requirement: Timeline horizontal scroll and auto-scroll

The timeline SHALL support horizontal scrolling for simulations with many turns. When
the simulation has more turns than can fit in the visible width, the timeline SHALL be
scrollable. The most recent turn SHALL be auto-scrolled into view as new turns arrive
during a running simulation.

#### Scenario: Auto-scroll on new turn
- **WHEN** a new turn completes during a running simulation with 20+ turns
- **THEN** the timeline scrolls to show the latest turn position
