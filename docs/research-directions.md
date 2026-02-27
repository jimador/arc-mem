# Research Directions

Ongoing and future research areas for the dice-anchors project.

## Collaborative Anchor Mutation

The [collaborative-anchor-mutation roadmap](../../openspec/roadmaps/collaborative-anchor-mutation-roadmap.md) is the primary active research track — how to allow legitimate revisions of established anchors without compromising adversarial drift resistance.

### Problem Statement

When a collaborator (human or AI) attempts to revise an established anchor via natural language, the anchor framework's drift resistance treats the revision as an adversarial attack. The framework correctly defends the original fact but incorrectly blocks legitimate updates. This is the core tension: how to distinguish "the king is actually an ancient lich" (legitimate world reveal) from "the king was never real" (adversarial contradiction).

### Feature Waves

| Wave | Feature | Priority | Status |
|------|---------|----------|--------|
| 1 | Revision intent classification (`ConflictType` enum) | MUST | Implemented |
| 1 | Prompt compliance revision carveout | MUST | Not started |
| 2 | Dependent anchor cascade during supersession | SHOULD | Not started |
| 2 | Anchor provenance metadata (extraction turn, speaker role) | SHOULD | Not started |
| 3 | UI-controlled mutation (explicit sidebar editing) | MAY | Partially implemented (ChatView sidebar) |

### Completed Research Tasks

| Task | Question | Key Finding |
|------|----------|-------------|
| R00 | What fails when a collaborator revises an anchor via chat? | The conflict resolver treats revisions as contradictions; `KEEP_EXISTING` blocks the update. Root cause: no distinction between revision intent and adversarial intent. |
| R01 | How reliably can an LLM classify revision intent? | Two-step detection (detect conflict → classify type) with chain-of-thought reasoning. < 5% false-positive rate achievable with GPT-4.1/Claude Sonnet. Conservative default: ambiguous = CONTRADICTION. |
| R02 | Which cascade strategy for dependent anchors? | Two-phase: temporal co-creation as fast filter (zero LLM cost) + LLM semantic dependency as precision filter. Authority-gated: hard cascade for PROVISIONAL/UNRELIABLE, soft cascade (review) for RELIABLE, CANON exempt. |
| R03 | How should mixed-authority revisions work? | PROVISIONAL/UNRELIABLE: revision-eligible by default. RELIABLE: configurable (default off). CANON: never. Adds ~155 tokens for `[revisable]` annotation in prompt template. |
| R04 | How do existing AI memory frameworks handle mutation? | No surveyed framework (Letta/MemGPT, Zep/Graphiti, LangMem, Mem0, A-MemGuard, MemOS) distinguishes update from contradiction. We haven't found revision-vs-contradiction classification elsewhere. Graphiti's temporal supersession is the closest structural analog. |
| R05 | What prior art exists in non-AI domains? | 19 transferable patterns from 9 domains. Top 3: (1) Wikipedia ORES two-axis classification for intent × impact, (2) JTMS label propagation for cascade, (3) accounting materiality for authority × impact radius. |

---

## Current Direction: AGM Belief Revision Framework

Research task R05 identified AGM belief revision theory (Alchourrón, Gärdenfors, Makinson, 1985) as the strongest theoretical foundation for anchor mutation semantics.

### AGM ↔ Anchor Mapping

| AGM Concept | Anchor Equivalent | Implementation |
|-------------|-------------------|----------------|
| Belief set | Active anchor pool | `AnchorEngine.inject(contextId)` |
| Contraction (remove belief) | Anchor archival | `AnchorEngine.archive()` |
| Revision (remove + add) | Supersession | `AnchorEngine.supersede()` |
| Entrenchment ordering | Authority tiers | PROVISIONAL < UNRELIABLE < RELIABLE < CANON |
| Minimal change principle | Cascade scope constraint | Only invalidate anchors that logically depend on the revised one |
| Recovery postulate | Anchor revival | `AnchorEngine.revive()` (restore from archive) |

