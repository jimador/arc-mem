## Requirements

### Requirement: Light/dark toggle button in simulation header

`SimulationView` SHALL display a small toggle button in the header area that switches
between dark mode (default) and light mode. The button label SHALL reflect the mode
that will be activated on click: `"☀ LIGHT"` when in dark mode, `"🌙 DARK"` when in
light mode. The button SHALL use no variant (default ghost style) and SHALL remain
enabled in all simulation states.

#### Scenario: Default state is dark
- **WHEN** the app loads with no stored preference
- **THEN** the dark palette is active and the toggle button reads `"☀ LIGHT"`

#### Scenario: Clicking toggle switches palette
- **WHEN** the user clicks the toggle button while in dark mode
- **THEN** the page switches to the light palette immediately
- **AND** the button label changes to `"🌙 DARK"`

---

### Requirement: Light palette CSS block

`styles.css` SHALL contain an `html[theme~="light"]` block that overrides the dark
anchor palette variables to a readable light variant. At minimum:

| Variable | Light value |
|----------|-------------|
| `--anchor-bg` | `#f5f0e8` (warm cream) |
| `--anchor-surface` | `#ede8df` |
| `--anchor-text` | `#1a1a2e` |
| `--anchor-accent-cyan` | `#0097a7` (darker, readable on light) |
| `--anchor-accent-amber` | `#c65100` |
| `--anchor-accent-magenta` | `#7b1fa2` |
| `--anchor-accent-green` | `#1b5e20` |
| `--lumo-base-color` | `var(--anchor-bg)` |
| `--lumo-body-text-color` | `var(--anchor-text)` |

All other UI elements that use `var(--anchor-*)` or `var(--lumo-*)` tokens SHALL
automatically adapt without per-component Java changes.

#### Scenario: Light palette applied
- **WHEN** `html` has `theme="light"`
- **THEN** the page background is the warm cream color and text is dark

#### Scenario: Accent colors readable in both modes
- **WHEN** either mode is active
- **THEN** accent-colored text (cyan, amber, magenta, green) meets a minimum 3:1
  contrast ratio against the surface background

---

### Requirement: Preference persisted to localStorage

The selected theme SHALL be saved to `localStorage` under the key `"anchor-theme"`
(`"dark"` or `"light"`) via `Page.executeJs()`. On page load, the stored value SHALL
be read and applied before the first render, preventing a flash.

#### Scenario: Preference survives page reload
- **WHEN** the user switches to light mode and reloads the page
- **THEN** light mode is active without the user toggling again

#### Scenario: No stored preference defaults to dark
- **WHEN** `localStorage["anchor-theme"]` is absent or null
- **THEN** dark mode is active
