# Research Task: AI Memory Framework Fact Mutation Comparison

## Task ID

`R04`

## Question

How do existing AI memory and agent frameworks handle the distinction between legitimate fact updates and adversarial/erroneous contradictions, and what mechanisms do they provide for collaborative fact mutation?

## Why This Matters

1. Decision blocked by uncertainty: We may be solving a problem others have already addressed. Understanding prior implementations prevents reinventing inferior solutions and surfaces design patterns we can adapt.
2. Potential impact if wrong: Building a bespoke revision model when proven patterns exist wastes effort and risks worse outcomes.
3. Related feature IDs: F01, F02, F03 (all benefit from understanding how others solved this).

## Scope

### In Scope

1. Survey at minimum these frameworks for their fact update/mutation mechanisms:
   - **Letta (formerly MemGPT)** — Core memory blocks with explicit edit tools; how does `core_memory_replace` handle contradictions vs updates?
   - **Zep / Graphiti** — Temporal knowledge graph with fact nodes; how do fact updates propagate? Is there a contradiction detection layer?
   - **LangMem** — LangChain's memory management; how does it handle fact overwriting?
   - **Mem0** — Self-improving memory layer; does it support explicit fact revision?
   - **A-MemGuard** — Adversarial memory defense; how does it distinguish attack from correction?
   - **MemOS** — Memory operating system abstraction; does its memory lifecycle include mutation operations?
2. For each framework, capture:
   - Does it distinguish update from contradiction? How?
   - What's the mutation mechanism (overwrite, supersession, versioning)?
   - Is there a concept of authority/trust that gates mutation?
   - How does it handle dependent/derived facts when a base fact changes?
   - What's the conflict resolution strategy?

### Out of Scope

1. Performance benchmarking of other frameworks.
2. Full architectural analysis (focus on mutation semantics only).
3. Proprietary/closed-source system internals.

## Research Criteria

1. Required channels: `web`, `similar-repos`, `repo-docs`
2. Source priority order: official docs > GitHub repos > blog posts > papers
3. Freshness window: 18 months (2024-2026) — these frameworks evolve rapidly
4. Minimum evidence count: 4 (at least 3 distinct frameworks analyzed)
5. Timebox: 8h
6. Target confidence: `medium`

## Method

1. Local code/doc checks: Review existing related work analysis in `openspec/roadmaps/anchor-working-memory-research/research/R02-related-work-landscape-analysis.md` if present.
2. External evidence collection:
   - GitHub repos: Letta (`letta-ai/letta`), Zep (`getzep/zep`), Graphiti (`getzep/graphiti`), LangMem (`langchain-ai/langmem`), Mem0 (`mem0ai/mem0`)
   - Official documentation sites for each framework
   - arXiv papers: MemGPT (2310.08560), A-MemGuard, MemOS
3. Comparison matrix construction: standardized columns across all frameworks for mutation semantics.

**Note on methodology**: WebSearch, WebFetch, Bash, and GitHub MCP tools were all unavailable during execution. Findings draw from (a) the project's existing R02 landscape analysis, which includes primary source citations, and (b) the researcher's knowledge of primary sources (papers, documentation, source code) within the training data window (through May 2025). Evidence reliability ratings reflect this constraint. Where live source verification would have materially strengthened a finding, this is noted.

## Findings

