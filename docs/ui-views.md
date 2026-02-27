# UI Views — Developer Guide

dice-anchors exposes four Vaadin routes. Architecture, state management, and key interactions for each.

## SimulationView (`/`)

**File:** `src/main/java/dev/dunnam/diceanchors/sim/views/SimulationView.java`

Primary view for running simulation scenarios, including adversarial stress-test runs that deliberately induce contradiction/hallucination pressure.

### Layout

- **Control bar** — category selector, scenario combo, injection toggle, token budget field, max turns override, Run/Pause/Resume/Stop buttons, run history button
- **Left column (55%)** — `ConversationPanel` with turn-by-turn transcript
- **Right column (45%)** — tabbed panel with 6 tabs

### Right-Panel Tabs

| Tab | Component | Purpose |
|-----|-----------|---------|
| Details | Scenario details | Title, objective, focus, highlights, setting preview |
| Context Inspector | `ContextInspectorPanel` | Per-turn anchor injection block, "Browse" to Knowledge Browser, "Browse Graph" to entity mention network |
| Timeline | `AnchorTimelinePanel` | Turn-by-turn anchor rank/count chart; click to select turn |
| Knowledge Browser | `KnowledgeBrowserPanel` | Anchor text search; deep-link from Context Inspector |
| Results | `DriftSummaryPanel` | Post-run drift statistics; auto-selected on completion |
| Manipulation | `AnchorManipulationPanel` | Manual rank/authority/pin editing; visible only when PAUSED |

### State Machine

`SimControlState`: `IDLE` → `RUNNING` → `PAUSED` → `COMPLETED`

| State | Visible Controls | Tab Selection |
|-------|-------------------|---------------|
| IDLE | Run, scenario controls enabled | Details |
| RUNNING | Pause, Stop visible; Run hidden; controls disabled | Context Inspector |
| PAUSED | Resume, Stop visible; Manipulation tab shown | Manipulation |
| COMPLETED | Run re-enabled; controls enabled | Results |

Simulation execution runs in `CompletableFuture.runAsync()`. Progress events reach the UI via `ui.access()` through a `ProgressDispatcher` that fans out `SimulationProgress` events to all panels.

### Notable Features

- **Intervention impact tracking** — on resume after pause, `InterventionImpactBanner` shows the delta in anchor count and number of manual operations performed during the pause
- **Cross-panel navigation** — clicking an anchor in Context Inspector navigates to Knowledge Browser filtered by that anchor's text, or to the entity mention graph
- **Turn-click synchronization** — clicking a turn in ConversationPanel calls `timelinePanel.selectTurn()`, keeping the timeline in sync
- **Theme toggle** — dark/light switch persisted to `localStorage['anchor-theme']`
- **Category-grouped scenarios** — scenarios grouped into a `TreeMap<String, List<SimulationScenario>>` by category

---

## ChatView (`/chat`)

**File:** `src/main/java/dev/dunnam/diceanchors/chat/ChatView.java`

Interactive chat with an anchor-aware DM powered by Embabel Agent.

### Layout

- **Header** — title ("Bigby — Your D&D Dungeon Master") + navigation links
- **Conversation bar** — conversation ID display, Copy/New/Clone buttons, resume ID input + Resume button
- **Chat area (70%)** — scrolling message feed with user/bot/error bubbles, thinking indicator, progress indicator
- **Sidebar (30%)** — 5-tab panel

### Sidebar Tabs

| Tab | Contents |
|-----|----------|
| Anchors | Live anchor cards with inline editing: rank stepper (100–900), authority dropdown, Evict button, Revision text field + Revise button; "Create Anchor" form at bottom |
| Propositions | Non-anchor propositions with confidence %, status badge, Promote button |
| Knowledge | Alternative proposition view with "Promote to Anchor" and "Add Knowledge" form |
| Session Info | Read-only: context ID, active anchor count, proposition count, turn count |
| Context | Preview of actual LLM injection: anchor block, propositions block, full rendered system prompt |

### Conversation Management

- **New** — clears session state, generates new UUID
- **Clone** — calls `ConversationService.cloneConversation()` which copies all anchor metadata to a new contextId
- **Resume** — restores message history and hydrates the Embabel `ChatSession` from Neo4j
- **Deep-link** — `/chat?conversationId=<uuid>` restores session via `BeforeEnterObserver`

### State Management

- Session state persisted in `VaadinSession` under two keys: `SessionData` (ChatSession + response queue) and active conversation ID
- Each `sendMessage()` spawns a named thread (`dice-anchors-chat-<uuid>`) that calls `chatSession.onUserMessage()` and blocks on the response queue (120s timeout)
- After each response, a background daemon thread polls the sidebar 24 times at 5-second intervals to pick up asynchronous DICE extraction results
- Anchor cards use incremental DOM updates via `Map<String, Div>` — only changed fields are re-rendered

### Anchor Tools (LLM-callable)

The LLM can invoke these tools during chat via Embabel's `@MatryoshkaTools`:

