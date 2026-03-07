# Governed Working Memory for Long-Horizon LLM Conversations

Draft outline for an arXiv-style white paper on active working-memory governance for long-horizon LLM systems.

## Working assumptions

- Use a generic pattern name in the paper title and thesis. Do not lead with the current repo or prototype name.
- Describe the current codebase as a `prototype implementation` or `reference implementation`.
- Assume the `NO_TRUST` ablation exists and is included in the final evaluation matrix.
- Keep claim language conservative unless deterministic runs, confidence intervals, and stability checks are complete.

## Goal and audience

Goal:
- document governed working memory as a distinct design pattern for long-horizon LLM systems
- present one concrete prototype implementation without making the paper depend on that implementation name
- define an evidence posture that supports directional findings without overselling generality

Primary audience:
- agent-memory builders
- long-horizon consistency researchers
- applied agent engineers who care about evaluation and memory semantics
- framework contributors interested in proposition systems, orchestration, and memory governance

## Working title options

1. Governed Working Memory for Long-Horizon LLM Conversations
2. Active Working Memory Governance for Long-Horizon LLM Systems
3. Keeping Load-Bearing Facts in Play: Governed Working Memory for Long-Horizon LLM Conversations

## Draft Abstract

Long-running LLM conversations degrade even when raw context capacity is not exhausted: established facts lose force, later turns override earlier constraints, and contradiction, revision, and uncertainty become conflated over time. Existing memory approaches improve storage, retrieval, and persistence, but they do not explicitly govern which facts remain active, trusted, and resistant to overwrite as a conversation evolves. This paper presents governed working memory, a long-horizon memory-management architecture built around a bounded active set of proposition-like facts with explicit policies for promotion, trust, authority, conflict handling, decay, eviction, reactivation, and compliance gating. Governed working memory operates between volatile prompt context and archival memory, keeping load-bearing facts in play while allowing lower-priority facts to leave the active set and later return when they become relevant again. We implement this architecture on a proposition-based substrate with graph-backed persistence and evaluate it using deterministic and adversarial simulation. The evaluation measures fact survival, contradiction rate, time to first drift, repeated-attack erosion, and the effects of trust and authority ablations. The result is an explicit and testable framework for active memory policy in long-horizon LLM systems.

## Paper outline

### 1. Introduction

Purpose: establish the problem, motivate the pattern, and state contributions.

Subsections:
- 1.1 Motivation: why long-running conversations fail differently from single-turn prompts
- 1.2 Load-bearing facts: what must continue to matter over time
- 1.3 Why this is not just a bigger-context problem
- 1.4 Contributions

Key points to cover:
- Established facts lose force over time even when still technically recoverable.
- Retrieval and persistence are not the same thing as active governance.
- The proposed pattern assumes proposition-like facts as the atomic unit of memory mutation and control.
- The paper contributes a pattern, a prototype implementation, and an evaluation methodology.

### 2. Problem Statement and Terminology

Purpose: define the failure mode precisely enough to evaluate.

Subsections:
- 2.1 Long-horizon conversational drift
- 2.2 Volatile context vs. governed working memory vs. archival memory
- 2.3 Error taxonomy
- 2.4 Research questions

Definitions to lock down:
- `volatile context`: everything currently in prompt context, subject to crowding, summarization, and reweighting
- `governed working memory`: bounded active set of facts intentionally kept in play
- `archival memory`: persistent store from which facts can later be reactivated
- `proposition`: the atomic factual unit used for extraction, retrieval, revision, and governance
- `load-bearing fact`: a fact whose continued force matters for correctness or safe behavior

Error taxonomy:
- contradiction
- legitimate revision
- world progression
- omission / non-mention
- uncertainty / hedging
- scoped exception vs. global rule

Candidate research questions:
- `RQ1`: Does governed working memory improve fact survival under long-horizon pressure relative to no active-memory governance?
- `RQ2`: Do trust and authority controls contribute independently to stability and contradiction resistance?
- `RQ3`: Do compliance-gated high-authority facts improve resistance to deception and repeated erosion attacks?
- `RQ4`: Can explicit memory governance improve observability by exposing which facts are under attack and where enforcement fails?

### 3. Related Work

