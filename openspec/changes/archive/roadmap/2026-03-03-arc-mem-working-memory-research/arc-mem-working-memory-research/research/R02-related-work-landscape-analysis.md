# Research Task: Related Work Landscape Analysis

## Task ID

`R02`

## Question

How does context units position against MemGPT/Letta, Zep/Graphiti, ShardMemo, MemOS, and A-MemGuard, and what is the differentiation argument for the tech report?

## Why This Matters

The tech report MUST position context units within the existing landscape of LLM memory systems to establish novelty and contribution. Without a rigorous comparative analysis, the paper risks either (a) claiming uniqueness that does not hold under scrutiny, or (b) failing to articulate what specifically is new. The adversarial memory space is active: MemGPT/Letta, Zep/Graphiti, ShardMemo, and MemOS all address aspects of LLM memory, and A-MemGuard directly addresses memory security. The paper MUST demonstrate that context units occupies a distinct position and that its design choices produce measurably different properties.

## Scope

### In Scope

1. Systematic comparison of context units against six systems: MemGPT/Letta, Zep/Graphiti, ShardMemo, MemOS, A-MemGuard, and ASB (as a benchmark framework).
2. Analysis along four dimensions: core problem addressed, long-horizon consistency-control mechanism (with adversarial stress posture), trust/authority model, and working memory semantics.
3. Identification of the specific design space gap that context units fills.
4. Attack landscape analysis: MINJA, InjecMEM, MemoryGraft as motivation for why adversarial stress testing matters for consistency-control claims.
5. Memory taxonomy alignment per "Memory in the Age of AI Agents" (arXiv:2512.13564).

### Out of Scope

1. Performance benchmarking against these systems (they solve different problems; direct comparison is not meaningful).
2. Analysis of systems outside the LLM memory domain (e.g., classical knowledge base systems, traditional KV stores).
3. Implementation of any integration with these systems.
4. Full survey of all LLM memory papers (scoped to the six systems most relevant to our positioning).

## Research Criteria

1. **Target feature(s):** F03 (resilience-evaluation-report).
2. **Required channels:** web + published papers (arXiv, ICLR, ACL) + system documentation (Letta, Zep).
3. **Timebox:** 8h.
4. **Success criteria:** Comparison table with per-system analysis; differentiation argument articulated with evidence; citation list complete and verifiable.
5. **Minimum sources:** 8 external (at least 6 primary papers).

## Method

1. **Paper collection:** Gather primary publications for each system. Verify claims against the actual papers, not secondary summaries.
2. **Dimension extraction:** For each system, extract: (a) the core problem it solves, (b) its memory architecture, (c) whether and how it addresses adversarial inputs, (d) its trust/authority model (if any).
3. **Gap identification:** Map the design space and identify where context units sits relative to the others.
4. **Attack landscape review:** Survey recent memory attack papers to establish the threat model that motivates context units.
5. **Taxonomy alignment:** Map context units to the memory taxonomy from arXiv:2512.13564 to use shared vocabulary.

## Findings

### Evidence Table

