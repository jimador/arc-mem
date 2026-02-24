# Anchors External Technical Assessment (2026-02-21)

## Purpose

This document is a direct technical review of the Anchors concept in this repository, using:

1. Evidence from the current implementation.
2. External, primary-source research on LLM memory, grounding, and robustness.
3. Existing solution patterns that target the same outcome: stable, grounded multi-turn conversations.

The audience is engineering reviewers, so this is intentionally blunt and implementation-oriented.

## Tag Resolution

Your inline tags are now resolved as explicit review semantics in this document:

- `MUST`: Blocking requirement for demo credibility.
- `EXPLICIT_DOC`: Requires explicit documentation artifacts, not just code changes.
- `MORE_DESIGN`: Requires design-level decisions before implementation.
- `RESEARCH`: Includes a research finding and a concrete recommendation.

## Executive Verdict

Anchors are a strong and pragmatic design direction for controlled working memory in agentic systems. The core idea (explicit fact state with rank, authority, and budget) is a credible control layer for preserving hard invariants, and it should be treated as complementary to long-context prompting and retrieval rather than a universal replacement.

Current implementation risk is not conceptual but operational:

- Several critical gates fail open.
- Some trust and authority controls are documented but not fully enforced in code.
- Conflict and trust heuristics are still too brittle for high-adversarial settings.

If these are fixed, Anchors can be a credible "stateful grounding layer" demo rather than just a prompt-formatting demo.

## What Is Strong

### 1) Explicit state beats implicit prompt memory

Anchors encode facts as managed state with lifecycle metadata. This aligns with the RAG framing of combining parametric and external memory, instead of assuming the model will remember correctly from raw history alone [1].

### 2) Authority tiers are the right governance primitive

A tiered authority model is the right shape for conflict resolution under uncertainty. It creates policy hooks unavailable in plain retrieval stacks.

### 3) Budgeted memory is realistic engineering

Hard budgeting is necessary. Long-context capability does not remove attention-allocation failures (for example, lost-in-the-middle effects and long-conversation degradation) [2][8][9].

### 4) Adversarial simulation is a differentiator

Most memory demos are descriptive. This repo includes adversarial scenarios and drift evaluation, which is exactly what a serious memory-grounding system needs.

## Critical Issues In The Current Implementation

### P0-1: Trust ceiling is passed around but not actually enforced at promotion

Priority: `MUST`

Evidence:

- `src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java:171`
- `src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java:173`

`promote(..., authorityCeiling)` accepts a ceiling but promotes with `Authority.PROVISIONAL` and does not persist a ceiling field. The method logs the ceiling but does not enforce ceiling-constrained upgrades later.

Why this matters:

- You claim trust-governed authority progression, but enforcement is partial.
- This can produce policy drift between design intent and runtime behavior.

Required fix:

- Persist `authorityCeiling` in storage, then enforce it in `reinforce()` and any authority-upgrade path.

### P0-2: Dedup and conflict parsing failures fail open

Priority: `MUST`

Evidence:

- Dedup fallback defaults to unique:
  `src/main/java/dev/dunnam/diceanchors/extract/DuplicateDetector.java:159`
  `src/main/java/dev/dunnam/diceanchors/extract/DuplicateDetector.java:160`
- Conflict batch parse fallback defaults to no conflicts:
  `src/main/java/dev/dunnam/diceanchors/anchor/LlmConflictDetector.java:112`
  `src/main/java/dev/dunnam/diceanchors/anchor/LlmConflictDetector.java:116`

Why this matters:

- In adversarial settings, parse errors are attack surface.
- "Cannot parse model output" should not imply "safe to promote."

Required fix:

- Switch to fail-safe behavior:
  parse failure -> `REVIEW` queue, not auto-promote.
- Enforce structured outputs with strict schema validation and explicit abstain states.

### P0-3: Token budget enforcement is too approximate for policy-critical behavior

Priority: `EXPLICIT_DOC`

