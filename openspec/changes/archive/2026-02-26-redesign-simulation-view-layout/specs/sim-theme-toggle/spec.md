## MODIFIED Requirements

### Requirement: Theme toggle placement
The light/dark toggle button SHALL be positioned at the far right of the header row, alongside Chat and Benchmark navigation links. The toggle SHALL display "☀ LIGHT" or "🌙 DARK" based on current theme. Theme preference SHALL be persisted to `localStorage` under key `"anchor-theme"`.

#### Scenario: Toggle in header with nav links
- **WHEN** the SimulationView loads
- **THEN** the theme toggle appears at the far right of the header row, after the Chat and Benchmark navigation links

#### Scenario: Theme persistence
- **WHEN** the user toggles the theme
- **THEN** the preference is saved to localStorage and applied without page reload
