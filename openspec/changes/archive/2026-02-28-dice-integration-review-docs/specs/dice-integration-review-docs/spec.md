## NEW Requirements

### Requirement: DICE integration surface document

The repository SHALL include a canonical DICE integration surface document at `docs/dev/dice-integration.md` that explains how dice-anchors composes with DICE 0.1.0-SNAPSHOT for proposition extraction, revision, persistence, and incremental analysis.

#### Scenario: Integration document exists and is discoverable

- **GIVEN** the repository is cloned
- **WHEN** a contributor looks for DICE integration documentation
- **THEN** `docs/dev/dice-integration.md` SHALL exist
- **AND** `DEVELOPING.md` SHALL contain a reference to `docs/dev/dice-integration.md`

### Requirement: End-to-end integration flow documented

The document SHALL describe both extraction paths (chat and simulation) as end-to-end flows with file:line references.

#### Scenario: Chat extraction flow is documented

- **GIVEN** the integration document is reviewed
- **WHEN** the chat extraction flow section is read
- **THEN** it SHALL describe the event-driven path: `ConversationAnalysisRequestEvent` -> `ConversationPropositionExtraction` -> `PropositionIncrementalAnalyzer` -> `PropositionPipeline` -> `AnchorRepository` -> `AnchorPromoter`
- **AND** SHALL reference `ConversationPropositionExtraction.java` with the `@Async @EventListener` entry point
- **AND** SHALL document the `WindowConfig` sliding window configuration (window size, overlap, trigger interval)

#### Scenario: Simulation extraction flow is documented

- **GIVEN** the integration document is reviewed
- **WHEN** the simulation extraction flow section is read
- **THEN** it SHALL describe the synchronous path: `SimulationExtractionService.extract()` -> `PropositionPipeline.process()` -> `PropositionResults.persist()` with fallback -> `AnchorPromoter`
- **AND** SHALL reference `SimulationExtractionService.java` with the `extract()` entry point
- **AND** SHALL note the one-shot extraction (no windowing) in contrast to the chat path

### Requirement: Component API usage documented

The document SHALL describe each DICE component used by dice-anchors with method signatures and configuration.

#### Scenario: LlmPropositionExtractor usage is documented

- **WHEN** the component API section is reviewed
- **THEN** it SHALL document the builder pattern: `LlmPropositionExtractor.withLlm().withAi().withPropositionRepository().withSchemaAdherence().withTemplate()`
- **AND** SHALL reference `PropositionConfiguration.java:107-118` for the bean definition
- **AND** SHALL note the custom D&D extraction template (`dice/extract_dnd_propositions`)

#### Scenario: LlmPropositionReviser usage is documented

- **WHEN** the component API section is reviewed
- **THEN** it SHALL document the builder pattern: `LlmPropositionReviser.withLlm().withAi()`
- **AND** SHALL reference `PropositionConfiguration.java:125-132` for the bean definition

#### Scenario: PropositionPipeline usage is documented

- **WHEN** the component API section is reviewed
- **THEN** it SHALL document the builder pattern: `PropositionPipeline.withExtractor().withRevision()`
- **AND** SHALL document the `process(List<Chunk>, SourceAnalysisContext)` method returning `PropositionResults`
- **AND** SHALL document the `PropositionResults` contract: `propositionsToPersist()` and `persist(PropositionRepository, NamedEntityDataRepository)` with fallback behavior
- **AND** SHALL reference `PropositionConfiguration.java:138-146` for the bean definition

#### Scenario: PropositionIncrementalAnalyzer usage is documented

- **WHEN** the component API section is reviewed
- **THEN** it SHALL document the constructor: `new PropositionIncrementalAnalyzer<>(pipeline, chunkHistoryStore, MessageFormatter.INSTANCE, windowConfig)`
- **AND** SHALL document the `analyze(ConversationSource, SourceAnalysisContext)` method and its untyped `Object` return
- **AND** SHALL reference `ConversationPropositionExtraction.java:73-78` for the analyzer construction
- **AND** SHALL reference `ConversationPropositionExtraction.java:103` for the `analyze()` call site

### Requirement: Fragile coupling points identified

The document SHALL catalog all known fragile coupling points between dice-anchors and DICE 0.1.0-SNAPSHOT with risk assessment and mitigation strategy.