### Cross-Domain Patterns Informing Design

**TMS (Truth Maintenance Systems, Doyle 1979)** — JTMS-style label propagation is a well-established algorithm for cascade invalidation. When a parent anchor is revised, label-propagate "unsupported" status to dependents, then re-evaluate each. 40+ years of implementation history.

**Wikipedia ORES** — Two-axis classification (intent × impact) produces a 2×2 matrix that catches edge cases the flat `ConflictType` enum misses: good-faith-but-damaging edits, bad-faith-but-harmless edits. Worth exploring whether revision classification should decompose into independent intent and impact axes.

**Accounting Materiality (IAS 8)** — Materiality is a function of authority AND impact radius, not authority alone. A PROVISIONAL anchor with 10 dependents is more "material" than a RELIABLE anchor with zero dependents. Cascade priority should account for this.

**MVCC (Multi-Version Concurrency Control)** — Temporal supersession chains are structurally analogous to MVCC version chains. The `SUPERSEDES` relationship in Neo4j already supports this pattern.

---

## Empirical Evaluation

### Ablation Framework

The `BenchmarkRunner` supports multi-condition ablation experiments comparing:
- `FULL_ANCHORS` — complete anchor framework
- `NO_ANCHORS` — no anchor injection
- `FLAT_AUTHORITY` — anchors without authority hierarchy
- `NO_TRUST` — planned; anchors without trust evaluation

`ResilienceReportBuilder` produces Markdown reports with per-condition metrics, confidence intervals, and effect sizes.

### Benchmarking UI

`BenchmarkView` (`/benchmark`) — configure and run experiments, with inline fact drill-down and condition comparison.

### Positioning Against Related Systems

| System | Key Difference from Anchors |
|--------|----------------------------|
| MemGPT/Letta | Fixed memory blocks; no authority hierarchy or adversarial resistance focus |
| Zep/Graphiti | Temporal knowledge graphs; strongest complement (temporal model); no mandatory injection |
| ShardMemo | Shard-based memory management; no authority governance |
| MemOS | Operating system metaphor for memory; no adversarial testing |
| HippoRAG | PageRank-based retrieval; no guaranteed presence |
| ACON | Task-aware compression; no authority hierarchy |

What sets anchors apart, in our survey so far: adversarial resistance as a primary concern, authority-governed lifecycle management, and mandatory prompt injection. None of the surveyed systems combine all three.

---

## Future Work

### Track B: Creative Retrieval

Spreading activation and graph-native retrieval for serendipitous knowledge discovery. Current retrieval is rank-sorted injection; future work explores:
- Personalized PageRank over the proposition graph (inspired by HippoRAG)
- Activation spreading from query entities through `HAS_MENTION` edges
- Relevance-weighted retrieval complementing rank-sorted injection

### Track C: A2A Governance

Multi-agent anchor revision protocols for scenarios where multiple agents share a proposition graph:
- Revision authority scoping (which agent can revise which anchors)
- Conflict resolution when agents propose contradictory updates
- Consensus mechanisms for shared knowledge graphs

### Cross-Domain Generalization

Validation beyond tabletop RPGs:
- Healthcare: drug interaction invariants, patient history consistency
- Legal: privilege preservation, precedent tracking
- Operations: runbook invariants, incident state management
- Compliance: regulatory constraint persistence

### Graph-Native Retrieval

Current architecture stores propositions in Neo4j but uses rank-sorted injection, not graph traversal. Future work:
- Entity-centric traversal from query subjects
- Subgraph extraction for related anchor clusters
- Graph attention mechanisms for relevance scoring

### Memory Poisoning Threat Model

Systematic analysis of attack vectors targeting the anchor framework:
- Repetition attacks exploiting reinforcement mechanics
- Authority escalation through crafted revision chains
- Budget starvation via low-value anchor flooding (partially addressed by `budget-starvation-interference.yml` scenario)
- Extraction poisoning through adversarial proposition injection
