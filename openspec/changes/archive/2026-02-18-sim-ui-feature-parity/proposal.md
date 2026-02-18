## Why

dice-anchors was extracted from tor as a standalone demo, but the Vaadin UI was simplified to 3 files vs tor's 12. The chat integration diverges from the established urbot/impromptu patterns that Embabel and DICE users expect. People familiar with those projects should be able to dive right in — the demo MUST follow urbot conventions for ChatActions, ChatConfiguration, PropositionConfiguration, and event flow, then layer Anchor concepts on top. The simulation UI needs feature parity with tor's anchor-sim to demonstrate the full anchor drift resistance story.

## What Changes

### Simulation UI Parity (tor anchor-sim alignment)

- Add `AnchorManipulationPanel` — editable anchor table visible during PAUSED state (rank editing, archive, pin toggle, inject anchor, intervention log)
- Add `AnchorTimelinePanel` — horizontal injection state band (cyan ON / amber OFF), drift markers (diamond/square/dot by turn type), per-anchor event rows
- Add `DriftSummaryPanel` — 6-stat CSS grid (survival rate, contradictions, major count, mean first drift, attribution, absorption)
- Add `ConversationPanel` — extract conversation rendering from SimulationView into its own panel with injection state tags (ON/OFF), verdict-colored left borders (green/amber/magenta), turn type badges
- Add `SimControlState` enum — formalize IDLE/RUNNING/PAUSED/COMPLETED state machine
- Add `InterventionImpactBanner` — dismissible banner after resume showing intervention count and anchor count delta
- Add retro theme at `frontend/themes/anchor-retro/` — dark palette, monospace typography, accent colors, Vaadin component overrides
- Fix injection toggle to use `Supplier<Boolean>` so mid-run toggling takes effect on the next turn
- Fix authority badge colors: CANON=green, RELIABLE=cyan, UNRELIABLE=amber, PROVISIONAL=steel gray
- Add ghost mode to ContextInspectorPanel — muted "SUPPRESSED" styling when injection is disabled
- Add contradiction detail view to ContextInspectorPanel — targeted facts, drift strategy, DM response, evaluator verdict, anchor attribution
- Add navigation between SimulationView (`/`) and ChatView (`/chat`)

### Chat Integration Alignment (urbot pattern adoption)

- Refactor `ChatActions` to follow urbot record pattern: `@EmbabelComponent` record with `@Action(canRerun = true, trigger = UserMessage.class)` for `respond()`, separate `bindUser()` action, `context.ai().withLlm(...).withPromptElements(...).withTools(...).rendering(...)` chain
- Refactor `ChatConfiguration` to follow urbot pattern: `AgentProcessChatbot.utilityFromPlatform(...)` factory, `CommonTools` record wrapper, `@Bean Tool` for RAG (TryHyDE hint via `ToolishRag`), `@Bean MemoryProjector`
- Refactor `PropositionConfiguration` to follow urbot pattern: `@EnableAsync`, `DataDictionary.fromClasses(...)` for D&D schema, `LlmPropositionExtractor` with template, `PropositionPipeline` with reviser, `EscalatingEntityResolver`, `GraphProjector`, `GraphRelationshipPersister`
- Add `ConversationPropositionExtraction` service — `@Async @EventListener` on `ConversationAnalysisRequestEvent` using `PropositionIncrementalAnalyzer` with windowing
- Add `ChunkHistoryStore` (Drivine-backed) — track processed conversation windows to prevent duplicate extraction
- Publish `ConversationAnalysisRequestEvent` from ChatActions after every response (urbot pattern)
- Add `DiceAnchorsProperties` fields following urbot's `ImpromptuProperties` pattern: separate LLM configs for chat/extraction/entity resolution, extraction window config, embedding service name
- Wire anchor promotion into the extraction pipeline — after DICE extracts propositions, evaluate for anchor promotion via `AnchorPromoter`
- Add propositions panel to ChatView — sidebar or collapsible showing extracted propositions and promoted anchors

### Knowledge Browser (DICE REST + Anchor extensions)

