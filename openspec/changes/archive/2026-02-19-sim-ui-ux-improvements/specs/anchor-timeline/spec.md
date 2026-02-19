## MODIFIED Requirements

### Requirement: Two-band header layout (replaces single injection band)

The `AnchorTimelinePanel` header MUST consist of two visually distinct horizontal
bands stacked vertically:

1. **Injection strip** (4 px height): A thin color strip, cyan for injection ON and
   amber for injection OFF, spanning all turns. No text or symbols.
2. **Drift marker track** (≥ 20 px height): One marker per turn position, using
   Unicode symbols to indicate turn type and colored by verdict severity.

The previous single-band design (28 × 20 px cells with turn numbers inside) is
**REMOVED**. The new design MUST render within the existing panel width at ≥ 1280 px
viewport without horizontal overflow on the header bands alone.

#### Scenario: Injection strip renders thin and color-coded
- **WHEN** turns 1–3 have injection ON and turns 4–5 have injection OFF
- **THEN** a 4 px strip shows cyan for positions 1–3 and amber for positions 4–5

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
| CONFIRMED     | `--anchor-accent-green`       |
| NOT_MENTIONED | `--lumo-secondary-text-color` |
| CONTRADICTED  | `--anchor-accent-magenta`     |
| None / no eval| neutral gray                  |

The symbol is always a single character rendered at `1.1em` in the theme monospace font.

---

### Requirement: Per-anchor event rows use flex-wrap badge cells (replaces fixed-width cells)

Each per-anchor row MUST use a flex-wrap layout (`display: flex; flex-wrap: wrap;
gap: 4px`) with one badge per event occurrence. Badges are **not** aligned to fixed
turn-position columns. Each badge SHALL show the event-type initial letter (`C`, `R`,
`D`, `A`, `E`) in a pill-shaped element colored by event type.

The previous fixed-width 28 × 16 px cell grid is **REMOVED**.

Row header MUST include:
- Authority badge (colored by authority level using existing authority-badge CSS class)
- Truncated anchor text (max 20 chars, monospace, 0.75em)
- Injection-state dot: `●` in cyan when currently injected, `○` in steel-gray otherwise

#### Scenario: Anchor row with three events
- **WHEN** anchor "The king lives" has events: Created at T2, Reinforced at T5, Decayed at T8
- **THEN** its row shows three badge pills: `C` (green), `R` (cyan), `D` (amber)
- **AND** the badges wrap to a new line if the row overflows

---

### Requirement: Cell readability at default zoom (new invariant)

**I1**: All drift marker symbols and anchor event badges MUST be legible at 100 %
browser zoom on a 1280 × 800 viewport without requiring the user to scroll
horizontally to read the per-anchor rows. Badge font-size MUST be ≥ 0.7em.

---

### Retained Requirements (unchanged from base spec)

The following requirements from the base `anchor-timeline` spec are retained without
modification:
- Cross-panel turn selection (click dispatches turn-selection event → ContextInspectorPanel)
- Selected turn persists until changed (visual highlight on selected marker)
- Timeline horizontal scroll for simulations with many turns
- Auto-scroll to latest turn during running simulation
- Progressive rendering as turns complete
