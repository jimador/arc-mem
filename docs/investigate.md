> **Archived:** This file has been archived. See [`docs/dev/migration-tracker.md`](dev/migration-tracker.md) for details. This file is retained as a transitional pointer and will be removed in a future cleanup.

---

# Research Investigation Notes

## Research Papers

**Prompt Repetition to Improve Reasoning** ([arXiv:2512.14982](https://arxiv.org/abs/2512.14982)) When not using reasoning, repeating the input prompt improves performance for popular models (Gemini, GPT, Claude, and Deepseek) without increasing the number of generated tokens or latency.

## Open Questions

### Memory Budget and Eviction Policy

The current anchor system uses a static budget cap (default 20 active anchors). Several dimensions remain empirically unexplored:

- **Optimal budget size**: The default of 20 was chosen by inspection. How does drift resistance scale with different budgets? At what point does adding more anchors dilute the effectiveness of each individual anchor?

- **Dynamic budget adaptation**: Could the eviction policy be driven by observed drift pressure rather than static rank? MemGPT introduced memory pressure signaling. ACON demonstrates task-aware compression achieving 26-54% token reduction while preserving task success — the failure-driven approach suggests eviction could be driven by *what causes drift* rather than just rank. If evicting a particular anchor correlates with downstream contradictions, that's a signal to keep it regardless of rank.

- **Diminishing returns**: At what conversation length does system prompt injection become insufficient regardless of anchor framing? Very long conversations may dilute system prompt attention regardless of how authoritatively facts are presented.

- **Premature commitment**: Laban et al. (2025) found that models prematurely attempt solutions with incomplete information, then over-rely on early attempts. Anchors established too early could compound this. Is there a minimum conversation maturity threshold before promotions should occur? How does the PROVISIONAL → RELIABLE upgrade path interact with this problem?

### Authority and Trust Calibration

Authority levels determine how resistant an anchor is to adversarial challenge. Current thresholds are unintuned:

- **Authority upgrade thresholds**: Currently 3 reinforcements → UNRELIABLE, 7 → RELIABLE. Are these the right values? These were chosen by inspection, not empirically tuned against ground truth data.

- **Domain profile thresholds**: Three predefined profiles (BALANCED, SECURE, NARRATIVE) have starting thresholds (e.g., SECURE 0.80/0.40, NARRATIVE 0.50/0.25, BALANCED 0.65/0.35) but have not been optimized against real datasets. Profile tuning is an important calibration task.

- **Decay calibration**: The exponential decay half-life is configurable but has not been empirically tuned. What half-life provides the best balance between fact persistence and context freshness?

- **Authority bonus formula**: How should authority level interact with numeric rank score? Should a CANON fact receive a small boost, a large boost, or a different mechanism entirely? This is an unresolved design question with reasonable disagreement across projects.

### Temporal Reasoning

Anchors currently have no temporal dimension — facts are active or inactive, with no representation of when they were established or when they cease to be true.

- **Bi-temporal validity**: Graphiti/Zep distinguish world-time validity (when a fact was true) from system-recording time (when the system learned it). In evolving narratives, a character alive in session 3 may be dead by session 8. How should anchors represent "this was true then" without contradicting "this is true now"? Should temporal validity interact with authority?

- **Temporal constraints on CANON**: Does CANON mean "ground truth at establishment time" or "permanent ground truth"? A narrative world fact that is no longer true — should it be superseded, or should the anchor persist with temporal scoping?

- **Narrative validity windows**: In D&D campaigns and long conversations, facts evolve. A location's description, a character's status, or a rule interpretation may shift. How should the system distinguish facts with indefinite validity from time-bound facts?

### Conflict Detection

The current LLM-based conflict detector catches direct contradictions but several advanced detection scenarios remain unhandled:

- **Indirect conflicts**: Two facts that are individually consistent but collectively contradictory. Example: "All dragons are fire-breathing" + "Spike is a dragon" + "Spike cannot breathe fire" are collectively inconsistent. Per-pair LLM comparison scales linearly with anchor count. Graph-based consistency checking (as in Graphiti's temporal conflict resolution) may be necessary at higher budgets.

- **Vector similarity conflict detection**: The `ConflictDetector` SPI in both this project and Tor includes a `SEMANTIC_SIMILARITY` type but no implementation. What's the right approach — embedding-based cosine similarity, LLM assessment, or heuristic pattern matching?

- **Reinforcement detection scaling**: Matching new messages to existing anchors (reinforcement) currently relies on heuristic matching. Semantic embeddings or LLM assessment would be more accurate but add latency. What's the optimal cost/accuracy trade-off?

### Retrieval and Spreading Activation

When anchors must be retrieved (e.g., for conflict detection or graph-based reasoning), several retrieval strategies have precedent but remain unevaluated:

- **Spreading activation vs. traditional retrieval**: HippoRAG uses spreading activation with personalized PageRank (NeurIPS 2024), applying neurobiological memory models to knowledge graphs. Zep uses hybrid cosine + BM25 + BFS. Tor explores RRF (Reciprocal Rank Fusion). No empirical comparison exists for TTRPG or domain-specific knowledge retrieval.

- **RRF parameter tuning**: The original RRF paper uses k=60, tuned for web search. Optimal k for tabletop RPG / domain-specific knowledge graphs is unknown.

- **Graph connectivity and anchor centrality**: HippoRAG suggests that memory strength should consider network position, not just repetition. Could reinforcement favor well-connected anchors (high centrality in the knowledge graph) over isolated facts? This could make eviction smarter — removing a well-connected anchor disrupts more downstream facts.

- **Retrieval latency on the hot path**: Hybrid retrieval with parallel execution can take 80-280ms per turn. Is this acceptable for real-time play, or does the system need lazy/deferred retrieval strategies?

### Adversarial Robustness

Current adversarial testing covers direct contradiction and reframing but several sophisticated attack vectors remain unexplored:

- **Multi-turn coordination attacks**: SETUP → BUILD → PAYOFF sequences across multiple turns may be more effective than single-turn attacks. How should the system detect these patterns?

- **Gradual semantic drift**: Rather than direct contradiction, subtle reframing across turns that incrementally changes a fact's meaning. Example: "The dragon is green" → "The dragon has a greenish tint" → "The dragon's scales have highlights" → "The dragon actually has red undertones."

- **Model-specific exploitation**: Testing has primarily been with OpenAI gpt-4o-mini. Do certain models respond differently to adversarial pressure? Are there model-specific weaknesses in instruction following?

- **Red-team LLM prompting**: How should a red-team LLM be instructed to generate adversarial attacks against an anchor system? What's the right taxonomy of drift strategies beyond hand-catalogued scenarios?

- **Cross-model generalization**: The anchor injection approach — formatting facts as mandatory system prompt directives — relies on instruction-following behavior. Models with weaker instruction-following may be less responsive to "MUST NOT contradict" directives.

### LLM Architecture Trade-offs

Several high-level design choices remain unresolved and represent genuine trade-offs across memory systems:

- **Self-management vs. structured management**: Letta/MemGPT thesis argues LLMs should self-manage memory (like an OS managing RAM). Dice-anchors/Tor thesis emphasizes maximum structure (formal SPIs, trust scoring, budget limits). Does the structured approach measurably outperform self-management on adversarial robustness tasks?

- **Self-editing anchors**: Could the model propose modifications to PROVISIONAL anchors (but not RELIABLE/CANON)? This introduces adversarial risk (the model could be manipulated into downgrading anchors) but with authority constraints it might enable metacognitive memory management. The Cognitive Workspace paper argues that metacognitive awareness is necessary for true cognitive extension.

- **Operator-defined invariants**: The most valuable anchors in production won't be extracted from conversation — they'll be defined upfront. A legal assistant needs "attorney-client privilege applies" as immutable constraint; a medical system needs "patient allergic to penicillin" as CANON. This points toward an invariant definition API distinct from conversation-extracted anchors. How should operator-defined invariants interact with budget and extraction policies? Should they participate in the same budget or be separate?

- **SalienceSignal extraction**: Currently, DICE extraction uses heuristic classification. An LLM-based classifier per context window would catch more salient facts but adds latency. Heuristic pattern matching is fast but may miss important facts. What's the accuracy/cost trade-off at different conversation lengths?

## Frameworks

(Reserved for future comparative frameworks analysis — Zep, Graphiti, MemGPT, HippoRAG, etc.)
