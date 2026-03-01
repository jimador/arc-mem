## 1. Embabel API Inventory Documentation

- [ ] 1.1 Create `docs/dev/embabel-api-inventory.md` documenting: annotations used (with file:line), interfaces, patterns, best practices *(spec: embabel-mcp-server-config / Embabel API inventory document)*
- [ ] 1.2 Document current usage: `@EmbabelComponent`, `@Action`, `@LlmTool`, `@MatryoshkaTools`, `Ai` fluent API, template rendering, Spring events
- [ ] 1.3 Document available but unused: `@Goal`, `@Condition`, `@State`, `@AchievesGoal`, Blackboard, `@Requires`, condition-based branching
- [ ] 1.4 Document tool restructuring rationale: CQS principle, read-only vs. full-access contexts *(spec: embabel-mcp-server-config / Tool restructuring rationale documented)*

**Verification**: Inventory document complete and referenced from DEVELOPING.md or README.

## 2. Tool Group Restructuring

- [ ] 2.1 Create `AnchorQueryTools.java` with `@MatryoshkaTools(name="anchor-query-tools")` containing: `queryFacts(String)`, `listAnchors()`, `retrieveAnchors(String)` *(spec: anchor-llm-tools / AnchorQueryTools class)*
- [ ] 2.2 Consolidate `retrieveAnchors` from `AnchorRetrievalTools` into `AnchorQueryTools`, preserving retrieval-mode conditional logic (`@Condition` or manual check) and OTEL span tracking
- [ ] 2.3 Create `AnchorMutationTools.java` with `@MatryoshkaTools(name="anchor-mutation-tools")` containing: `pinFact(String)`, `unpinFact(String)`, `demoteAnchor(String)` *(spec: anchor-llm-tools / AnchorMutationTools class)*
- [ ] 2.4 Update `ChatActions.respond()` tool registration (line 128-143): register both tool groups unconditionally in chat; register only `AnchorQueryTools` in read-only contexts *(spec: anchor-llm-tools / Conditional tool registration)*
- [ ] 2.5 Delete `AnchorTools.java` and `AnchorRetrievalTools.java` (behavior moved to new classes)
- [ ] 2.6 Update all tests: `ChatActionsTest`, `AnchorToolsTest` (rename to `AnchorQueryToolsTest` and `AnchorMutationToolsTest`); verify tool descriptions are intact *(spec: anchor-llm-tools / Tools organized by concern)*

**Verification**: `./mvnw test` passes. Chat integration test confirms query + mutation tools available in chat; verify read-only mode uses query tools only.

## 3. DICE Integration Surface Documentation

- [ ] 3.1 Create `docs/dev/dice-integration.md` documenting end-to-end DICE integration flow *(spec: dice-integration-review-docs / DICE integration surface documentation)*
- [ ] 3.2 Document each component with API usage: `LlmPropositionExtractor`, `LlmPropositionReviser`, `PropositionPipeline`, `PropositionIncrementalAnalyzer` with file:line references *(spec: dice-integration-review-docs / Component API usage is documented)*
- [ ] 3.3 Document fragile coupling points: `PropositionView.toDice()` 13-param `Proposition.create()` overload, `DiceAnchorsChunkHistoryStore` delegation wrapper, windowed analysis parameters *(spec: dice-integration-review-docs / Fragile coupling points are identified)*
- [ ] 3.4 Establish monitoring strategy: monitor DICE 0.1.0-SNAPSHOT releases; add integration tests to catch API misalignments; document in DEVELOPING.md
- [ ] 3.5 Document responsibility boundaries: what dice-anchors implements vs. what DICE provides *(spec: dice-integration-review-docs / DICE vs. Anchors responsibility boundaries)*

**Verification**: Documentation complete; integration tests compile and execute without DICE API errors.

## 4. Goal & Condition Orchestration Evaluation

- [ ] 4.1 Create `docs/dev/embabel-goal-modeling.md` evaluating goal-directed orchestration for anchor lifecycle *(spec: embabel-goal-modeling / Goal-directed orchestration evaluation document)*
- [ ] 4.2 Document trust pipeline analysis: current 5-gate sequence, how it maps to Embabel `@Action` + `@Condition` + `@AchievesGoal` patterns *(spec: embabel-goal-modeling / Trust pipeline analysis is thorough)*
- [ ] 4.3 Document architectural trade-offs: side-effect handling (Embabel assumes typed data flow; anchor operations are Neo4j mutations), refactoring scope *(spec: embabel-goal-modeling / Architectural trade-offs are identified)*
- [ ] 4.4 Create comparison table: imperative (current) vs. goal-directed approach covering observability, testability, error handling, complexity *(spec: embabel-goal-modeling / Current vs. goal-directed comparison included)*
- [ ] 4.5 Document chatbot mode assessment: `utilityFromPlatform()` (utility mode) vs. goal-directed mode; recommend best fit for dice-anchors *(spec: embabel-goal-modeling / Chatbot mode assessment)*
- [ ] 4.6 Document Blackboard state opportunity: typed state via Blackboard/OperationContext; mark as "deferred to future change" *(spec: embabel-goal-modeling / Blackboard state opportunity document)*

**Verification**: Evaluation document complete with clear recommendation (adopt, adopt with deferred refactoring, or defer to future change).

## 5. Integration & Testing

- [ ] 5.1 Run full test suite: `./mvnw test` *(all tasks)*
- [ ] 5.2 Run chat integration test with both tool groups registered; verify LLM can use queries and mutations
- [ ] 5.3 Test read-only mode: verify only query tools are available (simulation, audit context)
- [ ] 5.4 Compile and verify no DICE API deprecation warnings: `./mvnw compile`
- [ ] 5.5 Update README or DEVELOPING.md with links to new documentation files

**Verification**: All tests pass; documentation integrated into development guides.
