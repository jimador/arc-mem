# Activation-Ranked Context: Governed Working Memory for Long-Horizon LLM Conversations

Draft outline for an arXiv-style white paper on governed working memory as a cognitively grounded systems pattern, with Activation-Ranked Context (ARC) as a concrete mechanism for long-horizon LLM conversations.

## Working assumptions

- Lead the paper with `Activation-Ranked Context (ARC)` as the named mechanism and `governed working memory` as the broader architectural pattern it implements.
- Describe the current codebase as a `prototype implementation` or `reference implementation`.
- Use ACT-R–inspired concepts as explanatory scaffolding, while avoiding claims of cognitive fidelity beyond the architectural analogy.
- Assume the `NO_TRUST` ablation exists and is included in the final evaluation matrix.
- Keep claim language conservative unless deterministic runs, confidence intervals, and stability checks are complete.

## Goal and audience

Goal:
- document governed working memory as a distinct design pattern for long-horizon LLM systems
- present ARC as a concrete, activation-ranked mechanism for implementing governed working memory
- ground the design in ACT-R–inspired distinctions between declarative memory and bounded working-memory buffers
- define an evidence posture that supports directional findings without overselling generality

Primary audience:
- agent-memory builders
- long-horizon consistency researchers
- applied agent engineers who care about evaluation and memory semantics
- framework contributors interested in semantic-unit extraction, orchestration, cognitive architectures, and memory governance

## Working title options

1. Activation-Ranked Context: Governed Working Memory for Long-Horizon LLM Conversations
2. ARC: Activation-Ranked Context for Long-Horizon LLM Conversations
3. Keeping Load-Bearing Semantic Units in Play with Activation-Ranked Context

## Draft Abstract

Long-running LLM conversations degrade even when raw context capacity is not exhausted: established constraints lose force, later turns override earlier commitments, and contradiction, revision, and uncertainty become conflated over time. Existing memory approaches improve storage, retrieval, and persistence, but they do not explicitly govern which conversational state remains active and protected inside the prompt as reasoning unfolds. This paper frames that gap through an ACT-R–inspired distinction between declarative memory and bounded working-memory buffers, and introduces governed working memory as the missing architectural layer for long-horizon LLM systems. We present Activation-Ranked Context (ARC) as a concrete mechanism for implementing that layer. ARC maintains a bounded, mutable, activation-ranked working set of semantic units extracted from conversation in a protected prompt-level region. Each unit carries an activation-like state influenced by recency, reinforcement, relevance, trust, and conflict pressure; higher-activation units earn retention in the active context, while lower-activation units decay, are evicted, or later reactivate when context changes. ARC is representation-agnostic at the pattern level, while the prototype implementation uses propositions on a graph-backed substrate. We evaluate this architecture using deterministic and adversarial simulation. The evaluation measures fact survival, contradiction rate, time to first drift, repeated-attack erosion, and the effects of trust and authority ablations. The result is an explicit and testable design pattern for governed working memory, and a concrete mechanism for studying prompt-level working-memory control in long-horizon LLM systems.

## Paper outline

### 1. Introduction

Purpose: establish the long-horizon failure mode, motivate governed working memory, and introduce ARC as a concrete, cognitively grounded mechanism.

Subsections:
- 1.1 Motivation: why long-running conversations fail differently from single-turn prompts
- 1.2 Load-bearing conversational state: what must continue to matter over time
- 1.3 Why this is not just a bigger-context problem
- 1.4 Contributions

Key points to cover:
- Established constraints lose force over time even when earlier conversation remains technically recoverable.
- Retrieval and persistence are not the same thing as active working-memory control.
- ACT-R offers a useful architectural distinction between declarative memory and bounded working-memory buffers.
- Governed working memory is the pattern; ARC is a concrete mechanism that operationalizes it.
- The mechanism operates over semantic units in general, while the prototype substrate uses propositions.
- The paper contributes a pattern, a concrete mechanism, a prototype implementation, and an evaluation methodology.

### 2. Problem Statement, Taxonomy, and Terminology

Purpose: define the failure mode, memory-layer taxonomy, and core terms precisely enough to support both formalization and evaluation.

Subsections:
- 2.1 Long-horizon conversational drift
- 2.2 Taxonomy of memory layers
- 2.3 Error taxonomy
- 2.4 Research questions

