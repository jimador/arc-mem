## Why

The simulation UI has accumulated 74 inline `getStyle().set()` calls across 12 view files, with almost no CSS class usage. Styling logic is tangled into Java component constructors, making the code hard to read and impossible to target with media queries or theme variations. The `applyProgress()` method in SimulationView is a 50-line god-method dispatching to 6 panels — every new panel requires touching this orchestrator. These structural issues block any future responsive or layout work (responsive redesign, mobile support) and make the existing codebase harder to maintain.

Cleaning up these two concerns first gives us the foundation to tackle responsiveness cheaply later, without a rewrite.

## What Changes

### P0: Inline styles to CSS classes

- Migrate all `getStyle().set()` calls across sim and chat views to named CSS classes in `frontend/themes/anchor-retro/styles.css`
- Replace `component.getStyle().set("font-size", "var(--lumo-font-size-s)")` patterns with `component.addClassName("...")` using semantic class names
- Consolidate duplicated style patterns (e.g., metric card styling, turn bubble borders, section headers) into reusable classes
- Preserve the existing anchor-retro aesthetic exactly — same palette, same spacing, same visual feel

### P1: Decompose `applyProgress()` dispatch

- Replace the monolithic `applyProgress()` method with a listener/observer pattern
- Each panel registers interest in the progress events it cares about and handles its own update logic
- SimulationView becomes a thin orchestrator: starts simulation, routes lifecycle events, manages state machine — but does not reach into panel internals

### Scope boundaries

- No functional changes — anchor engine, persistence, sim execution, chat flow all untouched
- No layout changes — same SplitLayout, same TabSheet structure, same panel placement
- No new dependencies
- No responsive/mobile work yet — that's a follow-up change once the CSS class foundation is in place

## Capabilities

### New Capabilities

- `style-migration`: Defines the contract for migrating inline Vaadin styles to CSS classes — naming conventions, class organization in `styles.css`, and the rule that no new inline styles should be added.
- `progress-dispatch`: Defines the observer pattern for simulation progress updates — how panels subscribe to progress events, what events exist, and how SimulationView delegates instead of dispatching directly.

### Modified Capabilities

None. This change modifies code organization, not behavior.

## Impact

- **All sim view files** (inline style migration):
  - `SimulationView.java` — inline styles + `applyProgress()` decomposition
  - `ConversationPanel.java` — inline styles (turn bubble rendering, badges)
  - `DriftSummaryPanel.java` — inline styles (metric cards, progress bars)
  - `ContextInspectorPanel.java` — inline styles (rank bars, trust scores, tabs)
  - `AnchorTimelinePanel.java` — inline styles (timeline bands, event badges)
  - `KnowledgeBrowserPanel.java` — inline styles (grid styling, search, graph)
  - `AnchorManipulationPanel.java` — inline styles (sections, forms, log entries)
  - `InterventionImpactBanner.java` — inline styles (banner layout)
  - `RunHistoryDialog.java` — inline styles (dialog sizing)
  - `RunInspectorView.java` — inline styles (comparison layout, tabs)
- **Chat view files** (inline style migration only):
  - `ChatView.java` — inline styles (message bubbles, sidebar)
- **CSS file**:
  - `frontend/themes/anchor-retro/styles.css` — new classes added, existing rules preserved
- **No changes** to: anchor engine, persistence, sim execution, DICE integration, or any non-view code
