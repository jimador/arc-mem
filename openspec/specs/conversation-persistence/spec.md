## ADDED Requirements

### Requirement: Conversation record lifecycle
The system SHALL persist a conversation record in Neo4j when a new chat conversation is created. The record SHALL include a unique `conversationId` (UUID), a `title` (defaulting to `"Untitled"` if not specified), and a `createdAt` timestamp. The `conversationId` SHALL be used as the `contextId` for all propositions and memory units created during that conversation.

#### Scenario: Create a new conversation
- **WHEN** a user starts a new chat conversation
- **THEN** the system creates a `ConversationNode` in Neo4j with a UUID `conversationId`, `createdAt` timestamp, and default title
- **AND** the `conversationId` is returned to the caller

#### Scenario: ConversationId used as contextId
- **GIVEN** a conversation with `conversationId = "abc-123"`
- **WHEN** DICE extraction runs on a DM response in that conversation
- **THEN** all extracted propositions have `contextId = "abc-123"`
- **AND** any memory units promoted from those propositions inherit the same `contextId`

### Requirement: Message persistence
The system SHALL persist each chat message to Neo4j as it is sent or received. Each mwe essage record SHALL include the `conversationId`, a `role` (PLAYER or DM), the raw `text` content, an `ordinal` (0-based turn position), and a `createdAt` timestamp.

#### Scenario: Player message persisted
- **WHEN** a user sends a message in conversation `"abc-123"`
- **THEN** the system persists a message record with role=PLAYER, the message text, the next ordinal, and a timestamp

#### Scenario: DM response persisted
- **WHEN** the DM responds in conversation `"abc-123"`
- **THEN** the system persists a message record with role=DM, the response text, the next ordinal, and a timestamp

#### Scenario: Message ordering is stable
- **GIVEN** conversation `"abc-123"` has 6 persisted messages
- **WHEN** `loadConversation("abc-123")` is called
- **THEN** messages are returned in ordinal order (0, 1, 2, 3, 4, 5)

### Requirement: Load conversation transcript
The system SHALL provide a `loadConversation(conversationId)` method that returns the full ordered transcript of messages for a given conversation. If no conversation exists with the given ID, the method SHALL return an empty result.

#### Scenario: Load existing conversation
- **GIVEN** conversation `"abc-123"` has 4 messages
- **WHEN** `loadConversation("abc-123")` is called
- **THEN** all 4 messages are returned in ordinal order with their role, text, and timestamps

#### Scenario: Load nonexistent conversation
- **WHEN** `loadConversation("nonexistent-id")` is called
- **THEN** an empty result is returned (no exception thrown)

### Requirement: Conversation listing
The system SHALL provide a method to list recent conversations, ordered by `createdAt` descending. Each entry SHALL include `conversationId`, `title`, `createdAt`, and `messageCount`.

#### Scenario: List conversations
- **GIVEN** 3 conversations exist
- **WHEN** the conversation list is requested
- **THEN** all 3 conversations are returned ordered by most recent first, each with their message count
