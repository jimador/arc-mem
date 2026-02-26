# Conversation Persistence

## Overview

Chat conversations are persisted to Neo4j so they survive browser refreshes and server restarts. Each conversation gets a stable `conversationId` (UUID) that serves as the correlation key for messages, propositions, and anchors.

## How It Works

### Conversation ID = Context ID

The `conversationId` replaces the old hardcoded `contextId = "chat"`. When a conversation is created, its UUID becomes the `contextId` for all propositions and anchors extracted during that conversation. This means:

- Each conversation has its own isolated anchor pool
- Propositions extracted by DICE get `contextId = conversationId` automatically
- All existing `AnchorRepository` queries work unchanged (they filter by `contextId`)

### Storage

Conversations are stored as two Neo4j node types:

```
(:Conversation {conversationId, title, createdAt})
(:ChatMessage {conversationId, role, text, ordinal, createdAt})
```

No relationships between them — just a shared `conversationId` property. Messages are ordered by `ordinal` (0-based).

### Service API

`ConversationService` provides:

| Method | Description |
|--------|-------------|
| `createConversation()` | Creates a new conversation, returns its UUID |
| `appendMessage(id, role, text)` | Appends a message (PLAYER or DM) |
| `loadConversation(id)` | Returns all messages in ordinal order |
| `findConversation(id)` | Returns conversation metadata + message count |
| `listConversations()` | Lists all conversations, most recent first |
| `cloneConversation(sourceId)` | Clones messages + active anchors to a new conversation |

## Conversation Cloning

Conversations can be cloned to create an independent copy with the same messages and active anchors. Two clone paths exist:

### Chat-to-Chat Clone

Click **Clone** in the conversation bar to duplicate the current conversation. The clone gets:
- Title: "Clone of: {original title}"
- All messages reproduced in order
- All active anchors copied with full metadata (rank, authority, reinforcement count, importance, decay, memory tier, etc.)
- New UUIDs and contextId — the clone is fully independent
- Supersession fields (`supersededBy`/`supersedes`) are nulled since they reference IDs from the source context

### Sim-to-Chat Clone

`SimToChatBridge.cloneRunToConversation()` creates a chat conversation from a simulation run. It copies turn messages and promotes anchors with their full state including reinforcement count, DICE importance, and decay rate.

## UI Flow

The chat view (`/chat`) has a conversation bar below the header:

- **Conversation ID field** — read-only, shows the current conversation UUID. Click "Copy ID" to copy to clipboard.
- **New** — starts a fresh conversation, clears messages, generates a new ID.
- **Clone** — duplicates the current conversation (messages + anchors) into a new independent conversation.
- **Resume** — paste a conversation ID and click Resume to load its transcript and continue chatting.

### Refresh Survival

The active `conversationId` is stored in the Vaadin session. On page refresh, the conversation is automatically restored from Neo4j. Closing the browser tab loses the session — the user must paste the ID to resume.

### Embabel Session Hydration

When resuming a conversation, a new Embabel `ChatSession` is created and the persisted messages are replayed into its `Conversation` object. This gives the LLM the full conversation context for generating coherent responses.

Note: Embabel agent blackboard state (tool call history, internal state) is not preserved across resume. Only the message transcript is replayed.

## Limitations

- **No auth/multi-user**: Any user can resume any conversation by ID.
- **No conversation delete**: Conversations are append-only, no cleanup mechanism.
- **No conversation list in UI**: Users must know their ID. A conversation browser is a natural follow-up.
- **No title editing**: Titles default to "Untitled".
- **Old anchors orphaned**: Anchors created under the old `"chat"` contextId won't appear in new conversations. No migration needed for a demo.
