# Research: Conflict Detection Calibration and Adjudication

## Objective

Define a proposal-ready design for upgrading conflict detection from heuristic/static-threshold behavior to a calibrated, auditable, and adversarially robust system.

This document targets the `RESEARCH` item for:

- `P1-1: Conflict detection is still heuristic-heavy and threshold-static`
- Source: `docs/anchors-external-technical-assessment-2026-02-21.md`

## Parent Objective Dependencies

Do not start implementation until these are complete:

1. Fail-open parse behavior fixed in dedup/conflict pipelines.
2. Authority ceiling persistence and enforcement completed.
3. Strict token accounting available for policy-critical prompt paths.

Reason: conflict decisions mutate memory authority and replacement paths. Running this on non-deterministic gates will invalidate evaluation.

## Current State in This Repo

### What exists

- Lexical negation detector with token overlap threshold.
- Semantic detector based on LLM contradiction prompts.
- Composite strategy (`LEXICAL_ONLY`, `SEMANTIC_ONLY`, `LEXICAL_THEN_SEMANTIC`).
- Authority-based conflict resolver with static confidence thresholds.

### Known limitations

- Static thresholds are not model/domain calibrated.
- Heuristic subject filtering can over/under-route candidates.
- Batch parse failures have historically defaulted to permissive outcomes.
- No graph-level contradiction detection for multi-hop inconsistency.

Relevant code:

- `src/main/java/dev/dunnam/diceanchors/anchor/NegationConflictDetector.java`
- `src/main/java/dev/dunnam/diceanchors/anchor/LlmConflictDetector.java`
- `src/main/java/dev/dunnam/diceanchors/anchor/CompositeConflictDetector.java`
- `src/main/java/dev/dunnam/diceanchors/anchor/AuthorityConflictResolver.java`
- `src/main/java/dev/dunnam/diceanchors/anchor/SubjectFilter.java`

## Findings from Existing Repo Investigation

Pulled forward from `docs/investigate.md`:

1. Need to handle indirect conflicts (collective inconsistency, not just pairwise contradiction).
2. `SEMANTIC_SIMILARITY` path exists conceptually but lacks concrete implementation.
3. Reinforcement matching accuracy/latency tradeoff is unresolved.
4. Temporal validity is under-modeled, causing avoidable false conflicts in evolving narratives.

## External Findings

### 1) Retrieval and conflict decisions should be self-correcting, not one-shot

CRAG and Self-RAG both support staged judgment and correction loops instead of direct trust in first-pass retrieval/classification.

Sources:

- https://arxiv.org/abs/2401.15884
- https://arxiv.org/abs/2310.11511

### 2) Long conversations increase inconsistency pressure

Long-context and long-conversation benchmarks show degradation and inconsistency accumulation, raising false-conflict and missed-conflict risk.

Sources:

- https://arxiv.org/abs/2308.14508
- https://arxiv.org/abs/2402.17753

### 3) Temporal graph memory is relevant for contradiction adjudication

Graphiti-style temporal memory indicates contradiction detection should include time-scoping and provenance context.

Source:

- https://arxiv.org/abs/2501.13956

## Design Position

Adopt a multi-stage conflict pipeline with calibrated thresholds and explicit abstention:

1. `FAST_FILTER` (cheap lexical/rule and entity gating)
2. `SEMANTIC_CHECK` (LLM or NLI-style contradiction classification)
3. `CONSISTENCY_CHECK` (optional graph/temporal validation for high-risk cases)
4. `ADJUDICATE` (`REPLACE`, `DEMOTE_EXISTING`, `COEXIST`, `KEEP_EXISTING`, `REVIEW`, `ABSTAIN`)

No stage should silently convert uncertainty into acceptance.

## Where This Overlaps with ToolishRag Work

### Shared components

1. Evidence retrieval context used for semantic conflict checks.
2. Decision trace and audit payload format.
3. `REVIEW` and `ABSTAIN` policy semantics.

### Clear separation

1. ToolishRag research covers retrieval orchestration and evidence quality gating.
2. This research covers contradiction semantics, calibration, and conflict adjudication policy.
3. Retrieval quality gate decides if evidence is usable; conflict detector decides what contradiction relation exists.

## Architecture Options

### Option A: Tune current lexical + LLM threshold stack

