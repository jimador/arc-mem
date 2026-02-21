# Research: Temporal Validity and Bi-Temporal Memory Semantics

## Objective

Define a proposal-ready design for temporal validity in working-memory anchoring so the system can distinguish:

1. facts that were true in the world at time `t`,
2. facts recorded by the system at time `t2`,
3. true contradiction vs narrative progression.

This document addresses the selected research track for temporal/bi-temporal semantics.

## Why This Matters

Without temporal semantics, conflict detection will misclassify evolving facts.

Example:

1. "The king is alive" (session 3).
2. "The king was assassinated during the siege" (session 8).

These are not always contradictory. They can both be true with correct time scoping.

## Parent Objective Dependencies

Start implementation only after:

1. fail-open gate fixes,
2. authority ceiling persistence/enforcement,
3. strict token-accounting path.

## Current Gap in This Repo

Current anchor lifecycle is status/rank/authority based, but not world-time aware.

Impacts:

1. False conflicts on state transitions.
2. Weak support for long-horizon narrative continuity.
3. No first-class handling for "superseded but historically valid" anchors.

## External Findings

1. Graphiti/Zep models bi-temporal memory with world validity and record time.
   Source: https://arxiv.org/abs/2501.13956
2. LoCoMo highlights persistent failures in long conversational memory and temporal consistency.
   Source: https://arxiv.org/abs/2402.17753

## Design Options

### Option A: Keep non-temporal anchors

Pros:

1. No schema changes.

Cons:

1. Contradiction logic remains brittle for evolving facts.
2. Hard to explain historical state.

### Option B: Lightweight bi-temporal metadata on propositions/anchors (recommended)

Pros:

1. Minimal migration surface.
2. Large improvement in temporal conflict handling.
3. Compatible with current architecture.

Cons:

1. Requires policy decisions for missing timestamps.

### Option C: Full temporal graph event model

Pros:

1. Strongest temporal reasoning capability.

Cons:

1. Highest complexity and demo risk.

## Recommendation

Use Option B for the demo and proposal.

Introduce a lightweight bi-temporal model now, then evolve to richer temporal graph constraints only after baseline validation.

## Proposed Data Model Additions

Add nullable fields:

1. `validFrom` (world time lower bound).
2. `validTo` (world time upper bound, null means open-ended).
3. `recordedAt` (system ingestion time).
4. `temporalConfidence` (optional confidence in extracted time scope).
5. `supersedesAnchorId` (optional link to prior anchor version).

## Temporal Conflict Policy (Draft)

For candidate `C` vs existing anchor `A`:

1. If time windows do not overlap and claims are semantically opposed:
   classify as `TEMPORAL_SUCCESSION`, not contradiction.
2. If windows overlap and claims are semantically opposed:
   classify as contradiction.
3. If time scope is missing/ambiguous:
   route to `REVIEW` or `ABSTAIN` depending on authority.

## Temporal Handling for `CANON`

Define explicit semantics:

1. `CANON` means "canonically accepted statement under its validity window."
2. A new state can supersede prior canon without mutating history.
3. Auto mutation of `CANON` remains prohibited; supersession requires governed workflow.

## Integration Plan (After Parent Objectives)

### Phase 1: Metadata and read behavior

1. Add temporal fields in persistence.
2. Default legacy anchors to unknown world-time and preserve behavior.
3. Expose temporal fields in inspection/debug UI.

### Phase 2: Temporal-aware conflict checks

1. Add temporal overlap checks before semantic contradiction decisions.
2. Introduce `TEMPORAL_SUCCESSION` relation in conflict assessment.

### Phase 3: Supersession lifecycle

1. Add explicit supersession flow.
2. Keep historical anchors queryable for timeline and audit.

## Evaluation Plan

### Hypotheses

1. Temporal metadata reduces false contradictions in evolving scenarios.
2. Supersession improves continuity without weakening anchor safety.

### Metrics

1. False contradiction rate on temporal progression test set.
2. Supersession correctness.
3. Drift stability over long narrative arcs.
4. Latency overhead per conflict check.

## Proposal-Ready Deliverables

1. Temporal schema RFC.
2. Conflict policy matrix with temporal relations.
3. Migration plan and compatibility strategy.
4. Benchmark plan for temporal contradiction detection.

## Sources

1. Graphiti/Zep: https://arxiv.org/abs/2501.13956
2. LoCoMo: https://arxiv.org/abs/2402.17753
3. Repo notes: `docs/investigate.md`