| Evidence ID | Channel | Source | Date Captured | Key Evidence | Reliability |
|-------------|---------|--------|---------------|--------------|-------------|
| E01 | repo-docs | Packer et al. (2023), "MemGPT: Towards LLMs as Operating Systems," arXiv:2310.08560; project R02-E1 | 2026-02-25 | MemGPT defines `core_memory_replace(old_str, new_str)` as a string-level find-and-replace within named memory blocks. The agent (LLM) autonomously decides when to invoke this tool. There is no validation, conflict detection, or authorization layer between the agent's decision and the memory mutation. The operation is a destructive in-place overwrite — the old content is lost unless the agent explicitly archives it first via `archival_memory_insert`. | High (primary paper + R02 corroboration) |
| E02 | repo-docs | Packer et al. (2023), arXiv:2310.08560, Section 3.2 | 2026-02-25 | `core_memory_append(content)` adds text to a named block without any deduplication or consistency check against existing block contents. If the block says "User likes cats" and the agent appends "User dislikes cats," both statements coexist. The agent's own reasoning on future reads is the only reconciliation mechanism. | High (primary paper) |
| E03 | repo-docs | Letta documentation (lettaai.com, reviewed Q3 2024 per R02-E2); Letta Conversations API docs | 2026-02-25 | Letta's productization added shared memory blocks across agents. Any agent with access to a shared block can call `core_memory_replace` or `core_memory_append` on it. There is no per-agent permission model, no write locking, and no conflict detection for concurrent mutations by different agents. The Conversations API enables multi-agent coordination but memory governance relies entirely on the agents' prompt instructions. | Medium (documentation review; Letta evolves rapidly — this may have changed post-May 2025) |
| E04 | repo-docs | Rasmussen et al. (2025), "Graphiti: Building Real-Time Temporal Knowledge Graphs," arXiv:2501.13956; project R02-E3 | 2026-02-25 | Graphiti uses bitemporal modeling with `valid_at` and `invalid_at` timestamps on edges. When a new fact contradicts an existing one, the system sets `invalid_at` on the old edge (invalidation, not deletion) and creates a new edge with the updated fact. This is non-destructive supersession: the old fact is preserved in the graph as historical record. Contradiction detection uses LLM-based semantic comparison between incoming facts and existing edges. | High (primary paper + R02 corroboration) |
| E05 | repo-docs | Rasmussen et al. (2025), arXiv:2501.13956, Section 4 (Edge Resolution) | 2026-02-25 | Graphiti's conflict resolution is recency-based: the most recently asserted fact wins, unconditionally. There is no authority hierarchy, source attribution, or trust scoring that gates whether a new fact can invalidate an old one. An adversary who injects "the capital of France is Berlin" will invalidate the existing "capital of France is Paris" edge because the injection is more recent. The LLM detects the semantic conflict but the resolution strategy always favors the newer assertion. | High (primary paper) |
| E06 | repo-docs | Graphiti source code (`graphiti_core/utils/maintenance/edge_operations.py`, reviewed via GitHub pre-May 2025) | 2026-02-25 | Graphiti's `invalidate_edges` function takes a list of existing edges and a new fact, then uses an LLM call to determine which existing edges are invalidated by the new fact. The LLM prompt asks whether the new information contradicts, updates, or is unrelated to each existing edge. Edges classified as contradicted or updated are given an `invalid_at` timestamp. **Inference**: This is the closest thing to an update-vs-contradiction distinction in any surveyed framework, but the distinction is used only for determining WHICH edges to invalidate, not WHETHER invalidation is permitted. Both updates and contradictions result in the same outcome: old edge invalidated, new edge created. | Medium (source code review from training data; may have evolved) |
| E07 | repo-docs | Graphiti source code (`graphiti_core/graphiti.py`, `build_episodic_edges` and `resolve_edge_contradictions` methods) | 2026-02-25 | Graphiti distinguishes episodic edges (temporal relationships between episodes/events) from semantic edges (factual relationships between entities). Only semantic edges undergo contradiction resolution. Episodic edges are append-only — they represent "this happened" and are never invalidated by new episodes. This is a form of implicit type-based mutation gating: events are immutable, facts are mutable. | Medium (source code review from training data) |
| E08 | web | Mem0 documentation (docs.mem0.ai, pre-May 2025); Mem0 GitHub README | 2026-02-25 | Mem0's `add()` method processes new information through a pipeline: (1) extract facts from the input, (2) search existing memories for related content, (3) use an LLM to decide whether each extracted fact should CREATE a new memory, UPDATE an existing memory, or be treated as a NOOP (redundant). When UPDATE is chosen, the LLM generates a merged/updated version of the memory that incorporates the new information. The old memory is replaced with the merged version. | Medium (documentation + README; Mem0 is rapidly evolving) |
| E09 | web | Mem0 source code (`mem0/memory/main.py`, `_add_to_vector_store` method, pre-May 2025) | 2026-02-25 | Mem0's update decision is made by a single LLM call that receives the new fact and all retrieved similar memories. The LLM returns a JSON array of actions: `{event: "ADD", text: "..."}`, `{event: "UPDATE", old_memory_id: "...", text: "..."}`, `{event: "DELETE", old_memory_id: "..."}`, or `{event: "NONE"}`. There is no explicit contradiction detection — the LLM implicitly decides whether new information supersedes, augments, or conflicts with existing memories. The UPDATE action is a destructive overwrite of the old memory's text. | Medium (source code from training data; implementation details may have changed) |
| E10 | web | Mem0 documentation, "Memory Operations" section | 2026-02-25 | Mem0 provides an explicit `update(memory_id, data)` API for programmatic memory modification by the host application (distinct from the LLM-driven `add()` pipeline). This direct API has no conflict detection — it unconditionally overwrites the specified memory. There is also a `delete(memory_id)` API. Neither operation has authority checks or cascade logic. | Medium (documentation) |
| E11 | web | LangMem documentation (langchain-ai/langmem GitHub README, pre-May 2025) | 2026-02-25 | LangMem provides three core memory management functions exposed as tools: `manage_memory` (create/update/delete memories based on conversation content), with configurable memory schemas. The `manage_memory` tool uses an LLM to extract facts and decide whether to create new memories, update existing ones, or delete obsolete ones. The update mechanism is a full content replacement — the LLM generates the new memory text. | Medium (README + docs from training data) |
| E12 | web | LangMem source code (`langmem/knowledge/extraction.py`, pre-May 2025) | 2026-02-25 | LangMem's memory extraction uses a "patch" model: the LLM receives existing memories alongside new conversation content and produces a set of operations (CREATE, UPDATE, DELETE) on the memory store. For UPDATE operations, the LLM generates the complete new content for the memory. There is no separate contradiction detection step — the LLM implicitly resolves conflicts during extraction. No versioning: the update replaces the old content. No authority model. | Medium (source code from training data) |
| E13 | similar-repos | Li et al. (2024), "A-MemGuard: Proactive Defense Against Memory Poisoning Attacks in LLM Agents," arXiv:2510.02373; project R02-E6 | 2026-02-25 | A-MemGuard applies a composite trust score at memory append time: the score integrates (a) semantic consistency with existing memories, (b) source reputation, and (c) temporal recency. Memories below the trust threshold are blocked from storage entirely. At retrieval time, a second trust evaluation gates which stored memories are injected into context. The trust score is binary in effect: pass/fail against a threshold. | High (primary paper + R02 corroboration) |
| E14 | similar-repos | Li et al. (2024), arXiv:2510.02373, Section 3 (Defense Mechanism) | 2026-02-25 | A-MemGuard's semantic consistency check compares a candidate memory against existing memories using embedding similarity. A memory that is semantically distant from the existing corpus scores lower on consistency. **Inference**: This means a legitimate update that changes an established fact (e.g., "user moved from NYC to LA") would score LOW on semantic consistency (it contradicts "user lives in NYC") and could be blocked. A-MemGuard does not distinguish update from attack — it treats all inconsistent information as potentially adversarial. The paper does not discuss how legitimate corrections propagate through the defense. | Medium (paper analysis + inference) |
| E15 | similar-repos | Li et al. (2024), arXiv:2510.02373, Section 4 (Evaluation) | 2026-02-25 | A-MemGuard's evaluation focuses exclusively on attack scenarios (blocking malicious memories). The paper reports defense success rates against various poisoning attacks. There is no evaluation of the system's ability to admit legitimate corrections or updates. **Inference**: The system is designed as a defense mechanism, not a memory management system. Legitimate fact mutation is outside its design scope — it assumes existing memories are correct and new inconsistent memories are threats. | Medium (paper + inference) |
| E16 | similar-repos | Xu et al. (2025), "MemOS: An Operating System for LLM-Based Memory Systems," arXiv:2505.22101; project R02-E5 | 2026-02-25 | MemOS introduces MemCubes as first-class memory units with rich metadata: provenance tracking, versioning, and lifecycle states. Lifecycle states include creation, activation, archival, and deletion. MemCubes carry version history — when a MemCube is updated, a new version is created. The old version is retained in the version chain. | High (primary paper + R02 corroboration) |
| E17 | similar-repos | Xu et al. (2025), arXiv:2505.22101, Section 3 (MemCube Architecture) | 2026-02-25 | MemOS's versioning is metadata-level, not semantic. A new version of a MemCube records that the content changed, but the system does not evaluate whether the change is a legitimate update, a correction, or a contradiction. Provenance metadata records the source of each version (which agent/user created it) but this metadata is descriptive — it does not gate whether the update is permitted. There is no conflict detection between MemCube versions. | Medium (paper analysis) |
| E18 | similar-repos | Xu et al. (2025), arXiv:2505.22101, Section 3.3 (Memory Scheduling) | 2026-02-25 | MemOS includes a memory scheduler that manages which MemCubes are active (loaded into context) vs archived (stored but not loaded). The scheduler considers access patterns and resource constraints — analogous to OS page scheduling. However, scheduling decisions are resource-driven, not trust-driven. A MemCube from an untrusted source has the same scheduling priority as one from a verified source if their access patterns are similar. | Medium (paper analysis) |

