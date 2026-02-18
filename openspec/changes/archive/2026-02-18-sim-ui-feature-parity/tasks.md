# Tasks: sim-ui-feature-parity

## 1. Foundation & Engine Changes

- [x] 1.1 Create `SimControlState` enum (`IDLE`, `RUNNING`, `PAUSED`, `COMPLETED`) with transition validation method and side-effect descriptions. Add to `sim/engine/` package. *(design D2, conversation-panel spec: click-to-select, anchor-manipulation spec: PAUSED visibility)*
- [x] 1.2 Change `SimulationService.runSimulation()` signature from `boolean injectionEnabled` to `Supplier<Boolean> injectionStateSupplier`. Update the simulation loop to evaluate the supplier per-turn. Update `SimulationTurnExecutor.executeTurn()` to accept the per-turn boolean. *(design D3, BREAKING change)*
- [x] 1.3 Add `injectionState` field to `SimulationProgress` so each progress snapshot carries whether injection was on/off for that turn. Wire through from `SimulationService` loop. *(conversation-panel spec: injection state tags)*
- [x] 1.4 Add turn type and attack strategy enums: `TurnType` (`WARM_UP`, `ESTABLISH`, `ATTACK`, `DISPLACEMENT`, `DRIFT`, `RECALL_PROBE`) and `AttackStrategy` (`SUBTLE_REFRAME`, `CONFIDENT_ASSERTION`, `AUTHORITY_HIJACK`, `EMOTIONAL_OVERRIDE`, `FALSE_MEMORY_PLANT`, `TIME_SKIP_RECALL`, `DETAIL_FLOOD`). Add fields to `SimulationTurn` record. *(simulation-scenarios spec: turn types, attack strategies)*
- [x] 1.5 Update `SimulationTurnExecutor` to read `type` and `strategy` from scripted turns, generate distinct adversarial prompt patterns per strategy, and handle `DISPLACEMENT`, `DRIFT`, and `RECALL_PROBE` turn types. *(simulation-scenarios spec: turn types, attack strategies)*
- [x] 1.6 Fix `ExponentialDecayPolicy` bean registration if missing (verify `@Component` or `@Bean` in `AnchorConfiguration`). Fix thread naming in `SimulationService` executor. Remove stale Javadoc referencing deleted classes. *(bug fixes identified during design review)*
- [x] 1.7 Verify phase 1: run `./mvnw.cmd compile -DskipTests` and `./mvnw.cmd test`. All 27 existing tests must pass with the new `Supplier<Boolean>` signature and enum additions.

## 2. Trust Model

- [x] 2.1 Create `TrustScore` record in `anchor/` package with fields: `score` (double, [0.0, 1.0]), `authorityCeiling` (Authority), `promotionZone` (PromotionZone), `signalAudit` (Map<String, Double>), `evaluatedAt` (Instant). *(trust-scoring spec: TrustScore record)*
- [x] 2.2 Create `PromotionZone` enum (`AUTO_PROMOTE`, `REVIEW`, `ARCHIVE`) in `anchor/` package. *(trust-scoring spec: PromotionZone enum)*
- [x] 2.3 Add `TrustScore trustScore` field to `Anchor` record (nullable). Add `Anchor.withoutTrust(...)` static factory to minimize churn in existing construction sites. Update all existing `Anchor` construction sites (engine, promoter, tests). *(trust-scoring spec: Anchor record includes TrustScore; design R1)*
- [x] 2.4 Create `TrustSignal` SPI interface with `evaluate(PropositionNode, String contextId) -> OptionalDouble`. Implement `SourceAuthoritySignal` (DM=0.9, PLAYER=0.3, SYSTEM=1.0) and `ExtractionConfidenceSignal` (passthrough from DICE confidence). *(trust-scoring spec: TrustSignal SPI)*
- [x] 2.5 Implement `GraphConsistencySignal` (word-overlap similarity vs existing anchors, 0.5 on cold-start) and `CorroborationSignal` (single source=0.3, two=0.6, three+=0.9). *(trust-scoring spec: TrustSignal SPI)*
- [x] 2.6 Create `DomainProfile` record with `name`, `weights` (Map<String, Double>), `autoPromoteThreshold`, `reviewThreshold`, `archiveThreshold`. Define three static profiles: `SECURE`, `NARRATIVE`, `BALANCED` with weights and thresholds per spec. *(trust-scoring spec: DomainProfile record and three profiles)*
- [x] 2.7 Create `TrustEvaluator` with weighted-sum computation, absent-signal weight redistribution, zone routing against DomainProfile thresholds, and authority ceiling derivation (>=0.80 -> RELIABLE, 0.50-0.79 -> UNRELIABLE, <0.50 -> PROVISIONAL, never CANON). *(trust-scoring spec: TrustEvaluator weighted sum with zone routing)*
- [x] 2.8 Create `TrustPipeline` facade composing TrustEvaluator + all TrustSignal implementations. Single method `evaluate(PropositionNode, String contextId) -> TrustScore`. Register as Spring bean. *(trust-scoring spec: TrustPipeline facade)*
- [x] 2.9 Refactor `AnchorPromoter` to accept `TrustPipeline` and replace confidence-threshold check with trust-scored zone routing. AUTO_PROMOTE -> promote, REVIEW -> flag, ARCHIVE -> skip. *(trust-scoring spec: Integration with AnchorPromoter)*
- [x] 2.10 Write unit tests for `TrustEvaluator` (all signals present, missing signal redistribution, authority ceiling boundaries, CANON never assigned), `TrustPipeline`, and each `TrustSignal` implementation. Update existing `AnchorPromoter` tests. *(verification)*

