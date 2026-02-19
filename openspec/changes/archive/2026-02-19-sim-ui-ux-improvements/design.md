# Design: sim-ui-ux-improvements

## Decisions

### D1 — Turn timing: timestamp at SimulationService boundary

**Decision**: Record timing in `SimulationService.runTurn()` using
`System.currentTimeMillis()` before/after the `executeTurnFull()` call, and populate
a new `turnDurationMs` field on `SimulationProgress`.

**Why not in `SimulationTurnExecutor`**: The executor has no knowledge of phase or
`SimulationProgress`; adding timing there would require threading it through multiple
return types. The service boundary is the cleanest single place.

**Data model change**: `SimulationProgress` is a record — add `turnDurationMs` (long)
field. All existing construction sites must be updated. Non-turn phases pass `0L`.

### D2 — Thinking indicator: `Div` with CSS animation, removed on response

**Decision**: Add a `thinkingIndicator` `Div` field to `SimulationView`. Show it via
`UI.getCurrent().access()` immediately after appending the player bubble; remove it
the same way immediately before appending the DM bubble.

**Status label rotation**: Use a `UI.getCurrent().access()` scheduled poll or a
`VerticalLayout` with CSS `@keyframes` opacity cycling. Simpler option: use a single
`Span` whose text is updated by the same push-thread that fires turn events — cycle
through messages in a `String[]` indexed by `(turnNumber % 3)`. No separate timer
thread needed.

**CSS**: Add `.thinking-indicator` class to `styles.css`:
```css
.thinking-indicator {
  border-left: 3px solid var(--anchor-accent-cyan);
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--lumo-secondary-text-color);
  font-size: var(--lumo-font-size-s);
}
.thinking-indicator vaadin-progress-bar {
  width: 60px;
}
```

### D3 — AnchorTimelinePanel: two-band rewrite

**Decision**: Replace the existing cell grid with three components stacked vertically:

```
[ injectionStrip: HorizontalLayout of 20px Divs (4px tall, cyan/amber) ]
[ driftTrack:     HorizontalLayout of Span markers (≥20px, symbol + tooltip) ]
[ anchorRows:     VerticalLayout of per-anchor HorizontalLayouts ]
```

All three share the same per-turn column width (`--timeline-cell-width: 24px`). The
injection strip and drift track scroll in sync via a shared `Div` with
`overflow-x: auto` wrapping all three.

**Per-anchor row**: Replace fixed-width cells with a `FlexLayout` (wrap enabled).
Each event gets a `Span` badge with `.event-badge` CSS class.

**Public API unchanged**: `addTurn(SimulationProgress)`, `highlightTurn(int)`,
`clear()` — callers in `SimulationView` need no changes.

**Existing CSS variable reuse**: Use `--anchor-accent-cyan`, `--anchor-accent-amber`,
`--anchor-accent-magenta`, `--anchor-accent-green` from the retro theme stylesheet.
New classes to add: `.injection-strip-cell`, `.drift-marker`, `.event-badge`,
`.anchor-row-header`, `.anchor-authority-dot`.

### D4 — Control buttons: remove semantic color variants

**Decision**: Remove `addThemeVariants(ButtonVariant.LUMO_SUCCESS)` from Resume and
`addThemeVariants(ButtonVariant.LUMO_ERROR)` from Stop. Apply no variant to Pause.
Run keeps `LUMO_PRIMARY`. All enabled/disabled state flows through `setEnabled()` only.

Buttons that are currently hidden via `setVisible(false)` in some states: **keep**
hiding as a UX choice (e.g., Resume only appears when paused), but never use
visibility *instead of* `setEnabled` within a state where the button should be
visible.

**Scenario combobox**: Currently it's unclear if it's disabled during a run. Add
`scenarioComboBox.setEnabled(simulationState == IDLE)` in `updateControlState()`.

### D5 — Theme toggle: `html` attribute + localStorage via executeJs

**Decision**: Dark is the default — the base `styles.css` rules apply without any
attribute on `<html>`. Light mode is activated by setting `theme="light"` on the
`<html>` element via `UI.getCurrent().getElement().setAttribute("theme", "light")`;
dark is restored by removing it.

**Why not Lumo's built-in `theme="dark"`**: Lumo's dark mode is an opt-in that assumes
light as the base. We want the opposite (dark as base, light as opt-in). Using our own
`theme="light"` sentinel keeps the CSS straightforward and avoids fighting Lumo.

**localStorage bridge**: On `SimulationView` init, call:
```java
getPage().executeJs("""
    const t = localStorage.getItem('anchor-theme');
    if (t) document.documentElement.setAttribute('theme', t);
    return t || 'dark';
""").then(String.class, theme -> applyTheme(theme));
```
On toggle:
```java
getPage().executeJs("localStorage.setItem('anchor-theme', $0)", newTheme);
```

**Flash prevention**: The `executeJs` on init runs before the Vaadin client-side
hydration completes, so the palette swap happens early enough to avoid a visible flash
for most connections. No additional `<style>` injection needed.

**Toggle button placement**: In the SimulationView header `HorizontalLayout`, rightmost
position, ghost variant, always enabled.

## Component Map

| Capability | File(s) |
|------------|---------|
| `sim-turn-timing` (data) | `SimulationProgress.java`, `SimulationService.java` |
| `sim-turn-timing` (UI) | `SimulationView.java` (bubble renderer), `AnchorTimelinePanel.java` (tooltip) |
| `sim-turn-loading-indicator` | `SimulationView.java`, `styles.css` |
| `anchor-timeline` | `AnchorTimelinePanel.java`, `styles.css` |
| `retro-theme` (buttons) | `SimulationView.java` |
| `sim-theme-toggle` | `SimulationView.java`, `styles.css` |

## Sequence: Turn timing data flow

```
SimulationService.runTurn()
  long start = System.currentTimeMillis()
  TurnResult result = executor.executeTurnFull(...)
  long duration = System.currentTimeMillis() - start
  → emit SimulationProgress(..., turnDurationMs = duration)

SimulationView.onProgress(SimulationProgress p)
  → append DM bubble with formatDuration(p.turnDurationMs()) badge
  → AnchorTimelinePanel.addTurn(p)  [duration stored for tooltip]
```

## Sequence: Thinking indicator lifecycle

```
SimulationView.onProgress(p) where p.phase() == RUNNING and p.lastDmResponse() == null
  → UI.access { conversationPanel.add(thinkingIndicator) }

SimulationView.onProgress(p) where p.lastDmResponse() != null
  → UI.access {
      conversationPanel.remove(thinkingIndicator)
      conversationPanel.add(dmBubble)
    }
```

## Sequence: Theme toggle

```
SimulationView.init()
  → Page.executeJs(read localStorage 'anchor-theme')
  → .then(theme -> applyTheme(theme))   // sets html[theme] attr + updates button label

themeToggleButton.click()
  → newTheme = currentTheme == "dark" ? "light" : "dark"
  → UI.getElement().setAttribute("theme", newTheme)  // or removeAttribute for "dark"
  → Page.executeJs(write localStorage 'anchor-theme', newTheme)
  → update button label
```

## Non-decisions (out of scope)

- LLM-level latency breakdown (per-call timing within a turn) — requires
  instrumentation inside `SimulationTurnExecutor`; deferred
- Websocket/SSE streaming of partial DM responses — not supported by Spring AI blocking
  call pattern; deferred
- Vaadin Charts or D3 timeline visualization — overkill for this change
