# Proposal Outline: Working Memory Anchoring Fit for DICE

## Purpose

Provide a concrete, upstream-friendly proposal outline for integrating working-memory anchoring patterns into DICE, based on demo learnings from `dice-anchors`.

This is an outline artifact intended for upstream proposal drafting.

## Problem Statement

DICE currently provides strong proposition extraction and revision primitives. The demo introduces an additional requirement:

1. Stable working memory under long multi-turn and adversarial conversational pressure.
2. Explicit prioritization and lifecycle management for load-bearing facts.
3. Auditable decisions for promotions, conflicts, and demotions.

## Proposed Direction (Option B)

1. Keep `dice-anchors` as a proving ground for tiering and governance policy.
2. Upstream a minimal extension surface into DICE.
3. Avoid forking DICE unless extension points prove insufficient.

## Scope Boundaries

### In scope for potential DICE integration

1. Tier metadata plumbing.
2. Optional lifecycle/policy hook interfaces.
3. Structured mutation auditing contracts.
4. Temporal validity primitives.

### Out of scope for DICE core (app-specific)

1. Authority taxonomy (`PROVISIONAL/UNRELIABLE/RELIABLE/CANON`).
2. Domain-specific thresholds and eviction policy.
3. UI and simulation harness behavior.

## Candidate DICE Extension Points

Grounded by current usage in this repo (`PropositionConfiguration` and `ConversationPropositionExtraction`):

1. `PropositionPipeline` lifecycle hooks.
   - Post-extraction and post-revision hooks for tier candidate tagging.
2. `PropositionRepository` optional tier-aware queries.
   - Preserve existing methods; add optional tier filter support.
3. Incremental analysis context metadata.
   - Carry session/time metadata needed for temporal validity decisions.
4. Revision/conflict extension seam.
   - Allow temporal-aware and evidence-aware conflict classification.

## Suggested Minimal Interface Set

1. `MemoryTierClassifier`
   - Input: proposition + context.
   - Output: tier candidate + confidence + rationale.
2. `MemoryTierPolicy`
   - Input: proposition, tier, current state.
   - Output: transition decision (`promote`, `retain`, `demote`, `archive`, `review`).
3. `MemoryMutationAudit`
   - Event payload for every state mutation.

Default behavior: no-op implementations so current DICE adopters see no behavior changes.

## Data Contract Additions (Optional/Backward-Compatible)

1. `memoryTier`
2. `validFrom`
3. `validTo`
4. `recordedAt`
5. `provenance`

These can be nullable and ignored by default.

## Rollout Plan

1. Phase 0: Demo evidence collection in `dice-anchors`.
2. Phase 1: Upstream proposal review.
3. Phase 2: Introduce extension interfaces as optional features.
4. Phase 3: Validate with one reference implementation (this repo).

## Evaluation Criteria for Upstream Decision

1. Improved drift resistance vs baseline.
2. Lower harmful conflict replacements.
3. Acceptable latency overhead.
4. Clear auditability for memory mutations.
5. No regressions for non-tiering DICE users.

## Risks and Mitigations

1. Risk: Over-generalizing app-specific anchor semantics.
   Mitigation: keep authority/governance policy outside DICE core.
2. Risk: API bloat.
   Mitigation: small optional SPI interfaces with sane defaults.
3. Risk: Temporal complexity.
   Mitigation: start with nullable metadata and conservative semantics.

## Open Questions for Upstream Discussion

1. Should tier metadata live directly on propositions or in sidecar structures?
2. What minimum hook lifecycle is acceptable in `PropositionPipeline`?
3. Is temporal validity a first-class DICE concern or plugin-only concern?
4. What observability schema should be standardized vs app-owned?

## Related Research Docs

1. `docs/research/memory-tiering-dice-integration-research.md`
2. `docs/research/retrieval-quality-control-toolishrag-research.md`
3. `docs/research/conflict-detection-calibration-research.md`
