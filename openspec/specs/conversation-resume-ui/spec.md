## ADDED Requirements

### Requirement: Display current conversation ID
The chat UI SHALL display the current `conversationId` in a visible, copyable field so the user can record it for later resumption.

#### Scenario: Conversation ID shown on new conversation
- **WHEN** a user starts a new conversation
- **THEN** the conversation ID is displayed in the UI header area
- **AND** the user can copy the ID to clipboard

### Requirement: Start new conversation
The chat UI SHALL provide a "New Conversation" button that creates a fresh conversation, clears the message display, and shows the new conversation ID.

#### Scenario: User starts a new conversation
- **GIVEN** the user is in an active conversation with messages
- **WHEN** the user clicks "New Conversation"
- **THEN** the message area is cleared
- **AND** a new `conversationId` is generated and displayed
- **AND** seed units are initialized for the new conversation context

### Requirement: Resume conversation by ID
The chat UI SHALL provide an input field where the user can paste a `conversationId` and a "Resume" button to load that conversation. On resume, the full transcript SHALL be rendered in the message area and the chat session SHALL continue from where it left off.

#### Scenario: Resume an existing conversation
- **GIVEN** conversation `"abc-123"` has 6 messages (3 player, 3 DM)
- **WHEN** the user pastes `"abc-123"` and clicks Resume
- **THEN** all 6 messages are rendered in order in the chat message area
- **AND** the conversation ID display updates to `"abc-123"`
- **AND** subsequent messages are appended to that conversation

#### Scenario: Resume nonexistent conversation
- **WHEN** the user pastes `"nonexistent-id"` and clicks Resume
- **THEN** the UI shows an error notification: "No conversation found with that ID"
- **AND** the current conversation is not affected

### Requirement: Conversation survives page refresh
After a conversation has been started or resumed, refreshing the browser page SHALL automatically resume the most recent conversation for that session.

#### Scenario: Page refresh preserves conversation
- **GIVEN** the user is in conversation `"abc-123"` with 4 messages
- **WHEN** the user refreshes the browser page
- **THEN** the chat view loads with conversation `"abc-123"` restored
- **AND** all 4 messages are visible