Definitions to lock down:
- `volatile context`: the immediately assembled prompt context, including recent turns and instructions, subject to crowding, compaction, summarization, and reweighting
- `governed working memory`: the bounded active layer that keeps selected conversational state in play as a direct influence on reasoning
- `Activation-Ranked Context (ARC)`: a concrete governed-working-memory mechanism that maintains a protected prompt-level working set of semantic units ranked by activation-like state
- `semantic unit`: a representation-agnostic conversational unit used for extraction, ranking, revision, and reactivation; examples include propositions, claims, events, entities, sentences, structured facts, graph nodes, or summaries
- `semantic memory`: structured durable memory of extracted semantic units; in the prototype this serves as the closest analog to declarative memory
- `archival memory`: larger external storage and retrieval layer, such as documents, logs, transcripts, or vector-backed corpora
- `working-memory buffer`: the bounded active workspace that directly constrains reasoning in the current turn
- `load-bearing conversational state`: a semantic unit or constrained set of units whose continued force matters for correctness or safe behavior

Taxonomy to articulate:
- `volatile context`: what was just said or assembled
- `governed working memory`: what must still constrain reasoning right now
- `semantic memory`: what structured knowledge exists
- `archival memory`: what information could be retrieved if needed

Error taxonomy:
- contradiction
- legitimate revision
- world progression
- omission / non-mention
- uncertainty / hedging
- scoped exception vs. global rule

Candidate research questions:
- `RQ1`: Does ARC improve fact survival under long-horizon pressure relative to no active-memory governance?
- `RQ2`: Do activation dynamics, trust, and authority controls contribute independently to stability and contradiction resistance?
- `RQ3`: Do compliance-gated high-authority units improve resistance to deception and repeated erosion attacks?
- `RQ4`: Can ARC improve observability by exposing which units are under attack, which ones are decaying, and where enforcement fails?

### 3. Related Work

Purpose: position governed working memory and ARC relative to cognitive architectures, prompt-level workspace designs, hierarchical agent memory, and storage-oriented memory systems.

Subsections:
- 3.1 Multi-turn instability and conversational degradation
- 3.2 ACT-R, working-memory buffers, and cognitive workspace systems
- 3.3 Paging, hierarchical agent memory, and memory blocks
- 3.4 Semantic-memory and storage-oriented frameworks (for example, graph stores, Mem0-like systems, A-MEM-style memory extraction)
- 3.5 Long-term conversational memory benchmarks
- 3.6 Summarization and compaction limits
- 3.7 Poisoning, prompt injection, and constrained response enforcement
- 3.8 Belief revision as a theoretical lens

ACT-R + LLM papers to position in Section 3.2:
- Wu et al. (2024–2025), *LLM-ACTR / Cognitive LLMs* (arXiv:2408.09176): Transfers ACT-R knowledge to LLMs via latent neural representations injected into adapter layers. Orthogonal to ARC — embeds cognitive patterns INTO the model via fine-tuning, whereas ARC maintains cognitive-inspired structures OUTSIDE the model in a prompt-level working memory buffer.
- *Human-Like Remembering and Forgetting in LLM Agents* (2026, ACM): Integrates ACT-R memory dynamics (declarative retrieval, activation-based forgetting, interference) into LLM agent memory. Complementary — uses ACT-R activation/decay for similar goals but focuses on persistent agent memory retrieval, not bounded prompt-level working memory retention and governance.
- Meghdadi et al. (2026), *Integrating Language Model Embeddings into ACT-R* (Frontiers in Language Sciences): Replaces hand-coded ACT-R associations with LLM-derived embeddings for spreading activation. Orthogonal — enhances ACT-R with LLMs (opposite direction from ARC).
- Wu et al. (2023–2025), *Prompt-Enhanced ACT-R and Soar Model Development* (AAAI Fall Symposium): Uses LLMs as interactive interfaces to build ACT-R/Soar production rules. Orthogonal — tooling for cognitive model development, no overlap with prompt-level memory governance.

