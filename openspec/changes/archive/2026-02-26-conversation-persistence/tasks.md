## 1. Persistence Layer

- [x] 1.1 Create `ConversationNode` record — Neo4j node with `conversationId`, `title`, `createdAt` properties (labeled `:Conversation`)
- [x] 1.2 Create `ChatMessageNode` record — Neo4j node with `conversationId`, `role`, `text`, `ordinal`, `createdAt` (labeled `:ChatMessage`)
- [x] 1.3 Add conversation repository methods to `AnchorRepository` (or a new `ConversationRepository`): `createConversation()`, `appendMessage()`, `loadMessages(conversationId)`, `findConversation(conversationId)`, `listConversations()`
- [x] 1.4 Verify: compile passes, queries are parameterized Cypher (not string concat)

## 2. ConversationService

- [x] 2.1 Create `ConversationService` — Spring `@Service` wrapping repository with `createConversation()` → returns conversationId, `appendMessage(conversationId, role, text)`, `loadConversation(conversationId)` → ordered transcript, `listConversations()`
- [x] 2.2 Verify: compile passes

## 3. Dynamic contextId in Chat Flow

- [x] 3.1 Replace `DEFAULT_CONTEXT = "chat"` in `ChatActions` with dynamic `conversationId` sourced from the conversation — use a thread-local or pass via Embabel conversation metadata
- [x] 3.2 Update `ChatView.sendMessage()` to call `conversationService.appendMessage()` for both player messages and DM responses
- [x] 3.3 Update `ChatView.getOrCreateSession()` to create a conversation via `ConversationService` and store `conversationId` in `VaadinSession`
- [x] 3.4 Update sidebar refresh to use the active `conversationId` instead of `"chat"`
- [x] 3.5 Verify: compile passes, chat flow works with dynamic contextId

## 4. Conversation Resume UI

- [x] 4.1 Add conversation ID display field (read-only, copyable) to the chat header area
- [x] 4.2 Add "New Conversation" button that creates a fresh conversation, clears messages, resets session
- [x] 4.3 Add "Resume" input field + button: paste a conversationId, load transcript from Neo4j, render messages, continue session
- [x] 4.4 On resume failure (nonexistent ID), show error notification without disrupting current conversation
- [x] 4.5 On `onAttach()`, check `VaadinSession` for active `conversationId` and auto-resume from Neo4j (refresh survival)
- [x] 4.6 Verify: UI compiles, new/resume flow works end-to-end

## 5. Conversation Resume — Embabel Session Hydration

- [x] 5.1 When resuming, create a new `ChatSession` and replay persisted messages into the Embabel `Conversation` object so the LLM has full context
- [x] 5.2 Verify: resumed conversation continues coherently (LLM sees prior turns)

## 6. Tests

- [x] 6.1 Unit tests for `ConversationService`: create → append messages → load returns ordered transcript
- [x] 6.2 Unit test: propositions extracted during a conversation have `contextId = conversationId`
- [x] 6.3 Unit test: resume nonexistent conversation returns empty
- [x] 6.4 Verify: all tests pass (`./mvnw test`)

## 7. Documentation

- [x] 7.1 Update README or add `docs/conversation-persistence.md` explaining: how conversation IDs work, where they're stored, how to resume in the UI
