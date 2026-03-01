# Feature: Embabel Goal-Directed Orchestration Evaluation

## Context

Anchor lifecycle follows a 5-gate trust pipeline:
1. **Confidence gate** — Minimum extraction confidence threshold
2. **Dedup gate** — Semantic or heuristic duplicate detection
3. **Conflict gate** — LLM/negation-based conflict detection
4. **Trust gate** — Multi-signal trust evaluation (source authority, extraction confidence, reinforcement history)
5. **Promote gate** — Explicit promotion to anchor status

This sequence is currently imperative: `AnchorPromoter` calls gates sequentially, each returning boolean. Embabel provides goal-directed orchestration via `@Goal`, `@Condition`, and `@AchievesGoal` annotations that could model this as typed action chaining.

**Goal**: Evaluate whether goal-directed orchestration is a good fit for anchor lifecycle; document architectural trade-offs and recommendation without implementing refactoring.

## Proposal Seed

### Why This Feature

Goal-directed evaluation provides:
1. **Architectural clarity**: Assesses whether Embabel patterns align with anchor operations
2. **Trade-off analysis**: Documents what's gained (observability, testability) vs. cost (refactoring scope, type mismatch with Neo4j mutations)
3. **Future roadmap input**: Clear recommendation informs next roadmap or change decision
4. **Pattern catalog**: Evaluates Embabel's full toolkit (goals, conditions, state) for future use cases

### What Changes

**Documentation** (no code implementation):
- Create `docs/dev/embabel-goal-modeling.md` with evaluation
- Document trust pipeline analysis: current 5-gate sequence mapped to Embabel patterns
- Document architectural trade-offs: side-effect handling (Neo4j mutations vs. typed data flow), refactoring scope
- Create comparison table: imperative (current) vs. goal-directed approach
- Assess chatbot modes: utility mode (current) vs. goal-directed alternatives
- Document Blackboard state opportunity with "deferred to future change" marker
- Clear recommendation: adopt now, adopt with deferred refactoring, or defer entirely

### Success Criteria

- [ ] `docs/dev/embabel-goal-modeling.md` created with complete evaluation
- [ ] Trust pipeline analysis: current sequence + Embabel pattern mapping
- [ ] Architectural trade-offs: side-effect handling, refactoring scope, implementation cost
- [ ] Comparison table: observability, testability, error handling, complexity (current vs. goal-directed)
- [ ] Chatbot mode assessment: utility mode vs. goal-directed with recommendation
- [ ] Blackboard state opportunity documented (explicitly marked "deferred")
- [ ] Clear recommendation (no ambiguity; guides next decision)
- [ ] Documentation referenced from DEVELOPING.md or README
- [ ] No code changes required (evaluation-only feature)

### Visibility

- **UI**: None (documentation-only)
- **Observability**: Evaluation document guides future OTEL span design for multi-action flows

## Embabel API Research Findings

*Source: Embabel MCP server + codebase analysis. See `research/R01-embabel-api-surface.md` for full details.*

### How Embabel Action Chaining Actually Works

Actions chain via **type matching**: the return type of one `@Action` method becomes available as an input parameter to subsequent actions. The planning engine (GOAP) resolves execution order automatically.

**From Embabel examples (`Stages.java`):**
```java
@Action
public Cook chooseCook(UserInput userInput, Ai ai) {
    return ai.withAutoLlm().createObject(..., Cook.class);
}

@Action
public Order takeOrder(UserInput userInput, Ai ai) {
    return ai.withAutoLlm().createObject(..., Order.class);
}

@Action
@AchievesGoal(description = "Cook the meal according to the order")
public Meal prepareMeal(Cook cook, Order order, UserInput userInput, Ai ai) {
    // cook and order come from previous actions' return types
}
```

**Chaining**: `chooseCook() → Cook` + `takeOrder() → Order` → both available for `prepareMeal(Cook, Order)` → `Meal` achieves goal.

### Key Constraint: Side-Effect Model

Embabel's type-matching assumes **immutable typed data flow** — action outputs are values, not mutation descriptors. Anchor operations are **Neo4j mutations** (side-effects). This is the core tension.

**Options for side-effect handling:**
- **Option A**: Apply mutations inside each action (impure actions — breaks data flow model)
- **Option B**: Return side-effect descriptors; apply mutations at the end (functional style — high refactoring cost)
- **Option C**: Hybrid — gates return result objects; final `@AchievesGoal` action applies mutations

### GOAP vs. Utility Mode

**Current**: `AgentProcessChatbot.utilityFromPlatform(agentPlatform)` — utility mode

