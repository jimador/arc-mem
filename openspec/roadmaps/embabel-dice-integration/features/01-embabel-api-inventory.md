# Feature: Embabel API Inventory & Patterns

## Context

dice-anchors integrates with Embabel Agent 0.3.5-SNAPSHOT for chat orchestration. Current integration uses basic capabilities:
- Single `@Action` method in `ChatActions.respond()`
- `@MatryoshkaTools` for tool organization
- `@LlmTool` annotations for individual tool methods
- Template rendering via Jinja2 for context injection
- Spring event listeners for asynchronous extraction

Embabel provides richer capabilities not currently used:
- `@AchievesGoal(description, export)` — marks action as goal completion point; enables planning engine to know when objective is satisfied
- `@Agent(name, description, beanName)` — declarative agent registration (richer than @EmbabelComponent)
- `@LlmTool.Param(description, required)` — annotates method parameters for richer LLM tool descriptions
- `@Action(cost)` — planning cost parameter for path selection (we only use `trigger` and `canRerun`)
- `@Action(toolGroups)` — selective tool group binding per action (we pass tool objects manually)
- `@MatryoshkaTools(removeOnInvoke, categoryParameter, childToolUsageNotes)` — progressive disclosure parameters (we only use `name` and `description`)
- `@LlmTool(returnDirect, category)` — direct return and category grouping (we only use `description`)
- GOAP chatbot mode via `new AgentProcessChatbot(platform, agentSource, factory)` (we use utility mode)
- Blackboard binding system for typed state: `agentProcess.bindProtected()`, `agentProcess.addObject()`
- Message vs. Trigger pattern — `ChatTrigger` for ephemeral actions that don't clutter conversation history

**Goal**: Create comprehensive inventory documenting current usage vs. available capabilities, establishing foundation for evaluating where idioms could improve observability and testability.

## Proposal Seed

### Why This Feature

