## Why

The simulation UI gives no feedback on performance (how long each turn takes), goes
completely silent while waiting for the LLM, and shows a Timeline panel that is
visually cluttered compared to the cleaner two-band layout in the tor reference app.
Control buttons use color as the sole disabled-state signal, which is inconsistent and
functionally confusing. These gaps make the sim harder to use for benchmarking and
harder to trust during a run.

## What Changes

- **Turn timing**: capture per-turn wall-clock duration in `SimulationProgress` and
  render it as a small badge on each conversation bubble and as a tooltip/label on
  timeline cells.
- **Thinking indicator**: show an animated progress component (spinner + status label)
  in the conversation panel while a turn is in flight; dismiss it when the DM response
  arrives.
- **Timeline redesign**: replace the monolithic single-band cell grid with the
  tor-style two-band layout — a 4 px injection strip above a drift-marker track,
  followed by per-anchor event rows using flex-wrap badges instead of fixed-width cells.
- **Control button UX**: unify disabled-state signaling across Run/Pause/Resume/Stop
  using Vaadin's standard `setEnabled(false)` + Lumo opacity, removing the semantic
  color overload (green for Resume, red for Stop, etc.) and replacing with a single
  consistent button variant scheme.
- **Light/dark mode toggle**: add a persistent theme toggle that switches between the
  default dark (retro terminal) palette and a readable light (retro phosphor) palette;
  preference saved to `localStorage` and restored on reload.

## Capabilities

### New Capabilities

- `sim-theme-toggle`: Persistent light/dark mode toggle button in the header; dark
  is the default (retro terminal palette), light inverts to a warm cream/phosphor
  palette; preference saved to `localStorage`.
- `sim-turn-timing`: Per-turn wall-clock duration captured in `SimulationProgress`
  and exposed in the conversation panel (timing badge per DM bubble) and timeline
  (duration tooltip on drift-marker cell).
- `sim-turn-loading-indicator`: Animated thinking component shown in the conversation
  panel between the player message submission and the DM response arrival; includes a
  pulsing spinner and rotating status label ("Generating response…", "Evaluating
  drift…", "Extracting propositions…").

### Modified Capabilities

- `anchor-timeline`: Redesign `AnchorTimelinePanel` to match tor's two-band layout:
  thin (4 px) injection strip + 20 px drift-marker track (symbols + severity colors)
  as the header, then per-anchor rows using flex-wrap badge cells rather than
  fixed-width pixel grids. Requirement change: cells MUST be readable at default zoom
  without horizontal scroll on ≥1280 px viewport.
- `retro-theme`: Control button disabled-state contract — buttons MUST use
  `setEnabled(false)` as the primary disabled mechanism (Lumo renders opacity 0.5 +
  not-allowed cursor automatically); semantic colors (LUMO_SUCCESS for Resume,
  LUMO_ERROR for Stop) MUST be replaced with a single variant scheme so that color
  carries contextual meaning only, not state meaning. Interactive element consistency
  pass across `SimulationView`.

## Impact

- `SimulationProgress` record: add `turnDurationMs` long field (0 for non-turn phases)
- `SimulationService`: record `System.currentTimeMillis()` before/after
  `executeTurnFull()` and populate `turnDurationMs`
- `AnchorTimelinePanel`: near-complete rewrite of cell rendering; public API unchanged
- `SimulationView`: add thinking indicator `Div` with `@Push` update calls; restyle
  control buttons; wire `turnDurationMs` to conversation bubble renderer
- `SimulationView`: add theme toggle `Button` to header; `Page.executeJs()` calls for
  `localStorage` read (on init) and write (on toggle); `UI.getElement()` attribute flip
- `styles.css`: `html[theme~="light"]` block overriding anchor palette vars for light mode
- No new Java dependencies; no new Vaadin add-ons
- Vaadin Push (`@Push`) already configured — no new dependencies
