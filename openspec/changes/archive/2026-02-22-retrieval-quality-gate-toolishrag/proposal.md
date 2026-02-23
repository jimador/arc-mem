## Why

Anchor injection is currently bulk: all active anchors (up to budget) are injected into the system prompt regardless of relevance to the current turn. A RELIABLE anchor about "party inventory" is injected even when the user asks about "NPC motivations." This wastes token budget, dilutes signal-to-noise, and gives the LLM no explicit mechanism to request only the grounding it actually needs. The ToolishRAG pattern replaces bulk injection with tool-mediated, demand-driven retrieval — the LLM calls a retrieval tool when it needs grounding, and a quality gate scores and filters results by relevance before returning them.

## What Changes

- Introduce a retrieval quality gate that scores each anchor's relevance to the current query before injection, filtering below a configurable threshold.
- Add an `@LlmTool` retrieval tool (`retrieveAnchors`) that the LLM can invoke to search the anchor store by semantic similarity, replacing or supplementing bulk system-prompt injection.
- Add a relevance scoring function combining semantic similarity, authority weight, tier recency, and confidence.
- Add a `RetrievalMode` configuration (`BULK`, `TOOL`, `HYBRID`) controlling whether anchors are injected via the existing bulk path, the new tool path, or both (bulk baseline + tool for additional retrieval).
- Wire quality gate metrics into OTEL: retrieval count, relevance score distribution, citation rate (anchors retrieved vs. anchors the LLM actually referenced in output).
- Preserve backward compatibility: `BULK` mode retains current behavior with no quality gate; `HYBRID` is the default, injecting top-k high-relevance anchors as baseline context while making the full store available via tool.

## Capabilities

### New Capabilities
- `retrieval-quality-gate`: Relevance scoring, quality threshold filtering, and retrieval mode configuration for anchor injection.

### Modified Capabilities
- `anchor-assembly`: Assembly pipeline gains a pre-injection relevance filter step and support for reduced baseline injection in HYBRID mode.
- `observability`: New retrieval-specific span attributes and metrics (retrieval count, relevance scores, citation tracking).

## Impact

- **Assembly pipeline** (`AnchorsLlmReference`, `PromptBudgetEnforcer`): Relevance filter inserted between retrieval and budget enforcement. HYBRID mode reduces baseline injection count.
- **Chat flow** (`ChatActions`): Tool registry gains `retrieveAnchors` tool. System prompt updated to instruct LLM on tool availability.
- **Simulation flow** (`SimulationTurnExecutor`): Retrieval mode configurable per scenario. Tool-based retrieval available for adversarial evaluation.
- **Configuration** (`DiceAnchorsProperties`): New `RetrievalConfig` record under `dice-anchors.retrieval.*` with mode, relevance threshold, top-k, and scoring weights.
- **Observability**: New span attributes on assembly and retrieval operations.
- **No persistence changes**: Relevance scoring uses in-memory computation over existing anchor data (text similarity via existing LLM infrastructure, no new vector store required).
