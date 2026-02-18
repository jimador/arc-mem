## 1. Wire Reinforcement into Chat Flow

- [x] 1.1 In `ChatActions.respond()`, after `context.sendAndSave(assistantMessage)`, iterate over the `anchors` list and call `anchorEngine.reinforce(anchor.id())` for each active anchor
- [x] 1.2 Verify reinforcement fires: send 3 messages, check that anchor rank increases and reinforcement count increments

## 2. Promote Button in Propositions Tab

- [x] 2.1 In `ChatView.refreshPropositionsTab()`, add a "Promote" button to each proposition card that calls `anchorEngine.promote(prop.getId(), 500)` then `refreshSidebar()`
- [x] 2.2 Filter the propositions list to exclude nodes whose IDs are in the active anchors set (also fixed: removed `confidence > 0` filter that excluded DICE-extracted propositions with default confidence 0.0)

## 3. Extraction Trigger Tuning

- [x] 3.1 In `application.yml`, change `trigger-interval` from 6 to 2

## 4. Verification

- [x] 4.1 Compile and run tests pass (214 tests, 0 failures)
- [x] 4.2 Manual smoke test: create anchor, send 3 messages, verify rank increases in sidebar
- [ ] 4.3 Manual smoke test: send 2+ messages, verify propositions appear, promote one, verify it moves to Anchors tab (BLOCKED: DICE extraction pipeline not producing propositions — pre-existing issue)
