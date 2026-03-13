# Implementation Tasks

## 1. Response Records

- [x] 1.1 Create `UnitSummary` record in `chat/` package (id, text, rank, authority, pinned, confidence)
- [x] 1.2 Create `PinResult` record in `chat/` package (success, message)
- [x] 1.3 Add `@JsonClassDescription` annotations for LLM visibility

## 2. ContextTools Record

- [x] 2.1 Create `ContextTools` record in `chat/` package with `@MatryoshkaTools` annotation
- [x] 2.2 Constructor accepts `ArcMemEngine`, `ContextUnitRepository`, contextId String
- [x] 2.3 Implement `queryFacts(String subject)`:
  - [x] 2.3.1 Use `repository.semanticSearch(subject, contextId, topK, threshold)`
  - [x] 2.3.2 Map results to `List<UnitSummary>`
  - [x] 2.3.3 Add `@LlmTool` description: "Query established facts about a subject"
- [x] 2.4 Implement `listUnits()`:
  - [x] 2.4.1 Use `engine.inject(contextId)`
  - [x] 2.4.2 Map to `List<UnitSummary>`
  - [x] 2.4.3 Add `@LlmTool` description: "Get all currently active context units"
- [x] 2.5 Implement `pinFact(String unitId)`:
  - [x] 2.5.1 Verify context unit exists and is active
  - [x] 2.5.2 Verify context unit is not archived
  - [x] 2.5.3 Call `repository.updatePinned(unitId, true)` (existing method)
  - [x] 2.5.4 Return `PinResult(true, "Fact pinned successfully")`
  - [x] 2.5.5 Add `@LlmTool` description: "Pin an important fact to prevent eviction"
- [x] 2.6 Implement `unpinFact(String unitId)`:
  - [x] 2.6.1 Verify context unit exists, is active, and is pinned
  - [x] 2.6.2 Verify context unit is not CANON (CANON always preserved)
  - [x] 2.6.3 Call `repository.updatePinned(unitId, false)` (existing method)
  - [x] 2.6.4 Return `PinResult(true, "Fact unpinned")`
  - [x] 2.6.5 Add `@LlmTool` description: "Unpin a fact to allow normal eviction"

## 3. Repository Support

- [x] 3.1 Check if `pinUnit(id)` exists in ContextUnitRepository — `updatePinned(id, pinned)` already exists
- [x] 3.2 No change needed — `updatePinned(id, true)` handles pin
- [x] 3.3 Check if `unpinUnit(id)` exists — `updatePinned(id, pinned)` already exists
- [x] 3.4 No change needed — `updatePinned(id, false)` handles unpin

## 4. Wire into ChatActions

- [x] 4.1 Create `ContextTools` instance in `ChatActions.respond()`
- [x] 4.2 Pass contextId to ContextTools constructor
- [x] 4.3 Register tools via `.withToolObjects(tools)` on Embabel AI builder
- [x] 4.4 Verify tool registration doesn't break existing chat flow (all tests pass)

## 5. Safety & Logging

- [x] 5.1 Add guard: pinFact returns failure if context unit not found
- [x] 5.2 Add guard: pinFact returns failure if context unit archived
- [x] 5.3 Add guard: unpinFact returns failure if not pinned
- [x] 5.4 Add guard: unpinFact returns failure if CANON (inherently preserved)
- [x] 5.5 Log all tool invocations: `logger.info("LLM tool call: {} with {}", toolName, params)`
- [x] 5.6 Log tool results: `logger.info("Tool result: {}", result)`

## 6. Testing

- [x] 6.1 Unit tests for `ContextTools` methods
  - [x] 6.1.1 Test queryFacts returns matching context units
  - [x] 6.1.2 Test queryFacts returns empty for no matches
  - [x] 6.1.3 Test listUnits returns all active context units in rank order
  - [x] 6.1.4 Test pinFact succeeds on active context unit
  - [x] 6.1.5 Test pinFact fails on non-existent context unit
  - [x] 6.1.6 Test pinFact fails on archived context unit
  - [x] 6.1.7 Test unpinFact succeeds on pinned unit
  - [x] 6.1.8 Test unpinFact fails on unpinned context unit
  - [x] 6.1.9 Test unpinFact fails on CANON context unit
- [ ] 6.2 Integration test: tools wired into ChatActions
  - [ ] 6.2.1 Verify tools are available in AI builder context
  - [ ] 6.2.2 Verify tool calls reach ArcMemEngine/Repository

## 7. Verification

- [x] 7.1 Run full test suite: `./mvnw.cmd test` — 304 tests, 0 failures
- [x] 7.2 Build: `./mvnw.cmd clean compile -DskipTests` — BUILD SUCCESS
- [ ] 7.3 Manual chat test: Ask Bigby "What facts do we know?" — verify tool call
- [ ] 7.4 Manual chat test: Ask Bigby to pin an important fact — verify pin
- [ ] 7.5 Verify tool calls appear in logs
- [ ] 7.6 Verify pinned fact survives budget eviction in subsequent turns

## Definition of Done

- ✓ 4 context unit tools available during chat (@MatryoshkaTools)
- ✓ queryFacts and listUnits return structured data
- ✓ pinFact/unpinFact respect safety guards
- ✓ Tools wired into ChatActions via Embabel .withToolObjects()
- ✓ All tool invocations logged
- ✓ All tests pass
- ✓ LLM can query and manage its own knowledge during conversation