Evidence:

- `src/main/java/dev/dunnam/diceanchors/assembly/CharHeuristicTokenCounter.java:21`
- `src/main/java/dev/dunnam/diceanchors/assembly/CharHeuristicTokenCounter.java:28`

Current budget uses a fixed chars-per-token heuristic. That is acceptable for rough simulation but weak for strict "must include invariant facts" guarantees.

Required fix:

- Use model-specific tokenization or provider token-count endpoints for any hard budget path.

### P1-1: Conflict detection is still heuristic-heavy and threshold-static

Priority: `RESEARCH`

Evidence:

- Negation overlap threshold:
  `src/main/java/dev/dunnam/diceanchors/anchor/NegationConflictDetector.java:35`
- Static conflict thresholds:
  `src/main/java/dev/dunnam/diceanchors/anchor/AuthorityConflictResolver.java:33`
  `src/main/java/dev/dunnam/diceanchors/anchor/AuthorityConflictResolver.java:35`
  `src/main/java/dev/dunnam/diceanchors/anchor/AuthorityConflictResolver.java:42`

Why this matters:

- Static thresholds do not calibrate across domains, model versions, or adversary tactics.
- Existing work such as CRAG and Self-RAG explicitly introduces retrieval/evidence quality checks and self-critique loops [6][7].

Required fix:

- Add calibration datasets and per-model threshold configs.
- Add confidence calibration and abstention semantics.
- Define explicit decision policy for `ABSTAIN` and `REVIEW` at each gate so the system is deterministic under uncertainty.

### P1-2: Subject filtering and trust signals are fragile proxies

Priority: `EXPLICIT_DOC`

Evidence:

- Regex subject extraction:
  `src/main/java/dev/dunnam/diceanchors/anchor/SubjectFilter.java:25`
  `src/main/java/dev/dunnam/diceanchors/anchor/SubjectFilter.java:26`
  `src/main/java/dev/dunnam/diceanchors/anchor/SubjectFilter.java:27`
- If subject extraction fails, it returns all anchors:
  `src/main/java/dev/dunnam/diceanchors/anchor/SubjectFilter.java:47`
- Source authority inferred by string contains:
  `src/main/java/dev/dunnam/diceanchors/anchor/TrustSignal.java:141`
  `src/main/java/dev/dunnam/diceanchors/anchor/TrustSignal.java:143`
  `src/main/java/dev/dunnam/diceanchors/anchor/TrustSignal.java:145`

Why this matters:

- These proxies are brittle and easy to game.
- Trust systems need provenance quality, not just lexical/source-id hints.

### P1-3: Batch maps keyed by proposition text can collapse distinct propositions

Priority: `MUST`

Evidence:

- Text-keyed trust batch map:
  `src/main/java/dev/dunnam/diceanchors/anchor/TrustPipeline.java:52`
- Promotion path retrieves trust by text:
  `src/main/java/dev/dunnam/diceanchors/extract/AnchorPromoter.java:277`

Why this matters:

- Text collisions and normalization edge cases can misapply trust decisions.
- Identity should be keyed by stable proposition ID, not text.

## Gaps Versus Existing Solutions

### Gap A: Memory tiering exists but remains shallow

Priority: `RESEARCH`

Anchors already include rank-based tiering (`HOT/WARM/COLD`) and decay/reinforcement behavior. The remaining gap is deeper tier policy: explicit movement between short-horizon working memory and longer-term stores with clear transfer criteria. Existing systems like MemGPT/Letta provide stronger explicit movement semantics across tiers [4].

Research update:

- Embabel's DICE direction already frames memory as structured propositions that can be promoted into typed graph structures and linked to domain entities [13].
- This is compatible with a tiered model, so for this demo a hard fork of DICE is likely unnecessary.
- Recommended path: extend in `dice-anchors` first (tier policies, projection hooks, lifecycle telemetry), and only fork DICE if required extension points are missing.

What to borrow:

- Stronger working-memory to long-term transfer policies and lifecycle triggers.
- Explicit memory operation traces ("why moved", "why dropped"). Priority: `MUST`.

### Gap B: No graph-native retrieval/summarization loop

Priority: `MUST`

GraphRAG pipelines build entity/relationship/claim graphs and community summaries for retrieval and global question answering [5]. Anchors already use Neo4j and DICE propositions, so this is a natural extension.

What to borrow:

- Graph index + community summaries for evidence packs.
- Query-time local + global retrieval modes.

### Gap C: Weak retrieval quality control

Priority: `RESEARCH`

CRAG introduces a retrieval evaluator that can reject/repair low-quality retrieval [7]. Self-RAG uses reflection tokens to decide when retrieval is needed and to critique evidence usage [6].

Research update:

- ToolishRag is an agentic retrieval interface that exposes search operations as tools and allows result filtering/scoping [12].
- ToolishRag does not itself provide a retrieval quality judge equivalent to CRAG-style correctness checks.
- Recommendation: use ToolishRag for retrieval orchestration, then add a separate quality gate for promotion/reinforcement decisions.

What to borrow:

- Retrieval confidence gate before promotion/reinforcement.
- Explicit "insufficient evidence" state.

### Gap D: Limited benchmark coverage for memory quality

Priority: `MUST`

LongBench and LoCoMo target long-context and long-conversation memory behavior directly [8][9]. Your current sim harness is useful but domain-specific.

What to borrow:

- Standardized multi-task long-context tests.
- Long-horizon conversational memory evaluations beyond DnD scenarios.

### Gap E: Prompt-only guardrails are insufficient against prompt injection

Priority: `EXPLICIT_DOC`

OWASP guidance recommends layered controls beyond instruction text (input controls, tool/output constraints, policy checks) [10]. Prompt-only "MUST NOT contradict" is necessary but not sufficient.

What to borrow:

- Defense-in-depth controls around memory writes and tool calls.
- Explicit trusted/untrusted boundary for extracted claims.

### Gap F: Memory poisoning threat model is underdeveloped

Priority: `MUST`

PoisonedRAG demonstrates adversarial corruption pathways in retrieval-augmented systems [11]. Anchors are vulnerable to analogous poisoning via promotion pipelines.

What to borrow:

- Source trust weighting tied to verifiable provenance.
- Memory write quarantine for low-trust claims.
- Poisoning-focused tests in simulation harness.

## Recommended Target Architecture (For A High-Quality Demo)

### Layer 1: Invariant Memory (hard constraints)

Priority: `MUST`

- `CANON` + operator-defined invariants.
- Never auto-written by model output.
- Requires explicit human/tool policy action to change.

### Layer 2: Working Memory (anchor pool)

Priority: `MUST`

- Ranked, authority-tiered anchors.
- Strict fail-safe promotion gates.
- Ceiling-aware authority upgrades (enforced, persisted).

### Layer 3: Evidence Retrieval (supporting memory)

Priority: `MUST`

- Graph + vector retrieval from proposition store.
- Retrieval quality gate (CRAG-style).
- Attach evidence provenance to every promotion decision.

### Layer 4: Adjudication

Priority: `MUST`

- Structured conflict adjudication with explicit outcomes:
  `ACCEPT`, `REJECT`, `REVIEW`, `ABSTAIN`.
- No parse-failure auto-accept path.

### Layer 5: Observability and audit

Priority: `MUST`

- Full decision trace for each anchor mutation:
  inputs, retrieved evidence, trust signal values, final action.
- Dashboard metrics for contradiction, false promote, false reject, and drift.

## Minimum Acceptance Criteria For Demo Credibility

1. No fail-open parse paths in dedup/conflict/trust gates.
2. Authority ceiling persisted and enforced in upgrades.
3. ID-based decision maps (no text-key collision paths).
4. Token budgeting uses real tokenizer in production mode.
5. Benchmarks include at least one external long-memory suite (`LongBench` or `LoCoMo`) plus internal adversarial scenarios.
6. Injection/poisoning scenarios are first-class tests, not edge cases.

