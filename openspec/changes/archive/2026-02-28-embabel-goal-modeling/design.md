## Context

dice-anchors uses Embabel Agent 0.3.5-SNAPSHOT for chat orchestration. The current integration follows a utility-mode, single-action pattern:

- `ChatConfiguration` creates an `AgentProcessChatbot.utilityFromPlatform(agentPlatform)` chatbot
- `ChatActions` has a single `@Action(canRerun = true, trigger = UserMessage.class)` that handles each user message
- Anchor lifecycle (promotion, conflict resolution, trust evaluation) is orchestrated imperatively in `AnchorPromoter`

The `AnchorPromoter` 5-gate pipeline processes propositions through: confidence filtering, duplicate detection, conflict detection/resolution, trust evaluation, and promotion. Each gate either filters candidates (boolean/zone check) or triggers side-effects (Neo4j mutations via `AnchorEngine`). The pipeline is sequential, with each gate's pass/fail determining whether the next gate executes.

Embabel provides goal-directed orchestration via typed action chaining. In the `Stages.java` example pattern:
```
chooseCook(UserInput) -> Cook
takeOrder(UserInput) -> Order
prepareMeal(Cook, Order, UserInput) -> Meal  [@AchievesGoal]
```
Actions chain by type matching: the return type of one `@Action` becomes an available input parameter to subsequent actions. The GOAP planner resolves execution order and selects lowest-cost paths when alternatives exist.

This feature evaluates whether goal-directed patterns are a good fit for anchor lifecycle operations and documents the analysis for future decision-making.

## Goals / Non-Goals

**Goals:**
- Evaluate whether Embabel goal-directed orchestration improves anchor lifecycle management
- Map the 5-gate trust pipeline to `@Action` + `@AchievesGoal` patterns with typed chaining
- Analyze the core tension between Neo4j side-effects and Embabel's typed data-flow model
- Compare imperative (current) vs. goal-directed approaches across observability, testability, error handling, and complexity
- Assess whether utility-mode chatbot should be replaced with GOAP-mode
- Document blackboard state binding opportunity for future consideration
- Produce a clear, actionable recommendation

**Non-Goals:**
- Implement goal-directed orchestration (evaluation only)
- Refactor `AnchorPromoter` or `ChatActions`
- Change the chatbot mode
- Implement blackboard state bindings
- Document DICE-specific APIs (covered by separate F3 feature)
- Cover deprecated Embabel patterns

## Decisions

### 1. Document Location: `docs/dev/embabel-goal-modeling.md`

Place the evaluation under `docs/dev/` alongside other developer references (e.g., `embabel-api-inventory.md` from F1).

**Why**: Consistent with the established `docs/dev/` convention for implementation-oriented analysis documents.

### 2. Evaluation Document Structure

```
docs/dev/embabel-goal-modeling.md
  1. Overview
     - Purpose: Can goal-directed orchestration improve anchor lifecycle?
     - Scope: Trust pipeline, chatbot mode, blackboard state
  2. Current Anchor Lifecycle (Imperative)
     - 5-gate sequence with behavior descriptions
     - File:line references to AnchorPromoter.java
     - Gate interaction model (sequential, boolean/zone filtering)
     - Side-effect points (where Neo4j mutations occur)
  3. Trust Pipeline Analysis (Goal-Directed Mapping)
     - Typed action chain: PropositionCandidate -> ConfidenceResult -> DedupResult -> ConflictResult -> TrustResult -> PromotedAnchor
     - @AchievesGoal on terminal promote action
     - Cost annotation for path selection (not applicable -- fixed order)
     - Reference to Stages.java chaining pattern
  4. Side-Effect Handling
     - Core tension: typed data flow vs. Neo4j mutations
     - Option A: Impure actions (mutations inside @Action)
     - Option B: Functional descriptors (mutations applied at end)
     - Option C: Hybrid (result objects + terminal mutation)
     - Refactoring scope assessment per option
  5. Comparison Table
     - Dimensions: observability, testability, error handling, code clarity, refactoring scope, type safety
     - Winner per dimension with reasoning
  6. Chatbot Mode Assessment
     - Utility mode: current pattern, characteristics, suitability
     - GOAP mode: constructor pattern, characteristics, applicability
     - Fixed gate order vs. GOAP planning benefit
     - Recommendation
  7. Blackboard State Opportunity
     - Current: string context ID extraction
     - Alternative: typed bindings (AnchorEngine, TrustPipeline as @State)
     - Status: DEFERRED (requires multi-action refactoring)
  8. Recommendation
     - Chosen option with rationale
     - Conditions for re-evaluation
  9. References
     - Embabel docs, API inventory (F1), current code, Stages.java example
```

**Why**: Structure mirrors the feature doc outline. Each section maps to a spec requirement. The document reads top-down: what exists today, what could change, what the trade-offs are, what we recommend.

### 3. Side-Effect Analysis Approach

Evaluate all three options (impure, functional, hybrid) with explicit trade-off tables rather than pre-selecting a winner.

**Why**: The side-effect tension is the core architectural question. Pre-selecting an option would bias the evaluation. Presenting all three with honest trade-offs enables informed decision-making.

### 4. Comparison Table Format

Use a markdown table with columns: Aspect, Current (Imperative), Goal-Directed, Winner, Reasoning.

**Why**: Compact, scannable, forces explicit winner selection per dimension. The "Reasoning" column prevents hand-waving.

### 5. Recommendation Format

State exactly one of: adopt, adopt-deferred, defer. Include rationale and re-evaluation conditions.

**Why**: The feature's primary value is a clear decision signal. Ambiguous or conditional recommendations ("it depends") defeat the purpose.

### 6. Embabel Research via MCP Server

During task execution, use the Embabel MCP server tools (`docs_docs_vectorSearch`, `embabel_agent_findClassSignatureBySimpleName`) to verify API assumptions and discover relevant examples.

**Why**: Ground the evaluation in actual API signatures and documentation rather than assumptions from the research doc alone.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Evaluation biased toward adoption** | Spec requires balanced comparison table with honest winner selection per dimension. Imperative approach wins on code clarity and refactoring scope. |
| **File:line references drift** | Note references are valid as of completion date. Keep references coarse-grained (method level, not exact line). |
| **Recommendation becomes stale** | Include re-evaluation conditions so the recommendation has a built-in expiration trigger. |
| **Embabel API changes in future versions** | Pin analysis to 0.3.5-SNAPSHOT. Note that API evolution could change the assessment. |

## Open Questions

None -- all decisions resolved from the feature doc, prep doc, and research phase.
