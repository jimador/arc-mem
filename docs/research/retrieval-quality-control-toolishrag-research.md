# Research: Retrieval Quality Control with ToolishRag

## Objective

Define a proposal-ready design for retrieval quality control in `dice-anchors`, using ToolishRag for orchestration while adding explicit quality adjudication before memory mutation decisions.

This document targets the `RESEARCH` item from `Gap C` in `docs/anchors-external-technical-assessment-2026-02-21.md`.

## Parent Objective Dependencies

Complete these parent objectives first:

1. Fail-open parse fallback fixes.
2. Authority ceiling persistence/enforcement.
3. Strict token accounting for policy-critical paths.

Reason: quality control logic must operate on deterministic, fail-safe gate behavior.

## Current State in This Repo

Current behavior:

- Retrieval-like behavior exists indirectly via dedup/conflict checks and repository queries.
- Batch parse failures currently fall back to permissive outcomes in key paths.
- There is no explicit retrieval quality evaluator with accept/reject/retrieve-more semantics.

Relevant code:

- `src/main/java/dev/dunnam/diceanchors/extract/DuplicateDetector.java`
- `src/main/java/dev/dunnam/diceanchors/anchor/LlmConflictDetector.java`
- `src/main/java/dev/dunnam/diceanchors/anchor/CompositeConflictDetector.java`

## External Findings

### 1) ToolishRag is an orchestration surface, not a quality judge

Embabel docs describe ToolishRag as a way to expose retrieval as tools, with result filtering and listeners. It improves retrieval integration discipline but does not itself score factual sufficiency/correctness for state mutation decisions.

Source:

- https://docs.embabel.com/embabel-agent/guide/0.3.2-SNAPSHOT/

### 2) Retrieval systems need explicit quality gating

CRAG introduces a corrective evaluator for retrieval quality and fallback correction behavior.

Source:

- https://arxiv.org/abs/2401.15884

### 3) Self-reflective retrieval decisions improve robustness

Self-RAG shows value in explicit retrieve/critique control loops instead of blind retrieval trust.

Source:

- https://arxiv.org/abs/2310.11511

### 4) Prompt-only controls are insufficient for adversarial conditions

OWASP guidance supports defense in depth: policy checks and boundary enforcement are required in addition to prompt instructions.

Source:

- https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html

## Core Design Position

Use ToolishRag as the retrieval execution layer, then add an explicit **quality adjudication layer** that determines whether evidence is sufficient to mutate memory state.

Short version:

- ToolishRag answers "how to retrieve."
- Quality gate answers "is retrieval trustworthy enough to act on."

## Overlap with Conflict Detection Research

Related doc:

- `docs/research/conflict-detection-calibration-research.md`

Shared surfaces:

1. Evidence payload contracts and provenance fields.
2. `REVIEW`/`ABSTAIN` semantics.
3. Decision trace instrumentation.

Non-overlap (ownership boundary):

1. This doc owns retrieval sufficiency and mutation preconditions.
2. Conflict-detection doc owns contradiction semantics and authority-aware conflict policy.

## Architecture Options

### Option A: ToolishRag only + prompt instructions

Pros:

- Fastest initial integration.

Cons:

- Insufficient for adversarial and noisy retrieval conditions.
- No deterministic mutation policy.

### Option B: ToolishRag + deterministic quality evaluator (recommended)

Pros:

- Clear mutation contract.
- Auditable outcomes.
- Compatible with current architecture.

Cons:

- Additional design and calibration work.

### Option C: Full CRAG-style corrective loop with multi-pass retrieval

Pros:

- Highest potential robustness under difficult queries.

Cons:

- Added latency and implementation complexity.

Recommended adoption:

- Start with Option B, then selectively adopt Option C behaviors for high-risk paths.

## Proposed Quality Gate Model

Introduce a retrieval adjudicator with four outcomes:

1. `ACCEPT`
2. `REJECT`
3. `RETRIEVE_MORE`
4. `REVIEW`

