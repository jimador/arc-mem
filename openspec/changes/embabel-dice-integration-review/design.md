## Context

dice-anchors integrates with Embabel Agent (0.3.5-SNAPSHOT) and DICE (0.1.0-SNAPSHOT) for chat orchestration and proposition extraction. The current integration is straightforward and functional:

- **Single action pattern**: `ChatActions` uses one `@Action(trigger = UserMessage.class)` method (`respond()`) that handles all user messages imperatively. No multi-action orchestration, goals, or conditions.
- **Tool organization**: `AnchorTools` exposes 5 tools (3 reads, 2 writes) in a single `@MatryoshkaTools` class; `AnchorRetrievalTools` is a separate tool group conditionally registered based on retrieval mode (HYBRID/TOOL). Tools are properly described with `@LlmTool` annotations.
- **Context assembly**: Manual via `AnchorsLlmReference` and `PropositionsLlmReference` which load anchors from `AnchorEngine` and build template variables. Jinja2 template (`dice-anchors.jinja`) renders with RFC 2119 compliance framework and tiered authority levels.
- **Chatbot mode**: `AgentProcessChatbot.utilityFromPlatform()` — utility mode, no goal-directed orchestration.
- **State management**: String context ID extracted from `ActionContext.getProcessContext().getProcessOptions()`. No use of Blackboard or typed parameter binding.
- **DICE pipeline**: Event-driven in chat (`@EventListener @Async ConversationPropositionExtraction`), synchronous in simulation. Uses `LlmPropositionExtractor` → `LlmPropositionReviser` → `PropositionPipeline`. Known fragile points: 13-param `Proposition.create()` overload, `DiceAnchorsChunkHistoryStore` delegation wrapper.

### Current Data Flow

```mermaid
graph TD
    UM[UserMessage] --> CA[ChatActions.respond]
    CA --> ALR[AnchorsLlmReference<br/>loads anchors]
    CA --> PLR[PropositionsLlmReference<br/>loads propositions]
    ALR --> TP[Template: dice-anchors.jinja]
    PLR --> TP
    CA --> AI[context.ai.withDefaultLlm]
    AI --> RESP[AssistantMessage]
    RESP --> CPE[ConversationPropositionExtraction<br/>@EventListener @Async]
    CPE --> PIA[PropositionIncrementalAnalyzer]
    PIA --> PP[PropositionPipeline<br/>extract → revise → persist]
    PP --> AR[AnchorRepository<br/>Neo4j]
    PP --> AP[AnchorPromoter<br/>multi-gate pipeline]
    AP --> AE[AnchorEngine<br/>promote/demote/reinforce]
```

## Goals / Non-Goals

**Goals:**
- Create comprehensive inventory of Embabel Agent APIs used in dice-anchors vs. available capabilities
- Restructure `AnchorTools` and `AnchorRetrievalTools` following CQS principle (query tools separate from mutation tools)
- Evaluate goal-directed orchestration for the trust pipeline; document architectural trade-offs and recommendation
- Identify Blackboard/typed state opportunities for future multi-action refactoring
- Document DICE integration fragile points and establish monitoring strategy
- Assess utility vs. goal-directed chatbot modes; recommend best fit

**Non-Goals:**
- Implementing goal-directed orchestration (evaluation only)
- Adopting Blackboard state management (defer to future change)
- Upgrading Embabel or DICE versions
- Changing DICE extraction pipeline architecture (event-driven chat, synchronous sim remain)
- Adding new anchor capabilities or trust/authority semantics

## Decisions

### D1: Tool Group Restructuring

**Decision**: Split `AnchorTools` into two `@MatryoshkaTools` classes — `AnchorQueryTools` (read-only) and `AnchorMutationTools` (state-changing).

**Rationale**: Embabel tool groups map to LLM tool-use registrations. Separating query from mutation follows the CQS principle and allows selective tool registration (e.g., read-only mode for simulation, full access for chat).