## 3. Compaction Pipeline

- [x] 3.1 Create `CompactedContextProvider` in `assembly/` package: tracks message history size, evaluates compaction triggers (token threshold, message count, forced turns from scenario), manages in-memory summary store keyed by contextId + turn range. *(compaction spec: CompactedContextProvider)*
- [x] 3.2 Create `AnchorContentProtector` implementing `ProtectedContentProvider` SPI: protection priority = anchor rank, queries `AnchorRepository` for anchor-backing messages. *(compaction spec: AnchorContentProtector)*
- [x] 3.3 Create `PropositionContentProtector` implementing `ProtectedContentProvider` SPI: priority = `trustScore * 100` (falls back to `confidence * 100`). *(compaction spec: PropositionContentProtector)*
- [x] 3.4 Create `SimSummaryGenerator`: uses `ChatModel` to generate D&D-aware narrative summaries from a message list, stores results in memory by contextId + turn range. *(compaction spec: SimSummaryGenerator)*
- [x] 3.5 Create `CompactionDriftEvaluator`: takes before/after snapshots of protected content IDs, reports `COMPACTION_LOSS` events for missing content. *(compaction spec: CompactionDriftEvaluator)*
- [x] 3.6 Write unit tests for `CompactedContextProvider` trigger logic, `AnchorContentProtector` priority calculation, `PropositionContentProtector` fallback behavior, and `CompactionDriftEvaluator` loss detection.

## 4. Observability

- [x] 4.1 Add Maven dependencies to `pom.xml`: `embabel-agent-starter-observability`, `com.quantpulsar:opentelemetry-exporter-langfuse:0.4.0`, `opentelemetry-api`. *(observability spec: Maven dependencies present)*
- [x] 4.2 Create `ChatModelHolder` class implementing `ChatModel` (delegating pattern) with `ObservationRegistry` wired in and `switchModel(String)` for per-turn model selection. *(observability spec: ChatModelHolder with ObservationRegistry; design D9)*
- [x] 4.3 Create `SimulationLlmConfig` `@Configuration` class: wire `ChatModelHolder` bean wrapping the primary `ChatModel`. Update `SimulationTurnExecutor` and `SimulationService` constructors to accept `ChatModelHolder` instead of raw `ChatModel`. *(observability spec: SimulationLlmConfig; design D9 BREAKING)*
- [x] 4.4 Add `@Observed` annotations: `SimulationService.runSimulation()` -> `simulation.run`, `SimulationTurnExecutor.executeTurn()` -> `simulation.turn`, drift evaluator -> `simulation.drift_evaluation`. Add OTEL span attributes per turn (sim.scenario, sim.turn, sim.turn_type, sim.strategy, sim.target_fact). *(observability spec: @Observed annotations, OTEL span attributes)*
- [x] 4.5 Update `application.yml` with tracing config: `management.tracing.enabled=true`, `management.tracing.sampling.probability=1.0`, `spring.ai.chat.observations.include-input=true`, `spring.ai.chat.observations.include-output=true`, Langfuse endpoint/key config with env var defaults. *(observability spec: Application configuration, Langfuse OTEL exporter)*
- [x] 4.6 Verify phase 4: compile succeeds, existing tests pass with `ChatModelHolder` replacing raw `ChatModel` in sim path.

