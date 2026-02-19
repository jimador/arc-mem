## MODIFIED Requirements

### Requirement: Control button disabled state via setEnabled (replaces color-as-state)

All simulation control buttons (Run, Pause, Resume, Stop) MUST use
`button.setEnabled(false)` as the **primary** mechanism for conveying disabled state.
Vaadin's Lumo theme automatically renders disabled buttons with `opacity: 0.5` and
`cursor: not-allowed` — this is the only visual disabled indicator permitted.

Semantic color variants (e.g., `LUMO_SUCCESS` for Resume, `LUMO_ERROR` for Stop) are
**PROHIBITED** on control buttons. All four control buttons MUST use a single variant
scheme:
- Run: `ButtonVariant.LUMO_PRIMARY`
- Pause, Resume, Stop: no variant (default ghost/outlined style)

The previous mapping of LUMO_SUCCESS → Resume and LUMO_ERROR → Stop is **REMOVED**.

#### Scenario: Stop button disabled during IDLE
- **WHEN** the simulation is in IDLE state
- **THEN** the Stop button has `enabled=false`
- **AND** it renders with 50% opacity and not-allowed cursor
- **AND** it does NOT use a red/error color variant

#### Scenario: Resume button enabled only when PAUSED
- **WHEN** the simulation transitions to PAUSED state
- **THEN** the Resume button is `enabled=true`
- **AND** it uses default (no variant) button styling — not green

#### Scenario: Single-variant buttons look consistent
- **WHEN** all four control buttons are visible simultaneously
- **THEN** the only visual difference between them is their label text and enabled/disabled opacity

---

### Requirement: Interactive element consistency

All interactive elements in `SimulationView` (buttons, combo box, toggles, text
fields) MUST follow a single disabled-state contract:
- `setEnabled(false)` used uniformly; no ad-hoc color or visibility toggling to
  imply disabled state
- `setVisible(false)` MAY be used to hide elements that are never relevant in a given
  state (e.g., Manipulation tab only visible when PAUSED), but NOT as a substitute for
  proper disabled state

#### Scenario: Scenario combobox disabled during run
- **WHEN** a simulation is RUNNING
- **THEN** the scenario combobox is `enabled=false` (not hidden, not styled with red)

---

### Retained Requirements (unchanged from base spec)

The following requirements from the base `retro-theme` spec are retained without
modification:
- Dark palette CSS custom properties (`--anchor-bg`, `--anchor-surface`, `--anchor-text`)
- Monospace typography (`--lumo-font-family` override)
- Accent color system (`--anchor-accent-cyan`, `--anchor-accent-amber`,
  `--anchor-accent-magenta`, `--anchor-accent-green`)
- Vaadin component overrides (Lumo token overrides in `styles.css`)
- Theme annotation registration (`@Theme("anchor-retro")`)