**GOAP mode**: `new AgentProcessChatbot(agentPlatform, agentSource, conversationFactory)`
- Uses `@AchievesGoal` to determine when objective is satisfied
- Plans action sequences based on available types and costs
- `@Action(cost=100.0)` influences path selection (planner prefers cheaper paths)
- Multi-action per message with condition-based branching

**Question for evaluation**: Does the trust pipeline benefit from GOAP planning? Or is imperative sequencing sufficient since the gate order is fixed?

### Blackboard Binding System

State passed between actions via blackboard:
```kotlin
agentProcess.bindProtected(CONVERSATION_KEY, conversation)  // persists across turns
agentProcess.addObject(userMessage)                          // available for type matching
```

**Current dice-anchors state management**: String context ID extracted from `ActionContext.getProcessContext().getProcessOptions()` — no typed bindings.

**Opportunity**: Could pass `AnchorEngine`, `TrustPipeline` state as typed Blackboard entries instead of manual extraction. Requires multi-action refactoring.

### Message vs. Trigger Pattern

- **UserMessage** — persists in conversation history
- **ChatTrigger** — ephemeral signal that triggers actions without cluttering history

**Opportunity for dice-anchors**: Could use `ChatTrigger` for internal operations (reinforcement, extraction triggers) that shouldn't appear in conversation history.

### @Export for Remote Goal Access

```java
@AchievesGoal(
    description = "Write an amusing writeup...",
    export = @Export(
        remote = true,
        name = "starNewsWriteupJava",
        startingInputTypes = {StarPerson.class, UserInput.class}
    )
)
```

**Opportunity**: Could expose anchor lifecycle operations as remote goals for testing/simulation.

## Design Sketch

### Goal Modeling Evaluation Document Structure

```
docs/dev/embabel-goal-modeling.md
├── Overview
│   └── Can goal-directed orchestration improve anchor lifecycle?
├── Current Anchor Lifecycle (Imperative)
│   ├── 5-gate sequence: confidence → dedup → conflict → trust → promote
│   ├── Current implementation: AnchorPromoter calls gates sequentially
│   ├── Gate behavior: each returns boolean (pass/fail)
│   └── File:line references for current code
├── Trust Pipeline Analysis (Goal-Directed Mapping)
│   ├── Goal: @AchievesGoal("Anchor promoted with trust verification")
│   ├── Actions with typed chaining:
│   │   ├── @Action validateConfidence(PropositionCandidate) → ConfidenceResult
│   │   ├── @Action checkDedup(ConfidenceResult) → DedupResult
│   │   ├── @Action detectConflict(DedupResult) → ConflictResult
│   │   ├── @Action evaluateTrust(ConflictResult) → TrustResult
│   │   └── @Action @AchievesGoal promoteToAnchor(TrustResult) → PromotedAnchor
│   └── Type flow: each action's return type feeds the next action's parameter
├── Architectural Trade-Offs
│   ├── Side-effect handling
│   │   ├── Problem: Embabel assumes typed data flow (immutable outputs feed as inputs)
│   │   ├── Reality: Anchor operations are Neo4j mutations (side-effects)
│   │   ├── Mitigation: Could return side-effect descriptors + apply mutations separately
│   │   └── Cost: Refactoring scope (HIGH)
│   ├── Observability gains
│   │   ├── Current: Sequential gate calls in AnchorPromoter (opaque)
│   │   ├── Goal-directed: Each action is observable via Embabel instrumentation
│   │   └── Gain: Better OTEL span hierarchy and tracing
│   ├── Testability gains
│   │   ├── Current: Mock gates in promotion tests (integration-heavy)
│   │   ├── Goal-directed: Test each condition independently (unit-friendly)
│   │   └── Gain: Easier isolation and independent testing
│   └── Complexity trade-off
│       ├── Current: Straightforward sequential logic (easy to understand)
│       ├── Goal-directed: Multi-action chaining with condition predicates (more abstraction)
│       └── Trade-off: Observability + testability vs. code clarity
├── Chatbot Mode Assessment
│   ├── Current mode: AgentProcessChatbot.utilityFromPlatform()
│   │   ├── Single action per message
│   │   ├── No orchestration or branching
│   │   ├── No goal-directed flow
│   │   └── Suitable for simple request-response patterns
│   ├── Goal-directed alternative
│   │   ├── Multi-action per message
│   │   ├── Condition-based branching
│   │   ├── Goal-driven flow
│   │   └── Suitable for complex orchestration
│   └── Recommendation for dice-anchors
│       └── [To be determined by evaluation]
├── Blackboard State Opportunity
│   ├── Current state management: String context ID extracted from ActionContext
│   ├── Opportunity: Typed state bindings via @State parameters
│   │   ├── Could pass SimulationRunContext as @State
│   │   ├── Could pass TrustPipeline as @State
│   │   ├── Benefits: Type safety, explicit parameter passing
│   │   └── Drawback: Requires multi-action refactoring
│   └── Status: DEFERRED to future change (requires broader multi-action refactoring)
├── Recommendation
│   ├── Option 1: Adopt goal-directed orchestration for anchor lifecycle
│   │   ├── Benefit: Observability, testability improvements
│   │   ├── Cost: HIGH refactoring scope + type mismatch resolution
│   │   └── Timeline: Future roadmap (major change)
│   ├── Option 2: Adopt with deferred refactoring (hybrid approach)
│   │   ├── Keep current imperative gates; add @Action wrappers
│   │   ├── Benefit: Gradual migration path
│   │   ├── Cost: Temporary code duplication during transition
│   │   └── Timeline: Phased approach over multiple releases
│   └── Option 3: Defer goal-directed patterns entirely
│       ├── Rationale: Current patterns work; refactoring cost not justified
│       ├── Condition: Revisit if multi-action patterns become standard in codebase
│       └── Timeline: Defer until broader Embabel adoption
├── Comparison Table
│   └── Current vs. Goal-Directed
│       ├── Observability
│       ├── Testability
│       ├── Error Handling Clarity
│       ├── Implementation Complexity
│       ├── Refactoring Scope
│       └── Long-term Maintainability
└── References
    ├── Embabel documentation links
    ├── Current anchor lifecycle code (file:line)
    ├── Embabel examples for goal-directed patterns
    └── Related roadmap features (F1, F2, F3)
```

