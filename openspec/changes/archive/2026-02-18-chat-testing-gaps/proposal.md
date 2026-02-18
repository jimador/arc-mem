## Why

The chat workbench (`/chat`) is missing two functional gaps that make exploratory testing of anchor behavior misleading: reinforcement never fires (rank never changes organically), and extracted propositions can't be promoted to anchors without re-typing them. Additionally, DICE extraction triggers every 6 turns by default, meaning short test sessions produce no propositions at all.

## What Changes

- Wire `AnchorEngine.reinforce()` into `ChatActions.respond()` so anchors referenced during a turn get rank boosts and potential authority upgrades
- Add a "Promote" button to each proposition card in the ChatView Propositions tab, calling `AnchorEngine.promote()` directly
- Filter the Propositions tab to exclude already-promoted nodes (rank > 0)
- Tune `trigger-interval` from 6 to 2 so extraction fires after the second chat turn

## Capabilities

### New Capabilities

- `chat-reinforcement`: Wiring anchor reinforcement into the chat flow so rank and authority evolve during conversation

### Modified Capabilities

- `chat-anchor-management`: Adding promote-from-propositions capability to the existing anchor management UI
- `chat-knowledge-browser`: Filtering propositions tab to exclude promoted anchors

## Impact

- `ChatActions.java` — add reinforcement call after LLM response
- `ChatView.java` — add promote button to propositions tab, fix filter
- `application.yml` — change `trigger-interval` from 6 to 2

### Constitutional Alignment

This change aligns with the project constitution's emphasis on testability and the principle that anchors are enriched propositions with explicit lifecycle management. No specification overrides required.