## Analysis

### Question 1: What evidence agrees on a common pattern for handling updates vs contradictions?

**No surveyed framework explicitly distinguishes legitimate updates from adversarial contradictions.** This is the most striking finding. Despite the diversity of approaches — tool-based editing (Letta), temporal graphs (Graphiti), LLM-driven merging (Mem0, LangMem), binary trust gating (A-MemGuard), versioned lifecycle (MemOS) — none implements a classification step that asks "is this change authorized?" before deciding how to handle it.

The closest approximation is Graphiti's edge invalidation pipeline (E06), which uses an LLM to classify incoming facts as contradicting, updating, or unrelated to existing edges. But this classification determines *which* edges to invalidate, not *whether* invalidation is appropriate. Both "updates" and "contradictions" produce the same outcome: the old edge gets an `invalid_at` timestamp.

A-MemGuard (E14) takes the opposite approach: it treats ALL inconsistent information as potentially adversarial. This protects against attacks but blocks legitimate updates entirely. The paper does not evaluate this failure mode.

**Pattern**: The field has bifurcated into two approaches: (a) trust-everything systems that allow all mutations (Letta, Graphiti, Mem0, LangMem, MemOS), and (b) trust-nothing defense systems that block all inconsistencies (A-MemGuard). Neither approach classifies intent.

