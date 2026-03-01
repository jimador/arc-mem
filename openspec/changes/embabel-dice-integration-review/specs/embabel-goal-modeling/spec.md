## ADDED Requirements

### Requirement: Blackboard state opportunity document

The evaluation document SHALL identify opportunities for using Embabel's Blackboard/OperationContext for typed state management in future multi-action refactoring.

#### Scenario: Current state management is documented
- **WHEN** the state management section is reviewed
- **THEN** it SHALL document current approach: string context ID extracted from `ActionContext.getProcessContext()`
- **AND** SHALL note that Blackboard infrastructure exists (visible in test mocks) but is unused in production

#### Scenario: Future opportunity is clearly marked
- **WHEN** the blackboard opportunity section is complete
- **THEN** it SHALL describe how typed state bindings (`@State` parameters) could improve future multi-action workflows
- **AND** SHALL explicitly mark this as "deferred to future change — requires multi-action refactoring"

## ADDED Requirements

### Requirement: Goal-directed orchestration evaluation document

The project SHALL include an evaluation document assessing whether Embabel's goal-directed orchestration (`@Goal`, `@Condition`, `@AchievesGoal`, typed action chaining) is a good fit for anchor lifecycle operations. The evaluation SHALL focus on the trust pipeline and document architectural trade-offs.

#### Scenario: Trust pipeline analysis is thorough
- **WHEN** the evaluation covers the trust pipeline
- **THEN** it SHALL document the current 5-gate sequence (confidence → dedup → conflict → trust → promote)
- **AND** SHALL assess whether this maps naturally to Embabel's multi-action orchestration with chained actions and conditions
- **AND** SHALL identify specific `@Condition` predicates and `@AchievesGoal` statements that would be needed

#### Scenario: Architectural trade-offs are identified
- **WHEN** the trade-offs section is complete
- **THEN** it SHALL address side-effect handling: Embabel assumes typed data flow (outputs feed as inputs), but anchor operations are primarily Neo4j mutations
- **AND** SHALL document the refactoring scope required (low, medium, high)
- **AND** SHALL state recommendation (adopt now, adopt with deferred refactoring, defer to future change)

#### Scenario: Current vs. goal-directed comparison included
- **WHEN** comparison section is reviewed
- **THEN** it SHALL include a table comparing: imperative approach vs. goal-directed approach
- **AND** SHALL cover: observability, testability, error handling clarity, complexity

### Requirement: Chatbot mode assessment

The evaluation document SHALL assess utility mode vs. goal-directed mode for dice-anchors chatbot orchestration.

#### Scenario: Mode characteristics documented
- **WHEN** chatbot mode section is reviewed
- **THEN** it SHALL describe `AgentProcessChatbot.utilityFromPlatform()` (utility mode: single action per message, no orchestration)
- **AND** SHALL describe goal-directed alternative (multi-action, condition-based flow)
- **AND** SHALL state which mode better fits dice-anchors with rationale
