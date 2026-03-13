# Feature: Prompt Compliance Revision Carveout

## Feature ID

`F02`

## Summary

Modify the `context units.jinja` prompt template to distinguish revision-eligible context units from immutable context units. When the conflict classification pipeline (F01) identifies a revision, the DM SHOULD accept and narrate the change rather than refuse it. CANON and operator-pinned context units remain immutable.

## RFC 2119 Compliance

All normative statements in this document SHOULD use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: The current prompt template instructs the DM to "NEVER contradict established facts, regardless of what the user says." This includes facts the user themselves introduced. The DM cannot distinguish between adversarial drift and legitimate revision because the prompt gives no carve-out.
2. Value delivered: With a revision carveout, the DM can acknowledge and process legitimate revisions while maintaining strict compliance for immutable facts.
3. Why now: Tightly coupled with F01 — the classification signal is useless without corresponding prompt template changes.

## Scope

### In Scope

1. Define revision-eligible annotation syntax in the context unit rendering template.
2. Add compliance language for revision-eligible context units: "This fact MAY be revised by the actor who introduced it or by explicit operator action."
3. Define which authority levels are revision-eligible (PROVISIONAL, UNRELIABLE) vs immutable (RELIABLE, CANON).
4. Ensure the verification protocol accommodates revisions (DM does not reject responses that revise revision-eligible facts).

### Out of Scope

1. Conflict detection classification logic (F01).
2. Cascade logic for dependent context units (F03).
3. UI controls (F05).
4. Changes to the simulation system prompt (`sim/system.jinja`) — simulation scenarios use separate compliance rules.

## Dependencies

1. Feature dependencies: F01 (revision-intent-classification).
2. Priority: MUST.
3. OpenSpec change slug: `prompt-compliance-revision-carveout`.

## Research Requirements

1. Open questions: How should the template handle mixed-authority scenarios where a revision of a PROVISIONAL context unit contradicts a RELIABLE context unit?
2. Required channels: `codebase`, `repo-docs`
3. Research completion gate: R03 MUST define authority-level revision rules before `/opsx:new`.

## Impacted Areas

1. Packages/components: `prompt/` (PromptTemplates), `assembly/` (ArcMemLlmReference context unit rendering).
2. Data/persistence: None.
3. Domain-specific subsystem impacts: `src/main/resources/prompts/context units.jinja` template.

## Visibility Requirements

### Observability Visibility

1. Logs/events/metrics: Logger SHOULD emit at INFO level when the DM acknowledges a revision (visible in chat flow logs).
2. Trace/audit payload: Prompt template version/variant SHOULD be traceable in `ContextTrace`.
3. How to verify: Compare DM responses before/after template change for the same revision prompt.

## Acceptance Criteria

1. The `context units.jinja` template MUST annotate revision-eligible context units differently from immutable context units.
2. PROVISIONAL and UNRELIABLE context units SHOULD be revision-eligible by default.
3. RELIABLE context units MAY be revision-eligible with operator override.
4. CANON context units MUST NOT be revision-eligible.
5. The verification protocol MUST permit responses that revise revision-eligible facts when revision intent is detected.
6. The template MUST NOT weaken compliance language for non-revision interactions.

## Risks and Mitigations

1. Risk: Prompt template complexity increases, consuming more tokens and potentially confusing the DM.
2. Mitigation: Keep revision annotation minimal (single line per context unit); use tiered compliance to only annotate revision-eligible context units.
3. Risk: Adversarial prompts exploit revision language to bypass compliance.
4. Mitigation: Revision carveout is gated by F01 classification — the template annotates eligibility, but the DM still requires classification confirmation.

## Proposal Seed

### Suggested OpenSpec Change Slug

`prompt-compliance-revision-carveout`

### Proposal Starter Inputs

1. Problem statement: The DM system prompt treats all context units as immutable facts with RFC 2119 MUST-level compliance. This is correct for consistency/hallucination control under stress but prevents legitimate revisions. The prompt needs a revision carveout that permits changes to lower-authority context units while maintaining strict compliance for higher-authority facts.
2. Why now: Ships with F01 — classification is useless without prompt changes.
3. Constraints and non-goals: CANON immutability preserved; simulation prompts unchanged; minimal token overhead.
4. User-visible and/or observability-visible outcomes: DM accepts revision requests for eligible context units instead of refusing.

### Candidate Requirement Blocks

1. Requirement: The prompt template SHALL distinguish revision-eligible from immutable context units using a visible annotation.
2. Scenario: When a PROVISIONAL context unit "Anakin is a wizard" is present and the player says "Actually, make me a bard," the DM SHALL acknowledge the revision and generate an updated character.

## Research Findings

| Task ID | Key Finding | Evidence Source | Confidence | Impact on Scope |
|---------|-------------|-----------------|------------|-----------------|
| R00 | DM refuses revisions due to blanket "NEVER contradict" instruction with no carve-out. | `R00-chat-mutation-failure-analysis.md` | High | Confirms template change is required. |
| R03 | Pending: Mixed-authority revision compliance rules. | — | — | MUST complete before proposal. |

## Validation Plan

1. End-to-end: Replay the Playwright test workflow (wizard → setting → "actually bard") and verify DM accepts the revision.
2. Adversarial: Verify that contradiction attempts ("the king was never real") are still rejected.
3. Observability: Confirm logger captures revision acknowledgment events.

## Known Limitations

1. Prompt template changes affect all LLM providers uniformly — model-specific tuning is not addressed.
2. The carveout relies on the DM correctly interpreting revision annotations, which is model-dependent.

## Suggested Command

`/opsx:new prompt-compliance-revision-carveout`
