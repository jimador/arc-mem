# Research: Memory Tiering and DICE Integration Strategy

## Objective

Define a proposal-ready implementation strategy for introducing explicit memory tiering into `dice-anchors` while aligning with Embabel DICE direction and minimizing long-term maintenance risk.

This document targets the `RESEARCH` item from `Gap A` in `docs/anchors-external-technical-assessment-2026-02-21.md`.

## Demo North Star

This repository's purpose is to demonstrate a pattern that can be incorporated into DICE, not to build a parallel long-term forked memory stack.

Target outcome:

1. Deliver a compelling working-memory anchoring demonstration.
2. Validate `Option B` as the end state:
   local implementation in `dice-anchors` plus an upstream-friendly proposal for DICE integration.
3. Produce enough implementation evidence and measurement to justify upstream adoption.

Demo success criteria:

1. The demo clearly shows `extraction -> tiering -> anchor injection -> conflict handling -> lifecycle transitions`.
2. Behavior is explainable with auditable traces (why an item entered/left working memory).
3. The proposed DICE fit is incremental and does not require immediate breaking API changes.

## Parent Objective Dependencies

Do not execute this implementation until these parent objectives are complete:

1. `P0` fail-open gate fixes (`DuplicateDetector`, `LlmConflictDetector`).
2. Authority ceiling persistence + enforcement.
3. Production-grade token counting for strict budget mode.

Rationale: tiering amplifies complexity. If core gate correctness is unresolved, tiering behavior will be untrustworthy and difficult to evaluate.

## Current State in This Repo

Observed architecture:

- Anchors are a single active pool selected by rank/authority and budget.
- Proposition persistence exists in Neo4j via `AnchorRepository`.
- Promotion and lifecycle orchestration happen in `AnchorPromoter` + `AnchorEngine`.
- There is no explicit tier model (working, episodic, semantic/invariant).

Relevant code surfaces:

- `src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java`
- `src/main/java/dev/dunnam/diceanchors/extract/AnchorPromoter.java`
- `src/main/java/dev/dunnam/diceanchors/persistence/AnchorRepository.java`

## External Findings

### 1) Hierarchical memory is a proven pattern

- MemGPT introduces explicit memory hierarchy and context management rather than relying on a flat prompt window.
- This supports the design direction of tiered memory in Anchors.

Source:

- https://arxiv.org/abs/2310.08560

### 2) Embabel direction favors memory grounded in existing domain data

- Embabel documentation defines DICE as typed, domain-integrated context engineering.
- Rod Johnson's 2026 article argues memory should be a projection over existing domain entities, not a disconnected parallel store.

Sources:

- https://docs.embabel.com/embabel-agent/guide/0.1.3/
- https://medium.com/embabel/agent-memory-is-not-a-greenfield-problem-ground-it-in-your-existing-data-9272cabe1561

### 3) Long-horizon memory remains difficult even with long context + RAG

- LoCoMo highlights persistent failures in long-term conversational memory and temporal/causal consistency.

Source:

- https://arxiv.org/abs/2402.17753

### 4) Temporal knowledge graph memory is a strong comparative baseline

- Zep/Graphiti emphasizes temporally-aware memory and cross-session consistency, relevant for narrative evolution and anchor validity windows.

Source:

- https://arxiv.org/abs/2501.13956

## Key Question: Extend Locally, Upstream, or Fork DICE?

### Option A: Local tiering in `dice-anchors` only (no DICE changes)

Pros:

- Fastest path.
- No upstream dependency.
- Lowest coordination overhead.

Cons:

- Risk of duplicated semantics if DICE evolves similar concepts.
- Higher long-term drift from upstream idioms.

Best when:

- Demo timeline is short and upstream collaboration is uncertain.

### Option B: Local implementation + upstream-friendly extension proposal to DICE

Pros:

- Keeps demo velocity while preserving an upgrade path.
- Encourages convergence with DICE architecture.
- Avoids immediate fork burden.

Cons:

- Requires design discipline and API boundary planning.

Best when:

- You need production-quality demo behavior and future maintainability.

### Option C: Hard fork DICE now

Pros:

- Full control.

Cons:

- Highest maintenance cost.
- Most likely to diverge and become hard to merge.

Best when:

- Required extension points are impossible to achieve through adapters/SPI and timeline cannot wait.

### Recommendation

Use **Option B**:

1. Implement tiering in `dice-anchors` first behind explicit adapter boundaries.
2. Produce a small upstream proposal for DICE extension points (not behavior rewrite).
3. Fork only if blockers are proven by spike, not assumed.

This is not just preferred; it matches the stated goal of this repo.

## Proposed Tier Model

Define tiers with explicit contracts:

1. `T0_INVARIANT`
   - Operator-defined invariants + `CANON`.
   - Not writable by automatic extraction/promotion.
2. `T1_WORKING`
   - Active anchor set for prompt injection.
   - Rank/authority/budget managed aggressively.
3. `T2_EPISODIC`
   - Time-scoped propositions/events.
   - Retrieval candidate source, not directly injected by default.
4. `T3_STRUCTURED_DOMAIN`
   - References to domain entities and typed relations.
   - High-trust grounding source for adjudication.

## Tier Transition Rules (Draft)

1. Extraction writes to `T2_EPISODIC` first.
2. Promotion candidate can move `T2 -> T1` only through gates (dedup/conflict/trust).
3. High-confidence + schema match can project to `T3_STRUCTURED_DOMAIN`.
4. Invariants (`T0`) must use explicit human/tool governance path.
5. Expired/obsolete working anchors move `T1 -> T2` (not hard delete), preserving history.

