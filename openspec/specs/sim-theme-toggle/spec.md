## Requirements

### Requirement: Theme toggle placement
The light/dark toggle button SHALL be positioned at the far right of the header row, alongside Chat and Benchmark navigation links. The toggle SHALL display "☀ LIGHT" or "🌙 DARK" based on current theme. Theme preference SHALL be persisted to `localStorage` under key `"anchor-theme"`.

#### Scenario: Toggle in header with nav links
- **WHEN** the SimulationView loads
- **THEN** the theme toggle appears at the far right of the header row, after the Chat and Benchmark navigation links

#### Scenario: Theme persistence
- **WHEN** the user toggles the theme
- **THEN** the preference is saved to localStorage and applied without page reload

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
