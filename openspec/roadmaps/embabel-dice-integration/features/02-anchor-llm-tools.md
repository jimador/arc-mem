# Feature: Anchor LLM Tool Restructuring (Query/Mutation Separation)

## Context

Current `AnchorTools` (@MatryoshkaTools) mixes read operations with mutations in a single class:
- **Read**: `queryFacts(String subject)`, `listAnchors()`, `retrieveAnchors(String)` (in separate `AnchorRetrievalTools` class)
- **Write**: `pinFact(String anchorId)`, `unpinFact(String anchorId)`, `demoteAnchor(String anchorId)`

This organization violates Command Query Segregation (CQS) principle and prevents selective tool registration for read-only contexts (e.g., simulation audit mode where LLM should not mutate state).

**Goal**: Restructure tools into two `@MatryoshkaTools` classes (query and mutation), consolidate retrieval tools, and update registration to enable read-only contexts.

## Proposal Seed

### Why This Feature

Separating query from mutation tools provides:
1. **CQS clarity**: LLM can distinguish between safe reads and state-changing operations
2. **Selective registration**: Read-only contexts (simulation, audit) register only query tools; chat registers both
3. **Tool descriptions**: Query tools describe read-only guarantees; mutation tools describe state guarantees
4. **Embabel best practices**: Aligns with framework recommendations for tool organization

### What Changes

**Code**:
- Create `AnchorQueryTools.java` (@MatryoshkaTools) with: `queryFacts`, `listAnchors`, `retrieveAnchors`
- Create `AnchorMutationTools.java` (@MatryoshkaTools) with: `pinFact`, `unpinFact`, `demoteAnchor`
- Consolidate `AnchorRetrievalTools.java` into query tools (same retrieval-mode conditional logic)
- Update `ChatActions.respond()` to register both tool groups in chat mode
- Update read-only contexts to register only query tools
- Delete `AnchorTools.java` and `AnchorRetrievalTools.java`
- Update test classes: split `AnchorToolsTest` into `AnchorQueryToolsTest` and `AnchorMutationToolsTest`

**No version changes**: Embabel 0.3.5-SNAPSHOT remains as-is.

### Success Criteria

- [ ] `AnchorQueryTools` class created with three `@LlmTool` methods
- [ ] `AnchorMutationTools` class created with three `@LlmTool` methods
- [ ] `retrieveAnchors` consolidated from `AnchorRetrievalTools` (with HYBRID/TOOL mode conditional logic preserved)
- [ ] `ChatActions` updated for conditional tool registration (both groups in chat, query-only in read-only)
- [ ] All tests passing: `./mvnw test`
- [ ] Chat integration test verifies LLM can call query + mutation tools
- [ ] Read-only mode test verifies only query tools available
- [ ] Tool descriptions verified to be clear and specific
- [ ] OTEL instrumentation updated (span attributes track which tool group)

### Visibility

- **UI**: Chat tool availability changes; may affect LLM tool-use patterns
- **Observability**: Tool calls logged by group (query vs. mutation); OTEL spans tagged accordingly

## Embabel API Research Findings

*Source: Embabel MCP server + codebase analysis. See `research/R01-embabel-api-surface.md` for full details.*

### Specific API Changes to Make

#### 1. Add `@LlmTool.Param` Annotations

Current tools use bare `String` parameters. Embabel provides `@LlmTool.Param(description, required)` for richer LLM guidance:

```java
// BEFORE (current)
@LlmTool(description = "Search for established facts by subject")
public List<AnchorSummary> queryFacts(String subject) { ... }

// AFTER (with @LlmTool.Param)
@LlmTool(description = "Search for established facts by subject")
public List<AnchorSummary> queryFacts(
    @LlmTool.Param(description = "Subject or keyword to search for in anchors") String subject
) { ... }
```

Apply to all 6 tool methods across both groups.

#### 2. Use `@MatryoshkaTools(removeOnInvoke=false)` Explicitly

Both tool groups SHOULD set `removeOnInvoke = false` so tools persist across conversation turns (default is `true` which removes the facade after first invocation):

```java
@MatryoshkaTools(
    name = "anchor-query-tools",
    description = "Tools for querying established facts (anchors)",
    removeOnInvoke = false
)
```

#### 3. Consider `@Action(toolGroups=...)` for Registration

Instead of manually building `toolObjects` arrays in `ChatActions.respond()`:

```java
// CURRENT: manual tool object array
var toolObjects = new ArrayList<Object>();
toolObjects.add(tools);
if (retrievalMode == HYBRID || retrievalMode == TOOL) {
    toolObjects.add(retrievalTools);
}
context.ai().withToolObjects(toolObjects.toArray())

// ALTERNATIVE: declarative via @Action(toolGroups)
@Action(trigger = UserMessage.class, canRerun = true,
        toolGroups = {"anchor-query-tools", "anchor-mutation-tools"})
```

**Trade-off**: Declarative is cleaner but loses conditional registration (retrieval mode). Evaluate whether `@LlmTool(category)` within query tools could handle this instead.

#### 4. Consider `@LlmTool(category)` for Retrieval Conditional Logic

Instead of a separate conditional class, use category-based grouping within `AnchorQueryTools`:

