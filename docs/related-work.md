# Related Work

<!-- sync: openspec/specs/dice-integration-review-docs -->
<!-- last-synced: 2026-02-25 -->

How Anchors relate to prior work on LLM memory, grounding, and robustness.

---

## 1. Positioning Statement

Anchors are a **bounded working-memory governance layer** for multi-turn LLM conversations. They maintain a capped pool of ranked, authority-tiered propositions injected into system context, with explicit conflict resolution, trust-gated promotion, and budget enforcement.

Anchors are **not** a full memory management system. They do not replace long-term memory stores, retrieval pipelines, or graph-based knowledge systems. They are a control layer designed to preserve invariants under long-horizon context pressure, and they coexist with paging, graph memory, and retrieval systems as complementary infrastructure.

The goal is **policy-controlled attention shaping** — keeping critical facts in active model context when conversation length or context competition would otherwise cause them to be lost or contradicted. Adversarial turns are used as stress tests for this objective.

---

## 2. Comparison Table

| Feature                                 | Anchors                                | MemGPT/Letta          | Zep/Graphiti          | ShardMemo     | HippoRAG          | ACON               | Standard RAG     | Summarization   |
|-----------------------------------------|----------------------------------------|-----------------------|-----------------------|---------------|-------------------|--------------------|------------------|-----------------|
| Explicit fact state with lifecycle      | Yes                                    | Yes (memory blocks)   | Partial (temporal KG) | No            | No                | Partial            | No               | No              |
| Rank-ordered retention                  | Yes                                    | No                    | No                    | No            | No                | No                 | Relevance-scored | No              |
| Authority tiers for conflict resolution | Yes                                    | No                    | No                    | No            | No                | Partial (priority) | No               | No              |
| Trust-gated promotion                   | Yes                                    | No                    | No                    | No            | No                | No                 | No               | No              |
| Hard budget cap on active memory        | Yes (20 anchors)                       | Yes (context window)  | No                    | Shard-bounded | No                | No                 | Top-K            | Window-bounded  |
| Conflict detection and resolution       | Yes (lexical + LLM)                    | No                    | No                    | No            | No                | Yes                | No               | No              |
| Adversarial simulation harness          | Yes                                    | No                    | No                    | No            | No                | No                 | No               | No              |
| Long-term memory tiering                | No (working memory only)               | Yes (archival/recall) | Yes (temporal graph)  | Yes (shards)  | Yes (hippocampal) | No                 | Yes (index)      | Yes (summaries) |
| Graph-native retrieval                  | No (Neo4j storage, no graph retrieval) | No                    | Yes                   | No            | Yes               | No                 | Optional         | No              |
| Cross-session persistence               | Limited (per-session contexts)         | Yes                   | Yes                   | Yes           | Yes               | No                 | Yes              | Yes             |

---

## 3. Key Related Systems

### MemGPT / Letta

OS-style memory paging with explicit context hierarchy and memory blocks [1][2]. Letta structures memory as blocks with archival and recall tiers.

**Position relative to Anchors**: Overlapping goals around explicit memory management. Anchors center trust/authority control over proposition updates rather than paging semantics. The two approaches are composable — Anchors could serve as a policy layer within a MemGPT-style memory stack.

### Zep / Graphiti

Temporal knowledge graph memory with entity/relationship tracking over time [3][4].

**Position relative to Anchors**: Complementary. Anchors target bounded working memory with governance; Zep/Graphiti target long-range temporal recall. A combined architecture would use graph memory for evidence retrieval and Anchors for active context governance.

### ShardMemo

Sharded retrieval for long-horizon tasks, distributing memory across retrievable shards [5].

**Position relative to Anchors**: Anchors do not use sharded retrieval. Sharding is compatible as an upstream memory layer feeding the anchor promotion pipeline.

### HippoRAG

Hippocampal-inspired retrieval augmented generation using neurobiological memory principles [6].

**Position relative to Anchors**: Different abstraction level. HippoRAG focuses on retrieval architecture; Anchors focus on post-retrieval control of what stays in active context.

### ACON (Agentic Constraint Networks)

Priority-based constraint management for multi-agent systems with conflict handling.

**Position relative to Anchors**: Closest in intent. ACON uses priority-based conflict handling; Anchors use trust-gated promotion and authority-tiered conflict resolution instead. Anchors also include an adversarial simulation harness, which ACON doesn't currently ship.

### Standard RAG

Retrieval-augmented generation combining parametric and external memory [7].

**Position relative to Anchors**: RAG retrieves relevant context per query. Anchors maintain persistent control over what facts remain in context across turns. RAG is an evidence source; Anchors are a retention policy.

### Core Memory / Pinned Summary Patterns

Pinned memory blocks used in production agents (e.g., LlamaIndex summary memory buffer) [2][8].

**Position relative to Anchors**: Anchors add conflict detection, authority tiers, and rank-based eviction on top of the pinning concept. Pinned summaries don't currently address what happens when pinned facts conflict with each other or with new information.

---

## 4. Gaps Against Existing Solutions

Gaps identified from external technical assessment, with evidence from the current implementation.

