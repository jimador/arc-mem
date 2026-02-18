## 1. Fix Template Crash

- [x] 1.1 Replace dynamic include in `dice-anchors.jinja` with conditional persona selection (`{% if persona == "assistant" %}...{% else %}...{% endif %}`) followed by static includes for guardrails, anchor-context, and user elements
- [x] 1.2 Verify template renders correctly with `dm` persona (default)
- [x] 1.3 Verify template renders correctly with `assistant` persona
- [x] 1.4 Verify end-to-end chat flow: send message → receive LLM response → DICE extraction event fires

## 2. Tabbed Sidebar Layout

- [x] 2.1 Refactor ChatView sidebar from flat layout to Vaadin `TabSheet` with three tabs: Anchors, Propositions, Session Info
- [x] 2.2 Move existing anchor display into the Anchors tab
- [x] 2.3 Move existing propositions display into the Propositions tab
- [x] 2.4 Create Session Info tab showing context ID, anchor count, proposition count, and turn count

## 3. Anchor Management Controls

- [x] 3.1 Add "Create Anchor" form to the Anchors tab — text field, rank slider [100-900], authority dropdown (PROVISIONAL/UNRELIABLE/RELIABLE, no CANON), submit button
- [x] 3.2 Wire create form to save a `PropositionNode` via `GraphObjectManager` and promote via `AnchorRepository.promoteToAnchor()` (same pattern as `SimulationService.seedAnchor()`)
- [x] 3.3 Add inline rank slider per anchor — on change, update rank via `AnchorRepository`
- [x] 3.4 Add authority dropdown per anchor — only allow upgrades, disable lower values
- [x] 3.5 Add evict button per anchor — set rank to 0 to remove from active pool
- [x] 3.6 Refresh sidebar after each management operation

## 4. Knowledge Browser

- [x] 4.1 Propositions tab: display non-anchor propositions with text, confidence %, and knowledge status
- [x] 4.2 Session Info tab: display contextId, active anchor count, proposition count, turn counter (increment on each send)

## 5. Verification

- [x] 5.1 Compile and run existing tests pass
- [x] 5.2 Manual smoke test: open `/chat`, send message, verify response displays, verify sidebar populates
- [x] 5.3 Manual smoke test: create anchor via form, verify it appears in sidebar and in next LLM system prompt
- [x] 5.4 Manual smoke test: edit rank and authority, verify changes persist
