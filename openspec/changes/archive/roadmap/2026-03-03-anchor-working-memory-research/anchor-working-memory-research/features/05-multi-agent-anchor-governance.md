# Feature: Multi-Agent Anchor Governance

## Feature ID

`F05`

## Summary

Extend the anchor trust model from single-agent (one LLM, one context window) to multi-agent (multiple LLMs with heterogeneous capabilities, communicating via A2A or similar protocols). The core insight is that trust becomes two-dimensional: fact-level authority (existing PROVISIONAL to CANON hierarchy) and agent-level provenance (who asserted this fact, and how much do we trust that agent). Combined with per-agent budget partitioning based on context capacity, this makes anchors the governance layer for heterogeneous multi-agent memory -- a capability no existing system provides.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHOULD`, `SHOULD NOT`, `MAY`, and negations). Non-normative statements use lowercase equivalents.

## Why This Feature

1. **Problem addressed**: The current anchor model assumes a single agent consuming a single context window. In multi-agent systems (e.g., a large orchestrator coordinating small task agents), there is no mechanism to track which agent asserted a fact, no way to partition the anchor budget by agent capability, and no cross-agent authority promotion when multiple agents corroborate a fact.
2. **Value delivered**: Small-context-window agents (Haiku-class, 8K tokens, budget 5) receive the 5 highest-authority facts that fit their window -- not a random subset. Large orchestrators (GPT-4o-class, 128K tokens, budget 50) receive the full authority hierarchy. Anchor budget enforcement becomes the mechanism that makes small models useful in multi-agent coordination.
3. **Competitive positioning**: ShardMemo's Tier A (per-agent working state) is an unstructured blob with no governance. MemGPT/Letta's shared memory has no trust model. A-MemGuard has binary trust (trusted/untrusted), not a graduated hierarchy. This would be the first graduated trust model for heterogeneous multi-agent memory with provenance tracking and cross-agent corroboration.
4. **Why now**: This feature is Wave 3 (future work) because it requires the single-agent anchor model to be thoroughly evaluated first (Track A). It is documented now so the paper's "Future Work" section can describe a concrete architecture extension, and so the A2A protocol alignment can be tracked as it matures.

## Scope

### In Scope

1. Agent-level trust provenance on anchors: each anchor carries a `sourceAgent` field identifying which agent asserted it.
2. Per-agent budget configuration: each agent's anchor budget is derived from its context capacity (e.g., 8K window gets budget 5, 128K window gets budget 50).
3. Cross-agent authority promotion: when Agent A and Agent B independently assert the same fact, the fact's authority is promoted (e.g., PROVISIONAL to UNRELIABLE, or UNRELIABLE to RELIABLE).
4. Trust-degraded injection for untrusted agents: facts from agents with low trust scores arrive as PROVISIONAL regardless of the asserting agent's claimed authority.
5. Per-agent context assembly: each agent receives a rank-ordered, budget-constrained subset of anchors tailored to its context capacity.
6. Evaluation methodology for asymmetric agent scenarios (large orchestrator + small task agents).

### Out of Scope

1. Implementation of a full A2A protocol stack -- this feature defines the anchor governance layer, not the transport/communication protocol.
2. Agent discovery, authentication, or capability negotiation -- these are A2A protocol concerns.
3. Modifications to the authority upgrade-only invariant -- PROVISIONAL to CANON remains upgrade-only. CANON is never auto-assigned, even via cross-agent corroboration.
4. Real-time agent reputation learning -- agent trust scores are configured, not learned from interaction history.
5. Implementation-level class/method design -- deferred to OpenSpec spec/design artifacts.

## Dependencies

1. Feature dependencies: none (independent of F01-F03, but benefits from experiment framework for evaluation).
2. Protocol dependencies: Google A2A protocol or equivalent multi-agent communication standard. The feature SHOULD be protocol-agnostic at the governance layer.
3. Priority: MAY.
4. OpenSpec change slug: `multi-agent-anchor-governance` (scaffold when ready).

## Research Requirements

| Question | Channels | Timebox | Success Criteria |
|----------|----------|---------|------------------|
| What budget partitioning strategy optimizes information transfer to small-context agents? | Codebase analysis, context window research (Anthropic, OpenAI docs), information theory literature | 4h | Partitioning formula defined; validated against at least 3 model size tiers (8K, 32K, 128K) |
| How should cross-agent corroboration be detected? Exact match, semantic similarity, or entailment? | NLI literature, duplicate detection patterns in codebase (`DuplicateDetector`) | 3h | Detection method selected with precision/recall tradeoffs documented |
| What agent trust model prevents a compromised agent from poisoning the shared knowledge base? | Byzantine fault tolerance literature, federated learning trust models, A-MemGuard paper | 4h | Trust model defined; attack vectors enumerated; mitigation for each vector documented |
| How does the A2A protocol handle shared state and context? | Google A2A spec, A2A GitHub repo, implementation examples | 3h | Protocol alignment documented; gaps between A2A context model and anchor governance identified |

## Impacted Areas

1. **`anchor/`**: `Anchor` model extended with `sourceAgent` provenance field. `AnchorEngine` extended with per-agent budget enforcement and cross-agent corroboration logic.
2. **`assembly/`**: `AnchorsLlmReference` and `PromptBudgetEnforcer` extended to support per-agent context assembly with agent-specific budget constraints.
3. **`persistence/`**: `PropositionNode` extended with agent provenance fields. New Cypher queries for cross-agent corroboration detection (find propositions asserted by multiple agents).
4. **New `a2a/` package** (candidate): Agent registration, trust configuration, budget partitioning logic, and protocol integration.
5. **`sim.engine`**: Extended to support multi-agent simulation scenarios with heterogeneous agent configurations.
6. **Scenario definitions** (`src/main/resources/simulations/`): New scenario types with multi-agent configurations (agent count, model sizes, trust levels).

## Visibility Requirements

At least one is REQUIRED.

### UI Visibility

1. The context inspector panel SHOULD display agent provenance for each anchor -- which agent asserted it and at what trust level.
2. Per-agent budget utilization SHOULD be visible: how many of each agent's budget slots are filled, and what was evicted.
3. Cross-agent corroboration events SHOULD be highlighted: when two agents independently confirm a fact and it gets promoted.
4. The entity mention graph view SHOULD support filtering by source agent, showing each agent's contribution to the knowledge graph.

### Observability Visibility

1. Each anchor assertion MUST be logged with: anchor ID, source agent ID, agent trust level, claimed authority, effective authority (after trust adjustment), and target agent's budget state.
2. Cross-agent corroboration events MUST be logged with: the corroborated proposition ID, the corroborating agents, the previous authority, and the new authority after promotion.
3. Budget eviction events in multi-agent context MUST include: which agent's budget overflowed, what was evicted, and whether the evicted anchor was from a different source agent.
4. Aggregate metrics MUST be available: per-agent assertion rate, corroboration rate, trust-degradation rate (how often an agent's facts are downgraded), and budget utilization per agent.

## Acceptance Criteria

1. Each anchor MUST carry agent-level trust provenance identifying the source agent.
2. Per-agent budget configuration MUST be supported, allowing different budget sizes based on each agent's context capacity.
3. The authority upgrade-only invariant MUST be preserved across agents. No agent MAY downgrade another agent's anchor authority.
4. CANON authority MUST NOT be auto-assigned via cross-agent corroboration. CANON remains an explicit human/operator designation.
5. Cross-agent authority promotion SHOULD be supported: independent assertion of the same fact by N agents (configurable, default N=2) triggers authority promotion by one level.
6. Facts from agents with trust scores below a configurable threshold SHOULD arrive as PROVISIONAL regardless of the asserting agent's claimed authority level.
7. Per-agent context assembly MUST produce a rank-ordered, budget-constrained anchor set tailored to each agent's configured budget.
8. The feature SHOULD demonstrate measurable benefit in asymmetric scenarios: a small task agent (budget 5) with anchor governance SHOULD outperform the same agent without governance on factual consistency metrics.
9. The governance layer SHOULD be protocol-agnostic -- it defines trust semantics, not transport. Integration with Google A2A or similar MAY be provided as a reference implementation.
10. When operating in single-agent mode (one agent, no provenance), the system MUST behave identically to the current implementation (zero regression).

## Risks and Mitigations

1. **Risk**: A compromised or hallucinating agent floods the shared knowledge base with false propositions. **Mitigation**: Trust-degraded injection (AC #6) ensures low-trust agents' facts arrive as PROVISIONAL. Budget enforcement prevents any single agent from monopolizing anchor slots. Rate limiting on per-agent assertions MAY be added.
2. **Risk**: Cross-agent corroboration is gamed by colluding agents. **Mitigation**: Corroboration requires independent assertion (not forwarded facts). The number of agents required for promotion (N) is configurable. CANON is never auto-assigned, providing a hard ceiling on automated promotion.
3. **Risk**: Budget partitioning strategy is wrong -- small agents get too few or too many anchors. **Mitigation**: Budget is configurable per agent. Research task on optimal partitioning. Empirical evaluation with at least 3 model size tiers before committing to defaults.
4. **Risk**: A2A protocol is immature and may change significantly. **Mitigation**: Governance layer is protocol-agnostic (AC #9). A2A integration is a MAY, not a MUST. The anchor trust model has value independent of any specific protocol.
5. **Risk**: Complexity explosion -- multi-agent governance adds significant system complexity. **Mitigation**: Single-agent mode regression guarantee (AC #10). Multi-agent governance is additive, not a rewrite. The existing `AnchorEngine` is extended, not replaced.
6. **Risk**: Evaluation requires running multiple LLM agents simultaneously, which is expensive. **Mitigation**: Start with simulated agents (deterministic assertion patterns) before using real LLMs. Use the experiment framework (F01) for controlled evaluation.

## Proposal Seed

### Change Slug

`multi-agent-anchor-governance`

### Proposal Starter Inputs

1. **Problem statement**: Multi-agent LLM systems have no graduated trust model for shared memory. Existing approaches use either no governance (ShardMemo), no trust model (MemGPT/Letta), or binary trust (A-MemGuard). Anchors already implement graduated trust (PROVISIONAL to CANON) for single-agent contexts. Extending this to multi-agent requires two additions: agent-level provenance and per-agent budget partitioning.
2. **Why now**: The A2A protocol is emerging as a standard for multi-agent communication. Establishing anchor governance as the trust layer early positions dice-anchors as the reference implementation for governed multi-agent memory.
3. **Constraints/non-goals**: No protocol implementation (A2A transport is out of scope). No changes to upgrade-only authority invariant. No real-time trust learning. Single-agent regression guarantee.
4. **Visible outcomes**: Operators MUST be able to see which agent asserted each anchor, what trust level was applied, and how per-agent budgets are utilized. Researchers MUST be able to compare single-agent vs. multi-agent governance in controlled experiments.

### Suggested Capability Areas

1. Agent trust provenance model (source agent, trust level, effective authority).
2. Per-agent budget partitioning and context assembly.
3. Cross-agent corroboration detection and authority promotion.
4. Trust-degraded injection for untrusted or unknown agents.
5. Asymmetric agent evaluation methodology (large orchestrator + small task agents).

### Candidate Requirement Blocks

1. **Requirement**: The system SHALL track agent-level provenance on all anchors, recording the source agent and its trust level at assertion time.
2. **Requirement**: The system SHALL support per-agent budget configuration, with each agent receiving a budget proportional to its context capacity.
3. **Scenario**: A large orchestrator (budget 50) and a small task agent (budget 5) share a knowledge base of 30 anchors. The orchestrator receives all 30 (within budget). The task agent receives the top 5 by authority-then-rank ordering. The task agent's outputs are factually consistent because it received the highest-authority facts.
4. **Scenario**: Agent A (trust: HIGH) asserts "Elara is a healer." Agent B (trust: LOW) asserts "Elara is a warrior." Agent B's assertion arrives as PROVISIONAL. Agent A's assertion retains its original authority. The conflict detector flags the contradiction. The higher-authority assertion wins resolution.

## Validation Plan

1. **Single-agent regression**: Confirm that the system with multi-agent governance enabled but configured for one agent produces identical results to the current system.
2. **Asymmetric agent experiment**: Run a scenario with one large orchestrator (budget 50) and two small task agents (budget 5 each). Compare task agent factual consistency with and without anchor governance. Hypothesis: governed agents are significantly more consistent.
3. **Trust degradation experiment**: Introduce a "compromised" agent that asserts contradictory facts at high frequency. Measure how quickly the contradictions propagate to other agents with and without trust-degraded injection. Hypothesis: trust degradation contains the damage.
4. **Cross-agent corroboration experiment**: Run a scenario where two independent agents observe the same facts. Measure authority promotion rates and whether promoted facts are actually correct. Hypothesis: corroborated facts are more reliable than single-source facts.
5. **Budget partitioning sweep**: Vary small-agent budget (3, 5, 10, 15) and measure factual consistency vs. budget size. Identify the point of diminishing returns.

## Known Limitations

1. **No real-time trust learning**: Agent trust levels are configured statically. A dynamic trust model that adjusts based on agent behavior (e.g., how often an agent's assertions are later contradicted) is a natural extension but not in scope.
2. **Simulated multi-agent evaluation**: Initial experiments will use simulated agents with deterministic assertion patterns, not real multi-agent LLM deployments. Real deployment evaluation is deferred.
3. **Protocol coupling risk**: If A2A protocol semantics change significantly, the integration layer may need rework. The governance layer itself is protocol-agnostic, but the integration shim is not.
4. **No consensus mechanism**: Cross-agent corroboration is a simple counting mechanism (N agents agree). There is no formal consensus protocol (e.g., Byzantine fault tolerance). This is adequate for the research scope but may be insufficient for adversarial multi-agent deployments.
5. **Single-domain evaluation**: As with all features in this roadmap, evaluation is in the D&D/collaborative fiction domain. Whether multi-agent anchor governance generalizes to other domains (e.g., multi-agent code generation, multi-agent customer support) is an open question.

## Suggested Command

`openspec new change multi-agent-anchor-governance`