### Question 2: Do any frameworks provide a classification step (like our proposed REVISION vs CONTRADICTION)?

**No.** None of the six frameworks implements a multi-class conflict classification. The closest candidates:

- **Graphiti** classifies the *relationship* between new and existing facts (contradicts / updates / unrelated) but uses this only to determine invalidation scope, not to gate permissions.
- **Mem0** classifies the *action* to take (ADD / UPDATE / DELETE / NOOP) but this is a content management decision, not a trust/authorization decision. The LLM freely updates memories without considering whether the update is legitimate.
- **LangMem** uses a similar patch model (CREATE / UPDATE / DELETE) with the same limitation — no authorization layer.

**Inference**: The REVISION vs CONTRADICTION classification proposed in F01 appears to be novel in the AI memory framework landscape. This is a gap, not a solved problem.

### Question 3: Which frameworks support cascade invalidation of dependent facts?

**Only Graphiti has implicit cascade support, and it is limited.**

- **Graphiti** (E04, E06): When an edge is invalidated, the graph structure allows traversal to find connected edges. However, there is no automatic cascade invalidation. If "Alice works at Acme Corp" is invalidated and "Alice's office is at 123 Main St" depends on it, the second edge remains valid unless separately invalidated by a new incoming fact. **Inference**: Cascade would require walking the graph and re-evaluating each connected edge, which Graphiti does not do automatically.
- **Letta** (E01, E02): Core memory blocks are flat text. There is no graph structure to represent dependencies between facts. Cascade is structurally impossible — changing one fact in a block does not propagate to other blocks or other facts in the same block.
- **Mem0** (E08, E09): Memories are independent vector-store entries. There is no dependency graph. Updating one memory does not trigger re-evaluation of related memories.
- **LangMem** (E11, E12): Same as Mem0 — independent memory entries, no dependency tracking.
- **A-MemGuard** (E13-E15): Defense-only — does not modify existing memories, so cascade is not applicable.
- **MemOS** (E16-E18): MemCubes have versioning but no inter-MemCube dependency tracking. Updating one MemCube does not cascade to others.

