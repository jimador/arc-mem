# Feature: Serendipitous Knowledge Retrieval

## Feature ID

`F04`

## Summary

Introduce a spreading-activation-inspired retrieval mechanism that, with configurable probability, fetches graph-distant nodes alongside directly referenced Propositions. The goal is to inject controlled serendipity into unit-governed context assembly, enabling the LLM to surface creative connections it would not otherwise make. This is the GENERATIVE complement to the DEFENSIVE context unit resilience work in Track A.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHOULD`, `SHOULD NOT`, `MAY`, and negations). Non-normative statements use lowercase equivalents.

## Why This Feature

1. **Problem addressed**: Current retrieval is purely direct -- when the conversation references a Proposition, the system fetches exactly that node from the DICE knowledge graph. There is no mechanism for surfacing tangentially related knowledge that might inspire richer, more organic-feeling narrative connections. The DM never "just remembers" something unless explicitly cued.
2. **Value delivered**: Spreading activation injects controlled randomness into context assembly. In collaborative fiction (the D&D domain), this produces the "serendipitous recall" effect -- a DM who weaves in details about Elvish Ruins because the conversation touched on Thornfield, even though nobody asked about ruins. This is a qualitative leap from "accurate retrieval" to "creative retrieval."
3. **Theoretical grounding**: Collins and Loftus (1975) demonstrated that human semantic memory activates related concepts along associative pathways, with activation decaying over distance. Koestler's bisociation theory posits that creative insight arises from connecting frames of reference that are normally unrelated. This feature operationalizes both ideas in a knowledge graph context.
4. **Why now**: This feature is Wave 3 (future work) because it requires the experiment framework (F01) and benchmarking UI (F02) to be in place for credible evaluation. It is documented now so the paper's "Future Work" section can describe a concrete, scoped proposal rather than a vague aspiration.

## Scope

### In Scope

1. A retrieval extension that, on each Proposition fetch, probabilistically also retrieves a graph-distant node.
2. Configuration surface for activation probability (P), minimum hop distance, maximum hop distance, and decay function.
3. Conflict guard: serendipitous nodes MUST NOT contradict existing context units (checked via `NegationConflictDetector` or equivalent).
4. Integration with the context assembly pipeline (`assembly/` package) to inject serendipitous nodes alongside direct references.
5. Evaluation methodology: coherence, novelty, and factual accuracy metrics for serendipitous injections.
6. A/B experiment configuration: same scenario run with and without serendipitous retrieval for paired comparison.

### Out of Scope

1. Modifications to the core ARC-Mem engine (`context unit/` package) -- rank, authority, and budget semantics are unchanged.
2. Real-time graph learning or embedding-based similarity -- this feature uses structural graph distance (hop count), not semantic embeddings.
3. Multi-agent retrieval (covered by F05).
4. Implementation-level class/method design -- deferred to OpenSpec spec/design artifacts.

## Dependencies

1. Feature dependencies: none (independent of F01-F03, but benefits from experiment framework for evaluation).
2. Infrastructure dependencies: Neo4j graph with multi-hop traversal capability (already present via Drivine ORM).
3. Priority: MAY.
4. OpenSpec change slug: `serendipitous-knowledge-retrieval` (scaffold when ready).

## Research Requirements

| Question | Channels | Timebox | Success Criteria |
|----------|----------|---------|------------------|
| What hop distances produce useful serendipity vs. noise? | Codebase experiments, Collins & Loftus (1975), associative memory literature | 4h | Recommended range (min/max hops) with rationale; pilot data from 2-3 scenarios |
| How should activation probability decay with hop distance? | Spreading activation literature, ACT-R cognitive architecture docs | 3h | Decay function selected (exponential, inverse-square, or fixed); justification documented |
| What evaluation metrics distinguish "creative" from "incoherent"? | NLG evaluation literature (coherence, novelty metrics), LLM-as-judge patterns | 4h | Metric definitions, inter-rater agreement targets, example rubrics |
| Does graph density affect serendipity quality? | Codebase analysis of existing DICE graphs, graph theory literature | 2h | Density thresholds identified; sparse-graph fallback behavior defined |

## Impacted Areas

1. **`sim.engine`**: Retrieval path extended to support probabilistic multi-hop fetches alongside direct references.
2. **`assembly`**: `ArcMemLlmReference` or equivalent context assembly component extended to format and inject serendipitous nodes with clear provenance markers.
3. **`persistence`**: New Cypher queries for parameterized multi-hop traversal from a source node. Drivine `@Query` methods for random walk with distance constraints.
4. **`sim.benchmark`** (if available): New evaluation metrics for coherence, novelty, and accuracy of serendipitous injections.
5. **Scenario definitions** (`src/main/resources/simulations/`): Extended to support serendipitous retrieval configuration per scenario.

## Visibility Requirements

At least one is REQUIRED.

### UI Visibility

1. The context inspector panel (`ContextInspectorPanel`) SHOULD display serendipitous nodes distinctly from direct references -- different color, icon, or label indicating "serendipitous activation."
2. The entity mention graph view (`EntityMentionNetworkView`) SHOULD highlight the graph path from the source Proposition to the serendipitous node, showing the hop chain.
3. Experiment comparison views SHOULD display side-by-side results for scenarios with and without serendipitous retrieval.

### Observability Visibility

1. Each serendipitous activation event MUST be logged with: source Proposition ID, activated node ID, hop distance, activation probability used, and whether the node passed conflict checking.
2. Aggregate metrics MUST be available: activation rate (how often P triggers), conflict rejection rate (how often serendipitous nodes are filtered), and average hop distance of accepted nodes.
3. Telemetry payloads MUST include enough context for post-hoc analysis of which serendipitous injections led to coherent vs. incoherent outputs.

## Acceptance Criteria

1. The retrieval mechanism MUST support configurable probability of serendipitous retrieval, with P ranging from 0.0 (disabled) to 1.0 (always activate).
2. The retrieval mechanism MUST support configurable minimum hop distance (default: 2) and SHOULD support configurable maximum hop distance (default: 4).
3. Serendipitous nodes MUST NOT contradict existing active context units. Conflict detection MUST use the same mechanism as context unit conflict resolution (`NegationConflictDetector` or equivalent).
4. Serendipitous nodes MUST NOT count against the unit budget (max 20). They are injected as supplementary context, not promoted to context units.
5. The system SHOULD measure output quality along three dimensions: coherence (does the distant node integrate naturally?), novelty (does the LLM volunteer connections it would not otherwise make?), and factual accuracy (does the randomness introduce contradictions?).
6. The system SHOULD support A/B testing: identical scenario configurations run with P=0.0 and P>0.0 for paired comparison.
7. When P=0.0, the retrieval path MUST behave identically to the current direct-fetch mechanism (zero regression).
8. The feature MAY support multiple decay functions (exponential, linear, flat) for activation probability over hop distance.

## Risks and Mitigations

1. **Risk**: Serendipitous nodes introduce incoherence or contradiction. **Mitigation**: Conflict guard is mandatory (AC #3); coherence is a measured evaluation dimension (AC #5); P defaults to a conservative value (0.2).
2. **Risk**: Sparse graphs produce low-quality distant nodes (dead ends, orphan entities). **Mitigation**: Research task on graph density thresholds; fallback behavior when no qualifying distant node exists within hop range (silently skip, do not inject noise).
3. **Risk**: Evaluation of "creativity" is subjective and hard to reproduce. **Mitigation**: Use LLM-as-judge with calibrated rubrics (leveraging R01 evaluator validity work from Track A); supplement with human evaluation on a small sample.
4. **Risk**: Performance overhead from multi-hop graph traversal on every retrieval. **Mitigation**: Probability gate (P) is checked BEFORE traversal -- when P does not trigger, no additional query is issued. Traversal queries SHOULD be bounded by maximum hop distance and SHOULD use Neo4j's efficient path-finding algorithms.
5. **Risk**: Feature is novel and has no established baseline in the literature. **Mitigation**: This is explicitly positioned as exploratory research (MAY priority). Negative results (serendipity does not help or actively harms) are valid and publishable outcomes.

## Proposal Seed

### Change Slug

`serendipitous-knowledge-retrieval`

### Proposal Starter Inputs

1. **Problem statement**: Current retrieval is purely reactive -- it fetches what is referenced, nothing more. Human cognition does not work this way; associative activation surfaces tangentially related memories that enable creative connections. The DICE knowledge graph already contains these associations but they are never exploited.
2. **Why now**: Track A delivers the experiment framework needed to credibly evaluate this feature. Without controlled A/B comparison infrastructure, any claim about serendipity's value would be anecdotal.
3. **Constraints/non-goals**: No modification to context unit semantics (rank, authority, budget). No embedding-based similarity. No real-time graph learning. The mechanism is structural (hop-based), not semantic.
4. **Visible outcomes**: Users MUST be able to see which context elements are serendipitous vs. direct. Researchers MUST be able to compare scenario outcomes with and without serendipitous retrieval.

### Suggested Capability Areas

1. Graph traversal and random walk algorithms (Neo4j / Drivine).
2. Probabilistic retrieval gating and decay functions.
3. Conflict-aware context injection (extending existing `NegationConflictDetector`).
4. NLG evaluation methodology (coherence, novelty, accuracy).
5. A/B experiment design for creative retrieval evaluation.

### Candidate Requirement Blocks

1. **Requirement**: The system SHALL support probabilistic multi-hop retrieval from the DICE knowledge graph, gated by configurable activation probability P.
2. **Requirement**: Serendipitous nodes SHALL be conflict-checked against active context units before injection.
3. **Scenario**: When a conversation references "Elara the healer" and P triggers, the system fetches Elara directly AND walks 2+ hops to discover "Elvish Ruins" (via Thornfield). Both are injected into context. The DM weaves in the ruins organically.
4. **Scenario**: When P=0.0, retrieval behavior is identical to the current system. No serendipitous nodes are fetched or injected.

## Validation Plan

1. **Controlled experiment**: Run identical scenarios with P=0.0 (control) and P=0.2 (treatment). Compare coherence, novelty, and accuracy scores across conditions using the experiment framework (F01).
2. **Ablation on hop distance**: Vary minimum hop distance (2, 3, 4) with fixed P to determine optimal distance range.
3. **Conflict rejection analysis**: Measure what fraction of serendipitous candidates are rejected by the conflict guard. High rejection rates may indicate the graph is too dense or P is too high.
4. **Qualitative review**: Human evaluation of a sample (N >= 20) of serendipitous injections, rating each on a 1-5 scale for coherence, novelty, and accuracy. Compare with LLM-as-judge ratings to validate automated evaluation.
5. **Regression verification**: Confirm P=0.0 produces bit-identical results to the current system (no performance or output regression).

## Known Limitations

1. **Domain specificity**: Evaluation is in the D&D/collaborative fiction domain only. Whether serendipitous retrieval helps in factual domains (medical, legal) is an open question and explicitly out of scope.
2. **Structural distance is not semantic distance**: Two-hop graph neighbors may be semantically very close (e.g., two characters in the same location) or very distant (e.g., a location and an unrelated event that share a common entity). Hop count is a crude proxy for "creative distance."
3. **No learning loop**: The system does not learn which serendipitous injections were useful. Each activation is independent. A reinforcement learning extension (promote nodes that led to high-coherence outputs) is a natural follow-up but not in scope.
4. **Graph coverage dependency**: Serendipitous retrieval quality is bounded by the richness of the DICE knowledge graph. Sparse graphs with few multi-hop paths will produce few or no serendipitous activations.
5. **Single-model evaluation**: Initial experiments will use one LLM backend. Whether serendipity benefits vary across model capabilities (e.g., GPT-4o vs. Haiku) is a follow-up question.

## Suggested Command

`openspec new change serendipitous-knowledge-retrieval`
