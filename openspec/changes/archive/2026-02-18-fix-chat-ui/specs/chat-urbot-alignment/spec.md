## MODIFIED Requirements

### Requirement: Chat template renders without errors
The system SHALL render the `dice-anchors.jinja` template using static includes with conditional persona selection, replacing dynamic string concatenation in `{% include %}` tags that Embabel's Jinjava renderer cannot resolve.

#### Scenario: Default DM persona renders
- **GIVEN** the `dice-anchors.chat.persona` property is set to `"dm"`
- **WHEN** a user sends a message in the chat UI
- **THEN** the system renders `personas/dm.jinja` as the persona block and completes the LLM call without template errors

#### Scenario: Assistant persona renders
- **GIVEN** the `dice-anchors.chat.persona` property is set to `"assistant"`
- **WHEN** a user sends a message in the chat UI
- **THEN** the system renders `personas/assistant.jinja` as the persona block and completes the LLM call without template errors

#### Scenario: Unknown persona falls back to DM
- **GIVEN** the `dice-anchors.chat.persona` property is set to an unrecognized value
- **WHEN** a user sends a message in the chat UI
- **THEN** the system renders `personas/dm.jinja` as the default persona

### Requirement: End-to-end chat message flow completes
The system SHALL process user messages through the full pipeline: ChatView → Embabel ChatSession → ChatActions → LLM → response displayed → DICE extraction event published.

#### Scenario: User sends message and receives response
- **WHEN** a user types a message and clicks Send
- **THEN** the chat view displays a thinking indicator, delivers the message to Embabel, displays the LLM response as a bot bubble, and publishes a `ConversationAnalysisRequestEvent` for async extraction