Argument to make:
- Existing systems help with storage, paging, planning, temporal structure, or retrieval quality.
- ACT-R and related cognitive workspace ideas motivate the distinction between declarative memory and bounded working-memory buffers.
- MemGPT highlights memory-tier management, but ARC addresses a different question: how to maintain a protected prompt-level working-memory region once information has been selected.
- Hierarchical agent memory helps with task decomposition and subgoal management, but does not directly solve conversational state stability.
- Storage-oriented frameworks surface and preserve information, but typically stop at retrieval rather than explicit prompt-level activation control.
- Mem0-like and A-MEM-style systems are relevant comparisons because they strengthen extraction and storage, while ARC focuses on retention and control inside the active reasoning context.
- ARC differs by maintaining a protected prompt-level working-memory region with explicit activation, decay, reactivation, ranking, contradiction mediation, authority, and guardrail semantics.

### 4. Governed Working Memory Pattern and ARC Mechanism

Purpose: present governed working memory in implementation-agnostic terms and describe ARC as a concrete, activation-ranked mechanism for implementing it.

Subsections:
- 4.1 Design goal: keep load-bearing conversational state in a bounded working-memory buffer
- 4.2 ACT-R analogy: declarative memory vs. working-memory buffers
- 4.3 Activation-ranked working set and prompt budget
- 4.4 Activation dynamics: reinforcement, decay, eviction, and reactivation
- 4.5 Trust, authority, contradiction mediation, and guardrails
- 4.6 Protected prompt-level region and mutation control
- 4.7 Interaction with semantic memory and archival memory
- 4.8 Observability and conversational-drift monitoring

Core claims to articulate:
- Governed working memory is layered between volatile context and deeper semantic / archival memory layers, not a replacement for them.
- ARC operationalizes governed working memory as a bounded, mutable, activation-ranked working set of semantic units.
- Semantic units are representation-agnostic at the pattern level; propositions remain the prototype substrate because they can be extracted, revised, reactivated, and compared across turns.
- Units should be allowed to fade from the working set without being lost permanently.
- Higher activation should earn retention in the protected prompt-level region.
- Trust, authority, and contradiction mediation matter because active state is not just what is relevant; it is what should continue to constrain reasoning.
- ARC is not just storage or retrieval; it is a working-memory control mechanism responsible for activation ranking, decay, reactivation, contradiction mediation, authority handling, guardrails, and drift observability.

### 5. Formalization

Purpose: express governed working memory and ARC as a state model and policy rather than only descriptive prose.

Subsections:
- 5.1 Notation and variables
- 5.2 Per-unit state representation
- 5.3 Turn-by-turn activation update equations
- 5.4 Active-set selection under prompt budget
- 5.5 Reactivation and conflict-pressure modeling
- 5.6 Mapping formulas to observable metrics

Candidate notation:
- `u_i`: semantic unit `i`
- `t`: conversation turn
- `s_i(t)`: state of semantic unit `i` at turn `t`
- `x_i(t)`: activation-like score
- `tau_i(t)`: trust score
- `a_i(t)`: authority level
- `rho_i(t)`: recency term
- `k_i(t)`: conflict or contradiction pressure
- `e_i(t)`: erosion / attack pressure
- `v_i(t)`: utility for ARC retention
- `C_t`: ARC working set at turn `t`
- `B_t`: protected prompt-budget constraint at turn `t`

Candidate state model:
- `s_i(t) = [x_i(t), tau_i(t), a_i(t), rho_i(t), e_i(t)]`
- authority may be discrete while activation and trust are continuous
- inactive units remain in semantic or archival memory and may later re-enter `C_t`

Candidate activation update equations:
- `x_i(t+1) = alpha * x_i(t) + beta * reinforce_i(t) + eta * relevance_i(t) + zeta * rho_i(t) + kappa * tau_i(t) - gamma * decay_i(t) - delta * k_i(t)`
- `tau_i(t+1) = phi(tau_i(t), source_i(t), corroboration_i(t), conflict_i(t))`
- `rho_i(t+1) = lambda * rho_i(t) + mention_i(t)`
- `e_i(t) = sum_{j <= t} mu^(t-j) * attack_i(j)`

Candidate selection policy:
- `C_t = argmax_{S subseteq F_t} sum_{i in S} v_i(t)` subject to `cost(S) <= B_t`
- `v_i(t)` can combine activation, authority, trust, relevance, and guardrail priority
- highest-authority units may have hard retention constraints under the budget

Candidate reactivation rule:
- `reactivate_i(t) iff relevance_i(t) * x_i(t) * tau_i(t) * residualAuthority_i > theta_reactivate`

