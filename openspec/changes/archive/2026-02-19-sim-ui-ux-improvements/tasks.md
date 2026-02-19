## 1. Turn Timing Data (sim-turn-timing — data layer)

- [x] 1.1 Add `turnDurationMs` long field to `SimulationProgress` record; update all
      construction sites to pass `0L` for non-turn phases — Spec: `sim-turn-timing` R1
- [x] 1.2 In `SimulationService.runTurn()` (or equivalent method that calls
      `executeTurnFull`), capture `System.currentTimeMillis()` before and after the
      call and set `turnDurationMs` — Spec: `sim-turn-timing` R1
- [x] 1.3 Write unit test: verify `turnDurationMs > 0` for ATTACK/ESTABLISH turns and
      `== 0` for COMPLETE phase progress events

## 2. Turn Timing UI (sim-turn-timing — UI layer)

- [x] 2.1 Add `formatDuration(long ms)` helper in `SimulationView` returning `"Xs"` /
      `"XXs"` / `"Xm Ys"` — Spec: `sim-turn-timing` R2
- [x] 2.2 Render a small timing badge on each DM conversation bubble using
      `formatDuration(progress.turnDurationMs())`; color amber if > 30 000 ms —
      Spec: `sim-turn-timing` R2
- [x] 2.3 Pass `turnDurationMs` to `AnchorTimelinePanel.addTurn()` and store it per
      turn; show `T{N} | {TurnType} | {duration}` tooltip on drift-marker cells —
      Spec: `sim-turn-timing` R3

## 3. Thinking Indicator (sim-turn-loading-indicator)

- [x] 3.1 Add `.thinking-indicator` CSS class to `styles.css` with cyan left border,
      flex layout, secondary text color — Spec: `sim-turn-loading-indicator` R2
- [x] 3.2 Create `thinkingIndicator` field in `SimulationView`: `Div` containing an
      indeterminate `ProgressBar` (narrow) + `Span` for status label —
      Spec: `sim-turn-loading-indicator` R1, R2
- [x] 3.3 Show `thinkingIndicator` via `UI.access()` after player bubble is appended
      (i.e., when a turn begins executing) — Spec: `sim-turn-loading-indicator` R1, R3
- [x] 3.4 Rotate the status label text through
      `["Generating response…", "Evaluating drift…", "Extracting propositions…"]`
      keyed by `(turnNumber % 3)` when showing the indicator —
      Spec: `sim-turn-loading-indicator` R2
- [x] 3.5 Remove `thinkingIndicator` via `UI.access()` before appending DM bubble;
      ensure no duplicate indicators — Spec: `sim-turn-loading-indicator` R1

## 4. Timeline Redesign (anchor-timeline)

- [x] 4.1 Replace existing injection-band rendering in `AnchorTimelinePanel` with a
      4 px `HorizontalLayout` of per-turn `Div` cells using `.injection-strip-cell`
      CSS class (cyan / amber) — Spec: `anchor-timeline` R1
- [x] 4.2 Add drift-marker track: one `Span` per turn using Unicode symbol (■ ◇ · ●)
      colored by verdict severity; store symbols in a per-turn list for redraw —
      Spec: `anchor-timeline` R2
- [x] 4.3 Add `.injection-strip-cell`, `.drift-marker`, `.drift-marker-confirmed`,
      `.drift-marker-contradicted`, `.drift-marker-neutral` CSS classes to `styles.css`
- [x] 4.4 Rewrite per-anchor event rows using `FlexLayout` with wrap; each event
      becomes a `.event-badge` `Span` pill (letter + color) —
      Spec: `anchor-timeline` R3
- [x] 4.5 Add `.event-badge` CSS class (pill shape, 0.7em font, colored background by
      event type) to `styles.css`; add `.anchor-row-header` for authority badge +
      anchor text + injection dot — Spec: `anchor-timeline` R3
- [x] 4.6 Add turn-duration tooltip to each drift-marker `Span` (requires
      `turnDurationMs` from task 2.3) — Spec: `sim-turn-timing` R3
- [x] 4.7 Verify: public API of `AnchorTimelinePanel` (`addTurn`, `highlightTurn`,
      `clear`) unchanged; verify `SimulationView` calls compile without modification
- [x] 4.8 Verify: timeline renders without horizontal overflow at 1280 × 800 viewport
      (manual smoke test) — Spec: `anchor-timeline` I1

## 5. Control Button UX (retro-theme)

- [x] 5.1 Remove `addThemeVariants(LUMO_SUCCESS)` from Resume button and
      `addThemeVariants(LUMO_ERROR)` from Stop button; remove any other semantic-color
      variants from control buttons — Spec: `retro-theme` R1
- [x] 5.2 Ensure all four control buttons use `setEnabled(false/true)` exclusively for
      state signaling; audit `updateControlState()` method and remove any color-toggling
      workarounds — Spec: `retro-theme` R1
- [x] 5.3 Add `scenarioComboBox.setEnabled(state == IDLE)` in `updateControlState()` —
      Spec: `retro-theme` R2
- [x] 5.4 Verify: button disabled states are visually clear via opacity alone in the
      retro theme (manual smoke test) — Spec: `retro-theme` R1

## 6. Light/Dark Mode Toggle (sim-theme-toggle)

- [x] 6.1 Add `html[theme~="light"]` block to `styles.css` overriding all
      `--anchor-*` and `--lumo-base-color` / `--lumo-body-text-color` vars to the
      light palette — Spec: `sim-theme-toggle` R2
- [x] 6.2 Add `themeToggleButton` field to `SimulationView`; place in header
      (rightmost), ghost variant, always enabled — Spec: `sim-theme-toggle` R1
- [x] 6.3 On `SimulationView` init, read `localStorage["anchor-theme"]` via
      `Page.executeJs()` and apply stored theme (attribute + button label) —
      Spec: `sim-theme-toggle` R3
- [x] 6.4 On toggle click: flip `html` `theme` attribute, update button label, write
      to `localStorage` — Spec: `sim-theme-toggle` R1, R3
- [x] 6.5 Verify: toggle survives page reload (stored preference re-applied) —
      Spec: `sim-theme-toggle` R3
- [x] 6.6 Verify: all accent colors readable (no invisible text) in light mode —
      Spec: `sim-theme-toggle` R2

## 7. Integration Verification

- [x] 7.1 Run `./mvnw.cmd test` — all tests pass
- [x] 7.2 Start app, run a scenario, confirm: timing badges appear on DM bubbles,
      thinking indicator shows and dismisses, timeline renders two-band layout
- [x] 7.3 Confirm control buttons have consistent styling and disabled opacity is
      visually apparent in all sim states (IDLE, RUNNING, PAUSED, COMPLETED)
- [x] 7.4 Toggle light mode, reload page, confirm preference is restored without flash
