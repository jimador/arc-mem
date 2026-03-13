## Context

Embabel Agent supports `@MatryoshkaTools` records with `@LlmTool` methods that the LLM can call during conversation. context units doesn't use this feature yet — `ChatActions` only uses `@Action` + `rendering()`. Adding context unit tools lets Bigby (the DM) actively query and manage knowledge, which is a deep integration point.

The codebase already has:
- `ArcMemEngine` with `inject()`, `reinforce()`, `promote()`, `detectConflicts()`
- `ContextUnitRepository` with `findActiveUnits()`, `semanticSearch()`, `findById()`
- `Context Unit` record with `text()`, `rank()`, `authority()`, `pinned()`

We just need to wrap these in `@LlmTool` methods.

## Goals / Non-Goals

**Goals:**
- Expose context unit query and management as LLM-callable tools
- Follow Embabel patterns: `@MatryoshkaTools` record, `@LlmTool` methods
- Return structured data (records), not formatted strings
- Guard against dangerous operations (no CANON assignment, no arbitrary deletions)

**Non-Goals:**
- Add tools to simulation (chat-only)
- Expose full repository API (curated subset only)
- Add UI for tool usage (LLM decides when to call tools)
- Expose DICE extraction as a tool (extraction is automatic)

## Decisions

### 1. Tool Design: @MatryoshkaTools Record

```java
@MatryoshkaTools
public record ContextTools(ArcMemEngine engine, ContextUnitRepository repository) {

    @LlmTool("Query established facts about a subject. Returns matching context units with rank and authority.")
    public List<UnitSummary> queryFacts(String subject) { ... }

    @LlmTool("Get all currently active context units with their rank, authority, and confidence.")
    public List<UnitSummary> listUnits() { ... }

    @LlmTool("Pin an important fact so it cannot be evicted by budget enforcement.")
    public PinResult pinFact(String unitId) { ... }

    @LlmTool("Unpin a previously pinned fact, allowing normal eviction rules.")
    public PinResult unpinFact(String unitId) { ... }
}
```

**Why**: `@MatryoshkaTools` is the Embabel pattern for tool containers. Record ensures immutability. Methods return data records, not strings.

**Alternative considered**: Separate @LlmTool methods on ChatActions (mixes concerns). @MatryoshkaTools keeps tools isolated.

### 2. Tool Operations

| Tool | Purpose | Guards |
|------|---------|--------|
| `queryFacts(subject)` | Semantic search for context units matching subject | Read-only. Uses `repository.semanticSearch()` |
| `listUnits()` | List all active context units | Read-only. Uses `engine.inject(contextId)` |
| `pinFact(unitId)` | Pin context unit (immune to eviction) | MUST verify context unit exists. MUST NOT pin archived. |
| `unpinFact(unitId)` | Unpin context unit | MUST verify context unit exists and is pinned. |

**Why**: Read-heavy, write-guarded. LLM can query freely but mutations are limited to safe operations (pin/unpin). No authority changes, no rank manipulation, no deletions.

### 3. Response Records

```java
record UnitSummary(String id, String text, int rank, String authority, boolean pinned, double confidence) {}
record PinResult(boolean success, String message) {}
```

**Why**: Data over strings per coding style. LLM receives structured data it can reason about.

### 4. Wiring into ChatActions

Register tools in ChatActions.respond():
```java
var tools = new ContextTools(engine, repository);
context.ai()
    .withDefaultLlm()
    .withTools(tools)
    .rendering("context units")
    .respondWithSystemPrompt(conversation, templateVars);
```

**Why**: Tools available during conversation. LLM can call them as part of its response.

### 5. Safety Guards

- `pinFact`/`unpinFact`: Verify context unit exists, is active, and is not CANON (CANON is always treated as pinned)
- No tool for `promote()` — promotion is automatic via DICE pipeline
- No tool for authority changes — authority is upgrade-only via reinforcement
- No tool for rank manipulation — rank changes via reinforcement policy
- Log all tool invocations for audit

**Why**: LLM should manage knowledge, not break invariants.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **LLM calls tools excessively** | Tools are cheap (read-only or simple mutation). Log frequency. |
| **LLM pins everything** | Pin is reversible. Budget enforcement still works (pinned context units are immune to eviction but don't multiply). |
| **Tool descriptions confuse LLM** | Test with real conversations. Adjust descriptions if needed. |
| **Embabel API changes** | Pin to 0.3.5-SNAPSHOT. Test at each upgrade. |

## Migration Plan

1. Create `UnitSummary` and `PinResult` records
2. Create `ContextTools` @MatryoshkaTools record
3. Implement query tools (read-only)
4. Implement pin/unpin tools (guarded mutations)
5. Wire into `ChatActions.respond()` via `.withTools()`
6. Add safety guards and logging
7. Test with live chat conversation
8. Demo: Show LLM querying its own knowledge mid-conversation

## Open Questions

- Should LLM be able to suggest promotion? (Out of scope; promotion is automatic via DICE pipeline)
- Should tools be visible in chat UI? (Tool calls could be shown as system messages; nice-to-have)