Paper-level interpretation points:
- ARC is a constrained working-memory selection problem, not just a retrieval list
- activation is a policy variable for retention and re-entry, not a claim that the model internally stores explicit symbolic activation
- conflict pressure and erosion can be measured explicitly rather than described qualitatively
- the same formalization supports multiple semantic-unit representations even though the prototype uses propositions

### 6. Prototype Implementation

Purpose: show one concrete realization of ARC as a governed-working-memory mechanism.

Subsections:
- 6.1 Architecture flow from conversation to protected prompt-level working memory
- 6.2 Semantic-unit extraction and semantic-memory substrate
- 6.3 ARC item model and activation signals
- 6.4 Prompt assembly and protected context region
- 6.5 Contradiction mediation, trust scoring, and authority movement
- 6.6 Compliance enforcement and guardrails
- 6.7 Telemetry and run artifacts
- 6.8 Prototype substrate rationale

Implementation details to cover:
- architecture flow: Conversation -> Semantic Unit Extraction (for example, DICE) -> Semantic Memory / Declarative-Memory Analog -> Activation Evaluation -> ARC -> Prompt Context -> LLM Reasoning
- DICE extracts and persists structured semantic units; ARC manages the bounded working set that must remain active
- in the prototype, propositions instantiate semantic units and carry activation-related state
- ARC items have activation, authority, trust, and bounded membership
- active units are injected as an explicit protected prompt block
- trust and contradiction checks gate mutation into or out of the working set
- semantic-memory and archival retrieval can reactivate units when current context makes them relevant again
- turn-level telemetry supports later analysis of activation movement, drift, and enforcement behavior

Substrate rationale points:
- why the pattern is representation-agnostic even though the prototype uses propositions
- how DICE provides a useful semantic-memory / declarative-memory analog
- why propositions were a practical prototype substrate for activation-ranked control
- how graph-backed persistence supports reactivation, conflict comparison, and observability
- which orchestration or retrieval alternatives were considered and deferred

### 7. Evaluation Methodology

Purpose: define how governed working memory and ARC will be tested and what would count as evidence.

Subsections:
- 7.1 Evaluation goals and hypotheses
- 7.2 Scenario packs
- 7.3 Conditions and ablations
- 7.4 Metrics
- 7.5 Reproducibility requirements
- 7.6 Failure analysis protocol

Primary hypotheses:
- `H1`: full ARC improves fact survival and reduces contradictions versus no active-memory governance
- `H2`: activation dynamics, trust gating, and authority controls each contribute to stability and contradiction resistance
- `H3`: hierarchical authority improves contradiction handling beyond flat authority
- `H4`: compliance-gated high-authority units improve resistance to direct contradiction and deception attacks
- `H5`: ARC produces better diagnostic visibility into failure modes than retrieval-only baselines

#### 7.2 Scenario packs

Split evidence into two buckets:
- deterministic scripted pack for claim-facing results
- adaptive/stochastic pack for stress and exploratory analysis

Planned domain packs:
- narrative / tabletop
- operations / incident response
- support / fraud or policy review
- compliance / rule-bound assistant behavior

#### 7.3 Conditions and ablations

Core conditions:
- `FULL_ARC`: full ARC stack
- `NO_ACTIVE_MEMORY`: no explicit active working-memory injection
- `FLAT_AUTHORITY`: authority distinctions collapsed
- `NO_TRUST`: trust gating removed while leaving other components intact

Recommended secondary ablations:
- `NO_COMPLIANCE`: remove compliance-gated enforcement for highest-authority units
- `NO_LIFECYCLE`: disable activation reinforcement / decay / reactivation policy
- `RETRIEVAL_ONLY`: retrieval available, but no governed active set

Use the core matrix for primary claims. Use secondary ablations for mechanism analysis.

Condition terminology alignment (paper name → code name):
- `FULL_ARC` → `FULL_AWMU` (control: all subsystems active)
- `NO_ACTIVE_MEMORY` → `NO_AWMU` (no injection, no mutation/promotion)
- `FLAT_AUTHORITY` → `FLAT_AUTHORITY` (same in both)
- `NO_TRUST` → `NO_TRUST` (trust pipeline disabled; to be implemented)
- `NO_RANK_DIFFERENTIATION` → `NO_RANK_DIFFERENTIATION` (all ranks 500, mutation disabled; tests rank dynamics independently from authority — include as secondary ablation)
- `NO_COMPLIANCE` → `NO_COMPLIANCE` (compliance enforcement disabled; to be implemented)
- `NO_LIFECYCLE` → `NO_LIFECYCLE` (decay/reinforcement/reactivation disabled; to be implemented)

