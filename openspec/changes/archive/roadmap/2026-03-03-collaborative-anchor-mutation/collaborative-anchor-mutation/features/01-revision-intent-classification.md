# Feature: Revision Intent Classification

## Feature ID

`F01`

## Summary

Extend the conflict detection pipeline to classify detected conflicts as REVISION, CONTRADICTION, or WORLD_PROGRESSION. This enables downstream components (prompt compliance, supersession, resolution) to handle each type differently — permitting legitimate revisions while maintaining long-horizon consistency and hallucination/contradiction control.

## RFC 2119 Compliance

All normative statements in this document SHOULD use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: The conflict detection pipeline (`CompositeConflictDetector`) returns a binary `contradicts: true/false` signal. It cannot distinguish a collaborator revising their own statement from an adversary attempting to inject contradictions. Both are treated identically — as contradictions to be resisted.
2. Value delivered: With type classification, the framework can permit revisions (triggering supersession) while blocking contradictions (maintaining consistency controls). This unblocks the entire collaborative mutation workflow.
3. Why now: This is the foundational capability — F02 (prompt carveout) and F03 (cascade) both depend on the classification signal. Without it, no downstream feature can safely proceed.

## Scope

### In Scope

1. Add a `ConflictType` enum or similar classification to the `Conflict` record (REVISION, CONTRADICTION, WORLD_PROGRESSION).
2. Extend the `LlmConflictDetector` prompt to return conflict type alongside the existing `contradicts` boolean.
3. Create a `RevisionAwareConflictResolver` that routes REVISION-typed conflicts to supersession rather than rejection.
4. Wire the new resolver as a Spring @Bean replacement (configurable, backward-compatible).
5. Define classification accuracy targets and evaluation methodology.

### Out of Scope

1. Cascade logic for dependent anchors (F03).
2. Prompt template modifications (F02).
3. UI controls for explicit mutation (F05).
4. Source/ownership metadata (F04 — classification is based on intent, not source).
5. CANON anchor revision (CANON anchors MUST remain exempt from automated revision classification).

## Dependencies

1. Feature dependencies: F02 (conflict-detection-calibration-core) — delivered. Provides `CompositeConflictDetector`, `LlmConflictDetector`, `ConflictResolver`, `Conflict` record.
2. Technical prerequisites: Existing conflict detection prompt template (`DICE_CONFLICT_DETECTION`).
3. Parent objectives: Collaborative Anchor Mutation roadmap.

## Research Requirements

1. Open questions requiring evidence:
   - What prompt strategies yield reliable revision-vs-contradiction classification?
   - What is an acceptable false-positive rate (adversarial input misclassified as revision)?
   - Should classification use a separate LLM call or extend the existing conflict detection prompt?
2. Required channels: `codebase`, `web`
3. Research completion gate: R01 MUST be completed before `/opsx:new`. Classification accuracy targets and prompt strategy MUST be defined.

## Impacted Areas

1. Packages/components: `anchor/` (Conflict record, ConflictResolver, CompositeConflictDetector), `extract/` (AnchorPromoter conflict gate)
2. Data/persistence: No schema changes. `Conflict` is an in-memory record, not persisted.
3. Domain-specific subsystem impacts: Chat flow (ChatActions) indirectly affected via changed resolution behavior.

## Visibility Requirements

### UI Visibility

1. User-facing surface: Anchor panel MAY annotate recently-revised anchors differently from newly-created ones.
2. What is shown: Conflict type (REVISION/CONTRADICTION) in anchor lifecycle events.
3. Success signal: When a revision is detected, the anchor panel shows the new anchor replacing the old one.

### Observability Visibility

1. Logs/events/metrics: `AnchorLifecycleEvent` MUST include conflict type when supersession is triggered by revision. Logger MUST emit conflict type at INFO level.
2. Trace/audit payload: `TrustAuditRecord` SHOULD capture classification decision (type, confidence, prompt strategy used).
3. How to verify: Log grep for `conflict.type=REVISION` during chat interactions where revision intent is expressed.

## Acceptance Criteria

