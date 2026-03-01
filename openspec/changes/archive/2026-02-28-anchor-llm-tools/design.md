# Design: Anchor LLM Tool Restructuring (Query/Mutation Separation)

**Change**: `anchor-llm-tools`
**Status**: Design
**Date**: 2026-02-28

Keywords follow [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

---

## Context

The chat package exposes anchor operations to the LLM via two `@MatryoshkaTools` classes:

1. **`AnchorTools`** (`src/main/java/dev/dunnam/diceanchors/chat/AnchorTools.java`, line 31) — a single `@MatryoshkaTools(name = "anchor-tools")` record containing five `@LlmTool` methods: two read operations (`queryFacts`, `listAnchors`) and three mutations (`pinFact`, `unpinFact`, `demoteAnchor`).

2. **`AnchorRetrievalTools`** (`src/main/java/dev/dunnam/diceanchors/chat/AnchorRetrievalTools.java`, line 29) — a separate `@MatryoshkaTools(name = "anchor-retrieval")` record containing one `@LlmTool` method (`retrieveAnchors`) registered conditionally when retrieval mode is HYBRID or TOOL.

`ChatActions.respond()` (lines 128-148) constructs these tool objects and passes them to the Embabel AI builder:

```java
var tools = new AnchorTools(anchorEngine, anchorRepository, contextId);
// ...
var toolObjects = new ArrayList<Object>();
toolObjects.add(tools);
if (retrievalMode == RetrievalMode.HYBRID || retrievalMode == RetrievalMode.TOOL) {
    var retrievalTools = new AnchorRetrievalTools(anchorEngine, relevanceScorer, contextId, retrievalConfig);
    toolObjects.add(retrievalTools);
}
context.ai().withDefaultLlm().withToolObjects(toolObjects.toArray()) ...
```

This organization has two weaknesses: (1) CQS violation — read and write operations are indistinguishable from the tool group structure, and (2) no read-only registration path — any consumer registering `AnchorTools` gets mutations.

### Affected Classes

| Class | Package | Role |
|---|---|---|
| `AnchorTools` | `chat/` | Mixed query+mutation tools — to be deleted |
| `AnchorRetrievalTools` | `chat/` | Conditional retrieval tool — to be consolidated and deleted |
| `ChatActions` | `chat/` | Registration logic — to be updated |
| `AnchorToolsTest` | `chat/` (test) | Test class — to be split |
| `AnchorSummary` | `chat/` | Return type for query tools — unchanged |
| `PinResult` | `chat/` | Return type for mutation tools — unchanged |

---

## Goals / Non-Goals

### Goals

1. Split tool methods into two `@MatryoshkaTools` classes following CQS: query tools (read-only) and mutation tools (state-changing).
2. Consolidate `AnchorRetrievalTools.retrieveAnchors` into the query tools class.
3. Add `@LlmTool.Param` annotations to all tool method parameters for richer LLM guidance.
4. Set `removeOnInvoke = false` on both tool groups so tools persist across conversation turns.
5. Update `ChatActions` registration to construct and register both tool groups.
6. Delete `AnchorTools.java` and `AnchorRetrievalTools.java`.
7. Split `AnchorToolsTest` into `AnchorQueryToolsTest` and `AnchorMutationToolsTest`.
8. All tests pass after refactoring.

### Non-Goals

1. Changing tool behavior or logic — all method bodies are moved as-is.
2. Adding new tool methods or modifying return types.
3. Switching to `@Action(toolGroups=...)` declarative registration — the explicit `withToolObjects()` pattern is retained for conditional logic clarity.
4. Using `@LlmTool(category=...)` for retrieval conditional logic — constructor-level config gating is clearer.
5. Implementing read-only context registration — this change enables it; a future change will wire it for specific consumers.
6. Modifying OTEL instrumentation beyond preserving existing `retrieveAnchors` span attributes.

---

## Decisions

### D1: CQS Split into Two @MatryoshkaTools Records

**Decision**: Create `AnchorQueryTools` and `AnchorMutationTools` as separate Java records, each annotated with `@MatryoshkaTools`. Query tools contain `queryFacts`, `listAnchors`, and `retrieveAnchors`. Mutation tools contain `pinFact`, `unpinFact`, and `demoteAnchor`.

**Rationale**: CQS at the tool group level gives the LLM structural information about operation safety. The Embabel `@MatryoshkaTools` facade presents grouped tools to the LLM — separate groups for reads vs. writes conveys intent without relying on description text alone. Two records also enable independent registration: read-only contexts can omit mutation tools entirely.

**Trade-off considered**: Using `@LlmTool(category=...)` within a single class was considered (feature doc section 4). Rejected because category grouping does not enable selective registration — all methods in the class are registered together.

**Files created**: `AnchorQueryTools.java`, `AnchorMutationTools.java`
**Files deleted**: `AnchorTools.java`, `AnchorRetrievalTools.java`

---

### D2: @LlmTool.Param Adoption

**Decision**: All `@LlmTool` method parameters SHALL be annotated with `@LlmTool.Param(description = "...")`. The `required` attribute defaults to `true` and SHOULD NOT be overridden.

**Rationale**: The current tools use bare `String` parameters (e.g., `queryFacts(String subject)`) with no metadata. Embabel's `@LlmTool.Param` (discovered in API research, `R01-embabel-api-surface.md`, line 25) provides parameter descriptions that appear in the LLM's tool schema, improving tool-use accuracy. This is a low-cost improvement with high signal for tool-calling models.

**Files changed**: `AnchorQueryTools.java`, `AnchorMutationTools.java`

---

### D3: removeOnInvoke = false

**Decision**: Both `@MatryoshkaTools` annotations SHALL set `removeOnInvoke = false`.

**Rationale**: The default `removeOnInvoke = true` (API research, line 42) removes the facade after first invocation, replacing it with child tools. For anchor tools, the facade should persist across conversation turns so the LLM can repeatedly query and manage anchors. The current `AnchorTools` does not set this explicitly and relies on the default behavior — this change makes the intent explicit.

**Files changed**: `AnchorQueryTools.java`, `AnchorMutationTools.java`

---

### D4: Registration Strategy — Explicit withToolObjects

**Decision**: Retain the explicit `withToolObjects()` pattern in `ChatActions.respond()` rather than switching to `@Action(toolGroups=...)` declarative registration.

**Rationale**: The `retrieveAnchors` method requires conditional availability based on retrieval mode (HYBRID/TOOL). Declarative `toolGroups` cannot express runtime conditions. The explicit pattern (feature doc section 3) keeps conditional logic visible:

```java
var queryTools = new AnchorQueryTools(anchorEngine, anchorRepository, relevanceScorer,
        contextId, retrievalConfig, toolCallCounter);
var mutationTools = new AnchorMutationTools(anchorEngine, anchorRepository, contextId);
context.ai().withToolObjects(queryTools, mutationTools) ...
```

When retrieval mode is BULK, `AnchorQueryTools` is constructed with `config = null`, and `retrieveAnchors` returns an empty list (matching existing guard logic in `AnchorRetrievalTools`, line 55-58).

**Trade-off**: This means `retrieveAnchors` is technically registered even in BULK mode but is a no-op. An alternative is to not register it at all in BULK mode by using two different `AnchorQueryTools` constructors. The simpler approach (always register, guard in method body) is preferred to avoid constructor proliferation.

**Files changed**: `ChatActions.java` (lines 128-148)

---

### D5: Retrieval Consolidation into AnchorQueryTools

**Decision**: Move `retrieveAnchors` from `AnchorRetrievalTools` into `AnchorQueryTools`. The method body, OTEL span instrumentation (`retrieval.tool_call_count`), and `RelevanceScorer` blending logic are preserved unchanged. `AnchorQueryTools` gains `RelevanceScorer`, `RetrievalConfig`, and `AtomicInteger toolCallCounter` constructor parameters to support this.

**Rationale**: `retrieveAnchors` is a read operation. Keeping it in a separate class was necessary before the CQS split because the original `AnchorTools` mixed reads and writes. With the split, all reads belong in `AnchorQueryTools`. This eliminates the `AnchorRetrievalTools` class entirely and reduces the number of tool objects from two to two (query + mutation) instead of two to three.

**Files deleted**: `AnchorRetrievalTools.java`
**Files changed**: `AnchorQueryTools.java`

---

## Risks / Trade-offs

### R1: LLM tool-use regression

Splitting tools changes the tool schema the LLM sees. Tool names change from `anchor-tools` to `anchor-query-tools` and `anchor-mutation-tools`. Tool-calling models may behave differently with the new schema.

**Mitigation**: Smoke test the chat flow after refactoring. Tool descriptions are preserved verbatim from the original classes. The LLM selects tools by description content, not by group name. If regression is observed, tool descriptions can be tuned without structural changes.

### R2: retrieveAnchors always registered in BULK mode

With the consolidation approach (D4/D5), `retrieveAnchors` is registered as a tool even when retrieval mode is BULK. The method returns an empty list in this case, but the LLM may still attempt to call it.

**Mitigation**: The empty-list response is fast and harmless. If LLM calls to `retrieveAnchors` in BULK mode become noisy, the tool description can be updated to state "Returns results only when retrieval mode is enabled" or the method can be conditionally excluded via a separate constructor that omits it.