- Enable DICE REST endpoints via `@Import(DiceRestConfiguration.class)` — activates `MemoryController` (list/get/search/delete propositions by contextId) and `PropositionPipelineController` (extract from text/file) out of the box
- Add `AnchorBrowseController` — REST endpoints extending DICE's proposition browsing with anchor-specific fields (rank, authority, pinned, decayType, reinforcementCount). Endpoints: list active anchors, get anchor by ID, search anchors by authority level, get anchor history (reinforcement/decay events)
- Add `KnowledgeBrowserPanel` to the simulation review UI — Vaadin panel for browsing Neo4j propositions and anchors within a simulation context. Tabbed layout: Propositions tab (all DICE propositions for the contextId with status/confidence filters), Anchors tab (active anchors with rank/authority sorting), Graph tab (entity mention network visualization showing which entities are connected through propositions)
- Add anchor-specific filters: filter by authority level, rank range, pinned status, decay type
- Semantic search integration — text field that delegates to DICE's `POST /memory/search` (vector similarity) to find related propositions/anchors
- Link from ContextInspectorPanel anchor cards to KnowledgeBrowserPanel — click an anchor to see its full proposition history, mentions, and graph neighborhood

### Trust Model (rebuilt on dice-anchors' Anchor foundation)

dice-anchors already has `Anchor`, `Authority`, `AnchorEngine`, `AnchorPromoter`, and `ConflictDetector`. The trust model layers a composite scoring system on top of this existing foundation — it does NOT replace the anchor engine, it feeds into it.

