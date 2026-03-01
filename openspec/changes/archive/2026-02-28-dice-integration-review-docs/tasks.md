## 1. Create docs/dev/dice-integration.md Skeleton

- [ ] 1.1 Create `docs/dev/dice-integration.md` with section headings: Overview, End-to-End Integration Flow, Component API Usage, Fragile Coupling Points, API Stability Assessment, Responsibility Boundaries, Monitoring Strategy
- [ ] 1.2 Add document preamble noting DICE version (0.1.0-SNAPSHOT) and last-verified date

**Verification**: File exists with all section headings.

## 2. Document End-to-End Integration Flow

- [ ] 2.1 Document chat extraction path: `ConversationAnalysisRequestEvent` -> `ConversationPropositionExtraction.onConversationExchange()` (line 83) -> `PropositionIncrementalAnalyzer.analyze()` (line 103) -> `ChunkPropositionResult` -> `PropositionResults.persist()` with fallback (lines 140-160) -> `AnchorRepository.assignContextIds()` (line 165) -> `AnchorPromoter.evaluateAndPromote()` (line 170)
- [ ] 2.2 Document simulation extraction path: `SimulationExtractionService.extract()` (line 66) -> `PropositionPipeline.process()` (line 79) -> `PropositionResults.persist()` with fallback (lines 93-105) -> `AnchorRepository.assignContextIds()` (line 113) -> `AnchorRepository.tagSourceIds()` (line 120) -> `AnchorPromoter.batchEvaluateAndPromoteWithOutcome()` (line 123)
- [ ] 2.3 Add Mermaid data flow diagrams for both paths
- [ ] 2.4 Document key difference: chat uses windowed incremental analysis; simulation uses one-shot pipeline

**Verification**: Both extraction paths documented with accurate file:line references verified against source.

## 3. Document Component API Usage

- [ ] 3.1 Document `LlmPropositionExtractor` builder pattern and bean definition at `PropositionConfiguration.java:107-118`; note custom template `dice/extract_dnd_propositions`
- [ ] 3.2 Document `LlmPropositionReviser` builder pattern and bean definition at `PropositionConfiguration.java:125-132`
- [ ] 3.3 Document `PropositionPipeline` builder pattern and bean definition at `PropositionConfiguration.java:138-146`; document `process()` method signature and `PropositionResults` contract
- [ ] 3.4 Document `PropositionIncrementalAnalyzer` constructor at `ConversationPropositionExtraction.java:73-78`; document `analyze()` method and untyped `Object` return requiring `instanceof ChunkPropositionResult` check
- [ ] 3.5 Document `SourceAnalysisContext` builder pattern used at `ConversationPropositionExtraction.java:93-96` and `SimulationExtractionService.java:72-75`

**Verification**: All method signatures verified against current source; file:line references confirmed accurate.

## 4. Document 15-Parameter Proposition.create() Fragile Point

- [ ] 4.1 Document the full 15-parameter signature with position, type, and semantics for each parameter
- [ ] 4.2 Identify unclear parameters: position 7 (double, 0.0 — possibly importance/weight), position 12 (Instant, duplicated `revised` — possibly "accessed"), position 14 (int, 0 — possibly version counter)
- [ ] 4.3 Reference call site at `PropositionView.java:81-97`
- [ ] 4.4 Assess risk as HIGH (LOW stability); document that unclear parameters suggest incomplete API likely to change
- [ ] 4.5 Document mitigation: monitor DICE releases; integration test verifying method exists and signature unchanged (deferred)

**Verification**: Parameter list matches actual `PropositionView.toDice()` implementation.

## 5. Document ChunkHistoryStore Wrapper

- [ ] 5.1 Document `DiceAnchorsChunkHistoryStore` interface at `DiceAnchorsChunkHistoryStore.java:19-42`; three methods: `delegate()`, `clearByContext()`, `clearAll()`
- [ ] 5.2 Document `InMemoryDiceAnchorsChunkHistoryStore` implementation at `InMemoryDiceAnchorsChunkHistoryStore.java:22-51`
- [ ] 5.3 Document per-context clearing limitation: `clearByContext()` at line 38 resets the entire in-memory store because `InMemoryChunkHistoryStore` does not support per-context clearing
- [ ] 5.4 Document the `PropositionConfiguration.chunkHistoryStore()` bean at `PropositionConfiguration.java:97-99` that exposes the DICE delegate
- [ ] 5.5 Assess risk as MEDIUM; document that interface contract changes force wrapper updates

