## 1. Foundation — CSS classes and progress dispatch infrastructure

- [x] 1.1 Add `ar-` prefixed CSS class sections to `styles.css` for shared patterns: `ar-badge`, `ar-bubble`, `ar-bubble--player`, `ar-bubble--dm`, `ar-bubble--attack`, `ar-metric-card`, `ar-section-title`, `ar-system-message`, `ar-placeholder` with data-attribute selectors for verdict, turn-type, and health variants (style-migration spec: naming convention, dynamic accent colors)
- [x] 1.2 Create `SimulationProgressListener` interface in `sim/views/` with `onTurnStarted`, `onTurnCompleted`, `onSimulationCompleted` default methods (progress-dispatch spec: listener interface)
- [x] 1.3 Create `ProgressDispatcher` utility class in `sim/views/` with listener registration and three-way dispatch logic (progress-dispatch spec: dispatcher utility)

## 2. Panel style migration — simple panels first (D4 order)

- [x] 2.1 Migrate `InterventionImpactBanner` — replace all `getStyle().set()` with CSS classes (style-migration spec: migration completeness)
- [x] 2.2 Migrate `DriftSummaryPanel` — replace inline styles on metric cards, strategy bars, assertion cards with `ar-metric-card`, `ar-badge` classes and `data-health` attributes (style-migration spec: migration completeness, dynamic accent colors)
- [x] 2.3 Migrate `ConversationPanel` — replace inline styles on bubbles, badges, headers with `ar-bubble`, `ar-badge` classes and `data-verdict`/`data-turn-type` attributes; remove hardcoded color strings from `verdictColor()`, `turnTypeColor()`, `verdictBorderColor()` methods (style-migration spec: migration completeness, dynamic accent colors)
- [x] 2.4 Migrate `AnchorTimelinePanel` — replace remaining inline styles with CSS classes, consolidate badge color constants into CSS (style-migration spec: migration completeness)

## 3. Panel style migration — complex panels

- [x] 3.1 Migrate `ContextInspectorPanel` — replace inline styles on rank bars, trust scores, anchor cards, verdict rows, prompt display, compaction details (style-migration spec: migration completeness)
- [x] 3.2 Migrate `KnowledgeBrowserPanel` — replace inline styles on graph nodes, search UI, grid styling (style-migration spec: migration completeness)
- [x] 3.3 Migrate `AnchorManipulationPanel` — replace inline styles on form sections, intervention log entries, conflict queue cards (style-migration spec: migration completeness)
- [x] 3.4 Migrate `RunHistoryDialog` and `RunInspectorView` — replace inline styles on dialog sizing, comparison layout, turn timeline (style-migration spec: migration completeness)

## 4. Chat view style migration

- [x] 4.1 Migrate `ChatView` — replace inline styles on message bubbles, sidebar panels, tool-call indicators (style-migration spec: migration completeness)

## 5. Progress dispatch refactor

- [x] 5.1 Implement `SimulationProgressListener` on `ConversationPanel` — move thinking indicator lifecycle and bubble rendering into `onTurnStarted`/`onTurnCompleted`/`onSimulationCompleted` (progress-dispatch spec: panels implement listener)
- [x] 5.2 Implement `SimulationProgressListener` on `DriftSummaryPanel` — move verdict recording into `onTurnCompleted` and results display into `onSimulationCompleted` (progress-dispatch spec: panels implement listener)
- [x] 5.3 Implement `SimulationProgressListener` on `ContextInspectorPanel` — move context trace and compaction updates into `onTurnCompleted` (progress-dispatch spec: panels implement listener)
- [x] 5.4 Implement `SimulationProgressListener` on `AnchorTimelinePanel` — move turn append and anchor events into `onTurnCompleted` (progress-dispatch spec: panels implement listener)
- [x] 5.5 Implement `SimulationProgressListener` on `KnowledgeBrowserPanel` — move context ID init and refresh into `onTurnCompleted` (progress-dispatch spec: panels implement listener)
- [x] 5.6 Refactor `SimulationView.applyProgress()` — replace direct panel calls with `dispatcher.dispatch(progress)`, retain only progress bar, status label, banner, and state transitions (progress-dispatch spec: SimulationView delegates)

## 6. Verification

- [x] 6.1 Verify zero `getStyle().set()` calls remain in all migrated files (grep verification)
- [x] 6.2 Verify all existing tests pass (`./mvnw.cmd test`)
- [ ] 6.3 Visual smoke test — confirm dark and light themes render identically to pre-migration state
