# Embabel API Inventory

**Last verified:** February 28, 2026
**Embabel Agent version:** 0.3.5-SNAPSHOT
**Scope:** dice-anchors codebase integration surface

This document is a curated reference of Embabel Agent APIs used in dice-anchors, available-but-unused capabilities, and recommended patterns for chat integration. It is not a tutorial; see the [Embabel documentation](https://docs.embabel.com/embabel-agent/) and [examples](https://github.com/embabel/embabel-agent-examples/tree/main/examples-java) for comprehensive guides.

## Current Usage

### Annotations

| Annotation | Parameters Used | File:Line | Purpose |
|-----------|-----------------|-----------|---------|
| `@EmbabelComponent` | — | `ChatActions.java:45` | Registers the component as a Spring bean for automatic discovery by the Embabel agent platform |
| `@Action` | `trigger=UserMessage.class` `canRerun=true` | `ChatActions.java:68` | Declares that the `respond()` method executes on each user message; `canRerun` allows multiple invocations per session |
| `@MatryoshkaTools` | `name`, `description`, `removeOnInvoke=false` | `AnchorQueryTools.java:21` `AnchorMutationTools.java:13` | Groups read-only and mutation tools into separate facades; `removeOnInvoke=false` keeps both groups available across turns |
| `@LlmTool` | `description` | `AnchorQueryTools.java:41,69,84` `AnchorMutationTools.java:20,43,69` | Marks methods as callable tools; descriptions guide the LLM on tool purpose and usage |

### Nested Annotation

| Annotation | Parameters Used | File:Line | Purpose |
|-----------|-----------------|-----------|---------|
| `@LlmTool.Param` | `description` | `AnchorQueryTools.java:48,91` `AnchorMutationTools.java:26,49,77` | Documents method parameters for the LLM tool descriptions; helps the LLM understand what to pass |

### APIs and Interfaces

| Class / Interface | Usage | Location |
|------------------|-------|----------|
| `ActionContext` | Passed to `@Action` methods; used to resolve templates and access the LLM builder | `ChatActions.java:69` |
| `AiBuilder` | Part of `ActionContext`; chains tool registration and template rendering | `ChatActions.java` (implicit via context) |
| `AgentProcessChatbot.utilityFromPlatform()` | Creates the chatbot in utility mode (single action per message, no multi-action orchestration) | `ChatConfiguration.java:27` |
| `Chatbot` | Spring-managed bean injected into `ChatView`; creates per-session agent processes | `ChatConfiguration.java:26` |
| `Conversation` | Represents multi-turn chat state; carries anchors and propositions across messages | `ChatActions.java:69` |
| `UserMessage` | Event type matching `@Action(trigger = UserMessage.class)`; represents user input | `ChatActions.java:68` |

### Patterns in Use

**Template rendering via Jinja2:** The `ActionContext` chains template variables and tool registration via `.withToolObjects()` and `.rendering("dice-anchors")` to inject anchor context into each LLM response.

**Tool registration and CQS:** Tools are registered conditionally as `AnchorQueryTools` and `AnchorMutationTools` separate `@MatryoshkaTools` groups. Query tools are safe for simulation (read-only); mutation tools are chat-only. Both groups use `removeOnInvoke=false` to persist across turns.

**Spring event listeners for extraction:** `ConversationPropositionExtraction` listens for `ConversationAnalysisRequestEvent` (published after each LLM response) and asynchronously extracts propositions via DICE.

---

## Available But Unused

### Annotation Parameters (8 unused)

| Parameter | Annotation | Type | When Valuable |
|-----------|-----------|------|---------------|
| `cost` | `@Action` | `double` | Multi-action planning: when multiple action sequences could satisfy a goal, the planner prefers lower-cost paths. Valuable if dice-anchors adopts GOAP mode. |
| `toolGroups` | `@Action` | `String[]` | Declarative tool group binding: restrict an action to specific `@MatryoshkaTools` groups. Valuable if actions need fine-grained tool visibility control. |
| `removeOnInvoke` | `@MatryoshkaTools` | `boolean` (default: `true`) | Progressive disclosure: when `true`, the tool group is replaced by its child tools after the first invocation. Currently `false` to keep all anchor tools available across turns. |
| `categoryParameter` | `@MatryoshkaTools` | `String` (default: `"category"`) | Category-based child tool selection: if set to (e.g.) `"operation"`, the LLM can invoke `anchor-tools?operation=query` to select a specific subset of tools. Valuable for tool organization when a group grows large. |
| `childToolUsageNotes` | `@MatryoshkaTools` | `String` | LLM guidance for child tool usage: additional context the framework appends to tool descriptions. Valuable for explaining tool interdependencies or preferred usage order. |
| `returnDirect` | `@LlmTool` | `boolean` (default: `false`) | Bypass additional LLM processing of results: when `true`, tool output is returned directly without the LLM post-processing it. Valuable when tool results are already in the desired form (e.g., a formatted list). |
| `category` | `@LlmTool` | `String` | Tool grouping within `@MatryoshkaTools`: assigns the tool to a category. Used with `@MatryoshkaTools(categoryParameter)` for subcategory selection. |
| `name` | `@LlmTool` | `String` | Tool identifier: overrides the method name. Valuable for explicit control when refactoring method names would break LLM training or consistency. |

### Annotations (3 unused)

| Annotation | Purpose | When Valuable |
|-----------|---------|---------------|
| `@AchievesGoal` | Marks an action as a goal completion point; enables the planning engine to know when an objective is satisfied | Valuable in GOAP mode (multi-action planning) if dice-anchors adopts goal-driven conversation flows |
| `@Agent` | Declarative agent registration: richer than `@EmbabelComponent`, allows custom naming and description | Valuable if multiple agents coexist in the same Spring context |
| `@Export` | Sub-annotation of `@AchievesGoal`: exposes a goal as a remote HTTP endpoint | Valuable for multi-service orchestration (e.g., calling dice-anchors agent from another service) |

### Modes and Subsystems

| Capability | Current | When Valuable |
|-----------|---------|---------------|
| **GOAP chatbot mode** | Not used; dice-anchors uses utility mode (`AgentProcessChatbot.utilityFromPlatform()`) | Multi-action orchestration: if the agent needs to chain multiple actions to achieve a goal, GOAP mode selects and sequences actions automatically. Requires `@AchievesGoal` annotations on goal-completion actions. |
| **Blackboard binding** | Basic usage via `ActionContext`; not explicitly manipulated via `agentProcess.bindProtected()` | Advanced state persistence: if the agent needs shared, conversation-scoped objects beyond what `Conversation` carries, the blackboard provides a durable key-value store. |
| **Message vs. Trigger pattern** | Not explicitly used; dice-anchors uses only `UserMessage` | Ephemeral logic: `ChatTrigger` (or custom trigger types) execute side effects without adding to conversation history. Valuable for internal state updates or polling tasks. |

---

## Tool Organization: Command-Query Separation (CQS)

Dice-anchors organizes anchor tools into two `@MatryoshkaTools` groups following the **Command-Query Separation** principle:

**Query tools** (`AnchorQueryTools`, `anchor-query-tools`):
- `queryFacts(subject)` — semantic search over anchors
- `listAnchors()` — full anchor roster
- `retrieveAnchors(query)` — relevance-scored retrieval
- **Characteristics:** Read-only, safe for simulation, deterministic, no side effects

**Mutation tools** (`AnchorMutationTools`, `anchor-mutation-tools`):
- `pinFact(anchorId)` — mark anchor as budget-protected
- `unpinFact(anchorId)` — allow normal eviction
- `demoteAnchor(anchorId)` — lower authority level
- **Characteristics:** State-modifying, chat-only (not registered in simulation), guarded against invariant violations

**Rationale:** CQS enables **selective registration**: simulation runs register only `AnchorQueryTools` to prevent unwanted state mutations during benchmark adversarial turns. Chat sessions register both groups, allowing the LLM to manage anchor state when resolving contradictions or reinforcing facts.

This separation also clarifies tool purpose and reduces cognitive load — the LLM sees query and mutation tools as distinct concerns rather than a monolithic anchor-management namespace.

---

## Recommended Patterns

### Tool Descriptions

MUST provide concise, action-oriented descriptions that guide the LLM on purpose, return type, and usage.

**Good example:**
```java
@LlmTool(description = """
        Search for established facts (anchors) by subject or keyword. \
        Returns anchors with their ID, text, rank (100–900), authority level \
        (PROVISIONAL < UNRELIABLE < RELIABLE < CANON), pinned status, and confidence. \
        Use this before asserting facts to verify what is already known. \
        Returns an empty list if no matching anchors exist.""")
public List<AnchorSummary> queryFacts(String subject) { ... }
```

**Pattern checklist:**
- Lead with **action**: "Search for", "List", "Pin"
- Include **return shape**: "Returns a list of", "Returns success=true if"
- Mention **constraints**: "Only active anchors", "CANON anchors cannot be"
- Add **usage hint**: "Use this before", "Use when you need"

### Parameter Documentation

SHOULD use `@LlmTool.Param` to document parameters when their purpose is not obvious from name or type.

**Example:**
```java
@LlmTool(description = "Pin an anchor...")
public PinResult pinFact(
        @LlmTool.Param(description = "ID of the anchor to pin") String anchorId) {
    ...
}
```

This allows the framework to include parameter descriptions in the tool schema the LLM receives.

### Error Handling in Tools

MUST return result records (not throw exceptions) to keep errors in the LLM's context and allow graceful recovery.

**Good pattern:**
```java
public PinResult pinFact(String anchorId) {
    var nodeOpt = repository.findPropositionNodeById(anchorId);
    if (nodeOpt.isEmpty()) {
        return fail("Anchor not found: " + anchorId);  // Result record, not exception
    }
    // ...
    return ok("Fact pinned successfully");
}
```

**Why:** Exceptions bypass the LLM's reasoning loop. Result records keep errors as structured data the LLM can inspect and respond to conversationally.

### Guarding Invariants

Tool methods that modify state MUST validate against domain invariants before committing.

**Example invariants in anchor tools:**
- "CANON anchors cannot be unpinned" → check `Authority.valueOf(node.getAuthority()) == Authority.CANON`
- "Only active anchors can be pinned" → check `node.getStatus() == PropositionStatus.ACTIVE`
- "Archived anchors cannot be pinned" → check status before update

Validation happens **inside** the tool, not in the framework layer. The tool returns a failure result if invariants are violated.

### Message vs. Trigger Distinction

Use `UserMessage` (the default trigger) for normal conversational flow. Use custom trigger types (e.g., `ChatTrigger`) for internal side effects that should not appear in conversation history.

Currently unused in dice-anchors, but valuable if future features need to:
- Poll anchor state without user input
- Emit lifecycle events (e.g., when an anchor is promoted to CANON)
- Execute cleanup between turns

---

## See Also

- `CLAUDE.md` — Project governance and architecture overview
- `docs/dev/dice-integration.md` — DICE proposition extraction integration
- [Embabel Agent 0.3.5 API Docs](https://docs.embabel.com/embabel-agent/api-docs/0.3.5-SNAPSHOT/index.html)
- [Embabel Agent Examples (Java)](https://github.com/embabel/embabel-agent-examples/tree/main/examples-java)
- [Embabel Coding Style](https://github.com/embabel/embabel-agent/blob/main/embabel-agent-api/.embabel/coding-style.md)
