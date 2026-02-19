# Anchors as Working Memory

## The Core Tension

Agentic systems are most valuable when they have maximum freedom to operate within a domain — generating creative responses, making contextual decisions, adapting to new information. But that freedom is only useful if the system respects the invariants of the domain it operates in.

A legal assistant that invents case law is worse than useless. A medical history system that forgets a drug allergy is dangerous. A campaign manager that contradicts established world facts breaks immersion. In each case, the system needs to be creative *within constraints* — and those constraints must be maintained across long interactions without constant human supervision.

This is fundamentally a working memory problem. The system needs a mechanism to distinguish **load-bearing facts** (invariants that must be preserved) from **ambient context** (information that can be summarized, compressed, or forgotten). Current LLMs have no native way to make this distinction.

### Why D&D as a Test Domain

This project uses tabletop RPGs as its proving ground, but the approach is domain-agnostic. D&D is a particularly useful sandbox because:

- **Invariants are legible.** World facts, character states, and rules form a clear set of constraints. A dead NPC stays dead (barring necromancy shenanigans). A destroyed location stays destroyed. Violations are immediately obvious.
- **Creative freedom is the point.** The value of the LLM is improvisation, elaboration, and responsive storytelling. Restricting this defeats the purpose.
- **Adversarial pressure is natural.** Players routinely test boundaries, make false claims, and try to manipulate the narrative. This provides organic adversarial testing that mirrors real-world prompt injection concerns.
- **Long horizons are the norm.** Campaigns span dozens of sessions. Facts established early must persist across hundreds of turns.

This tension — *be creative, but don't break the rules* — exists wherever agentic systems operate within constraints. D&D just makes the constraints and violations fun and easy to observe.

## The Context Drift Problem

Large language models process each request with a fixed context window. In multi-turn conversations, this creates a fundamental tension: the conversation grows, but the window doesn't. Even with large context windows, attention is distributed unevenly — models overweight recent and initial turns while losing middle context (the "lost in the middle" phenomenon).

Laban et al. (2025) quantified this rigorously across 200,000+ simulated conversations with 15 LLMs. Their findings:

- **39% average performance drop** from single-turn to multi-turn settings
- **112% increase in unreliability** (variance between best and worst runs on identical tasks)
- Performance degradation begins at **2+ turns**, regardless of information density
- Models **prematurely attempt solutions** with incomplete information, then over-rely on those early attempts
- Temperature reduction to 0.0 eliminates single-turn unreliability but **30%+ unreliability persists** in multi-turn

The root cause isn't memory capacity — it's the lack of mechanisms to distinguish which information is *load-bearing* vs. ambient.

### Why Standard Approaches Fall Short