| Tool | Signature | Behavior |
|------|-----------|----------|
| `queryFacts` | `(String subject) → List<AnchorSummary>` | Semantic search over anchors (top-10, threshold 0.5) |
| `listAnchors` | `() → List<AnchorSummary>` | All active anchors by rank descending |
| `pinFact` | `(String anchorId) → PinResult` | Pin an active anchor (guards: must exist, must be active) |
| `unpinFact` | `(String anchorId) → PinResult` | Unpin (guards: must be pinned, must not be CANON) |
| `demoteAnchor` | `(String anchorId) → String` | Demote authority by one level; CANON routes through CanonizationGate |

### Revision Workflow

Inline revision in the sidebar:
1. User types revised text in the Revision field and clicks Revise
2. Routes through `AnchorMutationStrategy.evaluate()` with `MutationSource.UI`
3. `Allow` → executes `AnchorEngine.supersede()` (archives old anchor, creates new)
4. `Deny` → shows error notification
5. `PendingApproval` → shows "queued for approval" notification

### Context Initialization

`ChatContextInitializer` seeds pre-configured anchors on first use:
- Reads `dice-anchors.anchor.chat-seed` from config
- Checks existing anchors by case-insensitive text match (idempotent)
- Creates `PropositionNode` with confidence 1.0, promotes, sets authority and pinned status
- Bypasses canonization gate for seed data

---

## BenchmarkView (`/benchmark`)

**File:** `src/main/java/dev/dunnam/diceanchors/sim/views/BenchmarkView.java`

Multi-condition ablation experiments with statistical aggregation over repeated runs.

### Layout

All panels stacked vertically; visibility toggled by state:

| Panel | Purpose |
|-------|---------|
| `ExperimentConfigPanel` | Scenario and condition selection |
| `ExperimentProgressPanel` | Live progress during execution |
| `ConditionComparisonPanel` | Post-run side-by-side condition comparison |
| `FactDrillDownPanel` | Per-fact survival detail (inline within comparison) |
| `ExperimentHistoryPanel` | Persisted experiment reports |

### State Machine

`BenchmarkViewState`: `CONFIG` → `RUNNING` → `RESULTS`

| State | Visible Panels |
|-------|---------------|
| CONFIG | configPanel + historyPanel |
| RUNNING | progressPanel only |
| RESULTS | comparisonPanel + historyPanel |

### Execution

- Experiment runs in `CompletableFuture.supplyAsync()`
- Cancel calls `experimentRunner.cancel()`; experiment finishes current cell and returns with `cancelled=true`
- Hardcoded: injection enabled, token budget 4096

### Notable Features

- **Inline fact drill-down** — clicking a cell in the comparison grid reveals per-fact survival detail
- **Markdown export** — `ResilienceReportBuilder` generates downloadable Markdown reports
- **History persistence** — experiments stored in `RunHistoryStore`; can be loaded and compared
- **Transition guard** — `BenchmarkViewState.canTransitionTo()` prevents invalid state changes

---

## RunInspectorView (`/run`)

**File:** `src/main/java/dev/dunnam/diceanchors/sim/views/RunInspectorView.java`

Post-run inspection with optional cross-run comparison.

### URL Parameters

- `?runId=<id>` — load a single completed run
- `?runId=<id>&compare=<id>` — side-by-side comparison of two runs

### Layout

`SplitLayout` at 20/80:

- **Sidebar (20%)** — `ListBox<TurnSnapshot>` with verdict-colored dots (green=CONFIRMED, red=CONTRADICTED) and `Turn N — TYPE` labels
- **Main area (80%)** — 4-tab panel

### Tabs

| Tab | Contents | Mode |
|-----|----------|------|
| Conversation | Turn header, player/DM message bubbles with verdict badges; faded historical bubbles for prior turns | Both |
| Anchors | Anchor cards at selected turn: authority badge, rank bar (blue <400, orange 400–699, red ≥700), pinned badge, reinforcement count, trust score | Both |
| Drift | Per-turn verdict cards (badge + fact ID + explanation); cumulative drift summary (CONTRADICTED/CONFIRMED/NOT_MENTIONED counts, resilience rate) | Both |
| Anchor Diff | Anchor changes between previous and current turn: added (green), removed (red), changed (amber with rank/authority delta) | Single run |
| Comparison | Side-by-side: primary run left, compare run right; player message, DM response, verdict cards; full anchor diff between both runs at that turn | Cross-run |

### State Management

- All state derived at render time from `SimulationRunRecord` loaded in `beforeEnter()`
- `selectedTurnIndex` tracks current selection; changing it refreshes all tabs atomically
- No async operations — all data is already materialized in `TurnSnapshot` records
- Comparison tab vs Anchor Diff tab decided at build time based on presence of `compareRun`

### Notable Features

- **Anchor diff algorithm** — keys anchors by lowercased text; computes added/removed/changed sets
- **Cross-run turn matching** — uses `turnNumber` equality
- **Rich anchor cards** — CSS-driven rank bar with three color bands
- **Historical conversation replay** — faded prior-turn bubbles provide scrollable context
