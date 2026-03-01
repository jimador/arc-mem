<!-- sync: openspec/specs/dice-integration-review-docs -->
<!-- last-synced: 2026-02-25 -->

# Related Work

Technical positioning summary for anchors.

## Positioning statement

Anchors are a bounded working-memory governance layer for multi-turn LLM interactions.

What anchors explicitly add:
- rank-ordered retention in active context
- authority-aware conflict resolution
- trust-gated promotion
- hard budget cap for active memory
- stress-test harness for contradiction pressure

What anchors are not:
- not a full long-term memory platform
- not a graph-retrieval replacement
- not a complete retrieval quality framework

## Comparison snapshot

| Feature | Anchors | MemGPT/Letta | Zep/Graphiti | Standard RAG |
|---|---|---|---|---|
| Explicit fact lifecycle | yes | partial | partial | no |
| Authority tiers | yes | no | no | no |
| Trust-gated promotion | yes | no | no | no |
| Hard active-memory budget | yes | context-bound | no | top-k fetch |
| Long-term temporal memory | limited | yes | yes | external index |

## Integration perspective

Layering model:

```text
Retrieval / Graph Memory  -> provides candidate evidence
Anchors                   -> governs what stays active in prompt context
LLM response loop         -> consumes governed active context
```

This means Graphiti/Zep-like systems are complementary, not competitors.

## Current gaps in this repo

- shallow long-term transfer policy (working memory is stronger than long-term policy)
- no graph-native retrieval/summarization loop yet
- trust/retrieval quality checks still lightweight
- benchmark corpus still domain-skewed
- poisoning/prompt-injection hardening not complete

## Practical takeaway

Anchors operate as the policy/control plane for active memory. Upstream retrieval systems provide evidence; anchors govern what remains authoritative and injected across turns.

## References

- MemGPT: [arXiv 2310.08560](https://arxiv.org/abs/2310.08560)
- Letta memory blocks: [docs](https://docs.letta.com/guides/agents/memory-blocks)
- Zep temporal KG: [arXiv 2501.13956](https://arxiv.org/abs/2501.13956)
- Graphiti: [repo](https://github.com/getzep/graphiti)
- HippoRAG: [arXiv 2405.14831](https://arxiv.org/abs/2405.14831)
- GraphRAG: [docs](https://microsoft.github.io/graphrag/)
- CRAG: [arXiv 2401.15884](https://arxiv.org/abs/2401.15884)
- Self-RAG: [arXiv 2310.11511](https://arxiv.org/abs/2310.11511)
- OWASP prompt-injection cheat sheet: [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html)
- PoisonedRAG: [arXiv 2402.07867](https://arxiv.org/abs/2402.07867)