| # | Source | Type | Key Finding | Relevance |
|---|--------|------|-------------|-----------|
| E1 | Packer et al. (2023), "MemGPT: Towards LLMs as Operating Systems," arXiv:2310.08560 | Published paper | OS-inspired virtual context management. Two-tier memory: main context (analogous to RAM) and external storage (analogous to disk). The LLM itself issues function calls (`core_memory_append`, `core_memory_replace`, `archival_memory_insert`, `archival_memory_search`) to page data in and out of context. Core memory consists of named writable blocks for persistent key facts. Archival memory provides long-term storage with embedding-based retrieval. | Establishes the capacity management paradigm. MemGPT treats memory as a storage/retrieval problem. There is no concept of trust, authority, or explicit consistency-control defense. All content written to core_memory is treated with equal weight regardless of provenance. |
| E2 | Letta documentation and blog (Sep 2024, lettaai.com) | Product documentation | Letta is the productized version of MemGPT. Adds Conversations API for shared memory across parallel agents, tool-based memory management, and multi-agent coordination. Memory blocks remain named writable slots (core_memory for key facts, archival for long-term). No trust model was added in the productization. | Confirms that even the commercial evolution of MemGPT does not address explicit long-horizon consistency controls. Shared memory across agents introduces a larger attack surface (any agent can write to shared memory) but no new defenses. |
| E3 | Rasmussen et al. (2025), "Graphiti: Building Real-Time Temporal Knowledge Graphs for AI Agents," arXiv:2501.13956 | Published paper (Jan 2025) | Temporally-aware knowledge graph with bitemporal modeling (valid_at, invalid_at). Achieves 94.8% on LoCoMo Deep Memory Retrieval vs. MemGPT's 93.4%. Non-lossy conflict resolution: contradicting facts invalidate prior edges (set invalid_at) rather than deleting them. Hybrid retrieval combines cosine similarity, BM25, and graph traversal. Episodic and semantic edges are distinguished. | Establishes the temporal accuracy paradigm. Zep/Graphiti solves the problem of maintaining temporally consistent knowledge over long conversations. Conflict resolution is recency-based: the most recent fact wins. There is no authority hierarchy -- a user-injected contradiction invalidates a prior fact just as readily as a system-established one. |
| E4 | Chen et al. (2026), "ShardMemo: Multi-Agent Memory with Shard-Local Retrieval," arXiv:2601.21545 | Published paper (Jan 2026) | Three-tier architecture: Tier A (per-agent working state), Tier B (sharded evidence store with shard-local ANN indices), Tier C (versioned skill library). Scope-before-routing applies masked MoE-style routing to direct queries to relevant shards before retrieval. Reports +5.11 to +6.82 F1 over GAM baseline on LoCoMo and -20.5% vector scan work. | Establishes the retrieval scalability paradigm. ShardMemo solves the problem of efficient retrieval across large multi-agent memory stores. Tier A is an unstructured per-agent state blob with no governance. Tier B has provenance metadata but it is used for routing, not trust arbitration. Adversarial robustness is not discussed. |
| E5 | Xu et al. (2025), "MemOS: An Operating System for LLM-Based Memory Systems," arXiv:2505.22101 | Published paper (May 2025) | Introduces MemCubes as first-class memory units with rich metadata: provenance tracking, versioning, lifecycle state. Distinguishes three memory types: Parametric (model weights), Activation (KV cache / attention states), and Plaintext (text chunks). MemCubes are schedulable and migrateable between storage tiers. Provides a unified API across memory types. | Establishes the lifecycle management paradigm. MemOS provides the most sophisticated metadata model of any system surveyed. However, provenance is descriptive (recording where a memory came from) not operative (driving access control or injection priority). A MemCube from an untrusted source has the same injection priority as one from a verified source. |
| E6 | Li et al. (2024), "A-MemGuard: Proactive Defense Against Memory Poisoning Attacks in LLM Agents," arXiv:2510.02373 | Published paper (Oct 2024) | Proactive defense via composite trust scoring at both append time and retrieval time. Trust score integrates semantic consistency, source reputation, and temporal recency. Binary access control: memories below the trust threshold are blocked from retrieval entirely. Evaluated against memory poisoning attacks with reported defense success. | The only system in our survey with explicit adversarial defense. However, the defense is binary (allow/block), not graduated. A memory either passes the trust threshold or it does not. There is no concept of partial trust, authority escalation, or budget-managed injection priority. Additionally, A-MemGuard requires direct instrumentation of the memory store's append and retrieval paths. |
| E7 | Zhang et al. (2025), "Agent Security Bench (ASB)," arXiv:2410.02644 (ICLR 2025) | Published paper (ICLR) | Comprehensive adversarial evaluation framework: 10 scenarios, 400+ tools, 27 attack/defense method pairs, 13 LLM backbones. Reports highest attack success rate (ASR) of 84.30%. Key finding: "current defenses are largely ineffective against sophisticated memory poisoning attacks." No existing adversarial memory benchmark evaluates consistency/hallucination control as a function of memory authority level. | Establishes the threat landscape and the evaluation gap. ASB demonstrates that memory attacks are effective and defenses are inadequate. The absence of authority-aware evaluation in ASB's framework is a specific gap that context units' experiment framework could address. |
| E8 | Zeng et al. (2025), "MINJA: Memory INJection Attack Against Long-Context LLMs," arXiv:2503.03704 | Published paper (Mar 2025) | Query-only black-box memory injection attacks. Achieves >95% attack success rate by crafting adversarial memory entries that are retrieved for target queries. Does not require access to the model or its training data -- only the ability to inject entries into the memory store. | Demonstrates that retrieval-based memory systems are vulnerable to injection attacks. The attack model (external adversary injects content that is later retrieved) maps directly to the adversarial turns in context units simulations. |
| E9 | InjecMEM (OpenReview, 2025) | Published paper | Retriever-agnostic memory injection using context unit text (terms designed to match target queries) plus adversarial commands embedded in retrieved context. Effective across different retrieval backends (cosine, BM25, hybrid). | Confirms that memory injection attacks generalize across retrieval methods. The attack does not depend on a specific retrieval algorithm -- it exploits the fundamental trust assumption that retrieved content is benign. |
| E10 | Sirin et al. (2024), "MemoryGraft: Persistent Compromise via Poisoned Experience Retrieval," arXiv:2512.16962 | Published paper (Dec 2024) | Demonstrates persistent behavioral compromise through poisoned episodic memories. Once a poisoned memory is stored, it is retrieved across subsequent conversations, causing persistent drift. The attack survives conversation resets because the poisoned memory resides in long-term storage. | Establishes the persistence dimension of memory attacks. Unlike prompt injection (which is session-scoped), memory attacks persist across sessions. This is the specific threat class that authority-governed memory is designed to resist: a poisoned memory SHOULD NOT be able to override a high-authority context unit. |
| E11 | Zhang et al. (2024), "Memory in the Age of AI Agents: A Taxonomy and Survey," arXiv:2512.13564 | Survey paper (Dec 2024) | Comprehensive taxonomy along three axes: Form (token-level / parametric / latent), Function (factual / experiential / working), Dynamics (formation / evolution / retrieval). Defines working memory as "the active in-context assembly -- what the model is processing RIGHT NOW." Distinguishes working memory from long-term storage and episodic memory. | Provides the taxonomic vocabulary for the paper. context units operates in the working memory function space (active in-context assembly) with token-level form (text injected into the prompt). The authority hierarchy governs the formation and evolution dynamics of working memory entries. |