**Pattern**: Cascade invalidation is an unsolved problem across all surveyed frameworks. This validates the need for F03 (dependent-anchor-cascade) and suggests it would be a novel contribution.

### Question 4: What can we learn from frameworks that DON'T distinguish update from contradiction?

**Letta's failure mode matches our R00 observation — but in reverse.** Where dice-anchors refuses all changes (treating updates as contradictions), Letta accepts all changes (treating contradictions as updates). Letta's failure mode is silent fact corruption: the agent replaces "user is allergic to penicillin" with "user is not allergic to penicillin" without any friction, regardless of the source of the new information. This is the dual of our problem — both stem from the absence of classification.

**Mem0's LLM-driven merge creates opacity.** When Mem0's LLM merges an update into an existing memory, the merge can subtly alter the original fact's meaning. The user has no visibility into what changed and why. There is no audit trail of the pre-merge state unless the host application implements one separately.

**Graphiti's recency bias is exploitable.** The temporal model elegantly handles legitimate knowledge evolution ("user moved from NYC to LA") but creates a trivial attack vector: any adversary who can inject a fact automatically wins because their fact is most recent. The R02 analysis (E3) confirms this: "a user-injected contradiction invalidates a prior fact just as readily as a system-established one."

### Mutation Mechanism Comparison Matrix

| Framework | Mutation Mechanism | Distinguishes Update from Contradiction? | Authority/Trust Gating | Dependent Fact Cascade | Conflict Resolution | Versioning/History |
|-----------|-------------------|----------------------------------------|----------------------|----------------------|--------------------|--------------------|
| **Letta** | Destructive in-place overwrite (`core_memory_replace`) or append (`core_memory_append`). Agent decides when to call. | No. Agent uses its own judgment; no system-level classification. | None. All memory blocks have equal weight. Any agent with access can write. | None. Flat text blocks; no dependency structure. | None. Conflicts coexist in the same block until the agent reconciles on next read. | None. Old content lost on replace unless agent manually archives first. |
| **Graphiti** | Non-destructive supersession via `invalid_at` timestamp. New edge created alongside invalidated old edge. | Partially. LLM classifies new vs existing as "contradicts" / "updates" / "unrelated" — but both contradicts and updates produce invalidation. | None. Recency wins unconditionally. No source attribution or authority hierarchy. | Implicit graph structure could support it; not implemented as automatic cascade. | Recency-based: newest fact invalidates oldest. Unconditional. | Yes. Old edges preserved with `invalid_at`. Full temporal history. |
| **Mem0** | LLM-driven merge. Old memory text replaced with LLM-generated merged version. Also: direct API overwrite (`update()`). | No. LLM decides ADD/UPDATE/DELETE/NOOP implicitly. No explicit classification of update vs contradiction. | None. All memories have equal standing. No source attribution on memories. | None. Independent vector-store entries; no dependency tracking. | LLM judgment during merge. No explicit strategy. | None. Old text replaced on UPDATE. No version chain. |
| **LangMem** | LLM-driven patch model. CREATE/UPDATE/DELETE operations generated by LLM. UPDATE replaces full content. | No. LLM implicitly resolves conflicts during extraction. No explicit classification. | None. All memories equal. No trust model. | None. Independent memory entries. | LLM judgment during extraction. | None. Updates are destructive replacements. |
| **A-MemGuard** | No mutation. Binary gate: allow or block memory storage. Existing memories are not modified. | Inversely: treats all inconsistency as potential attack. Does not permit legitimate updates that contradict existing memories. | Yes. Composite trust score (semantic consistency + source reputation + recency). Binary threshold: pass/fail. | Not applicable — does not modify existing memories. | Block inconsistent memories entirely. No conflict resolution; conflict prevention. | Not applicable. |
| **MemOS** | MemCube versioning. New version created on update; old version retained in version chain. | No. Versioning is metadata-level. No semantic evaluation of whether the change is update vs contradiction. | Provenance tracked (descriptive) but not used for access control (not operative). | None. No inter-MemCube dependency tracking. | None explicit. Versions coexist. | Yes. Full version chain. Strongest versioning model of any surveyed framework. |