#### 7.4 Metrics

Primary metrics:
- fact survival rate
- drift absorption rate
- mean turns to first drift
- contradiction count
- major contradiction count

Secondary metrics:
- repeated-attack erosion rate
- reactivation success rate
- compliance recovery rate
- per-fact survival traces
- failure-mode counts by category
- observability metrics (for example: attacked units identified, activation drops, failed enforcement points)

Re-use the current verdict taxonomy where possible:
- `CONFIRMED`
- `CONTRADICTED`
- `NOT_MENTIONED`

### 8. Candidate Simulation Runs and Stress Tests

Purpose: identify the most paper-worthy runs before writing results.

#### 8.1 Deterministic claim-facing runs

- Repeated contradiction attack on a stable fact
  - Stress target: contradiction resistance
  - Must compare: `FULL_ARC`, `NO_ACTIVE_MEMORY`, `FLAT_AUTHORITY`, `NO_TRUST`
- Long tangent followed by recall probe
  - Stress target: maintaining load-bearing conversational state through displacement
  - Must compare: `FULL_ARC`, `NO_ACTIVE_MEMORY`
- Scoped exception vs. global rule
  - Stress target: exception handling vs. false contradiction
  - Must compare: `FULL_ARC`, `FLAT_AUTHORITY`
- Dormant fact reactivation
  - Stress target: semantic / archival recall plus governed re-entry into active memory
  - Must compare: `FULL_ARC`, `NO_LIFECYCLE`, `RETRIEVAL_ONLY`
- Deception or social-engineering attack
  - Stress target: compliance-gated high-authority units
  - Must compare: `FULL_ARC`, `NO_COMPLIANCE`, `NO_TRUST`

#### 8.2 Adaptive stress-facing runs

- repeated erosion against one semantic unit over many turns
- budget starvation / flooding with low-value units
- conflicting updates from mixed-trust sources
- authority laundering attempts through repeated weak reinforcement
- domain shift from one topic cluster to another and back

#### 8.3 Cross-domain scenario ideas

- Operations: production system in read-only incident mode
- Support: account under fraud review cannot be unlocked without override
- Healthcare: allergy or contraindication must not drift
- Legal/compliance: privilege, rule, or escalation boundary must remain active

### 9. Results Section Template

Purpose: pre-decide what the final paper should show.

Subsections:
- 8.1 Primary claim table
- 8.2 Per-domain results
- 8.3 Ablation table
- 8.4 Representative failure excerpts
- 8.5 Observability findings

Minimum artifacts to include:
- one overall metric table with confidence intervals
- one ablation table for mechanism differences
- one figure showing fact survival over turns
- one figure or table for repeated-attack erosion
- at least one failure excerpt per condition

### 10. Discussion

Purpose: interpret what governed working memory and ARC change, and what they do not.

Subsections:
- 9.1 Why protected working memory differs from retrieval alone
- 9.2 When activation-ranked context helps most
- 9.3 Where the approach still fails
- 9.4 Monitoring as a first-class outcome

Points to make:
- The key question is which semantic units stay active in the protected prompt-level working-memory region, not only which ones exist in storage.
- ARC is closer to a bounded working-memory buffer than a proposition store or retrieval cache.
- Compliance-gated high-authority units are useful beyond memory retention.
- Observability is part of the contribution: attacks, activation decay, revision pressure, and enforcement failures become measurable.
- Cognitive inspiration is useful because it clarifies layer boundaries, not because it proves human-like reasoning.
- Evaluation infrastructure is part of the contribution, not just supporting tooling.

### 11. Limitations

Purpose: narrow the claims and prevent overselling.

Subsections:
- 10.1 Prompt-based working memory is still model-dependent
- 10.2 Very long contexts still dilute even protected prompt content
- 10.3 ACT-R is an analogy, not a validated cognitive account of LLM internals
- 10.4 Domain skew and external validity limits
- 10.5 Calibration and threshold sensitivity
- 10.6 Evaluation maturity limits