- Add `TrustScore` record to the `anchor` package — composite score [0.0, 1.0], authority ceiling, promotion zone, signal audit map, evaluation timestamp. Integrates as a field on `Anchor` (extending the existing record)
- Add `PromotionZone` enum — `AUTO_PROMOTE`, `REVIEW`, `ARCHIVE`. Routes propositions after trust evaluation
- Add `TrustSignal` SPI (interface) with 4 implementations built for this repo:
  - `SourceAuthoritySignal` — DM=0.9, PLAYER=0.3, SYSTEM=1.0 (maps to dice-anchors' existing Authority enum levels)
  - `ExtractionConfidenceSignal` — passes through DICE extraction confidence
  - `GraphConsistencySignal` — word-overlap heuristic against existing anchors (uses `AnchorRepository` queries, not a separate KG)
  - `CorroborationSignal` — multi-source count from proposition sourceIds
- Add `DomainProfile` record — named weight configurations with thresholds. Ship 3 profiles: SECURE (graph-heavy, strict thresholds), NARRATIVE (source-heavy, permissive), BALANCED (equal weights)
- Add `TrustEvaluator` — weighted sum with absent-signal redistribution, zone routing via profile thresholds. Uses dice-anchors' `AnchorEngine` for context (existing anchors), not a separate fact store
- Add `TrustPipeline` — facade composing evaluator + zone router. Wires into `AnchorPromoter` — replaces the current confidence-threshold promotion with trust-scored promotion
- Add trust profile selection to simulation YAML — `trustEvaluation` section with profile name and optional weight overrides
- Add trust score display to ContextInspectorPanel — per-anchor trust breakdown showing individual signal contributions
- Add trust-based assertions for simulation scenarios — `trust-score-range`, `no-canon-auto-assigned`, `authority-at-most`, `promotion-zone` checks

### Simulation Scenarios (rebuilt for dice-anchors format)

dice-anchors has only 2 scenarios (`cursed-blade.yml`, `anchor-drift.yml`). tor has 15+ covering trust evaluation, dormancy, episodic recall, multi-session, compaction stress, and 7 distinct attack strategies. These need to be rebuilt to work with dice-anchors' `SimulationScenario` record and `ScenarioLoader`.

- Port and adapt 13 scenarios from tor, grouped by capability they exercise:
  - **Trust evaluation**: `trust-evaluation-basic`, `trust-evaluation-full-signals` — exercise trust pipeline with domain profiles
  - **Adversarial (core)**: `adversarial-contradictory`, `adversarial-poisoned-player`, `adversarial-displacement` — test contradiction resistance, poisoned sources, volume displacement
  - **Adversarial (complex)**: `dungeon-of-mirrors` (25 turns, 8 facts, 7 drift strategies), `dead-kingdom` (20 turns, political intrigue), `compaction-stress` (forced compaction at specific turns)
  - **Non-adversarial**: `balanced-campaign`, `narrative-dm-driven` — baseline runs with trust profile testing
  - **Dormancy/recall**: `dormancy-revival`, `episodic-recall`, `adversarial-dormancy-exploitation` — topic decay and revival mechanics
  - **Multi-session**: `multi-session-campaign` (30 turns, 3 named sessions)
- Adapt scenario YAML format as needed for dice-anchors' `SimulationScenario` record — add fields for trust evaluation config, seed anchors, dormancy config, session structure
- Extend `ScenarioLoader` to handle new scenario features (trust profiles, seed anchors, multi-session, dormancy config)
- Extend `SimulationTurnExecutor` to support turn types (ESTABLISH, DISPLACEMENT, DRIFT, RECALL_PROBE) and attack strategies (SUBTLE_REFRAME, CONFIDENT_ASSERTION, AUTHORITY_HIJACK, EMOTIONAL_OVERRIDE, FALSE_MEMORY_PLANT, TIME_SKIP_RECALL, DETAIL_FLOOD)

### Compaction Pipeline (rebuilt on dice-anchors foundation)

dice-anchors has no compaction support. tor's anchor-sim has a full pipeline. Rebuild it on top of dice-anchors' existing `AnchorEngine` and `AnchorRepository`.

- Add `CompactedContextProvider` — wraps anchor context assembly with compaction awareness. Tracks message history, evaluates compaction triggers (token threshold, message count, forced turns), prepends summaries from an in-memory summary store
- Add `AnchorContentProtector` — `ProtectedContentProvider` SPI that protects messages backing ACTIVE anchors from being compacted. Priority = anchor rank
- Add `PropositionContentProtector` — protects messages backing unpromoted propositions (EXTRACTED/REINFORCED status). Priority = trust score * 100
- Add `SimSummaryGenerator` — D&D-aware narrative summary generation using `ChatModel`. Produces session-level summaries
- Add `CompactionDriftEvaluator` — before/after snapshot comparator detecting `COMPACTION_LOSS` drift by comparing protected content IDs across a compaction cycle
- Add compaction tab to ContextInspectorPanel — trigger reason, token savings, protected content per provider, summary preview, duration
- Add `compactionConfig` section to `SimulationScenario` — `enabled`, `forceAtTurns`, `tokenThreshold`, `messageThreshold`
- Add `CompactionIntegrityAssertion` for verifying compaction doesn't lose critical facts

### Observability (Micrometer + OTEL + Langfuse)

- Add `embabel-agent-starter-observability` Maven dependency
- Add `opentelemetry-exporter-langfuse` (0.4.0) Maven dependency
- Add `@Observed` annotations on key operations:
  - `SimulationService.runSimulation()` → `simulation.run`
  - `SimulationTurnExecutor.executeTurn()` → `simulation.turn`
  - `SimulationTurnExecutor.extractAnchors()` → `simulation.extraction`
  - Drift evaluator → `simulation.drift_evaluation`
- Add OTEL span attributes per turn: `sim.scenario`, `sim.turn`, `sim.turn_type`, `sim.strategy`, `sim.target_fact`
- Add `ChatModelHolder` — delegating `ChatModel` with `ObservationRegistry` wired in so Spring AI observations fire automatically, plus `switchModel(String)` for per-turn model selection
- Add `SimulationLlmConfig` — `@Configuration` wiring `ChatModelHolder` with `ObservationRegistry`
- Configure `application.yml`: `embabel.observability.enabled=true`, `management.tracing.enabled=true`, `sampling.probability=1.0`, `spring.ai.chat.observations.include-input/output=true`
- Configure Langfuse connection: `management.langfuse.enabled=true`, endpoint/keys with env var defaults matching docker-compose init values (`pk-lf-dev-public` / `sk-lf-dev-secret`)
- Update `docker-compose.yml` with full Langfuse v3 stack (Postgres, ClickHouse, MinIO, Redis, langfuse-worker, langfuse web) — **DONE** (already written)

### Run History and Inspector

- Add `SimulationRunStore` — in-memory (50-entry LRU) storage of completed simulation runs
- Add `SimulationRunRecord` — captures scenario, timestamps, per-turn snapshots, resilience report, interventions, final anchor state
- Add `RunHistoryDialog` — Vaadin dialog listing stored runs with scenario name, date, turn count, resilience %, intervention count. Actions: Inspect, Delete, Load as Canon, Compare (select 2 for side-by-side)
- Add `RunInspectorView` at `@Route("run")` — tabbed per-turn inspector with sidebar. Tabs: Conversation, Prompt, Anchors, Drift, Attack, Compaction. Supports in-run anchor diff (compare 2 turns) and cross-run comparison (`?compare=<runId>`)

### Assertion Framework

- Add `SimulationAssertion` SPI — `evaluate(SimulationResult) → AssertionResult(name, passed, details)`
- Add concrete assertions: `AnchorCountAssertion`, `RankDistributionAssertion`, `TrustScoreRangeAssertion`, `PromotionZoneAssertion`, `AuthorityAtMostAssertion`, `KgContextContainsAssertion`, `KgContextEmptyAssertion`, `NoCanonAutoAssignedAssertion`, `CompactionIntegrityAssertion`
- Add `AssertionConfig` record — YAML-deserializable per-scenario assertion configuration
- Add `assertions` field to `SimulationScenario` — list of `AssertionConfig` with type + params
- Display assertion results in `DriftSummaryPanel` and `RunInspectorView`

### Bug Fixes

- **BREAKING**: `SimulationService.runSimulation()` signature changes — `boolean injectionEnabled` becomes `Supplier<Boolean> injectionStateSupplier`
- **BREAKING**: `SimulationTurnExecutor` now uses `ChatModelHolder` instead of raw `ChatModel` — enables per-turn model switching and observation
- Thread naming in ChatView uses parent thread ID instead of new thread ID
- `ExponentialDecayPolicy` exists but is never registered as a bean in `AnchorConfiguration` — add `@ConditionalOnMissingBean` default
- Stale Javadoc in `PropositionConfiguration` references deleted `ConversationAnalysisListener`

## Capabilities

### New Capabilities

- `retro-theme`: Dark retro color palette, monospace typography, accent color system, Vaadin custom theme directory and CSS custom properties
- `anchor-manipulation`: Interactive anchor editing during paused simulation — rank editing, archive, pin toggle, inject anchor, conflict queue, intervention log
- `anchor-timeline`: Horizontal timeline visualization with injection state band, drift markers by turn type, per-anchor event tracking, cross-panel turn selection
- `drift-summary`: Aggregate drift metrics panel — 6-stat grid computed from evaluation verdicts and fact lifecycle data
- `conversation-panel`: Extracted conversation renderer with injection state tags, verdict-colored borders, turn type badges, turn click listeners
- `chat-urbot-alignment`: Chat integration refactored to follow urbot/impromptu patterns — ChatActions record, ChatConfiguration factory, PropositionConfiguration pipeline, async extraction, chunk history tracking
- `sim-intervention`: Intervention impact banner, intervention recording during pause, resume impact display
- `knowledge-browser`: Browsable proposition and anchor explorer in the simulation review UI — DICE REST endpoint activation, anchor-specific REST extensions, Vaadin browse panel with proposition/anchor/graph tabs, semantic search, authority/rank filters
- `trust-scoring`: Composite trust model layered on dice-anchors' existing Anchor/Authority/AnchorEngine — TrustScore record, 4 signal SPIs, 3 domain profiles (SECURE/NARRATIVE/BALANCED), TrustEvaluator with weighted sum and zone routing, TrustPipeline wired into AnchorPromoter
- `simulation-scenarios`: Full scenario catalog rebuilt for dice-anchors — 13 scenarios ported from tor covering trust evaluation, adversarial resistance (7 attack strategies), dormancy/revival, episodic recall, multi-session campaigns, compaction stress. Extended ScenarioLoader, turn types, and assertion framework
- `compaction`: Context compaction pipeline — CompactedContextProvider, content protectors for anchors and propositions, summary generation, compaction drift evaluation, compaction tab in inspector
- `observability`: Micrometer @Observed annotations on sim operations, OTEL span attributes per turn, ChatModelHolder with ObservationRegistry, Langfuse v3 OTEL exporter, docker-compose Langfuse stack
- `run-history`: In-memory run storage, RunHistoryDialog, RunInspectorView with per-turn tabbed inspector, in-run anchor diff, cross-run comparison
- `assertion-framework`: SimulationAssertion SPI, 9 concrete assertions, YAML-driven per-scenario configuration, assertion results in UI

### Modified Capabilities

- (none — dice-anchors has no existing openspec specs yet; all capabilities are new)

## Impact

### Code Changes

| Area | Files Added | Files Modified |
|------|-------------|----------------|
| Sim views | 7 new panels/components | SimulationView.java, ContextInspectorPanel.java |
| Knowledge browser | 1 REST controller, 1 Vaadin panel | DiceAnchorsApplication.java (@Import DiceRestConfiguration) |
| Chat integration | 2 new (extraction, chunk history) | ChatActions.java, ChatConfiguration.java, PropositionConfiguration.java, ChatView.java, DiceAnchorsProperties.java |
| Trust model | ~10 new files (records, signals, evaluator, profiles, pipeline) | Anchor.java (add trustScore field), AnchorPromoter.java (trust-based promotion) |
| Compaction | ~5 new files (provider, protectors, summary gen, evaluator) | ContextInspectorPanel.java (compaction tab), SimulationScenario.java |
| Observability | SimulationLlmConfig, ChatModelHolder | SimulationService.java, SimulationTurnExecutor.java (@Observed), pom.xml, application.yml |
| Run history | RunStore, RunRecord, RunHistoryDialog, RunInspectorView | SimulationService.java (save runs) |
| Assertions | ~10 new files (SPI + 9 implementations + config) | SimulationScenario.java, DriftSummaryPanel, RunInspectorView |
| Scenarios | 13 new YAML files | ScenarioLoader.java, SimulationScenario.java, SimulationTurnExecutor.java |
| Engine | 0 | SimulationService.java (Supplier signature), SimulationTurnExecutor.java (ChatModelHolder) |
| Theme | 1 CSS file + theme directory | DiceAnchorsApplication.java (@Theme annotation) |
| Model | 1 enum (SimControlState) + turn type/strategy enums | SimulationProgress.java (injection state per turn) |
| Infra | docker-compose.yml (Langfuse v3 stack) | — |

### Dependencies

| Dependency | Status |
|-----------|--------|
| `embabel-agent-starter-observability` | **Add** — Micrometer @Observed, OTEL tracing |
| `com.quantpulsar:opentelemetry-exporter-langfuse:0.4.0` | **Add** — Langfuse OTEL exporter |
| `io.opentelemetry:opentelemetry-api` | **Add** — Span attribute enrichment |
| `embabel-agent-rag-neo-drivine` | Already present — exercise TryHyDE, Memory features |
| Vaadin, DICE, Embabel Agent | Already present |

### API Changes

- **BREAKING**: `SimulationService.runSimulation(scenario, boolean, Consumer)` → `runSimulation(scenario, Supplier<Boolean>, Consumer)`
- **BREAKING**: `SimulationTurnExecutor` uses `ChatModelHolder` instead of raw `ChatModel`
- `ContextTrace` — no structural change (already has `injectionEnabled`), but inspector rendering changes significantly
- `Anchor` record — adds `TrustScore` field
- `SimulationScenario` record — adds trust config, compaction config, assertions, dormancy config, session structure
- `ChatActions` — complete rewrite to record pattern (internal, no public API impact)

### Risk

- Retro theme requires frontend build tooling (Vaadin theme directory, CSS custom properties) — may need `vite.config.ts` changes
- urbot pattern adoption for ChatActions/ChatConfiguration is a significant refactor touching the chat pipeline — must verify against Embabel 0.3.5-SNAPSHOT APIs
- AnchorManipulationPanel directly calls `AnchorEngine` during pause — must ensure thread safety with the simulation loop
- Trust model adds `TrustScore` field to `Anchor` record — requires updating all Anchor construction sites, Neo4j persistence (PropositionNode needs trust fields), and test fixtures
- Scenario format extensions (trust config, seed anchors, dormancy, multi-session) need backward compatibility with existing `cursed-blade.yml` and `anchor-drift.yml`
- GraphConsistencySignal depends on existing anchor state — cold-start scenarios with no anchors will get neutral (0.5) scores until facts accumulate
- Langfuse v3 stack is heavy (Postgres + ClickHouse + MinIO + Redis + worker + web) — consider documenting a "lite" docker-compose without Langfuse for quick starts
- Compaction pipeline depends on Embabel's `CompactionPipeline` APIs — need to verify availability in 0.3.5-SNAPSHOT