## 5. Simulation Scenarios

- [x] 5.1 Extend `SimulationScenario` record with nullable nested config records: `TrustConfig` (profile + weightOverrides), `CompactionConfig` (enabled, forceAtTurns, tokenThreshold, messageThreshold), `List<AssertionConfig> assertions`, `DormancyConfig` (decayRate, revivalThreshold, dormancyTurns), `List<SessionConfig> sessions` (name, startTurn, endTurn). Ensure `@JsonIgnoreProperties(ignoreUnknown = true)` is present. *(simulation-scenarios spec: Extended SimulationScenario format; design D11)*
- [x] 5.2 Update `ScenarioLoader` to parse trust profile references (resolve to `DomainProfile`), dormancy config, session boundaries (validate no overlap), and assertions list. *(simulation-scenarios spec: ScenarioLoader extensions)*
- [x] 5.3 Port first batch of scenarios (6 files): `trust-evaluation-basic.yml`, `trust-evaluation-full-signals.yml`, `adversarial-contradictory.yml`, `adversarial-poisoned-player.yml`, `adversarial-displacement.yml`, `balanced-campaign.yml`. Each with ground truths, scripted turns using turn types/strategies, and assertions. *(simulation-scenarios spec: 13 ported scenarios)*
- [x] 5.4 Port second batch of scenarios (7 files): `dungeon-of-mirrors.yml`, `dead-kingdom.yml`, `compaction-stress.yml`, `narrative-dm-driven.yml`, `dormancy-revival.yml`, `episodic-recall.yml`, `multi-session-campaign.yml`. *(simulation-scenarios spec: 13 ported scenarios)*
- [x] 5.5 Verify phase 5: `ScenarioLoader.listScenarios()` returns all 15 scenarios (13 new + 2 existing) without parsing errors. Existing `cursed-blade.yml` and `anchor-drift.yml` load unmodified.

## 6. Assertion Framework

- [x] 6.1 Create `SimulationAssertion` SPI interface with `evaluate(SimulationResult) -> AssertionResult`. Create `AssertionResult` record (`name`, `passed`, `details`). Create `AssertionConfig` record (`type`, `params` Map). *(assertion-framework spec: SimulationAssertion SPI, AssertionConfig YAML record)*
- [x] 6.2 Implement first 5 assertions: `AnchorCountAssertion`, `RankDistributionAssertion`, `TrustScoreRangeAssertion`, `PromotionZoneAssertion`, `AuthorityAtMostAssertion`. Each reads params from `AssertionConfig.params()`. *(assertion-framework spec: Nine concrete assertions)*
- [x] 6.3 Implement remaining 4 assertions: `KgContextContainsAssertion`, `KgContextEmptyAssertion`, `NoCanonAutoAssignedAssertion`, `CompactionIntegrityAssertion`. *(assertion-framework spec: Nine concrete assertions; compaction spec: CompactionIntegrityAssertion)*
- [x] 6.4 Create assertion type alias registry mapping short names ("anchor-count", "rank-distribution", etc.) to concrete implementations. Wire into `ScenarioLoader` for YAML deserialization. *(assertion-framework spec: Type alias resolution)*
- [x] 6.5 Wire assertions into `SimulationService`: evaluate all configured assertions after run completes, include `List<AssertionResult>` in `SimulationRunRecord`. *(assertion-framework spec: Assertions evaluated on completion)*
- [x] 6.6 Write unit tests for all 9 assertion implementations covering pass and fail cases.

## 7. Chat Integration Alignment

