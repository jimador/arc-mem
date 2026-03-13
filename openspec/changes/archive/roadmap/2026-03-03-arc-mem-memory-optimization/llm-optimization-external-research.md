# External Research: Sleeping LLM & Google AI STATIC

**Date**: 2026-03-03
**Context**: Evaluating external research for techniques adoptable by context units

---

## 1. Sleeping LLM — Weight-Edited Memory Consolidation

**Source**: [github.com/vbario/sleeping-llm](https://github.com/vbario/sleeping-llm)
**Papers**: 6 papers on Zenodo (DOIs: 10.5281/zenodo.18778760 through 10.5281/zenodo.18779159)
**Zenodo Record 18779159**: Paper 6 — "Per-Fact Graduated Consolidation Resolves the Capacity Ceiling in Weight-Edited Language Models"
**HN Discussion**: [Show HN: Sleeping LLM](https://news.ycombinator.com/item?id=47162473)

### What It Is

A system that gives LLMs persistent conversational memory through a biologically-inspired wake/sleep cycle. During **wake**, facts are injected directly into model weights via MEMIT (Mass-Editing Memory in a Transformer) — no retrieval, no database, no context stuffing. During **sleep**, a maintenance cycle audits degraded memories, refreshes them with null-space constraints, then progressively transfers knowledge from MEMIT into fused LoRA weights.

MEMIT is short-term memory (fast, brittle, capacity-limited). LoRA is long-term memory (slow, stable, high-capacity). Sleep is the transfer between them. Inspired by Complementary Learning Systems theory from neuroscience.

### Architecture

```
WAKE                                    SLEEP (8-step pipeline)

User ←→ Chat                           1. Health Check — PPL baseline
   │                                    2. Curate — extract facts, inject via MEMIT
   ▼                                    3. Audit — test recall of each fact
Fact Extraction                         4. Maintain — refresh degraded edits
   │                                         with null-space constraints
   ▼                                    5. LoRA Consolidation — train LoRA on
MEMIT Injection                              active facts, fuse into weights,
   │  (direct weight edit,                   per-fact gating (advance/retreat)
   │   no constraints,                  6. MEMIT Scale-Down — reduce MEMIT deltas
   │   instant recall)                       as LoRA absorbs (1.0→0.5→0.1→0.0)
   │                                    7. Validate — PPL comparison, rollback
   ▼                                    8. Report — audit/consolidation summary
Weights updated.
MEMIT = short-term memory.              Trigger: /sleep command or automatic
LoRA = long-term memory.                drowsiness signal (degraded count > threshold)
```

### The 6 Papers

| # | Title | DOI | Key Contribution |
|---|-------|-----|------------------|
| 1 | Sleep-Wake Consolidation for Lifelong Conversational Memory in Local LMs | 10.5281/zenodo.18778760 | LoRA sleep-wake on 3B MacBook Air. Narrow learning rate window (~1e-4), spaced repetition effect. |
| 2 | The Alignment Tax on Continual Learning | 10.5281/zenodo.18778762 | RLHF suppresses LoRA-injected knowledge. Inverse scaling: 3B 47%, 8B 37%, 70B 0% recall. |
| 3 | Dual-System Memory Consolidation | 10.5281/zenodo.18778764 | MEMIT+LoRA dual system. Covariance-regularized MEMIT, cross-edit null-space constraints, Woodbury identity. MEMIT achieves 0.83 recall at 60 facts with near-zero PPL impact. |
| 4 | Sleeping LLM: Two-Phase Memory Consolidation | 10.5281/zenodo.18778766 | SWS+REM two-phase sleep. Per-fact staged consolidation. Pathway separation: MEMIT edits raw completion, LoRA edits chat. REM integration reduces SWS-induced PPL increase by 88%. |
| 5 | Sleep-Wake Memory Convergence in Weight-Edited LMs | 10.5281/zenodo.18778768 | MEMIT-only (LoRA removed). Wake capacity threshold, sleep convergence proof, pruning death spiral. |
| 6 | **Per-Fact Graduated Consolidation Resolves the Capacity Ceiling** | **10.5281/zenodo.18779159** | Per-fact (not per-edit) consolidation stages. Four-stage MEMIT dissolution (1.0→0.5→0.1→0.0). 100% advancement rate, 1.00 chat recall at 5-20 facts. Makes MEMIT capacity theoretically unbounded. Cumulative LoRA fusing reduces starting training loss by 79% (2.91→0.62). |

### Key Findings

- **Phase transition**: 8B model sustains 0.92 recall up to 13 facts, then crashes to 0.57 at 14 — a sharp cliff, not gradual decay.
- **Sleep convergence**: 30 facts at 40% initial recall recover to 100% within 4 sleep cycles.
- **Alignment tax**: RLHF actively fights LoRA-injected knowledge (3B: 47%, 8B: 37%, 70B: 0% recall). Solved by using LoRA only during sleep with per-fact gating.
- **Effective lifetime capacity**: Unbounded. Once LoRA absorbs facts (stage 3), MEMIT edits dissolve, freeing capacity.
- **Per-fact consolidation stages**: 0 (MEMIT only, scale 1.0) → 1 (absorbing, 0.5) → 2 (absorbing, 0.1) → 3 (LoRA carries, MEMIT dissolved at 0.0).

### Technical Details

**MEMIT formula**: `W' = W + R K^T (K K^T + λC)^{-1}`
- K = key vectors, R = distributed residual, C = empirical covariance (Woodbury identity)
- Wake injects without constraints (fast but interference accumulates)
- Sleep refreshes with null-space constraints (orthogonality to healthy memories)

**Configuration**:
- Target layers: MLP layers 8-15
- Covariance regularization: λ = 0.1
- Max active edits: 50 (hard cap, triggers pruning)
- LoRA learning rate: 1e-4, 10 iterations per fact
- Scale schedule: [1.0, 0.5, 0.1, 0.0]

**Health Monitor / Sleep Pressure** (3-dimensional):
```
pressure = (0.6 * edit_pressure) + (0.3 * time_pressure) + (0.1 * perplexity_pressure)
```
- Edit pressure: non-linear (exponent 1.5) relative to capacity (50 max)
- Time pressure: linear against max wake window (7200s)
- Perplexity pressure: current vs. baseline ratio
- Thresholds: nap at 0.4, full sleep at 0.8

**Fact Curation** (the "Amygdala"):
- Novelty (0-1): message length, question marks, technical markers
- Importance (0-1): correction markers ("wrong", "actually"), emphasis
- Utility (0-1): procedural knowledge, project-specific content
- Combined score = arithmetic mean; threshold filters low-quality exchanges
- Two-tier extraction: 20+ regex patterns primary, model-based Q&A fallback
- Dedup via (question, answer) tuple keys

**Hallucination Firewall** (3-pass verification):
1. Cleaning — remove extraction artifacts
2. Grounding check — score = grounded claims / total claims (proper nouns, numbers, quoted strings)
3. Model verification — optional for borderline scores (0.3-0.5)
- Rejection below `min_grounding_score` (default 0.5)

**The "Aria Effect"** (Paper 6): A consistently stubborn fact ("Aria Nakamura lives in Portland") requires extra consolidation cycles, likely due to token-level distribution overlap in pretraining data. Demonstrates that individual facts have inherent consolidation difficulty.

**Validation-Gated Operations**: Every sleep cycle can be fully reversed if post-validation fails. Weight snapshots enable complete rollback. PPL increase must stay within 15%.

**Codebase structure** (Python, ~1900 lines for MEMIT engine):
- `src/memory/memit.py` — MEMIT engine + EditLedger (delta persistence + format migration)
- `src/memory/health.py` — Sleep pressure calculation
- `src/memory/identity.py` — Core identity reinforcement (immune to training drift)
- `src/sleep/full_sleep.py` — 8-step maintenance + consolidation
- `src/sleep/curator.py` — Fact scoring (novelty, importance, utility)
- `src/sleep/validator.py` — Pre/post benchmark + drift detection + rollback
- `src/sleep/firewall.py` — Hallucination filter for extracted facts
- `src/sleep/trainer.py` — LoRA training orchestrator (train + fuse)
- `src/wake/extractor.py` — Fact extraction from conversation
- `src/wake/context.py` — Context window management with compaction
- `src/concurrency/model_lock.py` — Read/write lock for concurrent chat + sleep

---

## 2. Google AI STATIC — Sparse Matrix Constrained Decoding

**Paper**: [Vectorizing the Trie: Efficient Constrained Decoding for LLM-based Generative Retrieval on Accelerators](https://arxiv.org/abs/2602.22647) (Su et al., Feb 2026)
**Code**: [github.com/youtube/static-constraint-decoding](https://github.com/youtube/static-constraint-decoding)
**Coverage**: [MarkTechPost](https://www.marktechpost.com/2026/03/01/google-ai-introduces-static-a-sparse-matrix-framework-delivering-948x-faster-constrained-decoding-for-llm-based-generative-retrieval/)

### What It Is

STATIC (Sparse Transition Matrix-Accelerated Trie Index for Constrained Decoding) flattens a prefix tree (trie) into a static Compressed Sparse Row (CSR) matrix, transforming irregular tree traversals into fully vectorized sparse matrix operations. This enables constrained decoding on TPUs/GPUs at near-constant time regardless of constraint set size.

The problem it solves: in generative retrieval (LLM generates item IDs autoregressively instead of embedding-based nearest-neighbor search), business logic constraints (freshness, inventory, content policy) require restricting which tokens are valid at each decoding step. Traditional trie-walking causes pointer-chasing, non-contiguous memory access, and host-device round-trips that kill accelerator throughput.

### The Problem

In Generative Retrieval (GR) for recommendation systems, LLMs replace embedding-based nearest-neighbor search. Items (e.g., YouTube videos) are represented as Semantic IDs (SIDs) — discrete token sequences where semantically similar items share prefixes. Without intervention, the LLM will generate SIDs for items that are stale, out-of-stock, or restricted. Business logic constraints must be enforced *during* decoding, not after. Traditional trie-walking on CPUs causes pointer-chasing, non-contiguous memory access, and host-device round-trips that kill accelerator throughput (31.3ms per step on CPU vs. 0.033ms with STATIC).

### Architecture

**Hybrid index** with two phases:
1. **Dense layers** (first L steps): O(1) lookup tables for initial prefix tree layers where branching is uniform
2. **Sparse layers** (remaining steps): CSR matrix with Vectorized Node Transition Kernel (VNTK) for high-cardinality deeper layers

**Offline indexing** (`build_static_index`):
- Processes valid token sequence sets (millions of entries)
- Constructs hybrid dense/sparse index
- Generates: `start_mask`, `dense_mask`, `dense_states`, `packed_csr`, `csr_indptr`

**Online masking** (`sparse_transition_jax`/`_torch`):
- O(1) lookups for initial `dense_lookup_layers` steps
- Vectorized CSR burst-reads for deeper steps
- Fixed-size speculative slices maintain static computation graph
- Stacked CSR interleaves column indices and data into (N_edges, 2) tensor for coalesced reads

**Why fixed-size slices matter**: Rather than variable-length reads based on actual child count, VNTK reads a fixed-size slice corresponding to the maximum branch factor at that level. This eliminates data-dependent branching, enabling full XLA compilation on TPUs. The computation graph remains entirely static.

**Complexity**: O(1) I/O w.r.t. total constraint count; O(log K) w.r.t. branching factor K. Latency remains near-constant whether constraining to 1,000 items or 20,000,000 items.

### Performance

| Metric | Result |
|--------|--------|
| Per-step latency | 0.033ms |
| Speedup vs CPU trie | **948x** |
| Speedup vs HW binary search | 47–1033x |
| Latency scaling with |V| | Near-constant |
| Memory (20M items) | ~1.5 GB HBM (≤75% utilized) |
| Memory rule of thumb | ~90 MB per 1M constraints |
| Model used | 3B dense Gemini-like, TPU v6e |

### YouTube Production Results

Deployed for "last 7 days freshness" constraint on video recommendations (20M fresh items):
- +5.1% increase in 7-day fresh video views
- +2.9% increase in 3-day fresh video views
- +0.15% increase in CTR
- 100% constraint compliance
- 0.25% of total inference time

### Cold-Start Capability

Enables generative retrieval models to recommend cold-start items by constraining to only fresh/new items. Recall@1 goes from 0.00% (unconstrained model hasn't learned new items) to non-trivial levels.

---

## 3. Applicability to context units

### 3.1 Sleeping LLM → Context Unit Memory Management

**HIGH RELEVANCE** — The sleeping-llm architecture maps remarkably well to context units' context unit lifecycle.

| Sleeping LLM Concept | context units Equivalent | Adaptation Opportunity |
|---|---|---|
| MEMIT short-term memory | PROVISIONAL authority context units | Both represent fast, brittle initial storage |
| LoRA long-term memory | CANON authority context units | Both represent consolidated, stable knowledge |
| Per-fact consolidation stages (0→3) | Authority levels (PROVISIONAL→CANON) | context units already has this pattern |
| Sleep audit (test recall) | Decay policy + reinforcement checks | Both detect degraded knowledge |
| Null-space constraints (orthogonality) | Conflict detection | Both prevent new knowledge from destroying existing knowledge |
| Hallucination firewall | Trust pipeline + conflict resolution | Both gate incoming knowledge quality |
| Drowsiness signal (degraded count > threshold) | Budget enforcement (evict lowest-ranked) | Both manage capacity limits |
| MEMIT dissolve schedule | Authority demotion + eviction | Both remove knowledge that has been superseded |
| PPL drift detection | Compaction validator | Both verify model integrity after changes |

**Concrete adoptable patterns**:

#### A. Sleep-Cycle Maintenance for Context Units

context units currently has no periodic "maintenance sweep" — decay and reinforcement happen reactively per-turn. Sleeping LLM's 8-step sleep cycle suggests a **periodic context unit health audit**:

1. **Audit**: Check recall/relevance of all active context units against recent conversation
2. **Refresh**: Re-rank context units whose relevance has drifted
3. **Consolidate**: Promote consistently-reinforced RELIABLE context units toward CANON candidacy
4. **Prune**: Evict context units that fail the audit (not just budget overflow)
5. **Validate**: Run compaction validator to ensure protected facts survived

This could run every N turns or when context unit count exceeds a threshold ("drowsiness signal" analog).

#### B. Fact Scoring (Novelty + Importance)

Sleeping LLM's `curator.py` scores facts by novelty and importance before injection. context units' `UnitPromoter` could adopt a similar two-axis scoring:
- **Novelty**: How much new information does this proposition add? (vs. duplicate detection which is binary)
- **Importance**: How central is this to the current conversation context? (complementing `RelevanceScorer`)

#### C. Capacity Phase Transition Awareness

The sharp phase transition at 13-14 facts (0.92 → 0.57 recall) is a cautionary finding for context units' budget of 20 active context units. This suggests that **unit budget should consider not just count but interference density** — 20 unrelated facts may be fine, but 20 closely-related facts in the same domain could cause similar interference in prompt context.

Possible implementation: weight budget by context unit domain overlap, not just count.

#### D. Staged Consolidation Scale-Down

The MEMIT scale-down schedule (1.0 → 0.5 → 0.1 → 0.0) as LoRA absorbs knowledge suggests a parallel for context unit context injection: as an context unit becomes more firmly established (higher authority), it could receive **less prompt space** rather than more, since the LLM should already "know" it. Currently context units gives CANON context units the most protection — but perhaps their prompt footprint could shrink while maintaining their protective status.

#### E. Hallucination Firewall

Sleeping LLM's `firewall.py` filters hallucinated facts before injection into weights. context units has `TrustPipeline` which serves a similar function, but could adopt the specific pattern of **pre-injection validation against known facts** — checking that extracted propositions don't contradict established context units *before* they enter the promotion pipeline (currently conflict detection happens during promotion, not at extraction time).

### 3.2 Google AI STATIC → Prompt Assembly Optimization

**MODERATE RELEVANCE** — STATIC solves a different problem (constrained token-level decoding for retrieval) but its data structure innovations are transferable.

| STATIC Concept | context units Opportunity |
|---|---|
| CSR sparse matrix for constraint lookup | Context Unit lookup index for conflict detection |
| Hybrid dense/sparse index | Hot/cold context unit tiers with different access patterns |
| Precomputed offline index | Pre-indexed context unit relationships for fast conflict checks |
| O(1) constraint masking | Fast context unit filtering without N×N comparisons |
| Vocabulary management at scale (20M items) | Large context unit sets with efficient retrieval |

**Concrete adoptable patterns**:

#### F. Precomputed Conflict Index

STATIC's key insight is precomputing the constraint graph offline so online enforcement is O(1). context units currently checks conflicts per-proposition at promotion time (O(N) per candidate × context unit count). A precomputed **conflict adjacency matrix** — built when context units change and queried at promotion time — could eliminate redundant LLM calls:

1. When an context unit is created/modified, compute its conflict relationships with all other context units
2. Store as a sparse adjacency structure (CSR or similar)
3. At promotion time, look up precomputed conflicts in O(1) instead of calling the LLM

This is especially valuable because conflict detection is the most LLM-call-heavy operation in the pipeline.

#### G. Hybrid Hot/Cold Context Unit Access

STATIC's hybrid dense/sparse approach maps to context units' `MemoryTier` system:
- **HOT context units** (frequently referenced): Keep in a dense, O(1) lookup structure
- **WARM context units**: Standard access
- **COLD context units**: Sparse/compressed representation, loaded on demand

Currently all context units are loaded uniformly. A tiered access pattern could reduce prompt assembly latency.

#### H. Vectorized Batch Operations

STATIC's VNTK (Vectorized Node Transition Kernel) processes all beams in parallel with fixed-size speculative slices. context units' batch conflict detection already does something similar, but the **fixed-size batch** pattern (process exactly N candidates per batch, pad if needed) could improve throughput predictability compared to the current variable-size batching.

#### I. Stacked Data Layout for Coalesced Access

STATIC interleaves related data (column indices + next-state pointers) into a single tensor for coalesced memory reads. context units' `PropositionView` could adopt a similar pattern — pre-computing a "conflict-ready" view that bundles context unit text, authority, rank, and tier into a single structure optimized for the conflict detection prompt template, rather than assembling these from separate fields at prompt render time.

### 3.3 Combined Insights

#### J. Constraint-Aware Decoding for DM Responses

The most direct STATIC application: use constrained decoding to ensure DM responses respect context unit constraints. Currently context units injects context units into the system prompt and hopes the LLM complies. With constrained decoding, the model could be *prevented* from generating tokens that contradict established CANON context units. This would require:
1. Translating context unit propositions into token-level constraints
2. Building a constraint index (STATIC-style)
3. Applying during decoding

This is architecturally ambitious but would provide **guaranteed** factual consistency rather than probabilistic compliance. It would eliminate the need for post-hoc drift evaluation on CANON facts entirely.

#### K. Memory Pressure as Budget Signal

Both systems use degradation signals to trigger maintenance:
- Sleeping LLM: degraded-fact count > threshold → sleep
- STATIC: constraint set changes → offline re-indexing

context units could adopt a **memory pressure metric** combining:
- Context Unit count vs. budget
- Recent conflict detection rate
- Decay-triggered demotions
- Compaction frequency

When pressure exceeds threshold, trigger a maintenance sweep (pattern A above).

---

## 4. Prioritized Recommendations

### Quick Wins (Low effort, high impact)

| # | Recommendation | Source | Effort |
|---|---------------|--------|--------|
| E | Pre-injection conflict check at extraction time | Sleeping LLM firewall | Low |
| K | Memory pressure metric for proactive maintenance | Both | Low |
| H | Fixed-size batch padding for conflict detection | STATIC VNTK | Low |

### Medium-Term (Moderate effort, high impact)

| # | Recommendation | Source | Effort |
|---|---------------|--------|--------|
| A | Periodic context unit health audit cycle | Sleeping LLM sleep cycle | Medium |
| F | Precomputed conflict adjacency index | STATIC offline indexing | Medium |
| B | Novelty + importance scoring for propositions | Sleeping LLM curator | Medium |
| D | Inverse prompt footprint for established context units | Sleeping LLM scale-down | Medium |

### Research / Long-Term (High effort, potentially transformative)

| # | Recommendation | Source | Effort |
|---|---------------|--------|--------|
| J | Constraint-aware decoding for context unit compliance | STATIC + context units | High |
| C | Interference-density-aware budget enforcement | Sleeping LLM capacity findings | High |
| G | Tiered context unit storage by memory tier | STATIC hybrid index | Medium-High |

---

## 5. Key Takeaways

1. **Sleeping LLM validates the context unit authority ladder.** The MEMIT→LoRA consolidation stages (0→3) are isomorphic to PROVISIONAL→CANON. context units' design is already on the right track architecturally.

2. **Proactive maintenance beats reactive decay.** Sleeping LLM's explicit sleep cycle (audit → refresh → consolidate → prune → validate) is more robust than context units' current per-turn reactive approach. The biggest design gap to close.

3. **Capacity is not just count — interference matters.** The 13-fact phase transition means context units should consider domain overlap when enforcing budget, not just raw context unit count.

4. **Precomputed relationships eliminate redundant LLM calls.** STATIC's offline-index-for-online-lookup pattern directly applies to conflict detection, which is the largest source of LLM calls in the context unit pipeline.

5. **Constrained decoding could make context unit compliance deterministic.** The most ambitious but most impactful finding — moving from "ask the LLM to respect context units" to "prevent the LLM from violating context units" at the token level.

6. **Per-fact (not per-batch) tracking is the key to scaling.** Paper 6's core insight — tracking consolidation per individual fact rather than per batch edit — directly maps to context units' proposition-level tracking. The per-edit gating approach achieved 0% advancement; per-fact gating achieved 100%.

7. **The "Aria Effect" implies per-context unit difficulty tracking.** Some propositions will be inherently harder to maintain (conflicting with pretraining knowledge, ambiguous semantics). Tracking per-context unit consolidation difficulty could inform decay rates and eviction priority.

8. **Validation-gated operations with rollback prevent cascading damage.** Every maintenance operation should be reversible. Snapshot state before bulk context unit operations (decay, eviction, promotion cycles), validate invariants afterward, rollback if violated. Sleeping LLM rolls back when PPL increases > 15%.

9. **Pathway separation matters for prompt injection format.** Paper 4 discovered that raw-completion and chat-template access are representationally independent in the model. This means the *format* of context unit context injection (system prompt vs. user message vs. structured template) may significantly affect compliance. Worth experimenting with different injection strategies in `ArcMemLlmReference`.

10. **Cumulative reinforcement should compound.** Each LoRA fusing cycle reduces subsequent training loss by 79%. Similarly, each reinforcement event for an context unit should reduce the "effort" needed for future authority promotions — not just increment rank linearly.

---

## 6. Sources

- [vbario/sleeping-llm on GitHub](https://github.com/vbario/sleeping-llm)
- [Show HN: Sleeping LLM](https://news.ycombinator.com/item?id=47162473)
- [Paper 6: Per-Fact Graduated Consolidation (Zenodo)](https://doi.org/10.5281/zenodo.18779159)
- [Paper 1: Sleep-Wake Consolidation (Zenodo)](https://doi.org/10.5281/zenodo.18778760)
- [Paper 2: The Alignment Tax (Zenodo)](https://doi.org/10.5281/zenodo.18778762)
- [Paper 3: Dual-System Memory (Zenodo)](https://doi.org/10.5281/zenodo.18778764)
- [Paper 4: Two-Phase Consolidation (Zenodo)](https://doi.org/10.5281/zenodo.18778766)
- [Paper 5: Sleep-Wake Memory Convergence (Zenodo)](https://doi.org/10.5281/zenodo.18778768)
- [STATIC Paper: Vectorizing the Trie (arXiv)](https://arxiv.org/abs/2602.22647)
- [youtube/static-constraint-decoding on GitHub](https://github.com/youtube/static-constraint-decoding)
- [MarkTechPost: STATIC Coverage](https://www.marktechpost.com/2026/03/01/google-ai-introduces-static-a-sparse-matrix-framework-delivering-948x-faster-constrained-decoding-for-llm-based-generative-retrieval/)
