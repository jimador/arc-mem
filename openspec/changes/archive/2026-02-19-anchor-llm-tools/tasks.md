# Implementation Tasks

## 1. Response Records

- [x] 1.1 Create `AnchorSummary` record in `chat/` package (id, text, rank, authority, pinned, confidence)
- [x] 1.2 Create `PinResult` record in `chat/` package (success, message)
- [x] 1.3 Add `@JsonClassDescription` annotations for LLM visibility

## 2. AnchorTools Record

- [x] 2.1 Create `AnchorTools` record in `chat/` package with `@MatryoshkaTools` annotation
- [x] 2.2 Constructor accepts `AnchorEngine`, `AnchorRepository`, contextId String
- [x] 2.3 Implement `queryFacts(String subject)`:
  - [x] 2.3.1 Use `repository.semanticSearch(subject, contextId, topK, threshold)`
  - [x] 2.3.2 Map results to `List<AnchorSummary>`
  - [x] 2.3.3 Add `@LlmTool` description: "Query established facts about a subject"
- [x] 2.4 Implement `listAnchors()`:
  - [x] 2.4.1 Use `engine.inject(contextId)`
  - [x] 2.4.2 Map to `List<AnchorSummary>`
  - [x] 2.4.3 Add `@LlmTool` description: "Get all currently active anchors"
- [x] 2.5 Implement `pinFact(String anchorId)`:
  - [x] 2.5.1 Verify anchor exists and is active
  - [x] 2.5.2 Verify anchor is not archived
  - [x] 2.5.3 Call `repository.updatePinned(anchorId, true)` (existing method)
  - [x] 2.5.4 Return `PinResult(true, "Fact pinned successfully")`
  - [x] 2.5.5 Add `@LlmTool` description: "Pin an important fact to prevent eviction"
- [x] 2.6 Implement `unpinFact(String anchorId)`:
  - [x] 2.6.1 Verify anchor exists, is active, and is pinned
  - [x] 2.6.2 Verify anchor is not CANON (CANON always preserved)
  - [x] 2.6.3 Call `repository.updatePinned(anchorId, false)` (existing method)
  - [x] 2.6.4 Return `PinResult(true, "Fact unpinned")`
  - [x] 2.6.5 Add `@LlmTool` description: "Unpin a fact to allow normal eviction"

## 3. Repository Support

- [x] 3.1 Check if `pinAnchor(id)` exists in AnchorRepository — `updatePinned(id, pinned)` already exists
- [x] 3.2 No change needed — `updatePinned(id, true)` handles pin
- [x] 3.3 Check if `unpinAnchor(id)` exists — `updatePinned(id, pinned)` already exists
- [x] 3.4 No change needed — `updatePinned(id, false)` handles unpin

## 4. Wire into ChatActions

- [x] 4.1 Create `AnchorTools` instance in `ChatActions.respond()`
- [x] 4.2 Pass contextId to AnchorTools constructor
- [x] 4.3 Register tools via `.withToolObjects(tools)` on Embabel AI builder
- [x] 4.4 Verify tool registration doesn't break existing chat flow (all tests pass)

## 5. Safety & Logging

- [x] 5.1 Add guard: pinFact returns failure if anchor not found
- [x] 5.2 Add guard: pinFact returns failure if anchor archived
- [x] 5.3 Add guard: unpinFact returns failure if not pinned
- [x] 5.4 Add guard: unpinFact returns failure if CANON (inherently preserved)
- [x] 5.5 Log all tool invocations: `logger.info("LLM tool call: {} with {}", toolName, params)`
- [x] 5.6 Log tool results: `logger.info("Tool result: {}", result)`

## 6. Testing

- [x] 6.1 Unit tests for `AnchorTools` methods
  - [x] 6.1.1 Test queryFacts returns matching anchors
  - [x] 6.1.2 Test queryFacts returns empty for no matches
  - [x] 6.1.3 Test listAnchors returns all active anchors in rank order
  - [x] 6.1.4 Test pinFact succeeds on active anchor
  - [x] 6.1.5 Test pinFact fails on non-existent anchor
  - [x] 6.1.6 Test pinFact fails on archived anchor
  - [x] 6.1.7 Test unpinFact succeeds on pinned anchor
  - [x] 6.1.8 Test unpinFact fails on unpinned anchor
  - [x] 6.1.9 Test unpinFact fails on CANON anchor
- [ ] 6.2 Integration test: tools wired into ChatActions
  - [ ] 6.2.1 Verify tools are available in AI builder context
  - [ ] 6.2.2 Verify tool calls reach AnchorEngine/Repository

## 7. Verification

- [x] 7.1 Run full test suite: `./mvnw.cmd test` — 304 tests, 0 failures
- [x] 7.2 Build: `./mvnw.cmd clean compile -DskipTests` — BUILD SUCCESS
- [ ] 7.3 Manual chat test: Ask Bigby "What facts do we know?" — verify tool call
- [ ] 7.4 Manual chat test: Ask Bigby to pin an important fact — verify pin
- [ ] 7.5 Verify tool calls appear in logs
- [ ] 7.6 Verify pinned fact survives budget eviction in subsequent turns

## Definition of Done

- ✓ 4 anchor tools available during chat (@MatryoshkaTools)
- ✓ queryFacts and listAnchors return structured data
- ✓ pinFact/unpinFact respect safety guards
- ✓ Tools wired into ChatActions via Embabel .withToolObjects()
- ✓ All tool invocations logged
- ✓ All tests pass
- ✓ LLM can query and manage its own knowledge during conversation