- [x] 7.1 Refactor `ChatActions` record to use the urbot `context.ai().withLlm(...).withPromptElements(...).rendering(...)` chain pattern. Verify `@Action(canRerun = true, trigger = UserMessage.class)` annotation. Ensure `ConversationAnalysisRequestEvent` is published after each response. *(chat-urbot-alignment spec: ChatActions as @EmbabelComponent record)*
- [x] 7.2 Create `ChatConfiguration` `@Configuration` class: `Chatbot` bean via `AgentProcessChatbot.utilityFromPlatform()`, `CommonTools` record wrapper, RAG `Tool` bean (if DICE Memory available), `MemoryProjector` bean. Verify Embabel 0.3.5-SNAPSHOT API availability first. *(chat-urbot-alignment spec: ChatConfiguration with AgentProcessChatbot factory; design R3)*
- [x] 7.3 Create `PropositionConfiguration` `@Configuration @EnableAsync` class: `DataDictionary.fromClasses(...)` with D&D domain schema, `LlmPropositionExtractor`, `PropositionPipeline` with reviser + `EscalatingEntityResolver` + `GraphProjector` + `GraphRelationshipPersister`. *(chat-urbot-alignment spec: PropositionConfiguration with full DICE pipeline)*
- [x] 7.4 Create `ConversationPropositionExtraction` service: `@Async @EventListener` for `ConversationAnalysisRequestEvent`, uses `PropositionIncrementalAnalyzer` with configurable windowing. *(chat-urbot-alignment spec: ConversationPropositionExtraction async event listener)*
- [x] 7.5 Create `ChunkHistoryStore` backed by Drivine/Neo4j: tracks processed conversation windows by contextId + offset range. *(chat-urbot-alignment spec: ChunkHistoryStore persistence)*
- [x] 7.6 Expand `DiceAnchorsProperties` with separate LLM configs (`chatLlm`, `extractionLlm`, `entityResolutionLlm`), extraction window size, overlap size, embedding service name. Provide defaults. Update `application.yml`. *(chat-urbot-alignment spec: DiceAnchorsProperties expansion)*
- [x] 7.7 Wire anchor promotion into extraction pipeline: after DICE extracts propositions, evaluate each via `AnchorPromoter` (now trust-gated). *(chat-urbot-alignment spec: Anchor promotion wired into extraction pipeline)*

## 8. Knowledge Browser

- [x] 8.1 Add `@Import(DiceRestConfiguration.class)` to `DiceAnchorsApplication` to activate DICE REST endpoints (`MemoryController`, `PropositionPipelineController`). *(knowledge-browser spec: DICE REST endpoint activation)*
- [x] 8.2 Create `AnchorBrowseController` `@RestController`: `GET /anchors/{contextId}` (list active anchors with rank, authority, pinned, decayType, reinforcementCount), `GET /anchors/{contextId}/{id}`, `GET /anchors/{contextId}/search?authority={level}`, `GET /anchors/{contextId}/{id}/history`. *(knowledge-browser spec: AnchorBrowseController REST endpoints)*
- [x] 8.3 Create `KnowledgeBrowserPanel` Vaadin component with three tabs: Propositions (all propositions for contextId with status/confidence filters), Anchors (with rank/authority sorting and filters for authority, rank range, pinned, decay type), Graph (entity mention network). *(knowledge-browser spec: KnowledgeBrowserPanel with tabbed layout, Anchor-specific filters)*
- [x] 8.4 Add semantic search field to `KnowledgeBrowserPanel`: delegates to `POST /memory/search`, min 3 character query, displays results ranked by similarity. *(knowledge-browser spec: Semantic search integration)*
- [x] 8.5 Scope all `KnowledgeBrowserPanel` data to current simulation contextId. *(knowledge-browser spec: Context-scoped browsing)*

## 9. Simulation UI -- Core Panels

- [x] 9.1 Extract `ConversationPanel` from `SimulationView`: standalone `VerticalLayout` subclass with `appendTurn()`, `reset()`, system message rendering. Remove inline conversation DOM management from `SimulationView`. *(conversation-panel spec: Extracted ConversationPanel component, System messages preserved)*
- [x] 9.2 Add injection state tags per turn bubble: "INJ ON" (cyan) / "INJ OFF" (amber) inline with turn number header. Add turn type badges (WARM_UP, ESTABLISH, ATTACK, etc.) with color-coded backgrounds. *(conversation-panel spec: Injection state tags, Turn type badges)*
- [x] 9.3 Add verdict-colored left borders to DM response bubbles: green (all CONFIRMED), amber (NOT_MENTIONED), magenta (CONTRADICTED), neutral gray (no evaluation). *(conversation-panel spec: Verdict-colored left borders)*
- [x] 9.4 Add click-to-select turn listeners on turn bubbles: dispatch turn-selection event, highlight selected bubble, deselect previous. *(conversation-panel spec: Click-to-select turn listeners)*
- [x] 9.5 Update `ContextInspectorPanel`: add trust score display on anchor cards (composite score %, zone badge, expandable signal audit). Add "Browse" link per anchor card navigating to KnowledgeBrowserPanel. *(trust-scoring spec: Trust score display in ContextInspectorPanel; knowledge-browser spec: Link from ContextInspectorPanel)*
- [x] 9.6 Add Compaction tab to `ContextInspectorPanel`: trigger reason, token savings, protected content list, summary preview (200 chars), duration. Show "No compaction on this turn." when N/A. *(compaction spec: Compaction tab in ContextInspectorPanel)*
- [x] 9.7 Create `DriftSummaryPanel`: 3x2 CSS grid with six metrics (survival rate, contradiction count, major drift count, mean first drift turn, attribution accuracy, absorption rate). Visible after COMPLETED. Include assertion results section below the grid (pass/fail badges). *(drift-summary spec: all requirements; assertion-framework spec: Assertion results display in UI)*
- [x] 9.8 Wire `SimulationView.applyProgress()` to dispatch updates to `ConversationPanel`, `DriftSummaryPanel`, and `ContextInspectorPanel` via direct method calls (no event bus). *(design D1, R7)*