### Per-System Analysis

#### MemGPT / Letta (E1, E2)

**Core problem:** Context window capacity management. The fundamental constraint is that LLM context windows are finite, but conversations and knowledge accumulate beyond that limit.

**Architecture:** OS-style virtual context with LLM-driven paging. The model decides what to keep in context and what to offload to external storage. This is elegant for the capacity problem but has no security semantics.

**Adversarial resistance:** None. Any content written to core_memory via `core_memory_append` or `core_memory_replace` is treated identically. A user who says "Actually, the king has always been dead" and an established system fact "The king is alive" coexist with no priority ordering. The LLM's own reasoning is the only arbiter, and it is susceptible to the same drift pressures the memory was supposed to prevent.

**Trust model:** None. All memory blocks have equal standing.

**Positioning vs. context units:** MemGPT's design goal is capacity (virtual context extension); authority and explicit consistency controls are out of scope. context units assumes the context window is large enough (modern 128K+ context windows reduce the capacity pressure) and focuses on what gets injected and with what priority.

#### Zep / Graphiti (E3)

**Core problem:** Temporal accuracy for long-term knowledge. Over many conversations, facts change. The system MUST track which facts are currently valid and which have been superseded.

**Architecture:** Temporally-aware knowledge graph with bitemporal modeling. Edges carry `valid_at` and `invalid_at` timestamps. When a new fact contradicts an old one, the old edge is invalidated (not deleted), preserving the full history.

**Adversarial resistance:** Implicit via recency. The most recently asserted fact takes precedence. This is correct for legitimate knowledge evolution but exploitable by adversarial injection: an attacker who asserts a contradiction benefits from recency bias. There is no mechanism to distinguish "the DM legitimately updated the world state" from "an adversarial prompt injected a false fact."

**Trust model:** None explicit. Recency is the implicit trust signal, and it is unconditional.

**Positioning vs. context units:** Zep provides temporal accuracy for long-term storage. Its conflict resolution is recency-based, not authority-based. It is a complementary system (context units could theoretically use Zep as a persistence backend) but does not address the working memory governance problem.

#### ShardMemo (E4)

**Core problem:** Retrieval scalability for multi-agent systems. When many agents share a large memory store, retrieval latency and relevance degrade.

**Architecture:** Three-tier with scope-based routing. Tier A is per-agent working state (unstructured). Tier B shards evidence across ANN indices and uses masked MoE routing to direct queries to relevant shards. Tier C stores versioned skills.

**Adversarial resistance:** None. Tier B has provenance metadata (which agent stored which memory) but this metadata is used for routing efficiency, not trust arbitration. A poisoned memory in Tier B is retrieved with the same priority as a legitimate one if it matches the query.