### Comparison Table Template

| Aspect | Current (Imperative) | Goal-Directed | Winner |
|--------|----------------------|---------------|---------|
| **Observability** | Sequential gate calls (opaque) | Each action observable via instrumentation | Goal-directed |
| **Testability** | Integration-heavy (mocked gates) | Unit-friendly (independent conditions) | Goal-directed |
| **Error Handling** | Boolean pass/fail (limited context) | Exception types + structured results | Goal-directed |
| **Code Clarity** | Straightforward flow | More abstraction (harder to follow) | Current |
| **Refactoring Scope** | None | HIGH (side-effect handling) | Current |
| **Type Safety** | Loose (boolean returns) | Strict (typed action chaining) | Goal-directed |
| **Long-term Maintainability** | Brittle (mutation-heavy) | More robust (data-flow oriented) | Goal-directed |

## Dependencies

- **Depends on**: F1 (Embabel API Inventory) — inventory provides API reference for evaluation
- **References**: F2, F3 (to provide context of current integration)

## Open Questions

1. Are there other Neo4j mutation points in anchor lifecycle that would require refactoring?
   - **Recommendation**: Audit all gates during evaluation
2. Would a hybrid approach (incremental @Action wrappers) be practical?
   - **Recommendation**: Document as Option 2 in recommendation section
3. Are there Embabel examples of handling side-effects in goal-directed flows?
   - **Recommendation**: Research Embabel patterns and document findings

## Acceptance Gates

1. **Evaluation completeness**: All sections populated with thorough analysis
2. **Trade-off accuracy**: Architectural trade-offs validated against current code
3. **Comparison fairness**: Both approaches analyzed with balanced assessment
4. **Recommendation clarity**: Clear, actionable recommendation with reasoning
5. **Future-proofing**: Evaluation captures enough context for future decision-making
6. **DEVELOPING.md integration**: Documentation linked from project development guide

## Implementation Sequence (Documentation Only)

1. Analyze current `AnchorPromoter` and 5-gate sequence
2. Map to Embabel patterns: @Goal, @Condition, @AchievesGoal, typed actions
3. Assess side-effect handling (Neo4j mutations vs. typed data flow)
4. Research Embabel examples for similar patterns
5. Create comparison table with current vs. goal-directed approach
6. Assess chatbot modes (utility vs. goal-directed)
7. Document Blackboard opportunity with "deferred" marker
8. Write clear recommendation with reasoning

## Next Steps

Once feature is approved:
1. Create OpenSpec change: `/opsx:new` with slug `embabel-goal-modeling`
2. Work through proposal → spec → design → tasks
3. Complete evaluation document with thorough analysis
4. Archive change and sync specs via `/opsx:sync` or `/opsx:archive`

Recommendation will guide next roadmap planning (decide whether to pursue goal-directed patterns in future change).
