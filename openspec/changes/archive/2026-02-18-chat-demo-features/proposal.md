## Why

The chat UI (`/chat`) crashes on every message with a Jinja template rendering error (`InterpretException: Error rendering tag (FATAL)`) because Embabel's Jinjava renderer does not support `{% include %}` directives — there is no classpath resource loader configured. Additionally, the chat UI needs a way to manually create and manage knowledge (propositions) for the session, completing the demo loop: chat -> extract propositions -> promote to anchors.

## What Changes

- **Fix template crash**: Flatten `dice-anchors.jinja` by inlining all `{% include %}` content into a single template file, matching the pattern used by `SimulationTurnExecutor` (which works)
- **Add knowledge management tab**: Add a "Knowledge" tab to the sidebar that allows creating propositions directly (text + confidence), viewing all propositions, and promoting them to anchors
- **Consolidate proposition creation**: The existing "Create Anchor" form stays in the Anchors tab; add a separate "Add Knowledge" form in the Knowledge tab that creates propositions (not anchors)

## Capabilities

### New Capabilities
- `chat-knowledge-management`: Ability to manually create, view, and manage propositions (knowledge entries) in the chat UI sidebar

### Modified Capabilities
- `chat-urbot-alignment`: Fix template rendering to use flat templates instead of Jinja includes

## Impact

- `src/main/resources/prompts/dice-anchors.jinja` — flatten by inlining all includes
- `src/main/java/dev/dunnam/diceanchors/chat/ChatView.java` — add Knowledge tab with create/manage UI
- No API changes, no new dependencies