**Verification**: Interface and implementation documented with accurate line references.

## 6. Document Windowed Analysis (WindowConfig)

- [ ] 6.1 Document `WindowConfig` construction at `ConversationPropositionExtraction.java:68-72` with parameters from `DiceAnchorsProperties.memory()`
- [ ] 6.2 Document parameter semantics: `windowSize` (messages in sliding window), `windowOverlap` (messages carried forward), `triggerInterval` (messages before window advances)
- [ ] 6.3 Note that these semantics are inferred from usage — upstream DICE documentation does not define them
- [ ] 6.4 Assess risk as MEDIUM; overlap/trigger intervals may be refined in future SNAPSHOT

**Verification**: WindowConfig parameters match `DiceAnchorsProperties.memory()` fields.

## 7. Create API Stability Assessment Table

- [ ] 7.1 Create table with columns: Component, Stability, Risk Description
- [ ] 7.2 Populate from R02 research findings:
  - `Proposition.create()` 15-param: LOW — unclear params suggest incomplete API
  - `PropositionResults.persist()` fallback: MEDIUM — may evolve error handling
  - `WindowConfig` semantics: MEDIUM — overlap/trigger may be refined
  - `analyze()` untyped return: MEDIUM — may get generic type parameter
  - `PropositionStatus` enum: HIGH — core model, likely stable
  - `EntityMention` fields: HIGH — core model, likely stable
  - `PropositionRepository` SPI: HIGH — interface contract stable

**Verification**: Table entries match R02-dice-api-surface.md assessments.

## 8. Document Responsibility Boundaries

- [ ] 8.1 Document what dice-anchors implements: anchor lifecycle (rank, authority, promotion, eviction), trust pipeline (multi-gate evaluation), conflict detection and resolution (composite detector), context assembly and prompt injection (AnchorsLlmReference), budget enforcement, decay and reinforcement policies
- [ ] 8.2 Document what DICE provides: proposition extraction (LlmPropositionExtractor), proposition revision and deduplication (LlmPropositionReviser), chunk history management (ChunkHistoryStore), incremental windowed analysis (PropositionIncrementalAnalyzer), entity mention extraction (EntityMention)
- [ ] 8.3 Document extension points: `PropositionRepository` SPI (implemented by `AnchorRepository`), `ChunkHistoryStore` SPI (wrapped by `DiceAnchorsChunkHistoryStore`), extraction templates (customizable Jinja at `dice/extract_dnd_propositions`)

**Verification**: Boundaries are unambiguous; no overlap or gaps in responsibility assignment.

## 9. Document Monitoring Strategy

- [ ] 9.1 Document recommended SNAPSHOT release tracking cadence (monthly or on notification)
- [ ] 9.2 Document integration test recommendations: compile-time checks for method existence; runtime checks for signature compatibility (implementation deferred)
- [ ] 9.3 Document deprecation warning detection: enable DICE deprecation warnings in Maven build
- [ ] 9.4 Document maintenance calendar: plan SNAPSHOT compatibility fixes in quarterly maintenance cycle
- [ ] 9.5 Note explicitly that integration test implementation is deferred to a future maintenance cycle (outside this feature's scope)

**Verification**: Monitoring strategy is actionable with concrete cadence and process.

## 10. Integrate into DEVELOPING.md

- [ ] 10.1 Add reference to `docs/dev/dice-integration.md` in DEVELOPING.md under appropriate section
- [ ] 10.2 Brief description of what the document covers and when to consult it

**Verification**: DEVELOPING.md updated; link resolves to the new document.

## 11. Verification

- [ ] 11.1 Verify all file:line references are accurate against current source (grep for method names at documented line numbers)
- [ ] 11.2 Verify no code changes were introduced (documentation-only)
- [ ] 11.3 Verify document structure matches design.md section layout
- [ ] 11.4 Verify API stability assessment matches R02-dice-api-surface.md findings
- [ ] 11.5 Verify `./mvnw compile -DskipTests` still passes (sanity check: no accidental code changes)

**Verification**: All file references confirmed; build passes; no code changes.
