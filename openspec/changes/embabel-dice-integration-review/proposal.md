## Why

dice-anchors uses Embabel Agent in a straightforward imperative style: single-action chatbot with tool exposure and template rendering. The framework offers richer capabilities (goal-directed orchestration, condition-based branching, typed state management) that could improve observability and testability. This change audits our current Embabel usage comprehensively, identifies gaps and opportunities, and documents a path for leveraging Embabel more idiomatically without requiring major refactoring.

## What Changes

- **Comprehensive Embabel API inventory** — Document all annotations, interfaces, patterns, and best practices; identify what we use vs. what's available
- **Tool restructuring** — Split `AnchorTools` into `AnchorQueryTools` (reads) and `AnchorMutationTools` (writes) following Embabel best practices; consolidate `AnchorRetrievalTools`
- **Goal/condition evaluation** — Assess whether the trust pipeline (confidence → dedup → conflict → trust → promote) could benefit from `@Goal` + `@Condition` orchestration; document findings and architectural trade-offs
- **Blackboard opportunity** — Identify how typed state via Blackboard/OperationContext could improve future multi-action refactoring; defer implementation
- **DICE pipeline documentation** — Document fragile coupling points (13-param `Proposition.create()` overload, `DiceAnchorsChunkHistoryStore` delegation) and monitoring strategy
- **Chatbot mode assessment** — Evaluate utility vs. goal-directed modes; recommend best fit for dice-anchors architecture

## Capabilities

### New Capabilities
- `embabel-api-inventory`: Comprehensive audit of Embabel Agent framework usage in dice-anchors; catalog what we use, what's available but unused, and recommended patterns
- `embabel-goal-modeling`: Evaluation of goal-directed orchestration for trust pipeline; document architectural trade-offs and recommendation
- `embabel-blackboard-opportunity`: Identify how typed state management via Blackboard could improve future multi-action workflows; defer implementation

### Modified Capabilities
- `anchor-llm-tools`: Restructure tool groups to separate query tools from mutation tools; consolidate retrieval tools; follow Embabel best practices
- `dice-integration-review-docs`: Document DICE integration surface, fragile coupling points, and monitoring strategy for SNAPSHOT API changes

## Impact

- **Code**: `chat/` package — split `AnchorTools` into `AnchorQueryTools` and `AnchorMutationTools`; consolidate `AnchorRetrievalTools` into query tools
- **Documentation**: Create Embabel API inventory document; add DICE integration surface documentation under `docs/dev/`
- **Testing**: Existing chat and tool tests require updates after tool group restructuring
- **No version changes**: Embabel 0.3.5-SNAPSHOT and DICE 0.1.0-SNAPSHOT remain as-is
- **Deferred work**: Goal/condition refactoring, Blackboard adoption marked for future implementation