Purpose: position the pattern relative to existing memory and retrieval approaches.

Subsections:
- 3.1 Multi-turn instability and conversational degradation
- 3.2 External memory, paging, and memory blocks
- 3.3 Proposition-based user and memory models
- 3.4 Retrieval-augmented generation and graph-based memory
- 3.5 Long-term conversational memory benchmarks
- 3.6 Summarization and compaction limits
- 3.7 Poisoning, prompt injection, and constrained response enforcement
- 3.8 Belief revision as a theoretical lens

Argument to make:
- Existing systems help with storage, recall, temporal structure, or retrieval quality.
- GUM and DICE motivate propositions as a strong atomic unit for extracted memory.
- The missing layer is explicit policy over which facts stay active, trusted, and hard to overwrite.

### 4. Governed Working Memory Pattern

Purpose: present the pattern in implementation-agnostic terms.

Subsections:
- 4.1 Design goal: keep load-bearing facts in play
- 4.2 Proposition-first memory substrate
- 4.3 Bounded active set and memory budget
- 4.4 Promotion, reinforcement, decay, eviction, and reactivation
- 4.5 Trust and authority as governance primitives
- 4.6 Conflict typing: contradiction vs. revision vs. progression
- 4.7 Compliance-gated high-authority facts
- 4.8 Observability and monitoring surfaces

Core claims to articulate:
- Governed working memory is layered on top of archival memory, not a replacement for it.
- Proposition-like facts are the right atomic unit for working-memory governance because they can be extracted, revised, reactivated, and compared across turns.
- Facts should be allowed to fade from the active set without being lost permanently.
- High-authority facts can act as active constraints, not just stored facts.
- Monitoring which facts are attacked, reinforced, revised, or eroded is part of the value of the pattern.

### 5. Formalization

Purpose: express governed working memory as a state model and policy rather than only descriptive prose.

Subsections:
- 5.1 Notation and variables
- 5.2 Per-fact state representation
- 5.3 Turn-by-turn state update equations
- 5.4 Promotion and active-set selection policy
- 5.5 Reactivation and attack-pressure modeling
- 5.6 Mapping formulas to observable metrics

Candidate notation:
- `f_i`: fact or proposition `i`
- `t`: conversation turn
- `s_i(t)`: state of fact `i` at turn `t`
- `r_i(t)`: rank / salience score
- `tau_i(t)`: trust score
- `a_i(t)`: authority level
- `q_i(t)`: recency or activation term
- `e_i(t)`: erosion / attack pressure
- `u_i(t)`: utility for active-set selection
- `A_t`: active set at turn `t`
- `B_t`: prompt-budget constraint at turn `t`

Candidate state model:
- `s_i(t) = [r_i(t), tau_i(t), a_i(t), q_i(t), e_i(t)]`
- authority may be discrete while rank and trust are continuous
- inactive facts remain in archival memory and may later re-enter `A_t`

Candidate update equations:
- `r_i(t+1) = alpha * r_i(t) + beta * reinforce_i(t) - gamma * decay_i(t) - delta * e_i(t)`
- `tau_i(t+1) = phi(tau_i(t), source_i(t), corroboration_i(t), conflict_i(t))`
- `q_i(t+1) = eta * q_i(t) + mention_i(t)`
- `e_i(t) = sum_{k <= t} lambda^(t-k) * attack_i(k)`

Candidate promotion score:
- `p_i(t) = w_c * confidence_i + w_t * tau_i(t) + w_s * sourceAuthority_i + w_g * graphSupport_i - w_k * conflictRisk_i`
- threshold routing: promote / review / archive

Candidate active-set selection policy:
- `A_t = argmax_{S subseteq F_t} sum_{i in S} u_i(t)` subject to `cost(S) <= B_t`
- `u_i(t)` can combine rank, authority, relevance, and compliance priority
- highest-authority facts may have hard retention constraints under the budget

Candidate reactivation rule:
- `reactivate_i(t) iff relevance_i(t) * tau_i(t) * residualAuthority_i > theta_reactivate`

Paper-level interpretation points:
- the active set is a constrained optimization problem, not just a retrieval list
- attack pressure and erosion can be measured explicitly rather than described qualitatively
- the same formalization supports both implementation design and evaluation metrics