A living Embabel API inventory serves as:
1. **Reference** for current framework usage (what annotations, patterns, and APIs are in use)
2. **Opportunity catalog** (what's available but unused and when it might be valuable)
3. **Pattern guide** (Embabel-recommended best practices aligned with dice-anchors)
4. **Foundation** for F2, F3, F4 features (inventory informs tool restructuring, DICE integration decisions, and goal modeling evaluation)

### What Changes

- **New artifact**: `docs/dev/embabel-api-inventory.md` documenting Embabel Agent framework usage
- **Inventory sections**:
  - Annotations used with file:line references (e.g., `@EmbabelComponent` in `ChatActions.java:42`)
  - Interfaces and patterns adopted
  - Annotations available but unused (with descriptions of when valuable)
  - Recommended patterns from Embabel documentation
  - Tool organization best practices (CQS principle)

### Success Criteria

- [ ] Inventory document complete and reviewed
- [ ] Current usage catalog with file:line references for all Embabel annotations
- [ ] Available but unused capabilities clearly explained
- [ ] Recommended patterns aligned with Embabel docs
- [ ] Tool restructuring rationale (CQS, read-only vs. full access) documented
- [ ] Document referenced from DEVELOPING.md or README

### Visibility

- **UI**: None (documentation-only)
- **Observability**: Serves as reference for future OTEL instrumentation roadmap

## Design Sketch

### Embabel API Inventory Structure

```
docs/dev/embabel-api-inventory.md
├── Current Usage
│   ├── Annotations (@EmbabelComponent, @Action, @MatryoshkaTools, @LlmTool)
│   ├── Interfaces (Ai fluent API, Chatbot)
│   ├── Patterns (template rendering, tool registration, event listeners)
│   └── File:line references for each
├── Available But Unused
│   ├── @Goal / @AchievesGoal (goal-directed orchestration)
│   ├── @Condition (predicate-based action selection)
│   ├── @State (Blackboard typed state management)
│   ├── Multi-action pipelines
│   └── Condition-based branching
├── Recommended Patterns
│   ├── CQS principle (query tools vs. mutation tools)
│   ├── Tool descriptions best practices
│   ├── Error handling in tools
│   └── Async patterns (when to use @Async)
└── Tool Organization
    └── CQS rationale and how it enables read-only contexts
```

### Key Points to Capture

1. **Current usage**: List all Embabel annotations used in dice-anchors codebase with context
2. **Available capabilities**: Document Embabel features available in 0.3.5-SNAPSHOT but not used
3. **Best practices**: Align with Embabel documentation; note which are adopted and which are deferred with rationale
4. **Tool reorganization**: Explain how CQS principle enables selective registration (read-only simulation vs. full-access chat)

## Embabel API Research Findings

*Source: Embabel MCP server + codebase analysis. See `research/R01-embabel-api-surface.md` for full details.*

### Current Integration Footprint

**52 Embabel imports across 30 Java files**, spanning 10 packages:

| Package | Key Classes Used |
|---------|-----------------|
| `com.embabel.agent.api.annotation` | `@Action`, `@EmbabelComponent`, `@LlmTool`, `@MatryoshkaTools` |
| `com.embabel.agent.api.common` | `ActionContext`, `AiBuilder` |
| `com.embabel.agent.api.channel` | `OutputChannelEvent`, `MessageOutputChannelEvent`, `ProgressOutputChannelEvent` |
| `com.embabel.agent.core` | `AgentPlatform`, `DataDictionary` |
| `com.embabel.agent.rag` | `Chunk`, `NamedEntity`, `NamedEntityDataRepository`, `RetrievableIdentifier` |
| `com.embabel.chat` | `AssistantMessage`, `ChatSession`, `Chatbot`, `Conversation`, `Message`, `UserMessage` |
| `com.embabel.chat.agent` | `AgentProcessChatbot` |
| `com.embabel.common.ai.model` | `DefaultModelSelectionCriteria`, `EmbeddingService`, `LlmOptions`, `ModelProvider` |
| `com.embabel.common.core.types` | `SimilarityResult`, `SimpleSimilaritySearchResult`, `TextSimilaritySearchRequest` |
| `com.embabel.dice.*` | 24 imports — `Proposition`, `PropositionPipeline`, `ChunkHistoryStore`, etc. |

### Annotation Parameters We Don't Use (But Could)

| Annotation | Unused Parameter | What It Enables |
|-----------|-----------------|-----------------|
| `@Action` | `cost` (double) | Planning engine prefers lower-cost action paths; useful for multi-action orchestration |
| `@Action` | `toolGroups` (String[]) | Bind specific `@MatryoshkaTools` groups per action instead of manual `withToolObjects()` |
| `@MatryoshkaTools` | `removeOnInvoke` (boolean, default true) | Progressive disclosure — facade tool replaced by child tools after first invocation |
| `@MatryoshkaTools` | `categoryParameter` (String) | Category-based child tool selection within a group |
| `@MatryoshkaTools` | `childToolUsageNotes` (String) | LLM guidance text for child tool usage |
| `@LlmTool` | `returnDirect` (boolean) | Bypasses additional LLM processing of tool results |
| `@LlmTool` | `category` (String) | Groups tools within @MatryoshkaTools by category |
| `@LlmTool` | (nested) `@LlmTool.Param` | Rich parameter descriptions for LLM (description, required) |

### Concrete Opportunities Identified

1. **`@Action(toolGroups=...)`** — Replace manual `withToolObjects()` array building in `ChatActions.respond()` with declarative tool group binding
2. **`@LlmTool.Param`** — Add parameter descriptions to tool methods (e.g., `queryFacts(@LlmTool.Param(description="Subject to search") String subject)`)
3. **`@MatryoshkaTools(removeOnInvoke=false)`** — Keep anchor tools persistent across turns (current behavior, but explicitly declared)
4. **`@LlmTool(category="query")`** — Group tools by category within a single @MatryoshkaTools (alternative to CQS split)
5. **`@Agent`** — Replace `@EmbabelComponent` on `ChatActions` with `@Agent(name, description)` for richer agent metadata

## Dependencies

- None (foundational feature)
- Informs F2 (tool restructuring), F3 (DICE integration docs), F4 (goal modeling evaluation)

## Acceptance Gates

1. **Documentation completeness**: All sections populated with current codebase data
2. **File:line accuracy**: All code references verified against actual source
3. **Pattern clarity**: Each pattern explained with "when valuable" context
4. **DEVELOPING.md integration**: Inventory linked from project development guide

## Next Steps

Once feature is approved:
1. Create OpenSpec change: `/opsx:new` with slug `embabel-api-inventory`
2. Work through proposal → spec → design → tasks
3. Archive change and update main specs via `/opsx:sync` or `/opsx:archive`

Features F2, F3, F4 can reference this inventory in their proposal/spec sections.