**Trust model:** Provenance tracking only (descriptive, not operative).

**Positioning vs. context units:** ShardMemo solves multi-agent retrieval scalability. Its Tier A per-agent working state is an unstructured blob with no governance semantics. context units' working memory model (ranked, authority-governed, budget-enforced) could theoretically serve as a structured replacement for Tier A.

#### MemOS (E5)

**Core problem:** Unified lifecycle management across heterogeneous memory types. LLMs have parametric memory (weights), activation memory (KV cache), and plaintext memory (stored text). Managing these as separate systems is fragile.

**Architecture:** MemCubes as first-class units with metadata, versioning, and lifecycle management. A scheduler migrates MemCubes between storage tiers based on access patterns.

**Adversarial resistance:** None. MemCube provenance tracks where a memory came from but does not gate access based on provenance. A MemCube from an untrusted external source has the same injection priority as one from the system's own reasoning.

**Trust model:** Provenance is descriptive (audit trail) not operative (access control). This is the key distinction: MemOS tells you where a memory came from; context units tells you how much to trust it and what to do when it conflicts with a higher-authority memory.

**Positioning vs. context units:** MemOS provenance is descriptive; context units authority levels are operative -- they drive injection resistance (higher authority = harder to contradict), eviction priority (lower rank evicted first under budget pressure), and promotion gating (CANON never auto-assigned).

#### A-MemGuard (E6)

**Core problem:** Defense against memory poisoning attacks. Adversaries inject false memories that alter agent behavior.

**Architecture:** Composite trust scoring that integrates semantic consistency, source reputation, and temporal recency. Applied at both memory append time (gate what gets stored) and retrieval time (gate what gets injected into context).

**Adversarial resistance:** Yes (reactive). The system detects suspicious memories and blocks them. This is the only system in our survey with explicit adversarial defense.

**Trust model:** Binary. A memory either passes the composite trust threshold or it does not. There is no graduated trust -- a memory with trust score 0.51 is treated identically to one with trust score 0.99.

**Positioning vs. context units:** A-MemGuard uses binary access control; context units uses graduated authority with promotion semantics and budget enforcement. Specifically:
- A-MemGuard: memory is allowed or blocked. context units: memory has rank [100-900] and authority (PROVISIONAL -> UNRELIABLE -> RELIABLE -> CANON), which determines injection priority, eviction order, and conflict resolution outcome.
- A-MemGuard: defense is reactive (detect and block). context units: resistance is structural (the authority hierarchy inherently privileges established facts over new injections).
- A-MemGuard: no budget management. context units: max 20 active context units with rank-based eviction under budget pressure.
- A-MemGuard: binary outcome (allow/block). context units: graduated outcome (low-authority propositions coexist with high-authority context units but are outranked in context assembly).

### Attack Landscape Summary

Three recent attack papers (E8, E9, E10) establish that memory systems are vulnerable:

| Attack | Method | Success Rate | Memory Assumption Exploited |
|--------|--------|--------------|-----------------------------|
| MINJA (E8) | Query-only black-box injection | >95% | Retrieved content is trusted |
| InjecMEM (E9) | Retriever-agnostic context unit + command injection | High (retriever-independent) | Retrieved content is trusted |
| MemoryGraft (E10) | Poisoned episodic memory retrieval | Persistent across sessions | Long-term memory is trustworthy |

