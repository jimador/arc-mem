## Requirements

### Requirement: Dark palette CSS custom properties

The theme SHALL define CSS custom properties on `:host` in
`frontend/themes/arc-retro/styles.css` establishing a dark palette. The base
background SHALL be a near-black tone (e.g., `#0a0a0f`), the surface color SHALL be a
dark gray (e.g., `#12121a`), and the text color SHALL be a light off-white (e.g.,
`#e0e0e0`). All Vaadin Lumo overrides SHALL use these custom properties rather than
hardcoded color values.

#### Scenario: Dark background applied globally
- **WHEN** the application loads in a browser
- **THEN** the page background color matches the `--arc-bg` custom property (near-black)
- **AND** body text renders in the `--arc-text` custom property (light off-white)

#### Scenario: Surface color on panels
- **WHEN** any panel or card component renders (e.g., ContextInspectorPanel, ConversationPanel)
- **THEN** the component background uses the `--arc-surface` custom property (dark gray)

---

### Requirement: Monospace typography

The theme SHALL set the primary font family to a monospace stack (e.g., `'JetBrains
Mono', 'Fira Code', 'Cascadia Code', monospace`). The `--lumo-font-family` custom
property SHALL be overridden to this monospace stack. Font sizes SHALL preserve the
existing Lumo size scale (`--lumo-font-size-xs` through `--lumo-font-size-xl`).

#### Scenario: Monospace font in conversation bubbles
- **WHEN** a conversation bubble renders player or DM text
- **THEN** the text is displayed in the monospace font stack

#### Scenario: Monospace font in form controls
- **WHEN** a text field, combo box, or button renders
- **THEN** the control text uses the monospace font stack

---

### Requirement: Accent color system

The theme SHALL define four accent colors as CSS custom properties: cyan
(`--arc-accent-cyan`, e.g., `#00e5ff`) for injection-ON state and primary actions,
amber (`--arc-accent-amber`, e.g., `#ffab00`) for injection-OFF state and warnings,
magenta (`--arc-accent-magenta`, e.g., `#e040fb`) for contradictions and errors,
and green (`--arc-accent-green`, e.g., `#00e676`) for confirmations and CANON
authority. These accent colors SHALL be used consistently across all panels for
semantic meaning.

#### Scenario: Cyan accent on injection-enabled elements
- **WHEN** a UI element indicates injection is ON (e.g., injection toggle, timeline band)
- **THEN** the element uses `--arc-accent-cyan` for its color or border

#### Scenario: Magenta accent on contradiction verdicts
- **WHEN** a verdict card displays a CONTRADICTED result
- **THEN** the card border and verdict badge use `--arc-accent-magenta`

---

### Requirement: Vaadin component overrides

The theme SHALL override Vaadin Lumo theme tokens to integrate the dark palette. At
minimum, the following Lumo tokens MUST be overridden: `--lumo-base-color`,
`--lumo-body-text-color`, `--lumo-header-text-color`, `--lumo-secondary-text-color`,
`--lumo-primary-color`, `--lumo-primary-text-color`, `--lumo-error-color`,
`--lumo-success-color`, and `--lumo-contrast` variants. Buttons, progress bars, tabs,
combo boxes, and text fields SHALL render consistently with the dark palette without
requiring per-component style overrides in Java code.

#### Scenario: Button renders with dark theme
- **WHEN** a primary-variant Button renders
- **THEN** it uses the overridden `--lumo-primary-color` from the retro theme (cyan-tinted)
- **AND** the button text is legible against the button background

#### Scenario: Progress bar uses theme colors
- **WHEN** a ProgressBar renders with the retro theme active
- **THEN** the bar track uses the surface color and the value indicator uses a theme accent color

---

### Requirement: Control button disabled state via setEnabled

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

### Requirement: Theme annotation registration

The `ArcMemApplication` class SHALL be annotated with `@Theme("arc-retro")` to
activate the custom theme directory. The theme directory SHALL be located at
`frontend/themes/arc-retro/` and SHALL contain at minimum a `styles.css` file with
all custom property definitions and Lumo overrides.

#### Scenario: Theme directory structure
- **WHEN** the application builds successfully
- **THEN** the directory `frontend/themes/arc-retro/styles.css` exists and is
  included in the Vaadin frontend bundle

#### Scenario: Theme annotation present
- **WHEN** `ArcMemApplication.java` is inspected
- **THEN** it contains a `@Theme("arc-retro")` annotation