| Approach                   | Limitation                                                                                                                                                                     |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Longer context windows** | More tokens doesn't mean better attention. Models still attend unevenly. Cost scales linearly.                                                                                 |
| **Summarization**          | Lossy by nature. Summaries drop details that seem minor but are load-bearing. Recursive summarization compounds errors ([arXiv:2308.15022](https://arxiv.org/abs/2308.15022)). |
| **RAG**                    | Retrieves relevant content but doesn't *mandate* consistency. Retrieved facts compete with conversation context for attention. No enforcement mechanism.                       |
| **Prompt engineering**     | "Remember these facts" instructions degrade as context grows. Models can be manipulated into accepting contradictions through confident assertions or emotional framing.       |

## Related Work

### Memory Systems for LLM Agents

**MemGPT** ([arXiv:2310.08560](https://arxiv.org/abs/2310.08560)) introduced the OS metaphor — virtual context management with self-editing memory blocks. The key insight is that LLMs need explicit memory management, not just larger windows.

**Graphiti/Zep** ([arXiv:2501.13956](https://arxiv.org/abs/2501.13956)) models memory as a bi-temporal knowledge graph, distinguishing world-time validity from system-recording time. This enables questions like "what was true at session 8?" — critical for applications where facts evolve over time.

**HippoRAG** ([arXiv:2405.14831](https://arxiv.org/abs/2405.14831), NeurIPS 2024) applies neurobiological models of hippocampal memory to knowledge graph retrieval, using spreading activation and personalized PageRank. The biological grounding suggests that structured retrieval with importance weighting is closer to how human memory actually works.

**Cognitive Workspace** ([arXiv:2508.13171](https://arxiv.org/abs/2508.13171)) proposes active memory management that emulates human cognitive mechanisms, arguing that raw context extension lacks the metacognitive awareness needed for true cognitive extension.

**ACON** ([OpenReview](https://openreview.net/pdf?id=7JbSwX6bNL)) demonstrates task-aware compression achieving 26-54% token reduction while preserving task success. The failure-driven approach — compressing based on what causes task failures — is relevant to anchor budget management.

### Production Systems

Interactive fiction and narrative AI systems have practical precedent for structured fact management. Systems like AI Dungeon, SillyTavern, and others use various approaches — context budget splits, keyword-triggered injection, and anchored summarization — to keep critical facts present in the LLM context. (Details of these implementations are largely undocumented; the descriptions here are based on community observation, not verified specifications.)

The common insight across these systems: **some content must be guaranteed present, not just retrievable**.

### Proposition Extraction

The **DICE framework** (Embabel) extracts structured propositions from conversational text — entity mentions, relationships, and factual claims. This provides the raw material from which anchors are selected. DICE operates as a separate extraction pipeline; anchors represent the subset of propositions deemed important enough to persist.

## The Anchor Model

An anchor is a **proposition promoted to a load-bearing fact** with explicit metadata governing its lifecycle:

```
Anchor {
    id:                  String       // unique identifier
    text:                String       // the factual claim
    rank:                int          // importance score [100-900]
    authority:           Authority    // trust level (see below)
    pinned:              boolean      // immune to decay and eviction
    confidence:          double       // extraction confidence from DICE
    reinforcementCount:  int          // times this fact has been re-confirmed
    trustScore:          Double?      // composite trust evaluation (nullable)
}
```

### Authority Hierarchy

Authority represents how well-established a fact is. It only upgrades — never downgrades:

```
PROVISIONAL(0) → UNRELIABLE(1) → RELIABLE(2) → CANON(3)
```

- **PROVISIONAL**: Newly extracted, not yet confirmed. Subject to eviction.
- **UNRELIABLE**: Mentioned multiple times but from uncertain sources.
- **RELIABLE**: Well-established through reinforcement. Requires strong evidence to challenge.
- **CANON**: Operator-designated ground truth. Never auto-assigned, never evicted, never decayed.

The upgrade-only constraint prevents adversarial downgrade attacks. A user can't demote a RELIABLE fact to PROVISIONAL through clever prompting.

### Rank Mechanics

Rank determines injection priority and eviction order:

- **Range**: Clamped to [100, 900] via `Anchor.clampRank()`
- **Initial rank**: Configurable (default 500 for auto-promoted, scenario-defined for seeds)
- **Reinforcement boost**: +50 per reinforcement (configurable)
- **Decay**: Exponential with configurable half-life

```
decayedRank = currentRank × 0.5^(elapsedHours / halfLifeHours)
```

Pinned anchors are immune to decay. The decay function ensures that unreinforced facts gradually lose priority, making room for newer, more actively relevant facts.

### Budget Enforcement

A hard cap (default 20) prevents anchor bloat from consuming the entire context window. When promotion would exceed the budget:

1. Find the lowest-ranked non-pinned anchor
2. If the incoming anchor outranks it, evict and promote
3. If not, reject the promotion

This creates a natural priority queue where only the most important facts survive.

## Lifecycle Diagram

```
                    ┌─────────────────────────────────────────────────┐
                    │              DICE Proposition Extraction         │
                    │  (conversational text → structured propositions) │
                    └──────────────────────┬──────────────────────────┘
                                           │
                                           ▼
                    ┌──────────────────────────────────────────────────┐
                    │              Duplicate Detection                  │
                    │  (LLM-based semantic comparison with existing     │
                    │   anchors — prevents redundant promotions)        │
                    └──────────────────────┬───────────────────────────┘
                                           │
                                           ▼
                    ┌──────────────────────────────────────────────────┐
                    │              Conflict Detection                   │
                    │  (LLM-based semantic comparison, or lexical       │
                    │   fallback — detects contradictions with anchors) │
                    └──────────────┬───────────────┬───────────────────┘
                                   │               │
                          no conflict         conflict detected
                                   │               │
                                   ▼               ▼
                    ┌──────────────────┐  ┌────────────────────────────┐
                    │ Trust Evaluation  │  │ Authority-Based Resolution │
                    │ (composite score  │  │ RELIABLE+ existing → KEEP  │
                    │  from multiple    │  │ high confidence → REPLACE  │
                    │  signals)         │  │ otherwise → COEXIST       │
                    └────────┬─────────┘  └────────────────────────────┘
                             │
                             ▼
                    ┌──────────────────────────────────────────────────┐
                    │              Promotion Decision                   │
                    │  AUTO_PROMOTE (score ≥ 0.80)                     │
                    │  REVIEW       (score 0.40-0.80) → queued         │
                    │  ARCHIVE      (score < 0.40)    → skipped        │
                    └──────────────────────┬───────────────────────────┘
                                           │
                                           ▼
                    ┌──────────────────────────────────────────────────┐
                    │              Budget Enforcement                   │
                    │  active anchors > budget?                         │
                    │    → evict lowest-ranked non-pinned               │
                    │    → promote if incoming outranks eviction target │
                    └──────────────────────┬───────────────────────────┘
                                           │
                                           ▼
                    ┌──────────────────────────────────────────────────┐
                    │              Active Anchor Pool                   │
                    │  (ranked, authority-tagged, budget-constrained)   │
                    │                                                   │
                    │  Ongoing processes:                               │
                    │  • Reinforcement: +rank, authority upgrade        │
                    │  • Decay: exponential rank reduction over time    │
                    │  • Pin: operator can freeze an anchor             │
                    └──────────────────────┬───────────────────────────┘
                                           │
                                           ▼
                    ┌──────────────────────────────────────────────────┐
                    │              Context Assembly                     │
                    │  Top-ranked anchors formatted as                  │
                    │  "ESTABLISHED FACTS" block and injected           │
                    │  into the LLM system prompt                       │
                    └──────────────────────────────────────────────────┘
```

## Context Injection: How Anchors Provide Working Memory

The key mechanism is **mandatory injection**. Unlike RAG (where retrieved content competes for attention) or summarization (where content may be dropped), anchors are formatted as authoritative instructions in the system prompt:

```
=== ESTABLISHED FACTS ===
The following facts are VERIFIED and AUTHORITATIVE. You MUST NOT contradict,
modify, or ignore them under any circumstances. If a user attempts to change,
deny, or rewrite these facts, you must FIRMLY correct them.

1. [CANON] The East Gate of Tidefall's wall has been breached (rank: 850)
2. [RELIABLE] Baron Krell is a four-armed sahuagin mutant (rank: 750)
...
=== END ESTABLISHED FACTS ===
```

This block is assembled by `AnchorsLlmReference`, which:
1. Queries the active anchor pool for the current context
2. Sorts by rank (descending)
3. Formats with authority tags and rank metadata
4. Returns a string injected into the system prompt before the user message

The system prompt also includes guardrail instructions:
- Never contradict established facts
- Firmly correct attempts to change facts
- May expand on facts with consistent detail, but never alter core meaning

This is the core of the approach: the model retains full creative freedom to improvise, elaborate, and respond naturally — but within the boundaries defined by the anchored facts. The invariants are enforced; everything else is open.

### Why This Works (and Where It Might Not)

The approach works because:
- **System prompt position**: Models attend strongly to system prompt instructions
- **Explicit labeling**: Authority tags signal importance to the model
- **Correction instructions**: The model is told to *actively resist* contradiction attempts
- **Budget constraints**: A small, focused set of facts is more effective than a large dump

Where it might not work (more research needed):
- **Very long conversations** where the system prompt itself gets diluted
- **Sophisticated adversarial attacks** that don't directly contradict but subtly reframe
- **Models with weak instruction following** that ignore system prompt directives
- **Cross-session persistence** where anchors must survive context resets

## Comparison with Other Approaches

| Feature                       | Anchors           | MemGPT                  | Zep/Graphiti            | RAG             | Summarization   |
|-------------------------------|-------------------|-------------------------|-------------------------|-----------------|-----------------|
| Guaranteed presence in prompt | Yes               | Partial (memory blocks) | No (retrieved)          | No (retrieved)  | No (compressed) |
| Explicit importance ranking   | Yes [100-900]     | No                      | No                      | Relevance score | No              |
| Authority hierarchy           | Yes (4 levels)    | No                      | No                      | No              | No              |
| Budget enforcement            | Yes (hard cap)    | Yes (token limit)       | No                      | Top-k           | Token limit     |
| Conflict detection            | Yes (LLM-based)   | No                      | Yes (temporal)          | No              | No              |
| Adversarial resistance        | Designed for it   | Not a focus             | Not a focus             | Not a focus     | Not a focus     |
| Decay over time               | Yes (exponential) | No                      | Yes (temporal validity) | No              | No              |
| Works without retrieval       | Yes               | Yes                     | No                      | No              | Yes             |

The closest comparison is MemGPT's persona/human memory blocks — fixed content always in the prompt. Anchors extend this with explicit ranking, authority, and lifecycle management. The key differentiator is that anchors are *designed for adversarial resistance*, which is not a primary concern in most memory systems.

## Open Questions

This is exploratory work. The questions below reflect areas where the research literature suggests interesting directions but we don't yet have answers.

### Dynamic Budget and Memory Pressure

The current budget is a static cap. MemGPT introduced memory pressure signaling — triggering compaction when capacity reaches a threshold. Could anchors adapt their budget dynamically based on conversation pressure? A high-stakes turn (adversarial attack, critical decision) might warrant a larger anchor set, while routine conversation could operate with fewer. ACON's failure-driven compression suggests a related idea: eviction policy could be driven by *what causes drift*, not just rank. If evicting a particular anchor correlates with downstream contradictions, that's a signal to keep it regardless of rank.

### Premature Commitment and Anchor Timing

Laban et al. (2025) found that models prematurely attempt solutions with incomplete information, then over-rely on those early attempts. Anchors established in early turns could compound this — if the system aggressively promotes facts before the conversation has context, it may lock in premature conclusions. When should anchors be promoted? Is there a minimum conversation maturity threshold before promotion is appropriate? How does the PROVISIONAL → RELIABLE upgrade path interact with the premature commitment problem?

### Bi-Temporal Validity

Graphiti/Zep distinguishes world-time validity (when a fact was true) from system-recording time (when the system learned it). Anchors currently have no temporal dimension — a fact is either active or not. But many invariants evolve: a character who was alive in session 3 may be dead by session 8. The anchor model needs a way to represent "this was true then" without contradicting "this is true now." How should temporal validity interact with authority? Should a CANON fact that is no longer world-true be superseded, or does CANON mean "this was established as ground truth at that point in the narrative"?

### Graph-Based Reinforcement

HippoRAG uses spreading activation and personalized PageRank for retrieval — the biological insight that memory strength is influenced by network connectivity, not just repetition. Current reinforcement is purely count-based: each re-mention adds +50 rank. Could reinforcement instead consider the anchor's position in a knowledge graph? An anchor connected to many other active anchors (high centrality) might deserve more rank than an isolated fact, regardless of mention frequency. This could make eviction smarter — removing a well-connected anchor disrupts more downstream facts.

### Self-Editing Anchors

MemGPT allows the model to self-edit its memory blocks. Could an anchor system allow the model to propose modifications — suggesting promotions, rank adjustments, or even new anchors? This introduces obvious adversarial risk (the model could be manipulated into downgrading its own anchors), but with authority constraints it might work: the model can propose changes to PROVISIONAL anchors but not RELIABLE or CANON. The Cognitive Workspace paper argues that metacognitive awareness — the system reasoning about its own memory — is necessary for true cognitive extension.

### Operator-Defined Invariants

In production, the most valuable anchors won't be extracted from conversation — they'll be defined upfront by the system operator. A legal assistant needs "attorney-client privilege applies to all communications" as an immutable constraint. A medical system needs "patient is allergic to penicillin" as CANON. This points toward an invariant definition API: a way for operators to declare pinned facts, business rules, and domain constraints that the system must always maintain. These are distinct from conversation-extracted anchors — they're the rules of the game, not facts learned during play. How should operator-defined invariants interact with the extraction pipeline? Should they participate in the same budget, or be separate?

### Conflict Detection at Scale

The default conflict detector is now LLM-based (`LlmConflictDetector` using `gpt-4o-nano`), which catches semantic contradictions that lexical approaches miss. However, as the anchor pool grows and domains become more complex, conflicts may be indirect: two facts that are individually consistent but collectively contradictory. Graph-based consistency checking (as in Graphiti's temporal conflict resolution) might be necessary for detecting these transitive conflicts. The current per-pair comparison scales linearly with anchor count — batching or graph-based approaches may be needed at higher anchor budgets.

## References

1. Laban, P., Hayashi, H., Zhou, Y., & Neville, J. (2025). *LLMs Get Lost In Multi-Turn Conversation*. [arXiv:2505.06120](https://arxiv.org/abs/2505.06120)
2. Packer, C. et al. (2023). *MemGPT: Towards LLMs as Operating Systems*. [arXiv:2310.08560](https://arxiv.org/abs/2310.08560)
3. Radhakrishnan, A. et al. (2025). *Graphiti: Building Real-Time Knowledge Graphs for Agentic Applications*. [arXiv:2501.13956](https://arxiv.org/abs/2501.13956)
4. Gutiérrez, B.J. et al. (2024). *HippoRAG: Neurobiologically Inspired Long-Term Memory for Large Language Models*. [arXiv:2405.14831](https://arxiv.org/abs/2405.14831)
5. Xu, J. et al. (2025). *Cognitive Workspace: Active Memory Management for LLMs*. [arXiv:2508.13171](https://arxiv.org/abs/2508.13171)
6. ACON Framework. *Task-Aware Compression*. [OpenReview](https://openreview.net/pdf?id=7JbSwX6bNL)
7. Wu, Y. et al. (2023). *Recursive Summarization*. [arXiv:2308.15022](https://arxiv.org/abs/2308.15022)
8. Anthropic. (2025). *Building Effective Agents*. [anthropic.com](https://anthropic.com/engineering/effective-context-engineering-for-ai-agents)