Explicit limitation language:
- This is not a claim that memory is solved.
- This is not a replacement for retrieval, graph memory, semantic memory, or archival recall.
- ARC is a prompt-level working-memory mechanism, not a theory of all memory behavior in LLM agents.
- Cross-domain generalization has to be shown, not assumed.
- Even with `NO_TRUST` present, stronger causal claims still depend on deterministic completeness, calibration, and stability.

### 12. Safety, Misuse, and Ethics

Purpose: address risks of giving some semantic units extra force inside a protected working-memory region.

Subsections:
- 11.1 Over-anchoring and false certainty
- 11.2 Operator bias in high-authority units
- 11.3 Prompt-injection and poisoning risk
- 11.4 Hidden policy enforcement vs. transparent constraints
- 11.5 Human override and review paths

### 13. Conclusion

Purpose: close on the core thesis and the evidence standard.

Closing points:
- Long-horizon conversations need a governed working-memory layer, not just better storage and retrieval.
- ARC is a concrete, activation-ranked way to implement that layer in prompt-mediated LLM systems.
- The contribution is a cognitively grounded pattern, a concrete mechanism, and an evaluation framework, not a claim of solved memory.

### 14. Appendices

Appendix candidates:
- full metric definitions
- scenario templates
- prompt templates or prompt-shape examples
- run manifest / reproducibility checklist
- additional failure excerpts
- domain transfer notes

## Evidence guardrails

The paper should explicitly avoid:
- claiming broad generalization beyond tested domains and conditions
- implying the prototype is production-complete
- overstating causality where results are still directional
- claiming that ACT-R inspiration implies cognitive validity of the implementation

The paper should explicitly include:
- evidence-grade framing
- calibration and evaluator caveats
- implementation limitations
- clear boundary between cognitive analogy and implemented mechanism
- clear boundary between claim-facing and stress-facing results

## Short thesis

If the paper had to collapse to two sentences:

Governed working memory is the missing layer between volatile prompt context and deeper semantic or archival memory in long-horizon LLM systems. Activation-Ranked Context (ARC) makes that layer explicit as a bounded, mutable, activation-ranked working set of semantic units with decay, reactivation, contradiction mediation, authority, and guardrail semantics that can be evaluated directly under conversational pressure.

## Figures and tables to plan now

### Candidate figures

- Figure 1: taxonomy of memory layers: volatile context, governed working memory, semantic memory, archival memory
- Figure 2: ACT-R–inspired mapping from declarative memory and working-memory buffers to semantic memory and ARC
- Figure 3: ARC lifecycle (activation, reinforcement, decay, eviction, reactivation)
- Figure 4: architecture flow: Conversation -> Semantic Unit Extraction -> Semantic Memory -> Activation Evaluation -> ARC -> Prompt Context -> LLM Reasoning
- Figure 5: authority / trust / contradiction / guardrail interaction model
- Figure 6: turn-by-turn evaluation harness and verdict pipeline
- Figure 7: fact survival over turns by condition
- Figure 8: repeated-attack erosion curve by condition

### Candidate tables

- Table 1: taxonomy and terminology
- Table 2: ACT-R concept to ARC concept mapping
- Table 3: conditions and ablations
- Table 4: scenario packs and the mechanism each stresses
- Table 5: primary and secondary metrics
- Table 6: representative failure modes and how they are classified
- Table 7: cross-domain examples and load-bearing constraints

## Reference inventory

This is the starting bibliography/link inventory for the eventual paper. Verify final citation formatting before submission.

### Core problem framing

