## 1. Create AnchorQueryTools (D1, D2, D3, D5)

- [ ] 1.1 Create `src/main/java/dev/dunnam/diceanchors/chat/AnchorQueryTools.java` as a Java record annotated with `@MatryoshkaTools(name = "anchor-query-tools", description = "Read-only tools for querying established facts (anchors)", removeOnInvoke = false)`
- [ ] 1.2 Add constructor parameters: `AnchorEngine engine`, `AnchorRepository repository`, `RelevanceScorer scorer`, `String contextId`, `RetrievalConfig config`, `AtomicInteger toolCallCounter`
- [ ] 1.3 Add convenience constructor omitting `config` and `toolCallCounter` (defaults to `null` and `new AtomicInteger(0)`)
- [ ] 1.4 Move `queryFacts(String subject)` from `AnchorTools.java` (lines 38-63) — add `@LlmTool.Param(description = "Subject or keyword to search for in established facts")` to the `subject` parameter
- [ ] 1.5 Move `listAnchors()` from `AnchorTools.java` (lines 65-78) — no parameters, no `@LlmTool.Param` needed
- [ ] 1.6 Move `retrieveAnchors(String query)` from `AnchorRetrievalTools.java` (lines 45-80) — add `@LlmTool.Param(description = "Topic or question to find relevant anchors for")` to the `query` parameter; preserve OTEL span attribute writes and `RelevanceScorer` blending logic
- [ ] 1.7 Move the `toSummary(Anchor)` helper method from `AnchorTools.java` (lines 163-172) to `AnchorQueryTools`
- [ ] 1.8 Add `SEARCH_TOP_K` and `SEARCH_THRESHOLD` constants from `AnchorTools.java` (lines 35-36)

## 2. Create AnchorMutationTools (D1, D2, D3)

- [ ] 2.1 Create `src/main/java/dev/dunnam/diceanchors/chat/AnchorMutationTools.java` as a Java record annotated with `@MatryoshkaTools(name = "anchor-mutation-tools", description = "Tools for managing anchor state (pin, unpin, demote)", removeOnInvoke = false)`
- [ ] 2.2 Add constructor parameters: `AnchorEngine engine`, `AnchorRepository repository`, `String contextId`
- [ ] 2.3 Move `pinFact(String anchorId)` from `AnchorTools.java` (lines 80-100) — add `@LlmTool.Param(description = "ID of the anchor to pin")` to the `anchorId` parameter
- [ ] 2.4 Move `unpinFact(String anchorId)` from `AnchorTools.java` (lines 102-125) — add `@LlmTool.Param(description = "ID of the anchor to unpin")` to the `anchorId` parameter
- [ ] 2.5 Move `demoteAnchor(String anchorId)` from `AnchorTools.java` (lines 133-149) — add `@LlmTool.Param(description = "ID of the anchor to demote")` to the `anchorId` parameter
- [ ] 2.6 Move `fail(String)` and `ok(String)` helper methods from `AnchorTools.java` (lines 151-160)

## 3. Update ChatActions Registration (D4)

- [ ] 3.1 Replace tool object construction in `ChatActions.respond()` (lines 128-138) to instantiate `AnchorQueryTools` and `AnchorMutationTools` instead of `AnchorTools` and `AnchorRetrievalTools`
- [ ] 3.2 Pass `RelevanceScorer` and `RetrievalConfig` to `AnchorQueryTools` constructor when retrieval mode is HYBRID or TOOL; pass `null` config for BULK mode
- [ ] 3.3 Remove `AnchorRetrievalTools` import from `ChatActions.java`
- [ ] 3.4 Remove `AnchorTools` import from `ChatActions.java`
- [ ] 3.5 Verify `withToolObjects(queryTools, mutationTools)` replaces `withToolObjects(toolObjects.toArray())`

## 4. Split Test Classes

- [ ] 4.1 Create `src/test/java/dev/dunnam/diceanchors/chat/AnchorQueryToolsTest.java` with `QueryFacts` and `ListAnchors` nested test classes from `AnchorToolsTest.java` (lines 37-116)
- [ ] 4.2 Update test instances from `new AnchorTools(engine, repository, CONTEXT_ID)` to `new AnchorQueryTools(engine, repository, null, CONTEXT_ID, null, new AtomicInteger(0))` (or convenience constructor)
- [ ] 4.3 Create `src/test/java/dev/dunnam/diceanchors/chat/AnchorMutationToolsTest.java` with `PinFact`, `UnpinFact`, and `DemoteAnchor` nested test classes from `AnchorToolsTest.java` (lines 118-277)
- [ ] 4.4 Update test instances from `new AnchorTools(engine, repository, CONTEXT_ID)` to `new AnchorMutationTools(engine, repository, CONTEXT_ID)`
- [ ] 4.5 Copy `anchorNode` and `plainPropositionNode` helper methods to both test classes (or extract to a shared `TestFixtures` class in the test package)

## 5. Delete Old Classes

- [ ] 5.1 Delete `src/main/java/dev/dunnam/diceanchors/chat/AnchorTools.java`
- [ ] 5.2 Delete `src/main/java/dev/dunnam/diceanchors/chat/AnchorRetrievalTools.java`
- [ ] 5.3 Delete `src/test/java/dev/dunnam/diceanchors/chat/AnchorToolsTest.java`
- [ ] 5.4 Search codebase for remaining references to `AnchorTools` or `AnchorRetrievalTools` and update or remove them

## 6. Verification

- [ ] 6.1 Run `./mvnw clean compile -DskipTests` — compilation succeeds with zero errors
- [ ] 6.2 Run `./mvnw test` — all tests pass with zero failures
- [ ] 6.3 Verify no references to `AnchorTools` or `AnchorRetrievalTools` remain in production code or test code (excluding OpenSpec docs and git history)
- [ ] 6.4 Verify `@LlmTool.Param` annotations are present on all five parameterized tool methods
- [ ] 6.5 Verify `removeOnInvoke = false` is set on both `@MatryoshkaTools` annotations