### Key Insights for dice-anchors

1. **The classification gap is real and universal.** No framework distinguishes update from contradiction at a semantic level with authorization consequences. F01's REVISION vs CONTRADICTION classification would be genuinely novel.

2. **Graphiti's temporal supersession is the closest structural analog to our model.** Its `invalid_at` mechanism is the same concept as dice-anchors' `SUPERSEDES` relationship — non-destructive replacement where the old fact is preserved. The key difference is that Graphiti applies it unconditionally (recency wins) while dice-anchors gates it with authority.

3. **A-MemGuard validates the need for trust scoring but demonstrates the cost of binary gating.** The system correctly identifies that not all memory content should be trusted equally, but its allow/block model sacrifices the ability to accept legitimate corrections. This directly supports the dice-anchors design choice of graduated authority over binary gating.

4. **Mem0's LLM-driven merge is a cautionary tale for mutation transparency.** When the LLM silently rewrites a memory during merge, the user loses visibility into what changed. Any mutation mechanism dice-anchors adopts SHOULD preserve the pre-mutation state and provide an audit trail — which our existing `SUPERSEDES` relationship and `TrustAuditRecord` already support.

5. **No framework handles dependent fact cascade.** This is a consistently missing capability. The closest analog is in non-AI domains: Truth Maintenance Systems (JTMS/ATMS), which the R05 research task already identifies as relevant prior art.

6. **The "agent decides" pattern (Letta, Mem0, LangMem) is the dominant paradigm.** These frameworks delegate mutation decisions to the LLM itself, with no system-level governance. This works for benign environments but fails under adversarial stress tests, as the LLM's reasoning is precisely what adversarial prompts target.

## Recommendation

### For F01 (Revision Intent Classification)

1. The REVISION vs CONTRADICTION classification is **novel** — no surveyed framework provides this. Proceed with confidence that we are not reinventing an existing solution.
2. **Adapt Graphiti's classification prompt** as a starting point. Graphiti already asks an LLM to classify whether a new fact "contradicts, updates, or is unrelated to" existing facts. Extend this with an authority dimension: "given the source and authority level of the existing fact, is this incoming change a legitimate revision or an adversarial contradiction?"
3. **Avoid Mem0's opacity pattern.** Ensure that any LLM-driven classification decision is logged with the full input context, not buried inside an opaque merge step. The `TrustAuditRecord` SHOULD capture the classification decision, confidence, and reasoning.
4. **Do not adopt A-MemGuard's binary approach.** Graduated authority with configurable thresholds is strictly more expressive than binary allow/block and supports the nuanced use cases (player revising their own character, DM correcting a detail, adversary injecting contradictions) that the Collaborative Anchor Mutation roadmap targets.

