# ARC Whitepaper: Sources and Attribution

**Date**: 2026-03-17
**Status**: Preliminary — for reviewer use

---

## Core Problem Framing

Laban, P., Vig, J., Gehrmann, S., Sheng, E., Jhamtani, H., Ma, T., & Tumlin, W. (2025). *LLMs Get Lost In Multi-Turn Conversation*. arXiv:2505.06120.

Maharana, A., Lee, D., Tuladhar, B., Koralus, P., Bhatt, M., & Bansal, M. (2024). *Evaluating Very Long-Term Conversational Memory of LLM Agents*. LoCoMo dataset. arXiv:2402.17753.

---

## Cognitive Architecture Inspiration

Anderson, J. R. et al. ACT-R cognitive architecture. Carnegie Mellon University. https://act-r.psy.cmu.edu/

Wu, S. et al. (2024–2025). *Cognitive LLMs: Towards Integrating Cognitive Architectures and Large Language Models for Manufacturing Decision-making*. arXiv:2408.09176. — Orthogonal work: embeds ACT-R principles into model weights rather than the prompt layer.

*Human-Like Remembering and Forgetting for Large Language Model Agents* (2026). ACM. — Complementary: applies ACT-R activation-based retrieval to long-term external memory retrieval, not working memory governance.

Meghdadi, M. et al. (2026). *Integrating LLM Embeddings into ACT-R Memory Retrieval*. Frontiers in Psychology. — Orthogonal: enhances ACT-R's internal retrieval mechanism with LLM embeddings.

Wu, S. et al. (2023–2025). *Prompt-Enhanced ACT-R and Soar Cognitive Models*. AAAI. — Orthogonal: uses LLMs as tooling to instantiate symbolic cognitive models, not to govern prompt-layer memory.

---

## Memory Systems Compared

Packer, C., Fang, V., Patil, S. G., Moon, K., Wooders, S., & Gonzalez, J. E. (2023). *MemGPT: Towards LLMs as Operating Systems*. arXiv:2310.08560.

Letta memory blocks. Letta documentation. https://docs.letta.com/guides/agents/memory-blocks

An, T. (2025). *Cognitive Workspace: Active Memory Management for LLMs — An Empirical Study of Functional Infinite Context*. arXiv:2508.13171. — Closest architectural parallel to ARC.

Hu, M. et al. (2024). *HiAgent: Hierarchical Working Memory Management for Solving Long-Horizon Agent Tasks with Large Language Models*. arXiv:2408.09559.

*Mem0: Building Production-Ready AI Agents with Scalable Long-Term Memory*. arXiv:2504.19413.

Xu, W. et al. (2025). *A-MEM: Agentic Memory System for LLM Agents*. arXiv:2502.12110.

Rasmussen, P. et al. (2025). *Zep: A Temporal Knowledge Graph Architecture for Agent Memory*. arXiv:2501.13956. (Also known as Graphiti.)

Edge, D. et al. (2024). *From Local to Global: A Graph RAG Approach to Query-Focused Summarization*. Microsoft Research. https://microsoft.github.io/graphrag/

Gutiérrez, B. J. et al. (2024). *HippoRAG: Neurobiologically Inspired Long-Term Memory for Large Language Models*. arXiv:2405.14831.

---

## Belief Revision Theory

Alchourrón, C. E., Gärdenfors, P., & Makinson, D. (1985). On the logic of theory change: Partial meet contraction and revision functions. *Journal of Symbolic Logic*, 50(2), 510–530. — Foundational AGM framework for belief revision; theoretical basis for ARC's conflict resolution semantics.

Hansson, S. O. (2024). *Logic of Belief Revision*. Stanford Encyclopedia of Philosophy. https://plato.stanford.edu/entries/logic-belief-revision/

---

## Security and Adversarial Evaluation

Zou, A. et al. (2024). *PoisonedRAG: Knowledge Corruption Attacks to Retrieval-Augmented Generation of Large Language Models*. arXiv:2402.07867. — Basis for adversarial-poisoned-player scenario design.

OWASP. *LLM Prompt Injection Prevention Cheat Sheet*. https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html

---

## Implementation Stack

Embabel Agent framework (orchestration, tool calling, agent lifecycle). https://github.com/embabel/embabel-agent — Version 0.3.5-SNAPSHOT.

DICE (Declarative Information and Context Extraction — proposition extraction from LLM output). https://github.com/embabel/dice — Version 0.1.0-SNAPSHOT.

Spring Boot 3.5.10. https://spring.io/projects/spring-boot

Java 25. https://openjdk.org/

Neo4j 5.x graph database. https://neo4j.com/ — Drivine ORM for Cypher query mapping.

Vaadin 24.6.4 (simulation and benchmark UI). https://vaadin.com/

---

## Inspiration

*The Sleeping LLM* series — memory consolidation in LLM agents during idle cycles. Zenodo records 18778768 and 18779159.

Johnson, R. (2026). *Agent Memory Is Not A Greenfield Problem*. Embabel Engineering Blog. — Framing of the working memory problem in terms of existing cognitive architecture research.
