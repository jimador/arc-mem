## Why

The chat UI (`/chat`) is broken — the Jinja template engine fails at render time with `InterpretException: Error rendering tag (FATAL)` because `dice-anchors.jinja` uses dynamic include syntax (`{% include "personas/" + persona + ".jinja" %}`) that Embabel's Jinjava renderer cannot resolve. Beyond the crash, the chat UI lacks tools for manual testing: there is no way to view, add, or manage anchors within the chat session, and no way to inspect the knowledge state built up during conversation.

## What Changes

- Fix the Jinja template rendering crash by replacing dynamic includes with static includes or conditional blocks
- Add an anchor management sidebar to the chat view — display active anchors, allow manual anchor creation (text, rank, authority), and support rank/authority editing and eviction
- Add a knowledge browser panel to the chat view — show propositions extracted during the session, their promotion status, and trust scores
- Verify end-to-end chat flow: user message → Embabel ChatActions → LLM response → DICE extraction → anchor promotion

## Capabilities

### New Capabilities
- `chat-anchor-management`: Sidebar panel for viewing, creating, editing, and evicting anchors within the chat session context
- `chat-knowledge-browser`: Panel for inspecting propositions, extraction status, and trust scores during a chat session

### Modified Capabilities
- `chat-urbot-alignment`: Fix Jinja template crash, update template structure to use static includes compatible with Embabel's Jinjava renderer

## Impact

- `dev.dunnam.diceanchors.chat.ChatActions` — template rendering call
- `dev.dunnam.diceanchors.chat.ChatView` — new UI panels added to layout
- `src/main/resources/prompts/dice-anchors.jinja` — template restructured
- `src/main/resources/prompts/personas/*.jinja` — may be inlined or restructured
- No API changes, no dependency changes, no data model changes