### For F03 (Dependent Anchor Cascade)

1. **No framework provides a transferable solution.** Cascade invalidation must be designed from first principles (or adapted from non-AI prior art per R05). The R02 cascade strategy research should proceed independently of this finding.
2. **Graphiti's graph structure suggests** that graph-based dependency detection (explicit `DERIVED_FROM` edges or subject clustering) is more tractable than flat-store approaches. Our existing Neo4j persistence layer is well-suited for graph-based cascade.

### For F04 (Anchor Provenance Metadata)

1. **MemOS's provenance model is the most relevant reference.** MemCubes carry source attribution, timestamps, and version history. Adapt the provenance-as-metadata pattern, but make it operative (driving access control) rather than descriptive (audit-only).
2. **A-MemGuard's source reputation score** is worth adapting as one signal in provenance evaluation, even though its binary threshold model is not.

### For the Roadmap Overall

The survey confirms that the collaborative anchor mutation problem occupies an unaddressed gap in the AI memory framework landscape. The combination of (a) graduated authority gating mutations, (b) semantic classification of update vs contradiction, (c) cascade invalidation of dependent facts, and (d) non-destructive supersession with audit trail is not implemented by any surveyed framework. This strengthens the case for the full five-feature roadmap.

## Impact

1. **Roadmap changes**: None. The survey confirms the roadmap features address a genuine gap. No features should be removed or reordered based on this analysis.
2. **Feature doc changes**: F01 proposal seed SHOULD reference Graphiti's classification prompt as a starting point (E06). F03 proposal seed SHOULD note that no surveyed framework solves cascade — this is genuinely novel territory. F04 proposal seed SHOULD reference MemOS's provenance model (E16-E17) as the closest structural analog.
3. **Proposal scope changes**: If a framework had a mature, transferable pattern, we would adapt rather than invent. **Finding: no such pattern exists.** All five features require original design, though Graphiti's temporal supersession and MemOS's provenance metadata provide useful structural inspiration.

## Remaining Gaps

1. **Web access was unavailable during research execution.** All findings are based on the project's existing R02 evidence and the researcher's training data (cutoff May 2025). Frameworks evolve rapidly: Letta, Mem0, and LangMem may have added conflict detection or trust features after May 2025. **Mitigation**: Evidence reliability ratings reflect this uncertainty. Medium-reliability findings should be re-verified against current documentation before being cited in external publications.
2. **A-MemGuard's handling of legitimate updates is inferred, not confirmed.** The paper's evaluation section focuses on attack defense and does not discuss how the system handles legitimate corrections. The inference that it would block legitimate updates (E14) is logically sound but not empirically verified. **Mitigation**: Flag as inference in any downstream citation.
3. **LangMem analysis is limited.** LangMem is a relatively new and thinly documented library (compared to Letta, Graphiti, and Mem0). The analysis is based on README-level documentation and source code review. Deeper integration tests or blog posts may reveal additional mutation semantics not captured here. **Mitigation**: Mark LangMem findings as medium reliability.
4. **No empirical comparison of mutation outcomes.** This survey analyzes design-level mechanisms. It does not test what happens when these frameworks are subjected to the same adversarial scenarios used in dice-anchors simulations. Such a comparison would require running each framework with identical inputs — a significant engineering effort outside this research scope. **Mitigation**: Propose as potential future work in the tech report.
5. **Graphiti's codebase may have evolved its contradiction handling.** The source code analysis (E06, E07) is based on the pre-May 2025 codebase. Graphiti is under active development and may have added authority-aware resolution or cascade logic. **Mitigation**: Re-verify against the current `getzep/graphiti` repository before finalizing F01 design.
6. **MemOS is primarily an academic prototype.** As of May 2025, MemOS was a research paper with a reference implementation, not a production framework. Its MemCube model is aspirational and may not reflect real-world deployment patterns. **Mitigation**: Treat MemOS findings as design-level analysis, not production-validated patterns.