### Inputs to quality adjudicator

1. Query intent and mutation context (`dedup`, `conflict`, `promotion`, `reinforcement`).
2. Retrieved evidence set (records + provenance).
3. Model/tool confidence outputs.
4. Policy constraints (authority level, trust profile, invariant impacts).

### Deterministic minimum checks

1. `Coverage`: enough supporting evidence retrieved for claim scope.
2. `Consistency`: retrieved evidence is not internally contradictory.
3. `Provenance`: source class meets minimum trust threshold for mutation type.
4. `Specificity`: evidence refers to the same entities/temporal scope.

If any hard check fails:

- Return `REVIEW` or `REJECT`; never mutate state on weak evidence.

## Integration Points in `dice-anchors`

### 1) Promotion path

- Hook adjudicator before `AnchorEngine.promote(...)`.
- Low-quality evidence routes to `REVIEW`, not silent pass-through.

### 2) Conflict resolution path

- Require quality threshold before `REPLACE` or `DEMOTE_EXISTING`.
- If not met, return `COEXIST` or `REVIEW` depending on policy.

### 3) Dedup path

- If dedup evidence quality is weak, do not auto-mark unique.
- Route to `REVIEW` queue or retry retrieval.

## ToolishRag Utilization Model

Use ToolishRag for:

1. Query decomposition and retrieval tool invocation.
2. Metadata filtering (context, entity, session window).
3. Result listeners for telemetry and decision trace capture.

Do not use ToolishRag alone as adjudicator for:

- promotion,
- demotion,
- archival,
- invariant mutation.

## Data Contracts for Proposal

Define proposal-level contracts:

1. `EvidenceBundle`
   - list of retrieved records,
   - provenance metadata,
   - retrieval diagnostics,
   - temporal scope.
2. `QualityVerdict`
   - `decision`,
   - score breakdown,
   - hard-fail reasons,
   - audit payload.
3. `MutationPolicy`
   - required minimum quality by action type.

## Evaluation Plan

### Hypotheses

1. Quality gating reduces false promotions and unsafe replacements.
2. Quality gating improves adversarial resilience with acceptable latency overhead.
3. ToolishRag + quality gate outperforms ToolishRag-only on contradiction-heavy workloads.

### Metrics

- False promotion rate.
- False replace/demote rate.
- Retrieval precision/recall for contradiction decisions.
- `REVIEW` rate and resolution accuracy.
- Turn latency (`p50`, `p95`).

### Test categories

1. Direct contradiction.
2. Gradual semantic drift.
3. Provenance spoofing attempts.
4. Retrieval noise/partial evidence.
5. Timeout and parse-failure scenarios.

Use internal simulation scenarios plus external long-memory stress samples where applicable.

## Open Research Questions

1. When should `RETRIEVE_MORE` be attempted versus immediate `REVIEW`?
2. How should quality thresholds vary by authority tier and domain profile?
3. Which checks can be deterministic vs LLM-based without sacrificing recall?
4. What is the acceptable latency budget for "high-risk mutation" mode?

## Proposal-Ready Deliverables

After parent objectives:

1. Retrieval Governance spec.
2. Quality adjudicator API and policy matrix.
3. Failure handling contract (timeout, parse failure, low confidence).
4. Evaluation protocol with baseline and pass thresholds.
5. Instrumentation plan for decision trace and post-run analysis.

## Suggested Rollout Sequence

1. Implement adjudicator with read-only shadow mode.
2. Compare decisions against current pipeline on replayed runs.
3. Enable blocking mode only for high-risk mutations.
4. Expand blocking scope after measured stability.

## Sources

1. Embabel ToolishRag guide: https://docs.embabel.com/embabel-agent/guide/0.3.2-SNAPSHOT/
2. CRAG: https://arxiv.org/abs/2401.15884
3. Self-RAG: https://arxiv.org/abs/2310.11511
4. OWASP LLM Prompt Injection Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html
5. Repo investigation notes: `docs/investigate.md`
