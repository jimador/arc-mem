## Why

The anchor trust pipeline (`AnchorPromoter`) orchestrates a 5-gate promotion sequence -- confidence, dedup, conflict, trust, promote -- using imperative sequential logic. Each gate returns a boolean or filters candidates from a list. This works, but the pattern is opaque to instrumentation, tightly couples gate ordering to a single method body, and makes gates difficult to test in isolation.

Embabel Agent 0.3.5-SNAPSHOT provides goal-directed orchestration via `@Action`, `@AchievesGoal`, and typed action chaining (GOAP planning). These patterns could model the trust pipeline as a chain of typed actions where each gate's output feeds the next gate's input, with the planner resolving execution order automatically.

However, the trust pipeline operates via Neo4j mutations (side-effects), while Embabel's action chaining assumes immutable typed data flow. This tension -- side-effect-heavy operations vs. pure data-flow orchestration -- requires careful evaluation before committing to a refactoring path.

This feature produces an evaluation document that maps the current pipeline to Embabel patterns, analyzes the side-effect handling options, compares imperative vs. goal-directed approaches across multiple dimensions, and delivers a clear recommendation. The evaluation informs whether goal-directed orchestration belongs on a future roadmap.

## What Changes

- **New artifact**: `docs/dev/embabel-goal-modeling.md` -- evaluation document analyzing goal-directed orchestration for anchor lifecycle
- **DEVELOPING.md update**: Reference to the evaluation document
- **No code changes**: This is a documentation-only feature

## Capabilities

### New Capabilities
- `embabel-goal-modeling`: Evaluation document assessing Embabel goal-directed orchestration patterns for anchor trust pipeline, chatbot mode, and blackboard state management

### Modified Capabilities
(None -- additive documentation, no behavior changes)

## Impact

- **Files**: `docs/dev/embabel-goal-modeling.md` (new), `DEVELOPING.md` (updated reference)
- **APIs**: None
- **Config**: None
- **Dependencies**: None
- **Value**: Clear recommendation on whether to adopt goal-directed patterns; eliminates speculative discussion by grounding the decision in concrete analysis of current code vs. Embabel capabilities

## Constitutional Alignment

- RFC 2119 keywords: Spec uses MUST/SHALL/SHOULD per Article I
- No code changes: Articles II-VII (Neo4j, constructor injection, records, anchor invariants, simulation isolation, testing) are not affected