### Gap A: Memory tiering remains shallow

Anchors include rank-based tiering (`HOT/WARM/COLD`) and decay/reinforcement, but lack explicit transfer policies between working memory and longer-term stores. MemGPT/Letta has more developed movement semantics across tiers [2]. DICE's proposition-to-graph promotion direction is compatible but not yet implemented as a tier transfer mechanism.

### Gap B: No graph-native retrieval/summarization loop

Neo4j is used for storage, but there is no GraphRAG-style entity/relationship/community summary retrieval [9]. This is a natural extension given the existing graph infrastructure. Missing: graph index, community summaries, local + global retrieval modes.

### Gap C: Weak retrieval quality control

No retrieval evaluator equivalent to CRAG-style correctness checks [10] or Self-RAG reflection tokens [11]. Trust signals use fragile proxies (string-contains source authority at `TrustSignal` lines 141-145). ToolishRag [12] could provide retrieval orchestration, but a separate quality gate is still needed for promotion decisions.

### Gap D: Limited benchmark coverage for memory quality

Simulation harness is domain-specific (tabletop-narrative concentrated). No integration with standardized long-context benchmarks like LongBench [13] or LoCoMo [14]. External transfer to non-narrative domains remains unproven.

### Gap E: Prompt-only guardrails insufficient against prompt injection

OWASP guidance recommends layered controls beyond instruction text [15]. Current "MUST NOT contradict" prompt framing is necessary but not sufficient. Missing: defense-in-depth around memory writes, explicit trusted/untrusted boundary for extracted claims.

### Gap F: Memory poisoning threat model underdeveloped

PoisonedRAG [16] demonstrates adversarial corruption in retrieval systems. Anchors are vulnerable to analogous poisoning via the promotion pipeline. Missing: source trust weighting tied to verifiable provenance, memory write quarantine for low-trust claims, dedicated poisoning scenarios in simulation harness.

---

## 5. References

| #  | Reference                                                                               | URL                                                                                                              |
|----|-----------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| 1  | Packer et al., "MemGPT: Towards LLMs as Operating Systems" (2023)                       | https://arxiv.org/abs/2310.08560                                                                                 |
| 2  | Letta documentation -- memory blocks, context hierarchy                                 | https://docs.letta.com/guides/agents/memory-blocks                                                               |
| 3  | Zep temporal KG memory paper                                                            | https://arxiv.org/abs/2501.13956                                                                                 |
| 4  | Graphiti repository                                                                     | https://github.com/getzep/graphiti                                                                               |
| 5  | ShardMemo paper                                                                         | https://arxiv.org/abs/2601.21545                                                                                 |
| 6  | Gutierrez et al., "HippoRAG" (2024)                                                     | https://arxiv.org/abs/2405.14831                                                                                 |
| 7  | Lewis et al., "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks" (2020) | https://arxiv.org/abs/2005.11401                                                                                 |
| 8  | LlamaIndex summary memory buffer                                                        | https://docs.llamaindex.ai/en/stable/examples/memory/chatsummarymemorybuffer/                                    |
| 9  | Microsoft GraphRAG                                                                      | https://microsoft.github.io/graphrag/                                                                            |
| 10 | Yan et al., "Corrective Retrieval Augmented Generation" (2024)                          | https://arxiv.org/abs/2401.15884                                                                                 |
| 11 | Asai et al., "Self-RAG" (2023/2024)                                                     | https://arxiv.org/abs/2310.11511                                                                                 |
| 12 | Embabel ToolishRag documentation                                                        | https://docs.embabel.com/embabel-agent/guide/0.3.3-SNAPSHOT/                                                     |
| 13 | Bai et al., "LongBench" (2023)                                                          | https://arxiv.org/abs/2308.14508                                                                                 |
| 14 | Maharana et al., "LoCoMo" (2024)                                                        | https://arxiv.org/abs/2402.17753                                                                                 |
| 15 | OWASP LLM Prompt Injection Prevention Cheat Sheet                                       | https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html                  |
| 16 | Tang et al., "PoisonedRAG" (2024)                                                       | https://arxiv.org/abs/2402.07867                                                                                 |
| 17 | Johnson, "Agent Memory Is Not A Greenfield Problem" (Embabel, 2026)                     | https://medium.com/embabel/agent-memory-is-not-a-greenfield-problem-ground-it-in-your-existing-data-9272cabe1561 |
| 18 | Liu et al., "Lost in the Middle" (2023/2024)                                            | https://arxiv.org/abs/2307.03172                                                                                 |
| 19 | Park et al., "Generative Agents" (2023)                                                 | https://arxiv.org/abs/2304.03442                                                                                 |
| 20 | LongMemEval benchmark (2024)                                                            | https://arxiv.org/abs/2410.10813                                                                                 |
| 21 | Wu et al., "Recursive Summarization" (2023)                                             | https://arxiv.org/abs/2308.15022                                                                                 |
| 22 | Laban et al., "LLMs Get Lost In Multi-Turn Conversation" (2025)                         | https://arxiv.org/abs/2505.06120                                                                                 |
