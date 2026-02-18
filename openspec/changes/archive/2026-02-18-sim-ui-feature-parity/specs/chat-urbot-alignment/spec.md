## ADDED Requirements

### Requirement: ChatActions as @EmbabelComponent record

`ChatActions` SHALL remain an `@EmbabelComponent` record. The record SHALL accept constructor parameters for `AnchorEngine`, `ApplicationEventPublisher`, and `DiceAnchorsProperties`. The `respond()` method SHALL use the `@Action(canRerun = true, trigger = UserMessage.class)` annotation. The response chain SHALL use `context.ai().withLlm(...).withPromptElements(...).rendering(...)` following the urbot pattern. A separate `bindUser()` `@Action` MAY be added for user binding if needed.

#### Scenario: ChatActions record structure
- **WHEN** `ChatActions.java` is inspected
- **THEN** it is a record annotated with `@EmbabelComponent`
- **AND** the `respond()` method is annotated with `@Action(canRerun = true, trigger = UserMessage.class)`

#### Scenario: Response uses AI builder chain
- **WHEN** `respond()` executes
- **THEN** the assistant message is generated via `context.ai()` builder chain with LLM, prompt elements, and rendering specified

### Requirement: ChatConfiguration with AgentProcessChatbot factory

A `ChatConfiguration` class SHALL be a `@Configuration` class that provides the `Chatbot` bean via `AgentProcessChatbot.utilityFromPlatform()`. The configuration SHALL define a `CommonTools` record wrapper for any shared tools. A `@Bean Tool` for RAG (using `ToolishRag` with TryHyDE hint) SHALL be provided if DICE Memory is available. A `@Bean MemoryProjector` SHALL be provided for conversation memory management.

#### Scenario: Chatbot bean creation
- **WHEN** the Spring context initializes
- **THEN** a `Chatbot` bean exists created via `AgentProcessChatbot.utilityFromPlatform()`

#### Scenario: RAG tool bean
- **WHEN** DICE Memory is available in the context
- **THEN** a RAG `Tool` bean is registered using `ToolishRag`

### Requirement: PropositionConfiguration with full DICE pipeline

A `PropositionConfiguration` class SHALL be a `@Configuration @EnableAsync` class that wires the complete DICE proposition extraction pipeline. The configuration SHALL provide beans for: `DataDictionary.fromClasses(...)` using the D&D domain schema classes (`Character`, `Creature`, `DndItem`, `DndLocation`, `Faction`, `StoryEvent`), `LlmPropositionExtractor` with the extraction Jinja template, `PropositionPipeline` with reviser, `EscalatingEntityResolver`, `GraphProjector`, and `GraphRelationshipPersister`.

#### Scenario: DataDictionary includes D&D schema classes
- **WHEN** the `DataDictionary` bean initializes
- **THEN** it includes entity definitions for Character, Creature, DndItem, DndLocation, Faction, and StoryEvent

#### Scenario: PropositionPipeline bean wired
- **WHEN** the Spring context initializes
- **THEN** a `PropositionPipeline` bean exists with extractor, reviser, entity resolver, graph projector, and relationship persister

### Requirement: ConversationPropositionExtraction async event listener

A `ConversationPropositionExtraction` service SHALL handle `ConversationAnalysisRequestEvent` via an `@Async @EventListener` method. The handler SHALL use `PropositionIncrementalAnalyzer` with configurable windowing to extract propositions from the conversation. The handler SHALL consult `ChunkHistoryStore` to skip already-processed conversation windows, preventing duplicate extraction.

#### Scenario: Event triggers extraction
- **WHEN** a `ConversationAnalysisRequestEvent` is published after a chat response
- **THEN** `ConversationPropositionExtraction` processes the event asynchronously
- **AND** propositions are extracted from the conversation window

#### Scenario: Duplicate windows skipped
- **WHEN** the same conversation window has already been processed (tracked in ChunkHistoryStore)
- **THEN** the extraction handler skips that window without re-extracting

### Requirement: ChunkHistoryStore persistence

A `ChunkHistoryStore` SHALL track which conversation windows have been processed for proposition extraction. The store SHALL be backed by Drivine/Neo4j. Each entry SHALL record the contextId, window start offset, window end offset, and extraction timestamp. The store SHALL provide a method to check if a given window range has already been processed for a given contextId.

#### Scenario: Window tracked after extraction
- **WHEN** a conversation window [messages 5-10] is successfully extracted for context "chat"
- **THEN** `ChunkHistoryStore` records the window range [5, 10] for contextId "chat"

#### Scenario: Previously processed window detected
- **WHEN** `ChunkHistoryStore.isProcessed("chat", 5, 10)` is called after extraction
- **THEN** the method returns true

### Requirement: DiceAnchorsProperties expansion

`DiceAnchorsProperties` SHALL be expanded to include separate LLM configuration for chat, extraction, and entity resolution (following urbot's `ImpromptuProperties` pattern). The record SHALL include fields for: chat LLM name, extraction LLM name, entity resolution LLM name, extraction window size, extraction overlap size, and embedding service name. Default values SHALL be provided for all new fields.

#### Scenario: Separate LLM configs
- **WHEN** `DiceAnchorsProperties` is inspected
- **THEN** it contains distinct fields for `chatLlm`, `extractionLlm`, and `entityResolutionLlm`

#### Scenario: Default values provided
- **WHEN** no explicit LLM names are configured in `application.yml`
- **THEN** the default LLM names are used (e.g., all defaulting to the primary model)

### Requirement: Anchor promotion wired into extraction pipeline

After DICE extracts propositions from the conversation, each extracted proposition SHALL be evaluated for anchor promotion via `AnchorPromoter`. The promotion decision SHALL be based on the proposition's confidence score (and trust score when the trust-scoring capability is active). Promoted propositions SHALL become active anchors in the conversation's contextId.

#### Scenario: High-confidence proposition promoted
- **WHEN** DICE extracts a proposition with confidence >= the promotion threshold
- **THEN** `AnchorPromoter` evaluates the proposition
- **AND** if promotion criteria are met, `AnchorEngine.promote()` is called

#### Scenario: Low-confidence proposition not promoted
- **WHEN** DICE extracts a proposition with confidence below the promotion threshold
- **THEN** the proposition remains as an unpromoted proposition node

### Requirement: Propositions panel in ChatView

`ChatView` SHALL include a sidebar or collapsible panel displaying extracted propositions and promoted anchors for the current chat context. The panel SHALL show proposition text, confidence score, and promotion status (unpromoted / promoted with rank and authority). The panel SHALL update after each extraction cycle completes.

#### Scenario: Panel shows extracted propositions
- **WHEN** DICE extraction completes for the chat context
- **THEN** the propositions panel displays the newly extracted propositions with their confidence scores

#### Scenario: Promoted anchor distinguished
- **WHEN** a proposition has been promoted to anchor status
- **THEN** the panel displays the anchor's rank, authority, and a visual indicator distinguishing it from unpromoted propositions

### Requirement: ConversationAnalysisRequestEvent published after each response

`ChatActions.respond()` SHALL publish a `ConversationAnalysisRequestEvent` via `ApplicationEventPublisher` after every assistant response is sent. The event SHALL carry the contextId and the current `Conversation` object. This follows the existing pattern already in the codebase.

#### Scenario: Event published on response
- **WHEN** `ChatActions.respond()` completes and the assistant message is sent
- **THEN** a `ConversationAnalysisRequestEvent` is published with the current contextId and conversation
