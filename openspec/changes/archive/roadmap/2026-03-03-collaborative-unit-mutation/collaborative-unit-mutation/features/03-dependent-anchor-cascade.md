# Feature: Dependent Context Unit Cascade

## Feature ID

`F03`

## Summary

When an context unit is superseded via revision, identify and cascade-invalidate logically dependent context units. Addresses the "dangling proposition" problem where revising a parent fact (e.g., "wizard" → "bard") leaves orphaned dependent facts (e.g., "School of Evocation", "Lightning Bolt") that are no longer consistent.

## RFC 2119 Compliance

All normative statements in this document SHOULD use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: The existing supersession mechanism (F04 bi-temporal-validity-and-supersession) handles single-context unit replacement but has no logic for transitive dependencies. When "Anakin is a wizard" is superseded by "Anakin is a bard," the dependent context units "specializes in School of Evocation" and "signature spell Lightning Bolt" remain active and inconsistent with the new class.
2. Value delivered: Cascade invalidation ensures the context unit set remains internally consistent after revision.
3. Why now: Wave 2 — requires F01 (classification) to trigger revisions, and builds on F04 (supersession plumbing) which is already delivered.

## Scope

### In Scope

1. Define dependency detection strategies (temporal co-creation, semantic relationship, explicit graph edges).
2. Implement cascade invalidation during `ArcMemEngine.supersede()`.
3. Define cascade depth limits (configurable; default 1-hop).
4. Define which context units are cascade-eligible (PROVISIONAL and UNRELIABLE; RELIABLE requires confirmation; CANON exempt).
5. Emit cascade lifecycle events for audit and observability.

### Out of Scope

1. Automatic re-extraction of replacement dependent context units (e.g., generating bard-appropriate spells to replace wizard spells). This is a downstream LLM concern.
2. Undo/rollback of cascade operations.
3. Multi-hop transitive cascades beyond configurable depth.

## Dependencies

1. Feature dependencies: F01 (revision-intent-classification), F04 (bi-temporal-validity-and-supersession, done).
2. Technical prerequisites: `SUPERSEDES` relationship in Neo4j (delivered); `ArcMemEngine.supersede()` method (delivered).
3. Parent objectives: Collaborative Context Unit Mutation roadmap.

## Research Requirements

1. Open questions:
   - Which cascade strategy (temporal co-creation, semantic dependency, explicit DERIVED_FROM edges, subject clustering) best identifies dependent context units without over-invalidation?
   - What is the acceptable over-invalidation rate (independent context units incorrectly cascaded)?
   - Should cascade use LLM evaluation or static heuristics?
2. Required channels: `codebase`, `web`, `repo-docs`
3. Research completion gate: R02 MUST recommend a strategy with tradeoff analysis before `/opsx:new`.

## Impacted Areas

1. Packages/components: `context unit/` (ArcMemEngine, supersession flow), `persistence/` (ContextUnitRepository cascade queries).
2. Data/persistence: MAY require a `DERIVED_FROM` or `CO_CREATED_WITH` relationship in Neo4j if explicit graph edges strategy is chosen.
3. Domain-specific subsystem impacts: Chat flow cleanup after cascade; UI context unit panel refresh.

## Visibility Requirements

### UI Visibility

1. User-facing surface: Context Unit panel SHOULD visually indicate cascaded context units (e.g., grayed out with "cascaded" label before removal).
2. What is shown: Which context units were cascade-invalidated and why.
3. Success signal: After revising "wizard" to "bard", dependent wizard-specific context units disappear from the active list.

### Observability Visibility

1. Logs/events/metrics: `ContextUnitLifecycleEvent.cascadeInvalidated()` MUST be emitted for each cascaded context unit. Logger MUST emit cascade summary at INFO level (count, depth, strategy used).
2. Trace/audit payload: Cascade decision trail — which context units were evaluated, which were invalidated, which were spared and why.
3. How to verify: Log grep for `cascade.invalidated` events; context unit count decreases after revision.

## Acceptance Criteria

1. When an context unit is superseded via revision, the system MUST evaluate all active context units for dependency on the superseded context unit.
2. Dependent context units at PROVISIONAL or UNRELIABLE authority SHOULD be cascade-invalidated (archived with reason CASCADE_INVALIDATION).
3. Dependent context units at RELIABLE authority SHOULD be flagged for review rather than auto-invalidated.
4. CANON context units MUST NOT be cascade-invalidated.
5. Cascade depth MUST be configurable (default 1-hop).
6. Independent context units (same context but no logical dependency) MUST NOT be affected by cascade.
7. Cascade events MUST be published for audit and UI refresh.

## Risks and Mitigations

1. Risk: Over-cascade removes context units that are not truly dependent on the revised context unit.
2. Mitigation: R02 research to evaluate strategies; configurable cascade depth; RELIABLE context units flagged rather than auto-removed.
3. Risk: Cascade detection adds latency to the supersession flow.
4. Mitigation: Static heuristics (temporal co-creation) are fast; LLM evaluation reserved for ambiguous cases.

## Proposal Seed

### Suggested OpenSpec Change Slug

`dependent-unit-cascade`

### Proposal Starter Inputs

1. Problem statement: Superseding an context unit leaves dependent context units orphaned and inconsistent. "Anakin is a wizard" → "Anakin is a bard" should cascade-invalidate "School of Evocation" and "Lightning Bolt" but leave "Anakin is human" and "Sage background" intact. No cascade logic exists today.
2. Why now: Wave 2 — builds on F01 (classification) and F04 (supersession plumbing, delivered).
3. Constraints: CANON immune; configurable depth; additive to ArcMemEngine.
4. Visible outcomes: Cascaded context units removed from active list; lifecycle events emitted.

### Candidate Requirement Blocks

1. Requirement: The supersession flow SHALL identify and cascade-invalidate logically dependent context units when an context unit is superseded via revision.
2. Scenario: Revising "Anakin Skywalker is a wizard" to "Anakin Skywalker is a bard" SHALL cascade-invalidate "School of Evocation" and "Lightning Bolt" but SHALL NOT affect "Anakin Skywalker is human."

## Research Findings

| Task ID | Key Finding | Evidence Source | Confidence | Impact on Scope |
|---------|-------------|-----------------|------------|-----------------|
| R02 | Pending: Cascade strategy comparison. | — | — | MUST complete before proposal. |

## Validation Plan

1. Unit tests: Cascade with known dependency graphs; verify independent context units spared.
2. Integration test: End-to-end revision with cascade in chat flow.
3. Observability: Cascade lifecycle events emitted and inspectable.

## Known Limitations

1. Automatic re-generation of replacement dependent context units (bard-appropriate spells) is out of scope — the LLM will need to re-extract from the revised context.
2. Multi-hop transitive cascades may miss deeply nested dependencies.

## Suggested Command

`/opsx:new dependent-unit-cascade`