Pros:

- Smallest implementation delta.
- Fast to test.

Cons:

- Ceiling on accuracy for indirect/temporal conflicts.
- High dependence on prompt behavior.

### Option B: Add calibrated classifier + abstention policy (recommended)

Pros:

- Better stability than pure prompt heuristics.
- Easier to benchmark and tune.
- Compatible with existing composite detector.

Cons:

- Requires dataset curation and calibration pipeline.

### Option C: Graph-constrained contradiction detection for high-risk conflicts

Pros:

- Best path for indirect and temporal inconsistency.

Cons:

- Highest complexity and likely latency overhead.

Recommended rollout:

- Start with Option B, then add Option C selectively for high-impact contexts.

## Proposed Conflict Contract

Define explicit contracts:

1. `ConflictEvidence`
   - candidate text,
   - existing anchor IDs,
   - supporting retrieved facts,
   - temporal scope,
   - provenance metadata.
2. `ConflictAssessment`
   - relation (`CONTRADICTS`, `CONSISTENT`, `UNSURE`, `TEMPORAL_SUCCESSION`, `PARAPHRASE`),
   - confidence,
   - model/check provenance,
   - explanation payload.
3. `ConflictDecision`
   - final action from policy matrix,
   - reason codes,
   - reviewer-required flag.

## Calibration and Benchmark Plan

### Dataset requirements

Build a labeled set with at least:

1. Direct contradiction pairs.
2. Paraphrase/non-conflict pairs.
3. Temporal progression pairs (not contradiction).
4. Indirect multi-fact inconsistency cases.
5. Adversarial gradual drift examples.

### Calibration process

1. Train/fit thresholds per model profile.
2. Evaluate confidence reliability (calibration curve / expected calibration error).
3. Choose policy thresholds by cost function:
   false replacement cost > false coexist cost.

### Metrics

- Conflict precision, recall, F1.
- False replacement rate.
- False keep-existing rate.
- `REVIEW`/`ABSTAIN` incidence and downstream resolution quality.
- Latency impact (`p50`, `p95`) per conflict evaluation.

## Policy Matrix Draft (Proposal Seed)

Use authority-aware policy:

1. If `assessment = UNSURE`: route to `REVIEW` regardless of authority.
2. If `assessment = TEMPORAL_SUCCESSION`: prefer `COEXIST` + temporal update, not replacement.
3. If contradiction confidence is high and existing authority is low: allow `REPLACE`.
4. If existing authority is high and contradiction evidence is moderate: `DEMOTE_EXISTING` or `REVIEW`, avoid immediate replace.
5. `CANON` mutation requires explicit governance path outside auto conflict resolution.

## Integration Plan (After Parent Objectives)

### Phase 1: Contract and observability

1. Introduce `ConflictAssessment` and `ConflictDecision` data contracts.
2. Add structured audit logging for every conflict decision.

### Phase 2: Calibrated semantic checker

1. Add a calibrated semantic contradiction component with `UNSURE`.
2. Integrate with `CompositeConflictDetector`.
3. Enable shadow mode first, then blocking mode.

### Phase 3: Graph/temporal consistency checks

1. Add optional graph-level contradiction checks for high-risk contexts.
2. Add temporal validity awareness to reduce false positives.

## Open Research Questions

1. Should semantic contradiction use LLM-as-judge, compact NLI classifier, or hybrid?
2. What confidence threshold minimizes harmful replacements under adversarial pressure?
3. How should indirect conflict checks be bounded to control latency?
4. What fraction of conflicts should route to human review in demo mode?

## Proposal-Ready Deliverables

1. Conflict Detection RFC with architecture and contracts.
2. Calibration protocol and dataset specification.
3. Authority-aware policy matrix with rationale.
4. Shadow-mode evaluation report template.
5. Cutover criteria from shadow to blocking decisions.

## Sources

1. CRAG: https://arxiv.org/abs/2401.15884
2. Self-RAG: https://arxiv.org/abs/2310.11511
3. LongBench: https://arxiv.org/abs/2308.14508
4. LoCoMo: https://arxiv.org/abs/2402.17753
5. Zep/Graphiti: https://arxiv.org/abs/2501.13956
6. Repo investigation notes: `docs/investigate.md`