## 10. Simulation UI -- Advanced Panels

- [x] 10.1 Create `AnchorManipulationPanel`: visible only in PAUSED state, loads active anchors for contextId. Rank editing sliders [100, 900] with `AnchorRepository.updateRank()`. Pin toggle per anchor. Archive button (confirmation for pinned anchors). *(anchor-manipulation spec: Panel visibility, Rank editing, Pin toggle, Archive button)*
- [x] 10.2 Add inject-new-anchor form to `AnchorManipulationPanel`: text area (required), initial rank slider (default 500), authority combo (PROVISIONAL, UNRELIABLE, RELIABLE -- no CANON). Submit creates PropositionNode and calls `AnchorEngine.promote()`. *(anchor-manipulation spec: Inject new anchor form)*
- [x] 10.3 Add conflict queue display to `AnchorManipulationPanel`: list detected conflicts with both anchor texts and resolver recommendation. Accept/dismiss actions. *(anchor-manipulation spec: Conflict queue display)*
- [x] 10.4 Add intervention log to `AnchorManipulationPanel`: records timestamp, action type (RANK_CHANGE, ARCHIVE, PIN_TOGGLE, INJECT, CONFLICT_RESOLVE), target anchor ID, before/after values. Persists across re-renders within pause session. *(anchor-manipulation spec: Intervention log)*
- [x] 10.5 Create `AnchorTimelinePanel`: horizontal injection state band (cyan=ON, amber=OFF per turn), progressive rendering during run. Drift markers by turn type (diamond=drift/attack, square=establish, dot=warm-up) colored by verdict. *(anchor-timeline spec: Horizontal injection state band, Drift markers by turn type)*
- [x] 10.6 Add per-anchor event rows to `AnchorTimelinePanel`: one row per tracked anchor showing lifecycle events (creation, reinforcement, decay, archive) at turn positions. Support horizontal scroll and auto-scroll on new turn. *(anchor-timeline spec: Per-anchor event rows, Timeline scroll and zoom)*
- [x] 10.7 Wire cross-panel turn selection: clicking a turn on timeline or conversation panel updates ContextInspectorPanel, highlights selected turn. *(anchor-timeline spec: Cross-panel turn selection; conversation-panel spec: Click-to-select)*
- [x] 10.8 Create `InterventionImpactBanner`: displays intervention count and anchor count delta after resume from PAUSED. Styled with green (+ anchors) / magenta (- anchors). Dismissible via close button, auto-dismiss on next turn or 10s timeout. *(sim-intervention spec: all requirements)*
- [x] 10.9 Restructure `SimulationView` layout per design D1: header row, InterventionImpactBanner, SplitLayout (left: ConversationPanel + DriftSummaryPanel, right: TabSheet with ContextInspectorPanel, AnchorTimelinePanel, AnchorManipulationPanel, KnowledgeBrowserPanel), status bar. Integrate `SimControlState` enum to drive all visibility/enable state via `transitionTo()`. *(design D1, D2)*

## 11. Run History