## Data Model Additions for Proposal

Add fields (or equivalent metadata):

- `memoryTier` enum.
- `validFrom` / `validTo` (world-time).
- `recordedAt` (system-time).
- `sourceProvenance` (origin, actor, confidence context).
- `ceilingAuthority` persisted at promotion.

Why now:

- Temporal validity and provenance are prerequisites for robust cross-session consistency and safe conflict adjudication.

## Integration Plan (After Parent Objectives)

### Phase 1: Tier metadata and read paths

1. Add `memoryTier` and temporal metadata to persistence model.
2. Update repository queries so `inject()` only reads `T1_WORKING` (+ `T0_INVARIANT`).
3. Preserve backward compatibility by defaulting current anchors to `T1`.

### Phase 2: Transition pipeline

1. Route extracted propositions into `T2_EPISODIC`.
2. Update `AnchorPromoter` to explicitly transition eligible propositions into `T1`.
3. Add explicit demotion/archive transition paths from `T1` to `T2`.

### Phase 3: Temporal and domain projection

1. Add temporal conflict-aware checks (`validFrom`/`validTo`).
2. Add optional projection hooks from proposition to typed domain links (`T3`).

## Proposed Fit in the DICE Framework

This section is the proposal seed for upstream discussion.

### Fit principle

Keep DICE core responsibilities intact:

1. DICE continues to own proposition extraction/revision/persistence orchestration.
2. Tiering and anchor governance are introduced as optional, composable extension layers.
3. Existing users can ignore tiering with no behavior change.

### Candidate insertion points (based on current repo usage)

1. `PropositionPipeline` post-extraction/post-revision callbacks.
   Use for tier assignment hints and initial provenance tagging before promotion logic.
2. `PropositionRepository` query extensions.
   Add optional tier-aware query helpers (for example: `findByContextAndTier(...)`) while preserving existing contracts.
3. Incremental analysis context and history (`ChunkHistoryStore`, `IncrementalAnalyzer`).
   Carry tier-related metadata needed for temporal and session-aware decisions.
4. Revision/conflict seam (`PropositionReviser` or equivalent conflict hook).
   Allow temporal-aware contradiction classification and deferral to higher-level policy.

### Minimal upstream surface proposal

Propose capability interfaces rather than hard behavior:

1. `MemoryTierClassifier` (optional):
   classify proposition into tier candidates with confidence.
2. `MemoryTierPolicy` (optional):
   apply transition rules and demotion/archival outcomes.
3. `MemoryMutationAudit` (optional):
   emit structured decision events for observability.

These can be no-op by default, preserving backward compatibility.

### What remains app-specific in `dice-anchors`

1. Anchor authority model (`PROVISIONAL/UNRELIABLE/RELIABLE/CANON`).
2. Domain-specific invariants and policy thresholds.
3. Simulation/adversarial evaluation harness.

### What should be generalized for DICE

1. Tier metadata plumbing and lifecycle hooks.
2. Temporal validity support primitives.
3. Auditable mutation event schema.

## Evaluation Plan

Use both internal adversarial scenarios and external-style long-horizon tests.

### Hypotheses

1. Tiering reduces drift under long conversations versus single-pool anchors.
2. Tiering lowers false conflict rates when facts are temporally scoped.
3. Tiering improves recovery from adversarial poisoning attempts by containing low-trust writes in `T2`.

### Metrics

- Drift verdict distribution over turns.
- Conflict precision/recall on labeled contradiction sets.
- Promotion false-positive rate.
- Cross-session recall accuracy.
- Latency impact (`p50`/`p95`) per turn.
- Token usage of injected context.

### Suggested experiment matrix

1. Baseline (current single pool).
2. Tiering without temporal validity.
3. Tiering with temporal validity.
4. Tiering + domain projection checks.

## Open Research Questions to Carry into Proposal

From `docs/investigate.md` and assessment:

1. Dynamic budget by drift pressure vs fixed cap.
2. Promotion timing to avoid premature commitment.
3. Temporal semantics of `CANON` in evolving narratives.
4. Centrality-aware reinforcement (graph position, not just repetition count).

## Proposal-Ready Deliverables

Create these as follow-up artifacts when parent objectives complete:

1. Design RFC: Tier contracts and transition state machine.
2. Persistence migration plan + rollback strategy.
3. API contract for tier-aware query and promotion endpoints.
4. Validation plan with baseline metrics and pass/fail thresholds.
5. Upstream extension proposal draft for DICE SPI points.

Recommended packaging:

1. `Demo walkthrough` (10-15 min): concrete behavior and traceability.
2. `Design memo` (short): proposed extension points and compatibility story.
3. `Validation appendix`: before/after metrics vs baseline anchors.

## Go/No-Go Criteria for Forking DICE

Fork only if all are true:

1. Required extension cannot be achieved with adapters/SPI.
2. Upstream change is rejected or not feasible in timeline.
3. Team accepts ongoing merge and divergence maintenance cost.

If any condition is false, do not fork.

## Sources

1. MemGPT: https://arxiv.org/abs/2310.08560
2. LoCoMo: https://arxiv.org/abs/2402.17753
3. Zep/Graphiti: https://arxiv.org/abs/2501.13956
4. Embabel Agent Guide (DICE glossary): https://docs.embabel.com/embabel-agent/guide/0.1.3/
5. Embabel memory article (Rod Johnson, 2026): https://medium.com/embabel/agent-memory-is-not-a-greenfield-problem-ground-it-in-your-existing-data-9272cabe1561
6. Repo investigation notes: `docs/investigate.md`
