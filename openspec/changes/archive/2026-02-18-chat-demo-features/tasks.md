## 1. Fix Template Crash

- [x] 1.1 Flatten `dice-anchors.jinja` by inlining all `{% include %}` content (personas, guardrails, anchor-context, user) into a single template file
- [x] 1.2 Delete the now-unused individual template files (`elements/anchor-context.jinja`, `elements/guardrails.jinja`, `elements/user.jinja`, `personas/dm.jinja`, `personas/assistant.jinja`)

## 2. Add Knowledge Tab

- [x] 2.1 Add a `knowledgeTabContent` field and "Knowledge" tab to the sidebar `TabSheet` in `ChatView`
- [x] 2.2 Create `refreshKnowledgeTab()` that lists all non-anchor propositions with text, confidence, and a "Promote" button
- [x] 2.3 Create `buildAddKnowledgeForm()` with text field, confidence slider (0.0-1.0, default 0.8), and "Create" button that persists a `PropositionNode`
- [x] 2.4 Wire `refreshKnowledgeTab()` into `refreshSidebar()`

## 3. Verification

- [x] 3.1 Compile and run tests pass (214 tests, 0 failures)
- [x] 3.2 Manual smoke test: send a chat message and verify no template error, bot responds (verified: tavern scene generated successfully)
- [x] 3.3 Manual smoke test: add knowledge entry, verify it appears in Knowledge tab, promote it, verify it moves to Anchors tab (verified: "Grimjaw" entry created, promoted, appears in Anchors tab)
