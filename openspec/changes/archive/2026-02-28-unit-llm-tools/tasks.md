## 1. Create ContextUnitQueryTools (D1, D2, D3, D5)

- [ ] 1.1 Create `src/main/java/dev/dunnam/arcmem/chat/ContextUnitQueryTools.java` as a Java record annotated with `@MatryoshkaTools(name = "unit-query-tools", description = "Read-only tools for querying established facts (context units)", removeOnInvoke = false)`
- [ ] 1.2 Add constructor parameters: `ArcMemEngine engine`, `ContextUnitRepository repository`, `RelevanceScorer scorer`, `String contextId`, `RetrievalConfig config`, `AtomicInteger toolCallCounter`
- [ ] 1.3 Add convenience constructor omitting `config` and `toolCallCounter` (defaults to `null` and `new AtomicInteger(0)`)
- [ ] 1.4 Move `queryFacts(String subject)` from `ContextTools.java` (lines 38-63) — add `@LlmTool.Param(description = "Subject or keyword to search for in established facts")` to the `subject` parameter
- [ ] 1.5 Move `listUnits()` from `ContextTools.java` (lines 65-78) — no parameters, no `@LlmTool.Param` needed
- [ ] 1.6 Move `retrieveUnits(String query)` from `ContextUnitRetrievalTools.java` (lines 45-80) — add `@LlmTool.Param(description = "Topic or question to find relevant context units for")` to the `query` parameter; preserve OTEL span attribute writes and `RelevanceScorer` blending logic
- [ ] 1.7 Move the `toSummary(Context Unit)` helper method from `ContextTools.java` (lines 163-172) to `ContextUnitQueryTools`
- [ ] 1.8 Add `SEARCH_TOP_K` and `SEARCH_THRESHOLD` constants from `ContextTools.java` (lines 35-36)

## 2. Create ContextUnitMutationTools (D1, D2, D3)

- [ ] 2.1 Create `src/main/java/dev/dunnam/arcmem/chat/ContextUnitMutationTools.java` as a Java record annotated with `@MatryoshkaTools(name = "unit-mutation-tools", description = "Tools for managing context unit state (pin, unpin, demote)", removeOnInvoke = false)`
- [ ] 2.2 Add constructor parameters: `ArcMemEngine engine`, `ContextUnitRepository repository`, `String contextId`
- [ ] 2.3 Move `pinFact(String unitId)` from `ContextTools.java` (lines 80-100) — add `@LlmTool.Param(description = "ID of the context unit to pin")` to the `unitId` parameter
- [ ] 2.4 Move `unpinFact(String unitId)` from `ContextTools.java` (lines 102-125) — add `@LlmTool.Param(description = "ID of the context unit to unpin")` to the `unitId` parameter
- [ ] 2.5 Move `demoteUnit(String unitId)` from `ContextTools.java` (lines 133-149) — add `@LlmTool.Param(description = "ID of the context unit to demote")` to the `unitId` parameter
- [ ] 2.6 Move `fail(String)` and `ok(String)` helper methods from `ContextTools.java` (lines 151-160)

## 3. Update ChatActions Registration (D4)

- [ ] 3.1 Replace tool object construction in `ChatActions.respond()` (lines 128-138) to instantiate `ContextUnitQueryTools` and `ContextUnitMutationTools` instead of `ContextTools` and `ContextUnitRetrievalTools`
- [ ] 3.2 Pass `RelevanceScorer` and `RetrievalConfig` to `ContextUnitQueryTools` constructor when retrieval mode is HYBRID or TOOL; pass `null` config for BULK mode
- [ ] 3.3 Remove `ContextUnitRetrievalTools` import from `ChatActions.java`
- [ ] 3.4 Remove `ContextTools` import from `ChatActions.java`
- [ ] 3.5 Verify `withToolObjects(queryTools, mutationTools)` replaces `withToolObjects(toolObjects.toArray())`

## 4. Split Test Classes

- [ ] 4.1 Create `src/test/java/dev/dunnam/arcmem/chat/ContextUnitQueryToolsTest.java` with `QueryFacts` and `ListUnits` nested test classes from `ContextToolsTest.java` (lines 37-116)
- [ ] 4.2 Update test instances from `new ContextTools(engine, repository, CONTEXT_ID)` to `new ContextUnitQueryTools(engine, repository, null, CONTEXT_ID, null, new AtomicInteger(0))` (or convenience constructor)
- [ ] 4.3 Create `src/test/java/dev/dunnam/arcmem/chat/ContextUnitMutationToolsTest.java` with `PinFact`, `UnpinFact`, and `DemoteUnit` nested test classes from `ContextToolsTest.java` (lines 118-277)
- [ ] 4.4 Update test instances from `new ContextTools(engine, repository, CONTEXT_ID)` to `new ContextUnitMutationTools(engine, repository, CONTEXT_ID)`
- [ ] 4.5 Copy `unitNode` and `plainPropositionNode` helper methods to both test classes (or extract to a shared `TestFixtures` class in the test package)

## 5. Delete Old Classes

- [ ] 5.1 Delete `src/main/java/dev/dunnam/arcmem/chat/ContextTools.java`
- [ ] 5.2 Delete `src/main/java/dev/dunnam/arcmem/chat/ContextUnitRetrievalTools.java`
- [ ] 5.3 Delete `src/test/java/dev/dunnam/arcmem/chat/ContextToolsTest.java`
- [ ] 5.4 Search codebase for remaining references to `ContextTools` or `ContextUnitRetrievalTools` and update or remove them

## 6. Verification

- [ ] 6.1 Run `./mvnw clean compile -DskipTests` — compilation succeeds with zero errors
- [ ] 6.2 Run `./mvnw test` — all tests pass with zero failures
- [ ] 6.3 Verify no references to `ContextTools` or `ContextUnitRetrievalTools` remain in production code or test code (excluding OpenSpec docs and git history)
- [ ] 6.4 Verify `@LlmTool.Param` annotations are present on all five parameterized tool methods
- [ ] 6.5 Verify `removeOnInvoke = false` is set on both `@MatryoshkaTools` annotations