#### Scenario: 15-parameter Proposition.create() documented

- **WHEN** the fragile coupling section is reviewed
- **THEN** it SHALL document the 15-parameter `Proposition.create()` overload with all parameter positions, types, and semantics
- **AND** SHALL identify parameters with unclear semantics: position 7 (double, unknown), position 12 (duplicated Instant), position 14 (int, unknown)
- **AND** SHALL reference `PropositionView.java:81-97` as the call site
- **AND** SHALL assess risk as HIGH due to unclear parameters suggesting an incomplete API

#### Scenario: ChunkHistoryStore wrapper documented

- **WHEN** the fragile coupling section is reviewed
- **THEN** it SHALL document the `DiceAnchorsChunkHistoryStore` interface and `InMemoryDiceAnchorsChunkHistoryStore` implementation
- **AND** SHALL document the per-context clearing limitation: `clearByContext()` resets the entire in-memory store because `InMemoryChunkHistoryStore` does not support per-context clearing
- **AND** SHALL reference `DiceAnchorsChunkHistoryStore.java:19-42` for the interface
- **AND** SHALL reference `InMemoryDiceAnchorsChunkHistoryStore.java:38-44` for the clearing limitation
- **AND** SHALL assess risk as MEDIUM due to interface evolution potential

#### Scenario: Windowed analysis documented

- **WHEN** the fragile coupling section is reviewed
- **THEN** it SHALL document the `WindowConfig` parameters: `windowSize`, `windowOverlap`, `triggerInterval`
- **AND** SHALL reference `ConversationPropositionExtraction.java:68-72` for the configuration site
- **AND** SHALL note that these parameters are configurable via `DiceAnchorsProperties.memory()` but their semantics are undocumented upstream
- **AND** SHALL assess risk as MEDIUM due to potential semantic refinement in future SNAPSHOT releases

### Requirement: API stability assessment table

The document SHALL include an API stability assessment table rating each DICE integration point.

#### Scenario: Stability table is comprehensive

- **WHEN** the stability assessment section is reviewed
- **THEN** it SHALL include a table with columns: Component, Stability (LOW/MEDIUM/HIGH), Risk Description
- **AND** SHALL rate `Proposition.create()` 15-param overload as LOW stability
- **AND** SHALL rate `PropositionResults.persist()` fallback as MEDIUM stability
- **AND** SHALL rate `WindowConfig` semantics as MEDIUM stability
- **AND** SHALL rate `analyze()` untyped return as MEDIUM stability
- **AND** SHALL rate `PropositionStatus` enum as HIGH stability
- **AND** SHALL rate `EntityMention` fields as HIGH stability
- **AND** SHALL rate `PropositionRepository` SPI as HIGH stability

### Requirement: Responsibility boundaries documented

The document SHALL clearly distinguish which behaviors are implemented in dice-anchors vs. provided by upstream DICE components.

#### Scenario: Local vs. upstream behavior is unambiguous

- **WHEN** a developer reads the responsibility boundaries section
- **THEN** it SHALL state what dice-anchors implements: anchor lifecycle (rank, authority, promotion, eviction), trust pipeline (multi-gate evaluation), conflict detection and resolution, context assembly and prompt injection, budget enforcement
- **AND** SHALL state what DICE provides: proposition extraction (LLM-based), proposition revision (validation and deduplication), chunk history management, incremental windowed analysis, entity mention extraction
- **AND** SHALL identify extension points: `PropositionRepository` SPI (implemented by `AnchorRepository`), `ChunkHistoryStore` SPI (wrapped by `DiceAnchorsChunkHistoryStore`), extraction templates (customizable Jinja)

### Requirement: Monitoring strategy documented

The document SHALL establish a monitoring strategy for tracking DICE 0.1.0-SNAPSHOT API evolution.

#### Scenario: Monitoring strategy is actionable

- **WHEN** the monitoring strategy section is reviewed
- **THEN** it SHALL document the recommended release tracking cadence
- **AND** SHOULD recommend integration test patterns for API compatibility verification
- **AND** SHALL document deprecation warning detection approach
- **AND** SHOULD recommend a maintenance calendar for SNAPSHOT compatibility fixes
- **AND** SHALL note that integration test implementation is deferred to a future maintenance cycle (outside this feature's scope)