**Current state** (`AnchorTools.java`):
- `queryFacts(String subject)` — read
- `listAnchors()` — read
- `pinFact(String anchorId)` — write
- `unpinFact(String anchorId)` — write
- `demoteAnchor(String anchorId)` — write

**Proposed split**:
- `AnchorQueryTools`: `queryFacts`, `listAnchors`, `retrieveAnchors` (consolidated from `AnchorRetrievalTools`), with conditional registration based on retrieval mode
- `AnchorMutationTools`: `pinFact`, `unpinFact`, `demoteAnchor`

**Benefits**: CQS separation, selective tool registration (read-only for simulation, full access for chat), clearer tool descriptions.

### D2: Goal Modeling Evaluation (Document Only)

**Decision**: Evaluate whether Embabel's `@AchievesGoal` and typed action chaining could model the anchor trust pipeline, but do NOT implement in this change.

**Rationale**: The trust pipeline (confidence gate → dedup gate → conflict gate → trust gate → promote gate) is a natural fit for goal-directed orchestration. However, the current imperative approach works and changing it is high-risk. This change documents the evaluation findings for a potential future change.

**Evaluation scope**:
- Can the trust pipeline (confidence → dedup → conflict → trust → promote gates in `AnchorPromoter`) be expressed as chained `@Action` methods with typed inputs/outputs?
- Would `@AchievesGoal("Anchor promoted with trust verification")` improve observability and testability?
- Does Embabel's action dependency resolution (via `@Condition` predicates and parameter type matching) add architectural value over current explicit gate sequencing?
- What are the trade-offs? Neo4j mutations are side-effects; do they fit Embabel's type-driven data flow model?

**Recommendation scope**: Document findings and either recommend adoption with refactoring plan, or justify deferring until goal-directed patterns are more broadly adopted in codebase.

### D3: DICE Pipeline Fragility Documentation

**Decision**: Document DICE integration fragile coupling points and establish monitoring strategy for SNAPSHOT API changes.

**Known fragile points**:
- `PropositionView.toDice()` uses 13-param `Proposition.create()` overload (vulnerable to API evolution)
- `DiceAnchorsChunkHistoryStore` delegation wrapper around `ChunkHistoryStore` interface
- `PropositionIncrementalAnalyzer` windowed analysis (configurable window size, overlap, trigger interval)

**Approach**: Document current API surface in `docs/dev/dice-integration.md`; monitor DICE SNAPSHOT releases for breaking changes; add integration tests to catch API misalignments early.

## Risks / Trade-offs

**[Tool group split may affect LLM tool-use behavior]** → Splitting `AnchorTools` into two tool groups changes the LLM's view of available tools. Tool descriptions and grouping influence selection. Mitigation: ensure tool descriptions remain clear and specific; run chat smoke tests after restructuring; verify LLM doesn't regress in tool usage patterns.

**[DICE SNAPSHOT API drift]** → 0.1.0-SNAPSHOT APIs may change without notice. The 13-param `Proposition.create()` overload is especially fragile. Mitigation: document exact API surface; monitor SNAPSHOT releases; add integration tests for DICE compatibility.

**[Goal modeling evaluation may surface architectural mismatch]** → Embabel's action chaining assumes typed data flow between actions (outputs of one action feed as inputs to next). Anchor operations are primarily side-effectful (Neo4j mutations). Mitigation: evaluation is document-only; no code implementation; if adopted, would require significant refactoring.

**[Blackboard adoption deferred]** → Typed state management via Blackboard is not immediately needed (single-action flow). Future multi-action refactoring would benefit from it. Mitigation: document opportunity and defer to future change.

## Open Questions

- Should `retrieveAnchors` remain conditional (based on retrieval mode), or be always available?
- Is `AgentProcessChatbot.utilityFromPlatform()` (utility mode) the right chatbot choice, or should we investigate goal-directed mode?
- What are the specific architectural benefits of multi-action orchestration for dice-anchors? (Observable stepping? Better error handling? Easier testing?)