```java
@MatryoshkaTools(name = "anchor-query-tools", ...)
public record AnchorQueryTools(...) {
    @LlmTool(description = "...", category = "core")
    public List<AnchorSummary> queryFacts(String subject) { ... }

    @LlmTool(description = "...", category = "core")
    public List<AnchorSummary> listAnchors() { ... }

    @LlmTool(description = "...", category = "retrieval")  // conditional
    public List<ScoredAnchor> retrieveAnchors(String query) { ... }
}
```

**Trade-off**: Simpler class structure vs. less obvious conditional logic. Needs testing.

## Design Sketch

### Tool Group Structure

```java
// AnchorQueryTools.java
@MatryoshkaTools(
    name = "anchor-query-tools",
    description = "Read-only tools for querying established facts (anchors)",
    removeOnInvoke = false
)
public record AnchorQueryTools(AnchorEngine engine, AnchorRepository repository,
                                RelevanceScorer scorer, String contextId,
                                RetrievalConfig config, AtomicInteger toolCallCounter) {

    @LlmTool(description = "Search for established facts (anchors) by subject or keyword")
    public List<AnchorSummary> queryFacts(
        @LlmTool.Param(description = "Subject or keyword to search") String subject
    ) { ... }

    @LlmTool(description = "List all currently active anchors for the conversation context")
    public List<AnchorSummary> listAnchors() { ... }

    @LlmTool(description = "Retrieve anchors most relevant to a topic, scored by semantic and heuristic signals")
    public List<ScoredAnchor> retrieveAnchors(
        @LlmTool.Param(description = "Topic or question to find relevant anchors for") String query
    ) { ... }
    // retrieveAnchors conditional: only when retrieval mode is HYBRID or TOOL
}

// AnchorMutationTools.java
@MatryoshkaTools(
    name = "anchor-mutation-tools",
    description = "Tools for managing anchor state (pin, unpin, demote)",
    removeOnInvoke = false
)
public record AnchorMutationTools(AnchorEngine engine, AnchorRepository repository,
                                   String contextId) {

    @LlmTool(description = "Pin an anchor so it cannot be evicted when the budget is exceeded")
    public PinResult pinFact(
        @LlmTool.Param(description = "ID of the anchor to pin") String anchorId
    ) { ... }

    @LlmTool(description = "Unpin an anchor, restoring normal eviction eligibility")
    public PinResult unpinFact(
        @LlmTool.Param(description = "ID of the anchor to unpin") String anchorId
    ) { ... }

    @LlmTool(description = "Demote an anchor's authority by one level")
    public String demoteAnchor(
        @LlmTool.Param(description = "ID of the anchor to demote") String anchorId
    ) { ... }
}
```

### Registration Strategy

```java
// ChatActions.respond() - chat mode (full access)
// Option A: manual (current pattern, updated for two groups)
context.ai()
    .withDefaultLlm()
    .withToolObjects(queryTools, mutationTools)
    .rendering("dice-anchors")
    .respondWithSystemPrompt(...)

// Option B: declarative via @Action(toolGroups)
@Action(trigger = UserMessage.class, canRerun = true,
        toolGroups = {"anchor-query-tools", "anchor-mutation-tools"})
```

### Consolidated `retrieveAnchors`

Move from `AnchorRetrievalTools` to `AnchorQueryTools`:
- Preserve retrieval-mode conditional logic (HYBRID/TOOL modes only)
- Keep `RelevanceScorer` blending logic
- Keep OTEL span instrumentation
- Conditional: method guard or `@LlmTool(category="retrieval")` grouping

## Dependencies

- **Depends on**: F1 (Embabel API Inventory) — inventory documents tool organization patterns
- **No code dependencies** between features, but F1 provides context for design decisions

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Tool split changes LLM tool-use patterns | Smoke test chat; verify LLM still uses tools appropriately |
| Retrieval-mode conditional logic breaks | Preserve existing conditional checks; test HYBRID and TOOL modes |
| Tests break due to registration changes | Update ChatActionsTest and AnchorToolsTest to match new structure |

## Open Questions

1. Should `retrieveAnchors` remain conditional (HYBRID/TOOL modes), or always available?
   - **Recommendation**: Keep conditional; retrieval is optimization when enabled
2. Should read-only contexts use `@Condition` guard or explicit registration check?
   - **Recommendation**: Explicit registration check (clearer for Embabel config)

## Acceptance Gates

1. **Code completeness**: All tool methods moved; old classes deleted
2. **Test coverage**: `./mvnw test` passes; chat + read-only mode tests pass
3. **Integration test**: Chat integration test confirms query + mutation tools callable
4. **Tool descriptions**: Verified in chat tool list; descriptions clear and specific
5. **OTEL instrumentation**: Span attributes updated; tool source (query vs. mutation) tracked

## Implementation Sequence

1. Create `AnchorQueryTools.java` with query methods + consolidated `retrieveAnchors`
2. Create `AnchorMutationTools.java` with mutation methods
3. Update `ChatActions` registration logic
4. Update test classes
5. Delete old `AnchorTools.java` and `AnchorRetrievalTools.java`
6. Run `./mvnw test` and verify all integration tests pass
7. Run chat smoke test (manual or CI)

## Next Steps

Once feature is approved:
1. Create OpenSpec change: `/opsx:new` with slug `anchor-llm-tools`
2. Work through proposal → spec → design → tasks
3. Archive change and sync specs via `/opsx:sync` or `/opsx:archive`
