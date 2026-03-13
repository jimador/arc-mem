# Design: Context Unit LLM Tool Restructuring (Query/Mutation Separation)

**Change**: `unit-llm-tools`
**Status**: Design
**Date**: 2026-02-28

Keywords follow [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

---

## Context

The chat package exposes context unit operations to the LLM via two `@MatryoshkaTools` classes:

1. **`ContextTools`** (`src/main/java/dev/dunnam/arcmem/chat/ContextTools.java`, line 31) — a single `@MatryoshkaTools(name = "unit-tools")` record containing five `@LlmTool` methods: two read operations (`queryFacts`, `listUnits`) and three mutations (`pinFact`, `unpinFact`, `demoteUnit`).

2. **`ContextUnitRetrievalTools`** (`src/main/java/dev/dunnam/arcmem/chat/ContextUnitRetrievalTools.java`, line 29) — a separate `@MatryoshkaTools(name = "unit-retrieval")` record containing one `@LlmTool` method (`retrieveUnits`) registered conditionally when retrieval mode is HYBRID or TOOL.

`ChatActions.respond()` (lines 128-148) constructs these tool objects and passes them to the Embabel AI builder:

```java
var tools = new ContextTools(arcMemEngine, contextUnitRepository, contextId);
// ...
var toolObjects = new ArrayList<Object>();
toolObjects.add(tools);
if (retrievalMode == RetrievalMode.HYBRID || retrievalMode == RetrievalMode.TOOL) {
    var retrievalTools = new ContextUnitRetrievalTools(arcMemEngine, relevanceScorer, contextId, retrievalConfig);
    toolObjects.add(retrievalTools);
}
context.ai().withDefaultLlm().withToolObjects(toolObjects.toArray()) ...
```

This organization has two weaknesses: (1) CQS violation — read and write operations are indistinguishable from the tool group structure, and (2) no read-only registration path — any consumer registering `ContextTools` gets mutations.

### Affected Classes

| Class | Package | Role |
|---|---|---|
| `ContextTools` | `chat/` | Mixed query+mutation tools — to be deleted |
| `ContextUnitRetrievalTools` | `chat/` | Conditional retrieval tool — to be consolidated and deleted |
| `ChatActions` | `chat/` | Registration logic — to be updated |
| `ContextToolsTest` | `chat/` (test) | Test class — to be split |
| `UnitSummary` | `chat/` | Return type for query tools — unchanged |
| `PinResult` | `chat/` | Return type for mutation tools — unchanged |

---

## Goals / Non-Goals

### Goals

1. Split tool methods into two `@MatryoshkaTools` classes following CQS: query tools (read-only) and mutation tools (state-changing).
2. Consolidate `ContextUnitRetrievalTools.retrieveUnits` into the query tools class.
3. Add `@LlmTool.Param` annotations to all tool method parameters for richer LLM guidance.
4. Set `removeOnInvoke = false` on both tool groups so tools persist across conversation turns.
5. Update `ChatActions` registration to construct and register both tool groups.
6. Delete `ContextTools.java` and `ContextUnitRetrievalTools.java`.
7. Split `ContextToolsTest` into `ContextUnitQueryToolsTest` and `ContextUnitMutationToolsTest`.
8. All tests pass after refactoring.

### Non-Goals

1. Changing tool behavior or logic — all method bodies are moved as-is.
2. Adding new tool methods or modifying return types.
3. Switching to `@Action(toolGroups=...)` declarative registration — the explicit `withToolObjects()` pattern is retained for conditional logic clarity.
4. Using `@LlmTool(category=...)` for retrieval conditional logic — constructor-level config gating is clearer.
5. Implementing read-only context registration — this change enables it; a future change will wire it for specific consumers.
6. Modifying OTEL instrumentation beyond preserving existing `retrieveUnits` span attributes.

---

## Decisions

### D1: CQS Split into Two @MatryoshkaTools Records

**Decision**: Create `ContextUnitQueryTools` and `ContextUnitMutationTools` as separate Java records, each annotated with `@MatryoshkaTools`. Query tools contain `queryFacts`, `listUnits`, and `retrieveUnits`. Mutation tools contain `pinFact`, `unpinFact`, and `demoteUnit`.

**Rationale**: CQS at the tool group level gives the LLM structural information about operation safety. The Embabel `@MatryoshkaTools` facade presents grouped tools to the LLM — separate groups for reads vs. writes conveys intent without relying on description text alone. Two records also enable independent registration: read-only contexts can omit mutation tools entirely.

**Trade-off considered**: Using `@LlmTool(category=...)` within a single class was considered (feature doc section 4). Rejected because category grouping does not enable selective registration — all methods in the class are registered together.

**Files created**: `ContextUnitQueryTools.java`, `ContextUnitMutationTools.java`
**Files deleted**: `ContextTools.java`, `ContextUnitRetrievalTools.java`

---

### D2: @LlmTool.Param Adoption

**Decision**: All `@LlmTool` method parameters SHALL be annotated with `@LlmTool.Param(description = "...")`. The `required` attribute defaults to `true` and SHOULD NOT be overridden.

**Rationale**: The current tools use bare `String` parameters (e.g., `queryFacts(String subject)`) with no metadata. Embabel's `@LlmTool.Param` (discovered in API research, `R01-embabel-api-surface.md`, line 25) provides parameter descriptions that appear in the LLM's tool schema, improving tool-use accuracy. This is a low-cost improvement with high signal for tool-calling models.

**Files changed**: `ContextUnitQueryTools.java`, `ContextUnitMutationTools.java`

---

### D3: removeOnInvoke = false

**Decision**: Both `@MatryoshkaTools` annotations SHALL set `removeOnInvoke = false`.

**Rationale**: The default `removeOnInvoke = true` (API research, line 42) removes the facade after first invocation, replacing it with child tools. For context unit tools, the facade should persist across conversation turns so the LLM can repeatedly query and manage context units. The current `ContextTools` does not set this explicitly and relies on the default behavior — this change makes the intent explicit.

**Files changed**: `ContextUnitQueryTools.java`, `ContextUnitMutationTools.java`

---

### D4: Registration Strategy — Explicit withToolObjects

**Decision**: Retain the explicit `withToolObjects()` pattern in `ChatActions.respond()` rather than switching to `@Action(toolGroups=...)` declarative registration.

**Rationale**: The `retrieveUnits` method requires conditional availability based on retrieval mode (HYBRID/TOOL). Declarative `toolGroups` cannot express runtime conditions. The explicit pattern (feature doc section 3) keeps conditional logic visible:

```java
var queryTools = new ContextUnitQueryTools(arcMemEngine, contextUnitRepository, relevanceScorer,
        contextId, retrievalConfig, toolCallCounter);
var mutationTools = new ContextUnitMutationTools(arcMemEngine, contextUnitRepository, contextId);
context.ai().withToolObjects(queryTools, mutationTools) ...
```

When retrieval mode is BULK, `ContextUnitQueryTools` is constructed with `config = null`, and `retrieveUnits` returns an empty list (matching existing guard logic in `ContextUnitRetrievalTools`, line 55-58).

**Trade-off**: This means `retrieveUnits` is technically registered even in BULK mode but is a no-op. An alternative is to not register it at all in BULK mode by using two different `ContextUnitQueryTools` constructors. The simpler approach (always register, guard in method body) is preferred to avoid constructor proliferation.

**Files changed**: `ChatActions.java` (lines 128-148)

---

### D5: Retrieval Consolidation into ContextUnitQueryTools

**Decision**: Move `retrieveUnits` from `ContextUnitRetrievalTools` into `ContextUnitQueryTools`. The method body, OTEL span instrumentation (`retrieval.tool_call_count`), and `RelevanceScorer` blending logic are preserved unchanged. `ContextUnitQueryTools` gains `RelevanceScorer`, `RetrievalConfig`, and `AtomicInteger toolCallCounter` constructor parameters to support this.

**Rationale**: `retrieveUnits` is a read operation. Keeping it in a separate class was necessary before the CQS split because the original `ContextTools` mixed reads and writes. With the split, all reads belong in `ContextUnitQueryTools`. This eliminates the `ContextUnitRetrievalTools` class entirely and reduces the number of tool objects from two to two (query + mutation) instead of two to three.

**Files deleted**: `ContextUnitRetrievalTools.java`
**Files changed**: `ContextUnitQueryTools.java`

---

## Risks / Trade-offs

### R1: LLM tool-use regression

Splitting tools changes the tool schema the LLM sees. Tool names change from `unit-tools` to `unit-query-tools` and `unit-mutation-tools`. Tool-calling models may behave differently with the new schema.

**Mitigation**: Smoke test the chat flow after refactoring. Tool descriptions are preserved verbatim from the original classes. The LLM selects tools by description content, not by group name. If regression is observed, tool descriptions can be tuned without structural changes.

### R2: retrieveUnits always registered in BULK mode

With the consolidation approach (D4/D5), `retrieveUnits` is registered as a tool even when retrieval mode is BULK. The method returns an empty list in this case, but the LLM may still attempt to call it.

**Mitigation**: The empty-list response is fast and harmless. If LLM calls to `retrieveUnits` in BULK mode become noisy, the tool description can be updated to state "Returns results only when retrieval mode is enabled" or the method can be conditionally excluded via a separate constructor that omits it.
