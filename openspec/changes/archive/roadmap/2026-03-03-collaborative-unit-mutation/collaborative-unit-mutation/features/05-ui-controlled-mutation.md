# Feature: UI-Controlled Mutation

## Feature ID

`F05`

## Summary

Add Edit/Revise/Delete controls to the Context Units panel in the Chat UI. Clicking "Edit" triggers a controlled supersession flow that bypasses the chat LLM entirely — the user directly modifies the context unit text, and the system handles supersession and cascade without requiring the DM to process the change.

## RFC 2119 Compliance

All normative statements in this document SHOULD use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: Even with revision intent classification (F01), chat-based revision requires the LLM to correctly interpret revision language. A UI control makes revision intent unambiguous — clicking "Edit" is clearly a revision, not a contradiction.
2. Value delivered: Immediate, deterministic context unit mutation without LLM latency or classification risk. Provides a fallback when chat-based revision fails or is unavailable.
3. Why now: Wave 3 (MAY priority) — depends on F01 and F03 to ensure the underlying mutation and cascade logic exists.

## Scope

### In Scope

1. Add "Edit" button to each context unit card in the Context Units panel.
2. Clicking "Edit" opens an inline editor or modal where the user can modify context unit text.
3. On save: invoke `ArcMemEngine.supersede()` with the old context unit and create a new context unit with the modified text.
4. Trigger cascade invalidation (F03) for dependent context units.
5. Optionally inject a system message into the chat summarizing the change (so the DM can adapt narrative).
6. Authority-gated: PROVISIONAL and UNRELIABLE context units editable; RELIABLE requires confirmation; CANON requires CanonizationGate.

### Out of Scope

1. Bulk editing of multiple context units simultaneously.
2. Undo/rollback UI.
3. Editing context units from the simulation view (sim isolation constraints).

## Dependencies

1. Feature dependencies: F01 (revision-intent-classification), F03 (dependent-unit-cascade).
2. Technical prerequisites: Vaadin context unit panel components (existing).
3. Parent objectives: Collaborative Context Unit Mutation roadmap.

## Impacted Areas

1. Packages/components: `chat/` (ChatView context unit panel), `context unit/` (ArcMemEngine supersession flow).
2. Data/persistence: No new schema; uses existing supersession plumbing.
3. Domain-specific subsystem impacts: Chat transcript may include system-injected revision summary.

## Visibility Requirements

### UI Visibility

1. User-facing surface: "Edit" button on each context unit card; inline editor or modal for text modification.
2. What is shown: Current context unit text (editable); save/cancel controls; confirmation for RELIABLE+ context units.
3. Success signal: After editing, old context unit disappears from active list, new context unit appears with updated text.

## Acceptance Criteria

1. Each context unit card in the Context Units panel MUST display an "Edit" control for PROVISIONAL and UNRELIABLE context units.
2. RELIABLE context units SHOULD display an "Edit" control with a confirmation step.
3. CANON context units MUST NOT display an "Edit" control (CANON mutation requires CanonizationGate).
4. Editing MUST trigger `ArcMemEngine.supersede()` and cascade invalidation (F03).
5. The chat transcript SHOULD receive a system message summarizing the change.
6. The new context unit MUST start at PROVISIONAL authority with initial rank (500).

## Risks and Mitigations

1. Risk: Users accidentally edit context units, losing established facts.
2. Mitigation: Confirmation dialog for RELIABLE context units; supersession audit trail preserves history.

## Proposal Seed

### Suggested OpenSpec Change Slug

`ui-controlled-mutation`

### Proposal Starter Inputs

1. Problem statement: Chat-based revision depends on LLM classification accuracy. A direct UI control provides deterministic mutation without LLM risk.
2. Why now: MAY priority — convenience feature after core mutation logic is delivered.
3. Constraints: Authority-gated; CANON exempt; uses existing supersession plumbing.
4. Visible outcomes: Edit button on context unit cards; inline editor; cascade on save.

### Candidate Requirement Blocks

1. Requirement: The Context Units panel SHALL provide an "Edit" control for revision-eligible context units.
2. Scenario: The user clicks "Edit" on "Anakin is a wizard", changes it to "Anakin is a bard", clicks save. The old context unit is superseded, dependent context units are cascade-invalidated, and the DM receives a system message about the change.

## Validation Plan

1. UI test: Click Edit → modify text → save → verify supersession and cascade.
2. Authority gating: Verify CANON context units have no Edit control.
3. Chat integration: Verify system message appears in transcript after edit.

## Known Limitations

1. The DM may reference old facts from conversation history unless the system prompt is refreshed.
2. No bulk editing — each context unit must be edited individually.

## Suggested Command

`/opsx:new ui-controlled-mutation`