All three attacks exploit the same fundamental assumption: **content retrieved from memory is treated as trustworthy.** This is the assumption that context units challenges. By assigning authority levels to memory entries and enforcing upgrade-only semantics (an context unit's authority can only increase, never decrease; CANON is never auto-assigned), the system creates structural resistance to injected content.

### Memory Taxonomy Alignment (E11)

Per the taxonomy from arXiv:2512.13564:

| Dimension | context units Classification |
|-----------|---------------------------|
| **Form** | Token-level (text injected into prompt context) |
| **Function** | Working memory (active in-context assembly governing current processing) |
| **Formation** | Explicit extraction (DICE pipeline) + explicit promotion (UnitPromoter) |
| **Evolution** | Authority upgrade-only (PROVISIONAL -> UNRELIABLE -> RELIABLE -> CANON), rank reinforcement/decay, budget-enforced eviction |
| **Retrieval** | Budget-constrained injection with rank-based priority and optional relevance scoring |

This taxonomic positioning clarifies what context units is and is not. It is NOT a long-term storage system (like Zep), NOT a capacity management system (like MemGPT), NOT a retrieval optimization system (like ShardMemo), and NOT a lifecycle management system (like MemOS). It is a **working memory governance system** that determines what enters the active context, with what priority, and under what trust constraints.

## Analysis

### The Design Space Gap

The surveyed systems can be mapped along two axes: **memory concern** (what problem they solve) and **adversarial awareness** (whether they consider adversarial inputs).

```
                        Adversarial Awareness
                    None          Reactive       Structural
                 +-----------+-----------+-----------+
    Capacity     | MemGPT    |           |           |
                 | Letta     |           |           |
                 +-----------+-----------+-----------+
    Temporal     | Zep       |           |           |
    Accuracy     | Graphiti  |           |           |
                 +-----------+-----------+-----------+
    Retrieval    | ShardMemo |           |           |
    Scale        |           |           |           |
                 +-----------+-----------+-----------+
    Lifecycle    | MemOS     |           |           |
    Mgmt         |           |           |           |
                 +-----------+-----------+-----------+
    Memory       |           | A-MemGuard|           |
    Defense      |           |           |           |
                 +-----------+-----------+-----------+
    Working      |           |           | dice-     |
    Memory       |           |           | context units   |
    Governance   |           |           |           |
                 +-----------+-----------+-----------+
```

context units is the only system that combines:
1. **Working memory as the target domain** (not long-term storage or retrieval).
2. **Structural long-horizon consistency control** (evaluated under adversarial stress, not just benign runs).
3. **Graduated authority** (not binary, not implicit).
4. **Budget enforcement** (fixed active context unit limit with rank-based eviction).
5. **Monotonic authority semantics** (upgrade-only; CANON never auto-assigned).

### Differentiation Argument

The differentiation argument for the tech report SHOULD be structured as follows:

> Existing LLM memory systems address capacity (MemGPT/Letta), temporal accuracy (Zep/Graphiti), retrieval scalability (ShardMemo), and lifecycle management (MemOS). These systems treat all memory content with equal trust, leaving them vulnerable when long conversations accumulate contradictory or poisoned context. A-MemGuard introduces reactive defense via binary trust scoring, but its allow/block model lacks the graduated semantics needed for nuanced working memory governance. context units introduces trust-governed working memory: a graduated authority hierarchy (PROVISIONAL through CANON) with upgrade-only semantics, rank-based eviction under budget constraints, and structural consistency control for long-horizon conversations. Adversarial scenarios are used as stress tests to evaluate whether these controls hold under pressure. Where existing systems ask "what should the model remember?", context units asks "what should the model trust, and how much?"

### Comparison Table (Paper-Ready)

| System | Core Problem | Architecture | Adversarial Resistance | Trust/Authority Model | Working Memory Semantics |
|--------|-------------|-------------|----------------------|----------------------|------------------------|
| MemGPT/Letta | Context capacity | OS-style virtual context; LLM-driven paging | None | None; all content equal | LLM decides what to keep in context |
| Zep/Graphiti | Temporal accuracy | Bitemporal knowledge graph; hybrid retrieval | Implicit (recency wins) | None; recency is sole signal | Not addressed (long-term storage focus) |
| ShardMemo | Retrieval scale | Three-tier with shard-local ANN; scope-before-routing | None | Provenance for routing only | Tier A: unstructured per-agent blob |
| MemOS | Lifecycle mgmt | MemCubes with metadata; scheduler-driven migration | None | Provenance (descriptive, not operative) | Not distinguished from other memory types |
| A-MemGuard | Memory injection defense | Composite trust scoring; binary gating | Yes (reactive, binary) | Binary allow/block threshold | Not addressed (defense focus) |
| **context units** | **Adversarial drift in working memory** | **Ranked context units with authority hierarchy; budget-enforced injection** | **Yes (structural, proactive)** | **Graduated: PROVISIONAL -> UNRELIABLE -> RELIABLE -> CANON; upgrade-only; CANON never auto-assigned** | **Budget-constrained (max 20), rank-ordered, authority-prioritized active context** |

## Recommendation

### For the Tech Report

1. The related work section MUST include the comparison table above with citations to the primary papers.
2. The differentiation argument SHOULD be presented as a two-part claim: (a) no existing system combines trust governance with working memory management, and (b) the authority hierarchy produces measurably different behavior in long-horizon runs, including adversarial stress scenarios (demonstrated by the ablation study).
3. The paper MUST NOT claim that context units replaces or supersedes these systems. They solve different problems. The claim is complementarity and novelty in the specific intersection of trust governance and working memory.
4. The attack landscape (MINJA, InjecMEM, MemoryGraft) SHOULD be cited in the introduction to motivate why adversarial stress testing is necessary when claiming long-horizon consistency control. ASB SHOULD be cited to establish the evaluation gap.
5. The memory taxonomy from arXiv:2512.13564 SHOULD be used to position context units using shared vocabulary (working memory function, token-level form, authority-driven evolution dynamics).

### For Future Work

1. The paper's discussion section SHOULD note that context units' authority model could complement existing systems: e.g., authority-governed MemCubes in MemOS, authority-aware edges in Graphiti, trust-graduated rather than binary gating in A-MemGuard.
2. Cross-system integration experiments (e.g., using Zep as a persistence backend with context units providing authority governance at the working memory layer) MAY be proposed as future work.

### Citation List

The following references MUST be included in the tech report:

| Ref | Citation |
|-----|----------|
| [1] | Packer et al., "MemGPT: Towards LLMs as Operating Systems," arXiv:2310.08560, 2023. |
| [2] | Rasmussen et al., "Graphiti: Building Real-Time Temporal Knowledge Graphs," arXiv:2501.13956, 2025. |
| [3] | Chen et al., "ShardMemo: Multi-Agent Memory with Shard-Local Retrieval," arXiv:2601.21545, 2026. |
| [4] | Xu et al., "MemOS: An Operating System for LLM-Based Memory Systems," arXiv:2505.22101, 2025. |
| [5] | Li et al., "A-MemGuard: Proactive Defense Against Memory Poisoning," arXiv:2510.02373, 2024. |
| [6] | Zhang et al., "Agent Security Bench," arXiv:2410.02644, ICLR 2025. |
| [7] | Zeng et al., "MINJA: Memory INJection Attack," arXiv:2503.03704, 2025. |
| [8] | InjecMEM, OpenReview, 2025. |
| [9] | Sirin et al., "MemoryGraft: Persistent Compromise," arXiv:2512.16962, 2024. |
| [10] | Zhang et al., "Memory in the Age of AI Agents," arXiv:2512.13564, 2024. |
| [11] | Zheng et al., "Judging LLM-as-a-Judge with MT-Bench," arXiv:2306.05685, NeurIPS 2023. |

## Impact

| Area | Impact |
|------|--------|
| **F03 (Tech Report)** | Provides the complete related work section structure, comparison table, differentiation argument, and citation list. This is directly consumable by the paper. |
| **F01 (Experiment Framework)** | The ablation study design is informed by this analysis: the framework MUST support context units-enabled vs. context units-disabled conditions to demonstrate the behavioral difference that justifies the differentiation claim. |
| **Paper positioning** | The "trust-governed working memory" framing distinguishes context units from all surveyed systems without overclaiming. The paper does not need to demonstrate superiority on existing benchmarks (LoCoMo, etc.) because those benchmarks measure different properties (retrieval accuracy, temporal consistency). |
| **Vulnerability to critique** | The primary reviewer objection will likely be: "the D&D domain is narrow -- does this generalize?" The paper MUST acknowledge this as a limitation and note that the mechanism is domain-agnostic (authority semantics are not D&D-specific) even though the evaluation is domain-specific. |

## Remaining Gaps

1. **No head-to-head benchmark exists.** Because context units and the compared systems solve different problems, there is no shared benchmark for direct comparison. The paper MUST frame this as a feature (complementary systems in different parts of the design space), not a weakness.
2. **Letta evolution is ongoing.** Letta may add trust/authority features in future releases. The paper SHOULD cite the version and date of the Letta documentation reviewed and acknowledge that the comparison reflects the state of the system at time of writing.
3. **A-MemGuard comparison depth.** A-MemGuard is the closest comparable system. A deeper comparison (e.g., running both systems against the same attack scenarios) would strengthen the paper but is out of scope for this research task. This MAY be proposed as future work.
4. **Formal threat model.** The paper would benefit from a formal threat model (attacker capabilities, trust boundaries, attack surface) that maps to the attack papers surveyed. This is not developed here but SHOULD be included in the experiment framework design (F01).
5. **Missing systems.** Other memory systems exist (e.g., Reflexion, Generative Agents memory stream, RAISE). These were excluded because they do not directly address adversarial robustness or working memory governance. The paper SHOULD briefly acknowledge their existence and explain the scoping decision.
