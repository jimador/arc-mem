# Embabel Goal-Directed Orchestration Evaluation Specification

## ADDED Requirements

### Requirement: Evaluation document exists

An evaluation document SHALL exist at `docs/dev/embabel-goal-modeling.md`. The document MUST be a Markdown file and MUST be reachable from `DEVELOPING.md`.

#### Scenario: Document is present and linked
- **GIVEN** the dice-anchors repository
- **WHEN** a contributor opens `DEVELOPING.md`
- **THEN** a reference to `docs/dev/embabel-goal-modeling.md` is present
- **AND** the linked file exists and is non-empty

### Requirement: Trust pipeline analysis

The evaluation MUST contain a "Trust Pipeline Analysis" section that maps the current 5-gate `AnchorPromoter` sequence to Embabel goal-directed patterns.

#### Scenario: Current pipeline documented
- **GIVEN** the Trust Pipeline Analysis section
- **WHEN** a reader inspects the current pipeline documentation
- **THEN** all five gates are described with their current behavior:
  - Confidence gate (filters below `autoActivateThreshold`)
  - Dedup gate (`DuplicateDetector` -- exact text + LLM verification)
  - Conflict gate (`ConflictDetector` + `ConflictResolver` with KEEP/REPLACE/DEMOTE/COEXIST outcomes)
  - Trust gate (`TrustPipeline` producing `TrustScore` with `promotionZone` and `authorityCeiling`)
  - Promote gate (`AnchorEngine.promote` with budget enforcement)
- **AND** file:line references to `AnchorPromoter.java` are included

#### Scenario: Embabel pattern mapping documented
- **GIVEN** the Trust Pipeline Analysis section
- **WHEN** a reader inspects the goal-directed mapping
- **THEN** each gate is mapped to an Embabel pattern:
  - `@Action` methods with typed return values (e.g., `ConfidenceResult`, `DedupResult`, `ConflictResult`, `TrustResult`)
  - Type-matching chaining (output of one action feeds input of the next)
  - `@AchievesGoal` on the terminal promote action
- **AND** the mapping references the `Stages.java` chaining example from Embabel

### Requirement: Side-effect handling analysis

The evaluation MUST contain a "Side-Effect Handling" section analyzing how Neo4j mutations interact with Embabel's typed data-flow model. The section MUST document three options with trade-offs.

#### Scenario: Three options documented
- **GIVEN** the Side-Effect Handling section
- **WHEN** a reader inspects the options
- **THEN** the following three options are documented:
  - **Option A**: Impure actions -- apply Neo4j mutations inside each `@Action` method
  - **Option B**: Functional style -- return side-effect descriptors; apply mutations at the end
  - **Option C**: Hybrid -- gates return typed result objects; final `@AchievesGoal` action applies all mutations
- **AND** each option includes:
  - A description of the approach
  - Benefits (at least one)
  - Drawbacks (at least one)
  - Estimated refactoring scope (LOW / MEDIUM / HIGH)

#### Scenario: Core tension identified
- **GIVEN** the Side-Effect Handling section
- **WHEN** a reader inspects the problem statement
- **THEN** the document explicitly states that Embabel assumes immutable typed data flow while anchor operations are Neo4j mutations
- **AND** this tension is identified as the primary architectural constraint

### Requirement: Comparison table

The evaluation MUST contain a comparison table contrasting the current imperative approach with a goal-directed approach across at least six dimensions.

#### Scenario: Comparison table is complete
- **GIVEN** the Comparison Table section
- **WHEN** a reader inspects the table
- **THEN** the following dimensions are compared:
  - Observability
  - Testability
  - Error handling clarity
  - Code clarity / complexity
  - Refactoring scope
  - Type safety
- **AND** each dimension has entries for both Current (Imperative) and Goal-Directed approaches
- **AND** each dimension identifies a winner with brief reasoning

### Requirement: Chatbot mode assessment

The evaluation MUST contain a "Chatbot Mode Assessment" section comparing utility mode (current) with GOAP mode.

#### Scenario: Both modes documented
- **GIVEN** the Chatbot Mode Assessment section
- **WHEN** a reader inspects the assessment
- **THEN** utility mode is documented with:
  - Current usage pattern (`AgentProcessChatbot.utilityFromPlatform`)
  - Characteristics (single action per message, no orchestration)
  - File reference to `ChatConfiguration.java`
- **AND** GOAP mode is documented with:
  - Constructor pattern (`new AgentProcessChatbot(agentPlatform, agentSource, conversationFactory)`)
  - Characteristics (multi-action per message, `@AchievesGoal` completion, cost-based path selection)
- **AND** a recommendation is stated for which mode suits dice-anchors

#### Scenario: Fixed gate order question addressed
- **GIVEN** the Chatbot Mode Assessment section
- **WHEN** a reader looks for the GOAP applicability assessment
- **THEN** the document addresses whether the trust pipeline benefits from GOAP planning given that gate order is fixed

### Requirement: Blackboard state opportunity

The evaluation MUST contain a "Blackboard State Opportunity" section documenting the potential for typed state bindings via Embabel's blackboard system.

#### Scenario: Opportunity documented as deferred
- **GIVEN** the Blackboard State Opportunity section
- **WHEN** a reader inspects the section
- **THEN** the current state management approach is documented (string context ID from `ActionContext.getProcessContext().getProcessOptions()`)
- **AND** the blackboard alternative is described (`bindProtected`, `addObject`, typed parameter injection)
- **AND** the section is explicitly marked as "DEFERRED to future change"
- **AND** a rationale for deferral is provided

### Requirement: Clear recommendation

The evaluation MUST conclude with a "Recommendation" section that states one of three options: adopt, adopt-deferred, or defer.

#### Scenario: Recommendation is unambiguous
- **GIVEN** the Recommendation section
- **WHEN** a reader inspects the recommendation
- **THEN** exactly one of the following is stated:
  - **Adopt**: Goal-directed orchestration SHOULD be implemented for anchor lifecycle
  - **Adopt-Deferred**: Goal-directed orchestration SHOULD be adopted with phased refactoring
  - **Defer**: Goal-directed orchestration SHOULD NOT be adopted at this time
- **AND** a rationale is provided explaining why the chosen option is best for dice-anchors
- **AND** conditions for re-evaluation are stated (what would change the recommendation)

### Requirement: No code changes

This feature MUST NOT include any code changes. The evaluation document is the sole deliverable.

#### Scenario: No source files modified
- **GIVEN** the completed feature
- **WHEN** the changeset is reviewed
- **THEN** no files under `src/` are added, modified, or deleted

## Invariants

- **I1**: The evaluation document MUST NOT contain code changes or executable artifacts
- **I2**: File:line references MUST be validated against the codebase at the time of feature completion
- **I3**: The recommendation MUST be one of the three defined options (adopt / adopt-deferred / defer) -- no ambiguous or conditional recommendations
- **I4**: All three side-effect handling options MUST be documented -- omitting any is incomplete
- **I5**: The comparison table MUST cover at least six dimensions with winner identification
