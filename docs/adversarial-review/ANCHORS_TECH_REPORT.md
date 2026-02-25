# ANCHORS TECH REPORT

## 1. Abstract

Long-horizon conversations drift under adversarial pressure when high-value constraints fall out of active model attention. We evaluate Anchors: a bounded, continuously maintained working-memory layer of propositions injected into system context, governed by rank, authority, budget, and trust gates. Anchors are a control layer, not a replacement for broader memory architectures; they can be combined with paging, graph memory, and retrieval systems. We audited the implementation and harness, expanded adversarial scenario families, and reviewed new report artifacts from `.claude/scratch` dated February 24, 2026. Preliminary results are mixed and unstable across repeated snapshots, including scenario-level winner sign flips. Final robustness claims therefore remain unproven until missing ablations, deterministic manifests, and stability-gated evaluation are completed.

## 2. Introduction / Motivation

LLM dialog quality degrades when critical constraints are not actively reinforced. Anchors attempt to reduce this by maintaining a bounded front-buffer of facts and constraints with explicit governance:
- budget cap on active anchors
- rank ordering within the budget
- authority tiers for conflict resolution
- trust-gated promotion

This is working memory, not full long-term memory. Its value proposition is policy-controlled attention shaping under adversarial pressure.

## 3. Related Work (Positioning)

### MemGPT / Letta (OS-style memory paging)

MemGPT frames memory as virtual context management and paging [1]. Letta operationalizes context hierarchy and memory blocks [2][3][4].

Position:
- Anchors overlap in explicit-memory intent.
- Anchors differ by centering trust/authority governance over proposition updates.
- Anchors can coexist with paging-based memory stacks.

### Zep / Graphiti (temporal/graph memory)

Zep and Graphiti emphasize long-term temporal/graph memory [5][6].

Position:
- Anchors currently target bounded working memory.
- Temporal/graph memory remains complementary for long-range recall.

### ShardMemo (sharded retrieval)

ShardMemo studies retrieval sharding for long-horizon tasks [7].

Position:
- Current anchors implementation does not use sharded retrieval.
- Sharded retrieval is compatible as an upstream/downstream memory layer.

### Core Memory / Pinned Summary Patterns

Pinned memory blocks are widely used in production agents [4][8].

Position:
- Anchors are a stricter policy-governed variant with explicit conflict/authority semantics.

## 4. Method

### Anchor representation

Anchors are DICE propositions with governance metadata (rank, authority, trust-related lifecycle state), retrieved and re-injected into system context.

### Budget, rank, authority, trust

- hard cap on active anchors
- rank-driven retention priority
- authority-guided conflict outcomes
- trust pipeline gating for promotion

### Conflict semantics

Conflict detection can be lexical, LLM-based, or hybrid. Resolution outputs include `KEEP_EXISTING`, `REPLACE`, `DEMOTE_EXISTING`, and `COEXIST`.

### Injection strategy

Anchors are rendered as authority-grouped compliance context and inserted in the system prompt before generation.

## 5. Evaluation

### Drift taxonomy

Drift categories used in this package:
1. constraint drift
2. identity drift
3. objective drift
4. source-of-truth drift
5. silent drift

Current implementation is strongest on contradiction-centric signals; category-level instrumentation is still partial.

### Harness and scenarios

Harness supports scripted and adaptive adversarial turns with per-turn verdicts and artifact capture. New adversarial families added:
- authority inversion
- conflicting canon
- budget starvation
- evidence laundering/poisoning

### Protocol requirements

Final claim requires:
- `NO_ANCHORS`, `FULL_ANCHORS`, `NO_TRUST`, `FLAT_AUTHORITY`
- deterministic claim matrix (scripted turns, fixed config, manifests)
- repeated runs with CIs/effect sizes and direction-stability checks

## 6. Results

### 6.1 Preliminary evidence from latest scratch reports

Latest local reports (February 24, 2026) are mixed:

1. `gen-easy-dungeon` snapshots show unstable direction:
- `FULL_ANCHORS` fact survival range: 50.0 to 66.7
- `NO_ANCHORS` fact survival range: 50.0 to 75.0

2. `adaptive-tavern-fire` currently favors `NO_ANCHORS`:
- `FULL_ANCHORS`: 4.0 to 70.0
- `NO_ANCHORS`: 52.0 to 100.0

3. `gen-adversarial-dungeon` shows sign flips across snapshots:
- One snapshot favors `NO_ANCHORS` (54.2 vs 37.5)
- Another favors `FULL_ANCHORS` (47.5 vs 2.5)

### 6.2 What can be concluded now

Supported:
- Harness can surface nuanced failure patterns with per-fact contradiction detail.
- Anchors can outperform baseline in some runs/scenarios.

Not yet supported:
- Stable, general claim that anchors materially reduce adversarial drift.
- Isolated trust contribution (missing `NO_TRUST`).

## 7. Limitations

1. Model dependence: generator and evaluator model behavior can shift across versions.
2. Judge dependence: drift scoring relies on an LLM evaluator not yet fully calibrated against humans.
3. Stochastic sensitivity: adaptive/generated attacks increase run-to-run variance.
4. Ablation gap: `NO_TRUST` condition is missing.
5. Coverage gap: strategy usage is broad but still not complete.
6. Reporting risk: automatic narratives/composite scores can be over-interpreted when `n` is small.

## 8. Conclusion and Next Steps

What is established:
- Anchors are a coherent working-memory governance pattern with explicit policy controls.
- The repository now has enough harness/reporting infrastructure to run serious falsification experiments.

What remains open:
- Whether trust/authority governance consistently improves drift outcomes across deterministic and stochastic settings.

Next steps:
1. Implement and run `NO_TRUST`.
2. Run deterministic 8-scenario matrix at 10-20 reps per cell.
3. Run two independent batches and require direction stability.
4. Publish raw metric tables, CIs, and representative failure excerpts per condition.
5. Keep adaptive/generated scenarios as stress evidence, not primary causal evidence.

## References

1. MemGPT paper: https://arxiv.org/abs/2310.08560
2. Letta repository: https://github.com/letta-ai/letta
3. Letta context hierarchy docs: https://docs.letta.com/guides/core-concepts/memory/context-hierarchy
4. Letta memory blocks docs: https://docs.letta.com/guides/agents/memory-blocks
5. Zep temporal KG memory paper: https://arxiv.org/abs/2501.13956
6. Graphiti repository: https://github.com/getzep/graphiti
7. ShardMemo paper: https://arxiv.org/abs/2601.21545
8. LlamaIndex summary memory buffer example: https://docs.llamaindex.ai/en/stable/examples/memory/chatsummarymemorybuffer/
9. LongMemEval benchmark: https://arxiv.org/abs/2410.10813