### 6. Prototype Implementation

Purpose: show one concrete realization without making the paper depend on a brand name.

Subsections:
- 6.1 Proposition-based architecture and persistent storage substrate
- 6.2 Active-memory item model
- 6.3 Prompt assembly and established-facts injection
- 6.4 Conflict resolution and mutation control
- 6.5 Trust scoring and authority movement
- 6.6 Compliance enforcement
- 6.7 Telemetry and run artifacts
- 6.8 Prototype substrate rationale

Implementation details to cover:
- active-memory items have rank, authority, and bounded membership
- active facts are injected as an explicit prompt block
- trust and conflict checks gate mutation
- archival facts can be reactivated when context makes them relevant again
- turn-level telemetry supports later analysis

Substrate rationale points:
- why propositions are the right atomic unit for extracted memory
- how GUM motivates confidence-weighted propositions as a memory substrate
- why proposition extraction plus persistent graph storage was the right substrate
- how DICE extends proposition models into a graph-backed architecture
- why the orchestration layer was sufficient for this design
- which integration boundaries shaped the pattern
- which orchestration or retrieval alternatives were considered and deferred

### 7. Evaluation Methodology

Purpose: define how the pattern will be tested and what would count as evidence.

Subsections:
- 7.1 Evaluation goals and hypotheses
- 7.2 Scenario packs
- 7.3 Conditions and ablations
- 7.4 Metrics
- 7.5 Reproducibility requirements
- 7.6 Failure analysis protocol

Primary hypotheses:
- `H1`: full governed working memory improves fact survival and reduces contradictions versus no active-memory governance
- `H2`: trust gating improves stability beyond authority-only governance
- `H3`: hierarchical authority improves contradiction handling beyond flat authority
- `H4`: compliance-gated high-authority facts improve resistance to direct contradiction and deception attacks
- `H5`: governed working memory produces better diagnostic visibility into failure modes than retrieval-only baselines

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
- `FULL_GWM`: full governed working-memory stack
- `NO_ACTIVE_MEMORY`: no explicit active working-memory injection
- `FLAT_AUTHORITY`: authority distinctions collapsed
- `NO_TRUST`: trust gating removed while leaving other components intact

Recommended secondary ablations:
- `NO_COMPLIANCE`: remove compliance-gated enforcement for highest-authority facts
- `NO_LIFECYCLE`: disable reinforcement/decay/reactivation policy
- `RETRIEVAL_ONLY`: retrieval available, but no governed active set

Use the core matrix for primary claims. Use secondary ablations for mechanism analysis.

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
- observability metrics (for example: attacked facts identified, failed enforcement points)

Re-use the current verdict taxonomy where possible:
- `CONFIRMED`
- `CONTRADICTED`
- `NOT_MENTIONED`

### 8. Candidate Simulation Runs and Stress Tests

Purpose: identify the most paper-worthy runs before writing results.

#### 8.1 Deterministic claim-facing runs

- Repeated contradiction attack on a stable fact
  - Stress target: contradiction resistance
  - Must compare: `FULL_GWM`, `NO_ACTIVE_MEMORY`, `FLAT_AUTHORITY`, `NO_TRUST`
- Long tangent followed by recall probe
  - Stress target: maintaining load-bearing facts through displacement
  - Must compare: `FULL_GWM`, `NO_ACTIVE_MEMORY`
- Scoped exception vs. global rule
  - Stress target: exception handling vs. false contradiction
  - Must compare: `FULL_GWM`, `FLAT_AUTHORITY`
- Dormant fact reactivation
  - Stress target: archival recall + governed re-entry into active memory
  - Must compare: `FULL_GWM`, `NO_LIFECYCLE`, `RETRIEVAL_ONLY`
- Deception or social-engineering attack
  - Stress target: compliance-gated high-authority facts
  - Must compare: `FULL_GWM`, `NO_COMPLIANCE`, `NO_TRUST`

#### 8.2 Adaptive stress-facing runs

- repeated erosion against one proposition over many turns
- budget starvation / flooding with low-value facts
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

Purpose: interpret what the pattern changes and what it does not.

