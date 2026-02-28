## NEW Requirements

Keywords follow [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

### Requirement: AnchorQueryTools class

The system SHALL provide an `AnchorQueryTools` record annotated with `@MatryoshkaTools` containing three read-only `@LlmTool` methods. The `@MatryoshkaTools` annotation MUST specify:

- `name = "anchor-query-tools"`
- `description` summarizing read-only fact querying
- `removeOnInvoke = false`

The record MUST accept the following constructor parameters: `AnchorEngine engine`, `AnchorRepository repository`, `RelevanceScorer scorer`, `String contextId`, `RetrievalConfig config`, `AtomicInteger toolCallCounter`.

A convenience constructor MAY omit `config` and `toolCallCounter` for contexts where retrieval is not enabled, defaulting `config` to `null` and `toolCallCounter` to a new `AtomicInteger(0)`.

**File**: `src/main/java/dev/dunnam/diceanchors/chat/AnchorQueryTools.java`

#### Scenario: queryFacts returns matching anchors

- **GIVEN** active anchors exist in the repository for context "chat"
- **AND** semantic search for "king" returns scored propositions matching anchor nodes
- **WHEN** `queryFacts("king")` is called
- **THEN** the method SHALL return a `List<AnchorSummary>` containing only anchor nodes (rank > 0) matching the search
- **AND** non-anchor propositions SHALL be filtered out

#### Scenario: queryFacts with no matches

- **GIVEN** no propositions match the semantic search for "dragons"
- **WHEN** `queryFacts("dragons")` is called
- **THEN** the method SHALL return an empty list

#### Scenario: listAnchors returns all active anchors

- **GIVEN** active anchors exist for context "chat"
- **WHEN** `listAnchors()` is called
- **THEN** the method SHALL return all anchors from `engine.inject(contextId)` mapped to `AnchorSummary` records

#### Scenario: listAnchors with no anchors

- **GIVEN** no active anchors exist for the context
- **WHEN** `listAnchors()` is called
- **THEN** the method SHALL return an empty list

#### Scenario: retrieveAnchors scores and filters by relevance

- **GIVEN** active anchors exist for the context
- **AND** `RelevanceScorer` produces scored results
- **WHEN** `retrieveAnchors("topic")` is called
- **THEN** the method SHALL return `List<ScoredAnchor>` filtered by `minRelevance` and limited to `toolTopK`
- **AND** the `toolCallCounter` SHALL be incremented
- **AND** the OTEL span attribute `retrieval.tool_call_count` SHALL be set to the new counter value

#### Scenario: retrieveAnchors with no anchors available

- **GIVEN** `engine.inject(contextId)` returns an empty list
- **WHEN** `retrieveAnchors("topic")` is called
- **THEN** the method SHALL return an empty list without calling `RelevanceScorer`

---

### Requirement: AnchorMutationTools class

The system SHALL provide an `AnchorMutationTools` record annotated with `@MatryoshkaTools` containing three state-changing `@LlmTool` methods. The `@MatryoshkaTools` annotation MUST specify:

- `name = "anchor-mutation-tools"`
- `description` summarizing anchor state management operations
- `removeOnInvoke = false`

The record MUST accept the following constructor parameters: `AnchorEngine engine`, `AnchorRepository repository`, `String contextId`.

**File**: `src/main/java/dev/dunnam/diceanchors/chat/AnchorMutationTools.java`

#### Scenario: pinFact succeeds on active anchor

- **GIVEN** an active anchor "a1" exists that is not currently pinned
- **WHEN** `pinFact("a1")` is called
- **THEN** the method SHALL return `PinResult(true, ...)` and call `repository.updatePinned("a1", true)`

#### Scenario: pinFact fails on non-existent anchor

- **GIVEN** no anchor with ID "missing" exists
- **WHEN** `pinFact("missing")` is called
- **THEN** the method SHALL return `PinResult(false, ...)` with a message containing "not found"

#### Scenario: pinFact fails on archived anchor

- **GIVEN** an anchor "a1" exists with status SUPERSEDED
- **WHEN** `pinFact("a1")` is called
- **THEN** the method SHALL return `PinResult(false, ...)` with a message containing "archived"

#### Scenario: pinFact fails on non-anchor proposition

- **GIVEN** a proposition "p1" exists that is not an anchor (rank = 0)
- **WHEN** `pinFact("p1")` is called
- **THEN** the method SHALL return `PinResult(false, ...)` with a message containing "Not an anchor"

#### Scenario: unpinFact succeeds on pinned anchor

- **GIVEN** a pinned anchor "a1" exists with authority below CANON
- **WHEN** `unpinFact("a1")` is called
- **THEN** the method SHALL return `PinResult(true, ...)` and call `repository.updatePinned("a1", false)`

#### Scenario: unpinFact fails on CANON anchor

- **GIVEN** a pinned CANON anchor "a1" exists
- **WHEN** `unpinFact("a1")` is called
- **THEN** the method SHALL return `PinResult(false, ...)` with a message containing "CANON"

#### Scenario: unpinFact fails on unpinned anchor

- **GIVEN** an anchor "a1" exists that is not pinned
- **WHEN** `unpinFact("a1")` is called
- **THEN** the method SHALL return `PinResult(false, ...)` with a message containing "not pinned"

#### Scenario: demoteAnchor triggers engine demotion

- **GIVEN** a RELIABLE anchor "a1" exists
- **WHEN** `demoteAnchor("a1")` is called
- **THEN** `engine.demote("a1", DemotionReason.MANUAL)` SHALL be called
- **AND** the method SHALL return a non-empty string confirming the demotion

#### Scenario: demoteAnchor on non-existent anchor

- **GIVEN** no anchor with ID "missing" exists
- **WHEN** `demoteAnchor("missing")` is called
- **THEN** the method SHALL return a string containing "not found"
- **AND** `engine.demote()` SHALL NOT be called

#### Scenario: demoteAnchor on CANON anchor routes through engine

- **GIVEN** a CANON anchor "a2" exists
- **WHEN** `demoteAnchor("a2")` is called
- **THEN** `engine.demote("a2", DemotionReason.MANUAL)` SHALL be called (engine handles canonization gate routing)

---

### Requirement: @LlmTool.Param annotations

All `@LlmTool` method parameters in both `AnchorQueryTools` and `AnchorMutationTools` MUST be annotated with `@LlmTool.Param(description = "...")`. The descriptions MUST be concise and guide the LLM on what value to provide.

Required annotations:

| Method | Parameter | Description |
|--------|-----------|-------------|
| `queryFacts` | `subject` | "Subject or keyword to search for in established facts" |
| `retrieveAnchors` | `query` | "Topic or question to find relevant anchors for" |
| `pinFact` | `anchorId` | "ID of the anchor to pin" |
| `unpinFact` | `anchorId` | "ID of the anchor to unpin" |
| `demoteAnchor` | `anchorId` | "ID of the anchor to demote" |

`listAnchors()` has no parameters and requires no `@LlmTool.Param` annotation.

---

### Requirement: Conditional tool registration in ChatActions

`ChatActions.respond()` SHALL register tool groups as follows:

1. `AnchorQueryTools` SHALL always be registered as a tool object.
2. `AnchorMutationTools` SHALL always be registered as a tool object in chat mode.
3. The `retrieveAnchors` method within `AnchorQueryTools` SHALL only function when retrieval mode is `HYBRID` or `TOOL`. This MAY be achieved by passing `RetrievalConfig` and `RelevanceScorer` to the `AnchorQueryTools` constructor (existing conditional pattern), or by constructing `AnchorQueryTools` with a `null` config when retrieval is disabled — matching the existing guard logic in `AnchorRetrievalTools.retrieveAnchors()`.

The registration code SHALL replace the existing `toolObjects` construction (lines 128-138 of `ChatActions.java`) with construction of both tool group instances.

#### Scenario: Chat mode registers both tool groups

- **GIVEN** `ChatActions.respond()` is invoked in normal chat mode
- **WHEN** the tool objects are constructed
- **THEN** both `AnchorQueryTools` and `AnchorMutationTools` SHALL be passed to `context.ai().withToolObjects()`

#### Scenario: Retrieval mode configures query tools

- **GIVEN** retrieval mode is `HYBRID` or `TOOL`
- **WHEN** `AnchorQueryTools` is constructed
- **THEN** the `RetrievalConfig` and `RelevanceScorer` SHALL be provided so `retrieveAnchors` returns scored results

#### Scenario: Bulk retrieval mode omits retrieval config

- **GIVEN** retrieval mode is `BULK`
- **WHEN** `AnchorQueryTools` is constructed
- **THEN** the `config` parameter MAY be null, causing `retrieveAnchors` to return an empty list or skip scoring

---

### Requirement: Old classes deleted

After the split is complete and all tests pass:

- `src/main/java/dev/dunnam/diceanchors/chat/AnchorTools.java` SHALL be deleted
- `src/main/java/dev/dunnam/diceanchors/chat/AnchorRetrievalTools.java` SHALL be deleted
- No references to `AnchorTools` or `AnchorRetrievalTools` SHALL remain in the codebase

#### Scenario: No references to deleted classes

- **GIVEN** the refactoring is complete
- **WHEN** the codebase is searched for `AnchorTools` (excluding test history and OpenSpec docs)
- **THEN** zero references SHALL be found in production code or test code

---

### Requirement: Test coverage

All existing test scenarios from `AnchorToolsTest` (`src/test/java/dev/dunnam/diceanchors/chat/AnchorToolsTest.java`) SHALL be preserved in the split test classes:

- `AnchorQueryToolsTest` SHALL contain tests for `queryFacts` and `listAnchors`
- `AnchorMutationToolsTest` SHALL contain tests for `pinFact`, `unpinFact`, and `demoteAnchor`

Test helper methods (`anchorNode`, `plainPropositionNode`) SHALL be duplicated or extracted to a shared test utility as needed.

The full test suite (`./mvnw test`) MUST pass with zero failures after the refactoring.

#### Scenario: All tests pass after split

- **GIVEN** the tool classes have been split and old classes deleted
- **WHEN** `./mvnw test` is executed
- **THEN** all tests SHALL pass with zero failures