- [x] 11.1 Create `SimulationRunRecord` record: `runId`, `scenarioId`, `startedAt`, `completedAt`, `turnSnapshots` (per-turn anchor state, verdicts, context trace, intervention log), `resilienceReport`, `interventionCount`, `finalAnchorState`, `injectionEnabled`, `assertionResults`. *(run-history spec: SimulationRunRecord)*
- [x] 11.2 Create `SimulationRunStore`: `LinkedHashMap`-backed LRU with 50-entry eviction. Methods: `save()`, `get()`, `list()`, `delete()`. *(run-history spec: SimulationRunStore; design D6)*
- [x] 11.3 Wire `SimulationService` to save a `SimulationRunRecord` to the store upon simulation completion. *(run-history spec: Store run on completion)*
- [x] 11.4 Create `RunHistoryDialog`: Vaadin dialog listing stored runs (scenario, date, turns, resilience %, interventions). Actions per row: Inspect, Delete (with confirmation), Compare (checkbox for 2-run selection). Accessible via "History" button in `SimulationView`. *(run-history spec: RunHistoryDialog)*
- [x] 11.5 Create `RunInspectorView` at `@Route("run")` with `runId` query param: sidebar turn list, tabbed main area (Conversation, Prompt, Anchors, Drift, Attack, Compaction). Turn selection updates all tabs. Invalid runId shows error. *(run-history spec: RunInspectorView)*
- [x] 11.6 Add in-run anchor diff to `RunInspectorView`: compare anchor state between two turns (added, removed, rank/authority/pin changes). Add cross-run comparison mode via `?compare={runId2}` query param with side-by-side layout. *(run-history spec: In-run anchor diff, Cross-run comparison)*

## 12. Retro Theme

- [x] 12.1 Create theme directory `frontend/themes/anchor-retro/styles.css` with dark palette CSS custom properties (`--anchor-bg: #0a0a0f`, `--anchor-surface: #12121a`, `--anchor-text: #e0e0e0`), monospace font stack override (`--lumo-font-family`), and four accent colors (`--anchor-accent-cyan`, `--anchor-accent-amber`, `--anchor-accent-magenta`, `--anchor-accent-green`). *(retro-theme spec: Dark palette, Monospace typography, Accent color system)*
- [x] 12.2 Override Vaadin Lumo tokens (`--lumo-base-color`, `--lumo-body-text-color`, `--lumo-header-text-color`, `--lumo-secondary-text-color`, `--lumo-primary-color`, `--lumo-primary-text-color`, `--lumo-error-color`, `--lumo-success-color`, `--lumo-contrast` variants) in `styles.css`. Ensure buttons, progress bars, tabs, combos, text fields render correctly. *(retro-theme spec: Vaadin component overrides)*
- [x] 12.3 Add `@Theme("anchor-retro")` annotation to `DiceAnchorsApplication`. *(retro-theme spec: Theme annotation registration)*

## 13. Navigation & Integration

- [x] 13.1 Create navigation component (AppLayout or shared header) with links between SimulationView (`/`), ChatView (`/chat`), and RunInspectorView (`/run`). *(cross-view navigation)*
- [x] 13.2 Add propositions sidebar panel to `ChatView`: displays extracted propositions and promoted anchors for the chat context with confidence, rank, authority, and promotion status. Updates after each extraction cycle. *(chat-urbot-alignment spec: Propositions panel in ChatView)*
- [x] 13.3 Wire cross-panel linking: anchor "Browse" links in ContextInspectorPanel navigate to KnowledgeBrowserPanel filtered to that anchor's ID. Scenario combo box groups scenarios by category with headers. *(knowledge-browser spec: Link from ContextInspectorPanel; simulation-scenarios spec: Scenario categories in UI)*

## 14. Verification

- [x] 14.1 Full build verification: `./mvnw.cmd clean compile -DskipTests` succeeds with all new classes, configurations, theme directory, and Maven dependencies.
- [x] 14.2 Test verification: `./mvnw.cmd test` passes all existing tests (27+) plus new tests for trust model, assertions, and compaction. No test regressions.
- [x] 14.3 Backward compatibility check: existing `cursed-blade.yml` and `anchor-drift.yml` load and execute without modification via `ScenarioLoader`.
- [x] 14.4 End-to-end walkthrough: start app with `docker-compose up -d` + `./mvnw.cmd spring-boot:run`, verify SimulationView loads with all 15 scenarios in combo box, run a scenario with injection toggle mid-run, pause/manipulate/resume flow works, DriftSummaryPanel populates on completion, RunHistoryDialog shows completed run, ChatView with propositions panel functions.