Subsections:
- 9.1 Why active governance differs from retrieval alone
- 9.2 When governed working memory helps most
- 9.3 Where the approach still fails
- 9.4 Monitoring as a first-class outcome

Points to make:
- The pattern is about which facts stay active, not only which facts exist.
- Compliance-gated high-authority facts are useful beyond memory retention.
- Observability is part of the contribution: attacks, erosion, revision pressure, and enforcement failures become measurable.
- Binary trust is too coarse for long-horizon collaborative systems.
- Consistency and collaboration can pull in different directions.
- Evaluation infrastructure is part of the contribution, not just supporting tooling.

### 11. Limitations

Purpose: narrow the claims and prevent overselling.

Subsections:
- 10.1 Prompt-based active memory is still model-dependent
- 10.2 Very long contexts still dilute even protected prompt content
- 10.3 Domain skew and external validity limits
- 10.4 Calibration and threshold sensitivity
- 10.5 Evaluation maturity limits

Explicit limitation language:
- This is not a claim that memory is solved.
- This is not a replacement for retrieval, graph memory, or archival recall.
- Cross-domain generalization has to be shown, not assumed.
- Even with `NO_TRUST` present, stronger causal claims still depend on deterministic completeness, calibration, and stability.

### 12. Safety, Misuse, and Ethics

Purpose: address risks of giving some facts extra force.

Subsections:
- 11.1 Over-anchoring and false certainty
- 11.2 Operator bias in high-authority facts
- 11.3 Prompt-injection and poisoning risk
- 11.4 Hidden policy enforcement vs. transparent constraints
- 11.5 Human override and review paths

### 13. Conclusion

Purpose: close on the core thesis and the evidence standard.

Closing points:
- Long-horizon conversations need a way to keep load-bearing facts in play.
- Governed working memory is a candidate pattern for doing that explicitly.
- The contribution is a pattern plus an evaluation framework, not a claim of solved memory.

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

The paper should explicitly include:
- evidence-grade framing
- calibration and evaluator caveats
- implementation limitations
- clear boundary between claim-facing and stress-facing results

## Short thesis

If the paper had to collapse to two sentences:

Governed working memory treats memory as an actively governed working set rather than a passive store of retrieved facts. By attaching explicit lifecycle, trust, authority, and compliance semantics to a bounded active set and evaluating the result under long-horizon pressure, the paper makes a largely implicit part of LLM system design explicit and testable.

## Figures and tables to plan now

### Candidate figures

- Figure 1: volatile context vs. governed working memory vs. archival memory
- Figure 2: active-memory lifecycle (promotion, reinforcement, decay, eviction, reactivation)
- Figure 3: authority/trust/compliance interaction model
- Figure 4: turn-by-turn evaluation harness and verdict pipeline
- Figure 5: fact survival over turns by condition
- Figure 6: repeated-attack erosion curve by condition

### Candidate tables

- Table 1: terminology and definitions
- Table 2: conditions and ablations
- Table 3: scenario packs and the mechanism each stresses
- Table 4: primary and secondary metrics
- Table 5: representative failure modes and how they are classified
- Table 6: cross-domain examples and load-bearing constraints

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

- Alchourrón, C. E., Gärdenfors, P., and Makinson, D. (1985). *On the Logic of Theory Change: Partial Meet Contraction and Revision Functions*. [PDF](https://fitelson.org/piksi/piksi_22/agm.pdf)
- Hansson, S. O. (2024 archive). *Logic of Belief Revision*. [Stanford Encyclopedia of Philosophy](https://plato.stanford.edu/archives/win2024/entries/logic-belief-revision/)

### Prototype inspirations and supporting material

- Sleeping LLM series (memory consolidation inspiration). [Zenodo record 18778768](https://doi.org/10.5281/zenodo.18778768), [Zenodo record 18779159](https://doi.org/10.5281/zenodo.18779159)
- Johnson, R. (2026). *Agent Memory Is Not A Greenfield Problem*. [Embabel](https://medium.com/embabel/agent-memory-is-not-a-greenfield-problem-ground-it-in-your-existing-data-9272cabe1561)

### Implementation substrate links

- Embabel Agent. [GitHub](https://github.com/embabel/embabel-agent)
- DICE. [GitHub](https://github.com/embabel/dice)