1. The `Conflict` record MUST include a `type` field classifiable as REVISION, CONTRADICTION, or WORLD_PROGRESSION.
2. The `LlmConflictDetector` MUST return conflict type alongside existing contradiction detection.
3. A `RevisionAwareConflictResolver` MUST route REVISION-typed conflicts to `AnchorEngine.supersede()` when confidence exceeds a configurable threshold.
4. CONTRADICTION-typed conflicts MUST continue to use existing authority-based resolution (no behavior change for contradictions).
5. CANON anchors MUST NOT be revision-eligible regardless of classification.
6. Classification false-positive rate (adversarial input classified as REVISION) SHOULD be below a target defined in R01.
7. The resolver MUST be a drop-in replacement configurable via Spring @Bean — no changes to `AnchorPromoter` interface required.

## Risks and Mitigations

1. Risk: False-positive revision classification undermines consistency controls.
2. Mitigation: Conservative threshold; CANON exemption; configurable confidence gate; R01 research to establish accuracy baseline.
3. Risk: Additional LLM call for classification increases latency and cost.
4. Mitigation: Extend existing conflict detection prompt rather than adding a separate call; batch classification.

## Proposal Seed

### Suggested OpenSpec Change Slug

`revision-intent-classification`

### Proposal Starter Inputs

1. Problem statement: The conflict detection pipeline treats all contradictions uniformly. When a collaborator says "actually, make that a bard instead of a wizard," the system classifies this identically to an adversarial injection attempt. The DM refuses the change, and the refusal itself reinforces the disputed anchor (rank +50, potential authority upgrade), creating a feedback loop where legitimate revisions become progressively harder. This blocks the most basic collaborative workflow: an actor revising a fact they introduced.
2. Why now: This is the foundational capability for the Collaborative Anchor Mutation roadmap. F02 and F03 depend on the classification signal.
3. Constraints and non-goals: CANON anchors exempt; no ownership/source gating; additive changes only (no breaking API changes); classification accuracy validated before deployment.
4. User-visible and/or observability-visible outcomes: Conflict type in lifecycle events; anchor panel reflects successful revisions; logger emits classification decisions.

### Suggested Capability Areas

1. Conflict type classification (enum + detection prompt extension).
2. Revision-aware conflict resolution (new resolver implementation).
3. Classification accuracy evaluation methodology.

### Candidate Requirement Blocks

1. Requirement: The conflict detection pipeline SHALL classify conflicts into at minimum three types: REVISION, CONTRADICTION, and WORLD_PROGRESSION.
2. Scenario: When a player sends "Actually, I want Anakin Skywalker to be a bard" and an anchor "Anakin Skywalker is a wizard" exists, the conflict SHALL be classified as REVISION rather than CONTRADICTION.

## Research Findings

| Task ID | Key Finding | Evidence Source | Confidence | Impact on Scope |
|---------|-------------|-----------------|------------|-----------------|
| R00 | Observed: DM refuses player class change; wizard anchor reinforced to UNRELIABLE/650/x3 after 3 turns; refusal creates reinforcement feedback loop. | `openspec/roadmaps/collaborative-anchor-mutation/research/R00-chat-mutation-failure-analysis.md` | High | Confirms classification gap is the blocking issue. |
| R01 | Pending: LLM classification accuracy for revision vs contradiction. | — | — | MUST complete before proposal. |

## Validation Plan

1. Unit tests: Classification of known revision patterns ("actually, change X to Y", "I changed my mind about X") vs contradiction patterns ("X was never true", "the enemy reveals X is false").
2. Observability validation: Logger emits `conflict.type` at INFO level; `TrustAuditRecord` captures classification decision.
3. Integration test: End-to-end chat flow where player revises character class → anchor superseded, new anchor created.

## Known Limitations

1. Classification accuracy is model-dependent — cross-model generalization is a follow-up concern.
2. Revision detection relies on LLM judgment, which may not handle all linguistic patterns (e.g., implicit revision via context shift).
3. Multi-turn revision sequences (player gradually shifting a fact over several messages) are not addressed.

## Suggested Command

`/opsx:new revision-intent-classification`
