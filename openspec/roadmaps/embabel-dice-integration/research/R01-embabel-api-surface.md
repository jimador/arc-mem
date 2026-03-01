# Research: Embabel Agent API Surface (0.3.5-SNAPSHOT)

## Source

Embabel MCP server (class signature lookups, doc searches) + codebase analysis of dice-anchors imports and usage patterns.

## Annotations Inventory

### Currently Used in dice-anchors

| Annotation | Package | Parameters Used | Location |
|-----------|---------|-----------------|----------|
| `@EmbabelComponent` | `com.embabel.agent.api.annotation` | (none) | `ChatActions.java:45` |
| `@Action` | `com.embabel.agent.api.annotation` | `trigger=UserMessage.class, canRerun=true` | `ChatActions.java:68` |
| `@MatryoshkaTools` | `com.embabel.agent.api.annotation` | `name, description` | `AnchorTools.java:31`, `AnchorRetrievalTools.java:29` |
| `@LlmTool` | `com.embabel.agent.api.annotation` | `description` | `AnchorTools.java:38,65,80,102,133`, `AnchorRetrievalTools.java:45` |

### Available but Unused

| Annotation | Package | Parameters | Purpose |
|-----------|---------|------------|---------|
| `@AchievesGoal` | `com.embabel.agent.api.annotation` | `description` (String), `export` (@Export) | Marks action as goal completion point; enables planning engine to know when objective is satisfied |
| `@Agent` | `com.embabel.agent.api.annotation` | `name`, `description`, `beanName` | Declarative agent registration (richer than @EmbabelComponent) |
| `@Export` | sub-annotation of `@AchievesGoal` | `remote` (boolean), `name` (String), `startingInputTypes` (Class[]) | Exposes goal as remote endpoint |
| `@LlmTool.Param` | nested in `@LlmTool` | `description` (String), `required` (boolean, default true) | Annotates method parameters for LLM tool descriptions |

### @Action Full Parameter Set

| Parameter | Type | Default | Purpose |
|-----------|------|---------|---------|
| `trigger` | Class | — | Event type that triggers this action (e.g., `UserMessage.class`) |
| `canRerun` | boolean | false | Whether action can execute multiple times |
| `cost` | double | — | Planning cost (planner prefers lower-cost paths) |
| `toolGroups` | String[] | — | Which `@MatryoshkaTools` groups this action can use |

### @MatryoshkaTools Full Parameter Set

| Parameter | Type | Default | Purpose |
|-----------|------|---------|---------|
| `name` | String | required | Tool group identifier the LLM sees |
| `description` | String | required | Guides LLM on when to invoke the group |
| `removeOnInvoke` | boolean | true | Whether facade is replaced by child tools after invocation |
| `categoryParameter` | String | "category" | Parameter name for category-based child tool selection |
| `childToolUsageNotes` | String | "" | Guidance for LLM on child tool usage |

### @LlmTool Full Parameter Set

| Parameter | Type | Default | Purpose |
|-----------|------|---------|---------|
| `description` | String | "" | Tool description shown to LLM |
| `name` | String | "" | Tool identifier (falls back to method name) |
| `returnDirect` | boolean | false | Whether results bypass additional LLM processing |
| `category` | String | "" | Groups tools within @MatryoshkaTools by category |

## Action Chaining Mechanics

Actions chain automatically based on **type matching**:
- Output of one `@Action` method becomes available input to subsequent actions
- Method parameters are resolved from available types in the execution context
- `cost` parameter influences path selection when multiple routes exist

**Example from Embabel examples (`Stages.java`):**
```
chooseCook(UserInput) → Cook
takeOrder(UserInput) → Order
prepareMeal(Cook, Order, UserInput) → Meal  [@AchievesGoal]
```

## AgentProcessChatbot Modes

**Location**: `com.embabel.chat.agent.AgentProcessChatbot`

### Utility Mode (Current dice-anchors usage)
```java
AgentProcessChatbot.utilityFromPlatform(agentPlatform)
```
- Single action per message, no orchestration
- Actions drive behavior; LLM tools provide tool calling
- Conversation-driven (multi-turn)
- **dice-anchors uses this** in `ChatConfiguration.java:27`

### GOAP Mode (Goal-Oriented Action Planning)
```java
new AgentProcessChatbot(agentPlatform, agentSource, conversationFactory)
```
- Default mode; multi-action orchestration
- Plans action sequences to achieve goals
- Uses `@AchievesGoal` to determine completion
- Selects actions based on type matching and cost

## Blackboard / State Binding

Tools and state passed via **blackboard binding system**:
```kotlin
agentProcess.bindProtected(CONVERSATION_KEY, conversation)  // persists across turns
agentProcess.addObject(userMessage)                          // available for type matching
agentProcess.addObject(chatTrigger)                          // ephemeral trigger
```

### Message vs. Trigger Pattern
- **UserMessage** — Persists in conversation history
- **Trigger** (e.g., `ChatTrigger`) — Executes logic without cluttering history; not stored in conversation

### Asset Tracking
- **Conversation-level**: `Conversation.assets` — persists across messages
- **Message-level**: Assets on `AssistantMessage` — scoped to specific response
- Merged view via `Conversation.assets` property

## Template/Rendering Integration

```java
context.ai()
    .withDefaultLlm()
    .withToolObjects(toolObjects.toArray())
    .rendering("dice-anchors")              // loads classpath:/prompts/dice-anchors.jinja
    .respondWithSystemPrompt(messages, templateVars)
```

- Templates use Jinja2 syntax
- Framework auto-resolves `classpath:/prompts/*.jinja`
- Tool objects registered alongside rendered prompt

## Current dice-anchors Integration Points (52 Embabel imports across 30 files)

### By Package

| Package | Import Count | Key Classes |
|---------|-------------|-------------|
| `com.embabel.agent.api.annotation` | 4 | Action, EmbabelComponent, LlmTool, MatryoshkaTools |
| `com.embabel.agent.api.common` | 2 | ActionContext, AiBuilder |
| `com.embabel.agent.api.channel` | 3 | OutputChannelEvent, MessageOutputChannelEvent, ProgressOutputChannelEvent |
| `com.embabel.agent.core` | 2 | AgentPlatform, DataDictionary |
| `com.embabel.agent.rag` | 3 | Chunk, NamedEntity, NamedEntityDataRepository, RetrievableIdentifier |
| `com.embabel.chat` | 6 | AssistantMessage, ChatSession, Chatbot, Conversation, Message, UserMessage |
| `com.embabel.chat.agent` | 1 | AgentProcessChatbot |
| `com.embabel.common.ai.model` | 4 | DefaultModelSelectionCriteria, EmbeddingService, LlmOptions, ModelProvider |
| `com.embabel.common.core.types` | 3 | SimilarityResult, SimpleSimilaritySearchResult, TextSimilaritySearchRequest |
| `com.embabel.dice.*` | 24 | Proposition, PropositionPipeline, ChunkHistoryStore, etc. |
