# dice-anchors

A standalone exploration of **Anchors** — ranked, persistent facts injected into LLM prompts to resist context drift in multi-turn conversations.

## The Problem

Any long-horizon application that maintains persistent state — campaign managers, tutoring systems, legal assistants, medical histories, design tools — faces the same fundamental issue: **load-bearing facts silently disappear as conversations grow**.

Laban et al. (2025) demonstrate a **39% average performance drop** when models operate across multiple turns versus single-turn settings, with unreliability increasing by 112% ([arXiv:2505.06120](https://arxiv.org/abs/2505.06120)). Models make assumptions early, prematurely attempt final solutions, and overweight first/last turns while losing middle context.

Standard mitigations (longer context windows, summarization, RAG) don't solve this because they don't distinguish *importance*. A summary might drop a critical detail. A longer window still lets the model attend unevenly. RAG retrieves relevant content but doesn't *mandate* consistency.

## Why D&D?

This project uses tabletop RPGs as its test domain, but anchors are domain-agnostic. D&D is a useful proving ground because it sits at the intersection of two competing demands:

1. **Strict invariants**: A campaign has established world facts, character states, rules, and narrative history. These cannot be violated without serious, deliberate intervention. A dead king stays dead. A destroyed bridge stays destroyed.

2. **Maximum creative freedom**: Within those invariants, the system should have full latitude to improvise, elaborate, and respond creatively. That's the entire point of using an LLM — the generative capability is the value.

This tension — *be creative, but don't break the rules* — exists in any domain where an agentic system operates within constraints. D&D just makes the constraints legible and the violations obvious, providing a natural sandbox for testing the boundaries of agentic systems.

This project is a working reference extracted from [tor](https://github.com/dunnam/tor), a D&D campaign assistant where long-running sessions routinely exceed the context window and established world facts get lost, contradicted, or overwritten by player manipulation.

## The Anchor Approach

Anchors are **propositions promoted to load-bearing facts** with explicit rank, authority, and lifecycle management. They're always injected into the system prompt, creating a persistent working memory that the model can't ignore.

```
=== ESTABLISHED FACTS ===
The following facts are VERIFIED and AUTHORITATIVE. You MUST NOT contradict them.

1. [CANON] The East Gate of Tidefall's wall has been breached (rank: 850)
2. [RELIABLE] Baron Krell is a four-armed sahuagin mutant (rank: 750)
3. [RELIABLE] The Tidecaller Conch controls the harbor currents (rank: 700)
4. [PROVISIONAL] Captain Nyssa secretly negotiates with the sahuagin (rank: 550)
=== END ESTABLISHED FACTS ===
```

Key properties:
- **Ranked [100-900]**: Higher-ranked anchors survive budget eviction. Rank changes via reinforcement and decay.
- **Authority hierarchy**: PROVISIONAL → UNRELIABLE → RELIABLE → CANON. Upgrade-only; CANON is never auto-assigned.
- **Budget-constrained**: Hard cap (default 20) prevents prompt bloat. Lowest-ranked non-pinned anchors are evicted when over budget.
- **Conflict-aware**: Negation detection and authority-based resolution handle contradictions.
- **Decay and reinforcement**: Exponential time-based decay; threshold-based reinforcement boosts rank and upgrades authority.

## How It Works

```
User message → DICE Proposition Extraction → Duplicate Detection → Conflict Check
    → Trust Evaluation → Promotion (if qualified) → Anchor Budget Enforcement
    → Context Assembly (ranked anchors injected into system prompt)
    → LLM Response → Drift Evaluation (against ground truth)
```

See [docs/architecture.md](docs/architecture.md) for detailed data flow diagrams.

## Simulation Harness

The project includes an adversarial simulation harness that tests anchor resilience:

1. **Seed anchors** from ground truth facts
2. **Run scripted adversarial turns** (displacement, drift, recall probes)
3. **Evaluate each response** against ground truth for contradictions
4. **Measure drift metrics**: survival rate, contradiction count, attribution accuracy

Example: the `adversarial-contradictory` scenario runs 15 turns with 5 ground truth facts, using attack strategies like SUBTLE_REFRAME, CONFIDENT_ASSERTION, and AUTHORITY_HIJACK.

See [docs/simulation-harness.md](docs/simulation-harness.md) for scenario design and metrics.

## Current Status

This is an **early-stage exploration**, not a production system. The core anchor lifecycle (promotion, reinforcement, decay, eviction, conflict detection) works. The simulation harness runs end-to-end with a Vaadin UI. Several areas need further work (see [docs/known-limitations.md](docs/known-limitations.md)).

## Tech Stack

| Component | Version |
|-----------|---------|
| Java | 25 |
| Spring Boot | 3.5.10 |
| Vaadin | 24.6.4 |
| Neo4j | 5.x (Drivine ORM) |
| Embabel Agent | 0.3.5-SNAPSHOT |
| DICE | 0.1.0-SNAPSHOT |

## Quick Start

```bash
# Prerequisites: Java 25, Docker

# Start Neo4j
docker-compose up -d

# Run (needs an OpenAI-compatible API key)
OPENAI_API_KEY=sk-... ./mvnw.cmd spring-boot:run

# Simulation UI: http://localhost:8089
# Chat UI: http://localhost:8089/chat
# Neo4j browser: http://localhost:7474 (neo4j/diceanchors123)
```

## Documentation

| Document | Description |
|----------|-------------|
| [Anchors as Working Memory](docs/anchors-as-working-memory.md) | Theory, related work, and the Anchor approach |
| [Architecture](docs/architecture.md) | System architecture and data flow diagrams |
| [Simulation Harness](docs/simulation-harness.md) | Adversarial testing methodology, scenarios, and metrics |
| [Known Limitations](docs/known-limitations.md) | Identified gaps, honest assessment, and roadmap |

## References

- Laban, P., Hayashi, H., Zhou, Y., & Neville, J. (2025). *LLMs Get Lost In Multi-Turn Conversation*. [arXiv:2505.06120](https://arxiv.org/abs/2505.06120)
- Packer, C., Wooders, S., Lin, K., Fang, V., Patil, S.G., Stoica, I., & Gonzalez, J.E. (2023). *MemGPT: Towards LLMs as Operating Systems*. [arXiv:2310.08560](https://arxiv.org/abs/2310.08560)
- Radhakrishnan, A. et al. (2025). *Graphiti: Building Real-Time Knowledge Graphs for Agentic Applications*. [arXiv:2501.13956](https://arxiv.org/abs/2501.13956)
- Gutiérrez, B.J. et al. (2024). *HippoRAG: Neurobiologically Inspired Long-Term Memory for Large Language Models*. [arXiv:2405.14831](https://arxiv.org/abs/2405.14831) (NeurIPS 2024)
- Xu, J. et al. (2025). *Cognitive Workspace: Active Memory Management for LLMs*. [arXiv:2508.13171](https://arxiv.org/abs/2508.13171)
- Anthropic. (2025). *Building Effective Agents*. [anthropic.com](https://anthropic.com/engineering/effective-context-engineering-for-ai-agents)

## License

This project is a research exploration. See LICENSE for details.
