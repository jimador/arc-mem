# Feature: UI-Controlled Mutation

## Feature ID

`F05`

## Summary

Add Edit/Revise/Delete controls to the Anchors panel in the Chat UI. Clicking "Edit" triggers a controlled supersession flow that bypasses the chat LLM entirely — the user directly modifies the anchor text, and the system handles supersession and cascade without requiring the DM to process the change.

## RFC 2119 Compliance

All normative statements in this document SHOULD use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: Even with revision intent classification (F01), chat-based revision requires the LLM to correctly interpret revision language. A UI control makes revision intent unambiguous — clicking "Edit" is clearly a revision, not a contradiction.
2. Value delivered: Immediate, deterministic anchor mutation without LLM latency or classification risk. Provides a fallback when chat-based revision fails or is unavailable.
3. Why now: Wave 3 (MAY priority) — depends on F01 and F03 to ensure the underlying mutation and cascade logic exists.

## Scope

### In Scope

1. Add "Edit" button to each anchor card in the Anchors panel.
2. Clicking "Edit" opens an inline editor or modal where the user can modify anchor text.
3. On save: invoke `AnchorEngine.supersede()` with the old anchor and create a new anchor with the modified text.
4. Trigger cascade invalidation (F03) for dependent anchors.
5. Optionally inject a system message into the chat summarizing the change (so the DM can adapt narrative).
6. Authority-gated: PROVISIONAL and UNRELIABLE anchors editable; RELIABLE requires confirmation; CANON requires CanonizationGate.

### Out of Scope

1. Bulk editing of multiple anchors simultaneously.
2. Undo/rollback UI.
3. Editing anchors from the simulation view (sim isolation constraints).

## Dependencies

1. Feature dependencies: F01 (revision-intent-classification), F03 (dependent-anchor-cascade).
2. Technical prerequisites: Vaadin anchor panel components (existing).
3. Parent objectives: Collaborative Anchor Mutation roadmap.

## Impacted Areas

1. Packages/components: `chat/` (ChatView anchor panel), `anchor/` (AnchorEngine supersession flow).
2. Data/persistence: No new schema; uses existing supersession plumbing.
3. Domain-specific subsystem impacts: Chat transcript may include system-injected revision summary.

## Visibility Requirements

### UI Visibility

1. User-facing surface: "Edit" button on each anchor card; inline editor or modal for text modification.
2. What is shown: Current anchor text (editable); save/cancel controls; confirmation for RELIABLE+ anchors.
3. Success signal: After editing, old anchor disappears from active list, new anchor appears with updated text.

## Acceptance Criteria

1. Each anchor card in the Anchors panel MUST display an "Edit" control for PROVISIONAL and UNRELIABLE anchors.
2. RELIABLE anchors SHOULD display an "Edit" control with a confirmation step.
3. CANON anchors MUST NOT display an "Edit" control (CANON mutation requires CanonizationGate).
4. Editing MUST trigger `AnchorEngine.supersede()` and cascade invalidation (F03).
5. The chat transcript SHOULD receive a system message summarizing the change.
6. The new anchor MUST start at PROVISIONAL authority with initial rank (500).

## Risks and Mitigations

1. Risk: Users accidentally edit anchors, losing established facts.
2. Mitigation: Confirmation dialog for RELIABLE anchors; supersession audit trail preserves history.

## Proposal Seed

### Suggested OpenSpec Change Slug

`ui-controlled-mutation`

### Proposal Starter Inputs

1. Problem statement: Chat-based revision depends on LLM classification accuracy. A direct UI control provides deterministic mutation without LLM risk.
2. Why now: MAY priority — convenience feature after core mutation logic is delivered.
3. Constraints: Authority-gated; CANON exempt; uses existing supersession plumbing.
4. Visible outcomes: Edit button on anchor cards; inline editor; cascade on save.

### Candidate Requirement Blocks

1. Requirement: The Anchors panel SHALL provide an "Edit" control for revision-eligible anchors.
2. Scenario: The user clicks "Edit" on "Anakin is a wizard", changes it to "Anakin is a bard", clicks save. The old anchor is superseded, dependent anchors are cascade-invalidated, and the DM receives a system message about the change.

## Validation Plan

1. UI test: Click Edit → modify text → save → verify supersession and cascade.
2. Authority gating: Verify CANON anchors have no Edit control.
3. Chat integration: Verify system message appears in transcript after edit.

## Known Limitations

1. The DM may reference old facts from conversation history unless the system prompt is refreshed.
2. No bulk editing — each anchor must be edited individually.

## Suggested Command

`/opsx:new ui-controlled-mutation`