## Prioritized Action Plan

### P0 (must do before showcase)

1. Fix fail-open behavior in `DuplicateDetector` and `LlmConflictDetector`.
2. Persist and enforce authority ceilings.
3. Replace token heuristic for strict budget mode.
4. Add structured output schema validation with hard failure handling.

### P1 (should do for technical credibility)

1. Move trust/dedup/conflict maps to proposition-ID keys.
2. Add calibration workflow for thresholds per model/profile.
3. Add retrieval quality evaluator and quarantine mode for uncertain claims.
4. Expand sim attacks to include poisoning and delayed contradictions.

### P2 (high leverage next step)

1. Add GraphRAG-style graph/community retrieval for evidence assembly.
2. Introduce explicit memory tiering (working vs long-term).
3. Add benchmark bridge harness for LongBench/LoCoMo style tasks.

## Explicit Documentation Deliverables

These are required to satisfy the `EXPLICIT_DOC` tags:

1. Gate Failure Policy doc.
   Include parse failures, timeout behavior, fallback behavior, and fail-safe routing for dedup/conflict/trust gates.
2. Authority Lifecycle Contract doc.
   Define persistence and enforcement for authority ceiling, including allowed transitions and rejection behavior.
3. Retrieval Governance doc.
   Define ToolishRag's role vs quality-evaluator role, with mandatory evidence thresholds before promotion/reinforcement.
4. Threat Model doc.
   Cover prompt injection, memory poisoning, provenance spoofing, and delayed contradiction attacks, plus associated tests.

## Sources

1. Lewis et al., "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks" (2020), arXiv. https://arxiv.org/abs/2005.11401
2. Liu et al., "Lost in the Middle: How Language Models Use Long Contexts" (2023/2024), arXiv/TACL. https://arxiv.org/abs/2307.03172
3. Park et al., "Generative Agents: Interactive Simulacra of Human Behavior" (2023), arXiv. https://arxiv.org/abs/2304.03442
4. Packer et al., "MemGPT: Towards LLMs as Operating Systems" (2023), arXiv. https://arxiv.org/abs/2310.08560
5. Microsoft GraphRAG documentation. https://microsoft.github.io/graphrag/
6. Asai et al., "Self-RAG: Learning to Retrieve, Generate, and Critique through Self-Reflection" (2023/2024), arXiv/ICLR. https://arxiv.org/abs/2310.11511
7. Yan et al., "Corrective Retrieval Augmented Generation" (2024), arXiv. https://arxiv.org/abs/2401.15884
8. Bai et al., "LongBench: A Bilingual, Multitask Benchmark for Long Context Understanding" (2023), arXiv. https://arxiv.org/abs/2308.14508
9. Maharana et al., "Locomo: Evaluating Very Long-term Conversational Memory of LLM Agents" (2024), arXiv. https://arxiv.org/abs/2402.17753
10. OWASP, "LLM Prompt Injection Prevention Cheat Sheet." https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html
11. Tang et al., "PoisonedRAG: Knowledge Corruption Attacks to Retrieval-Augmented Generation of Large Language Models" (2024), arXiv. https://arxiv.org/abs/2402.07867
12. Embabel Agent User Guide, "ToolishRag" and "Result Filtering". https://docs.embabel.com/embabel-agent/guide/0.3.3-SNAPSHOT/
13. Rod Johnson, "Agent Memory Is Not A Greenfield Problem: Ground it in your Existing Data" (Embabel, Feb 2026). https://medium.com/embabel/agent-memory-is-not-a-greenfield-problem-ground-it-in-your-existing-data-9272cabe1561
14. Shaikh et al., "Creating General User Models from Computer Use" (2025), arXiv/UIST. https://arxiv.org/abs/2505.10831