- Laban, P. et al. (2025). *LLMs Get Lost In Multi-Turn Conversation*. [arXiv:2505.06120](https://arxiv.org/abs/2505.06120)
- Maharana, A. et al. (2024). *LoCoMo: Evaluating Very Long-term Conversational Memory*. [arXiv:2402.17753](https://arxiv.org/abs/2402.17753)
- Wu, Y. et al. (2023). *Recursive Summarization*. [arXiv:2308.15022](https://arxiv.org/abs/2308.15022)

### Memory systems and retrieval

- Shaikh, O. et al. (2025). *Creating General User Models from Computer Use*. [arXiv:2505.10831](https://arxiv.org/abs/2505.10831)
- Packer, C. et al. (2023). *MemGPT: Towards LLMs as Operating Systems*. [arXiv:2310.08560](https://arxiv.org/abs/2310.08560)
- Letta memory blocks documentation. [docs](https://docs.letta.com/guides/agents/memory-blocks)
- *Cognitive Workspace: Active Memory Management for LLMs -- An Empirical Study of Functional Infinite Context*. [arXiv:2508.13171](https://arxiv.org/abs/2508.13171)
- Hu, M. et al. (2024). *HiAgent: Hierarchical Working Memory Management for Solving Long-Horizon Agent Tasks with Large Language Model*. [arXiv:2408.09559](https://arxiv.org/abs/2408.09559)
- *Mem0: Building Production-Ready AI Agents with Scalable Long-Term Memory*. [arXiv:2504.19413](https://arxiv.org/abs/2504.19413)
- Xu, W. et al. (2025). *A-MEM: Agentic Memory for LLM Agents*. [arXiv:2502.12110](https://arxiv.org/abs/2502.12110)
- Radhakrishnan, A. et al. (2025). *Graphiti: Building Real-Time Knowledge Graphs*. [arXiv:2501.13956](https://arxiv.org/abs/2501.13956)
- Graphiti repository. [GitHub](https://github.com/getzep/graphiti)
- Gutierrez, B. J. et al. (2024). *HippoRAG*. [arXiv:2405.14831](https://arxiv.org/abs/2405.14831)
- GraphRAG documentation. [docs](https://microsoft.github.io/graphrag/)
- CRAG. [arXiv:2401.15884](https://arxiv.org/abs/2401.15884)
- Self-RAG. [arXiv:2310.11511](https://arxiv.org/abs/2310.11511)

### Security, poisoning, and compliance

- PoisonedRAG. [arXiv:2402.07867](https://arxiv.org/abs/2402.07867)
- OWASP LLM Prompt Injection Prevention Cheat Sheet. [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html)
- ACON Framework. *Task-Aware Compression*. [OpenReview](https://openreview.net/pdf?id=7JbSwX6bNL)
- Su, Z. et al. (2026). *Vectorizing the Trie: Efficient Constrained Decoding for LLM-based Generative Retrieval on Accelerators* (STATIC). [arXiv:2602.22647](https://arxiv.org/abs/2602.22647)

### Theory background

- ACT-R official site. [CMU](https://act-r.psy.cmu.edu/)
- Wu, Y. et al. (2024–2025). *Cognitive LLMs: Towards Integrating Cognitive Architectures and Large Language Models for Manufacturing Decision-making*. [arXiv:2408.09176](https://arxiv.org/abs/2408.09176)
- *Human-Like Remembering and Forgetting in LLM Agents: An ACT-R-Inspired Memory Architecture*. (2026, ACM).
- Meghdadi, A. et al. (2026). *Integrating Language Model Embeddings into the ACT-R Cognitive Modeling Framework*. Frontiers in Language Sciences.
- Wu, Y. et al. (2023–2025). *Prompt-Enhanced ACT-R and Soar Model Development*. AAAI Fall Symposium.
- Alchourrón, C. E., Gärdenfors, P., and Makinson, D. (1985). *On the Logic of Theory Change: Partial Meet Contraction and Revision Functions*. [PDF](https://fitelson.org/piksi/piksi_22/agm.pdf)
- Hansson, S. O. (2024 archive). *Logic of Belief Revision*. [Stanford Encyclopedia of Philosophy](https://plato.stanford.edu/archives/win2024/entries/logic-belief-revision/)

### Prototype inspirations and supporting material

- Sleeping LLM series (memory consolidation inspiration). [Zenodo record 18778768](https://doi.org/10.5281/zenodo.18778768), [Zenodo record 18779159](https://doi.org/10.5281/zenodo.18779159)
- Johnson, R. (2026). *Agent Memory Is Not A Greenfield Problem*. [Embabel](https://medium.com/embabel/agent-memory-is-not-a-greenfield-problem-ground-it-in-your-existing-data-9272cabe1561)

### Implementation substrate links

- Embabel Agent. [GitHub](https://github.com/embabel/embabel-agent)
- DICE. [GitHub](https://github.com/embabel/dice)

### Evaluated and excluded

- Ng, D. (2025). *LLM Neuroanatomy: Layer Duplication for Enhanced Reasoning* (RYS). [Blog](https://dnhkng.github.io/posts/rys/). Evaluated for relevance — addresses transformer architectural depth (reasoning circuit duplication) rather than prompt-level working memory governance. Tangential; excluded from Related Work.
