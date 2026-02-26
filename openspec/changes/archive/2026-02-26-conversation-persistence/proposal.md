## Why

Chat conversations are ephemeral — they exist only in the Embabel agent's in-memory blackboard and the Vaadin session. A browser refresh or server restart destroys the entire conversation history. For a demo where users explore anchor drift resistance over multiple turns, losing context is disruptive. Persistent conversations let users resume where they left off and correlate propositions/anchors back to the conversation that produced them.

## What Changes

- Persist chat message transcripts (role, text, timestamp, ordering) to Neo4j so they survive restarts.
- Replace the hardcoded `contextId = "chat"` with a per-conversation ID (the `conversationId` becomes the `contextId`).
- Add UI controls to create new conversations, resume existing ones by ID, and see the current conversation ID.
- Provide service methods: `createConversation()`, `appendMessage()`, `loadConversation()`.
- Associate propositions and anchors with the conversation's ID (they already use `contextId` — this change makes it dynamic).

## Capabilities

### New Capabilities
- `conversation-persistence`: Neo4j storage of chat message transcripts with create/append/load lifecycle.
- `conversation-resume-ui`: UI controls for creating new conversations, displaying the current conversation ID, and resuming a prior conversation by pasting its ID.

### Modified Capabilities
- `chat-anchor-management`: Propositions and anchors MUST be scoped to the conversation's ID instead of the hardcoded `"chat"` context. No new spec-level requirements — the existing `contextId` contract is preserved, but the value becomes dynamic.

## Impact

- **Persistence layer**: New `ConversationNode` in Neo4j (follows existing JSON-in-Neo4j pattern from `RunHistoryStore`). New repository methods.
- **ChatView.java**: New conversation ID input/display controls. `getOrCreateSession()` becomes conversation-aware.
- **ChatActions.java**: `DEFAULT_CONTEXT` replaced with dynamic `conversationId` sourced from the active conversation.
- **ChatContextInitializer**: Called with the conversation-specific ID instead of `"chat"`.
- **Existing anchors**: Anchors created under the old `"chat"` contextId will not appear in new conversations. This is acceptable — no migration needed for a demo.
- **Simulation**: Unaffected. Sim runs already use `sim-{uuid}` contextIds.
