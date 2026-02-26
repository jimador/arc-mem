## Why

The SimulationView layout doesn't work well on a 14" MacBook screen. The DriftSummaryPanel renders below the ConversationPanel on completion, pushing the conversation off-screen. The controls row crams a scenario dropdown, checkbox, two spinners, and five buttons (most disabled) into one line. The Scenario Brief panel consumes ~200px of vertical space above the split. A Benchmark tab is embedded in the right panel despite having its own dedicated route.

## What Changes

- Move DriftSummaryPanel from below the conversation into a "Results" tab in the right TabSheet (auto-selected on completion)
- Move Scenario Brief into a "Scenario Details" tab in the right TabSheet (selected by default before run)
- Remove Benchmark tab from SimulationView right panel
- Group Scenario ComboBox dropdown by category (adversarial, baseline, dormancy, etc.)
- Split controls into two rows: config fields on top, numeric fields + action buttons below
- Show only contextually relevant buttons per simulation state (IDLE: Run/History, RUNNING: Pause/Stop, PAUSED: Resume/Stop/History, COMPLETED: Run/History)
- Add Chat and Benchmark nav links to header row alongside theme toggle (right-aligned)
- Implement tab auto-selection: Scenario Details on load, Context Inspector on run start, Results on completion, Manipulation on pause

## Capabilities

### New Capabilities

- `simulation-view-layout`: Layout structure, tab organization, controls arrangement, and responsive behavior of the SimulationView page

### Modified Capabilities

- `conversation-panel`: DriftSummaryPanel no longer stacks below conversation; conversation gets full column height
- `drift-summary`: DriftSummaryPanel moves from left column to a right-panel tab
- `simulation-scenarios`: Scenario ComboBox grouped by category property
- `sim-theme-toggle`: Theme toggle moves to header row alongside new nav links

## Impact

- **SimulationView.java** — Major restructure: header row, controls layout, tab organization, button visibility logic, tab auto-selection
- **DriftSummaryPanel.java** — No internal changes, but parented differently (tab content instead of left column child)
- **ConversationPanel.java** — Minimal change: remove DriftSummaryPanel sibling, gains full column height
- **CSS (styles.css)** — Controls row styling, button visibility, grouped ComboBox styling
- **No new routes, no persistence changes, no API changes**
