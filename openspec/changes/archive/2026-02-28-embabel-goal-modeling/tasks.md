# Implementation Tasks

## 1. Create Evaluation Document Skeleton

- [ ] 1.1 Create `docs/dev/embabel-goal-modeling.md` with section headings: Overview, Current Anchor Lifecycle, Trust Pipeline Analysis, Side-Effect Handling, Comparison Table, Chatbot Mode Assessment, Blackboard State Opportunity, Recommendation, References
- [ ] 1.2 Add introductory paragraph stating document purpose, Embabel Agent version (0.3.5-SNAPSHOT), and scope (trust pipeline, chatbot mode, blackboard state)

## 2. Analyze Current AnchorPromoter 5-Gate Sequence

- [ ] 2.1 Document the confidence gate: `prop.getConfidence() < threshold` filter, no I/O, fast-path rejection
- [ ] 2.2 Document the dedup gate: `DuplicateDetector.isDuplicate()` combining exact-text matching with optional LLM verification; batch mode with `batchIsDuplicate()`
- [ ] 2.3 Document the conflict gate: `AnchorEngine.detectConflicts()` + `resolveConflicts()` private method with 4-outcome resolution (KEEP_EXISTING, REPLACE, DEMOTE_EXISTING, COEXIST); trust re-evaluation on surviving anchors
- [ ] 2.4 Document the trust gate: `TrustPipeline.evaluate()` producing `TrustScore` with `promotionZone` (AUTO_PROMOTE, REVIEW, ARCHIVE) and `authorityCeiling`
- [ ] 2.5 Document the promote gate: `AnchorEngine.promote()` with budget enforcement (eviction of lowest-ranked non-pinned anchors)
- [ ] 2.6 Identify all Neo4j mutation points in the pipeline: `AnchorEngine.promote()`, `AnchorEngine.supersede()`, `AnchorEngine.demote()`, `AnchorEngine.reEvaluateTrust()`, `AnchorEngine.reinforce()`
- [ ] 2.7 Add file:line references to `AnchorPromoter.java` for each gate

## 3. Map Gates to Embabel Patterns

- [ ] 3.1 Design typed action chain: `PropositionCandidate -> ConfidenceResult -> DedupResult -> ConflictResult -> TrustResult -> PromotedAnchor`
- [ ] 3.2 Map each gate to an `@Action` method with typed input/output: `validateConfidence(PropositionCandidate) -> ConfidenceResult`, etc.
- [ ] 3.3 Annotate the terminal action with `@AchievesGoal(description = "Proposition promoted to anchor with trust verification")`
- [ ] 3.4 Document how type matching resolves the execution order (each return type feeds the next action's parameter)
- [ ] 3.5 Reference the `Stages.java` pattern (`chooseCook -> Cook` + `takeOrder -> Order` -> `prepareMeal(Cook, Order) -> Meal`)
- [ ] 3.6 Note that `@Action(cost)` is not applicable since gate order is fixed (no alternative paths for the planner to select)

## 4. Assess Side-Effect Handling

- [ ] 4.1 State the core tension: Embabel assumes immutable typed data flow; anchor operations are Neo4j mutations
- [ ] 4.2 Document Option A (Impure actions): mutations inside `@Action` methods; benefits (minimal refactoring); drawbacks (breaks data-flow model, side-effects invisible to planner)
- [ ] 4.3 Document Option B (Functional style): return side-effect descriptors (e.g., `ArchiveCommand`, `DemoteCommand`); apply mutations in a final step; benefits (pure data flow, composable); drawbacks (HIGH refactoring scope, introduces command pattern indirection)
- [ ] 4.4 Document Option C (Hybrid): gates return typed result objects (no mutations); terminal `@AchievesGoal` action applies all mutations; benefits (clean separation, moderate refactoring); drawbacks (mutation batching complexity, error handling for partial failures)
- [ ] 4.5 Assign refactoring scope estimate to each option (LOW / MEDIUM / HIGH)

## 5. Research Embabel Examples via MCP Server

- [ ] 5.1 Use `mcp__embabel__docs_docs_vectorSearch` to search for goal-directed patterns, side-effect handling, and multi-action examples in Embabel documentation
- [ ] 5.2 Use `mcp__embabel__embabel_agent_findClassSignatureBySimpleName` to verify API signatures for `AchievesGoal`, `AgentProcessChatbot`, `Action`, and related classes
- [ ] 5.3 Document any findings that affect the side-effect analysis or comparison table
- [ ] 5.4 Check for Embabel examples handling external state mutations (databases, APIs) within goal-directed flows

## 6. Create Comparison Table

- [ ] 6.1 Build comparison table with columns: Aspect, Current (Imperative), Goal-Directed, Winner, Reasoning
- [ ] 6.2 Fill in the following dimensions:
  - [ ] 6.2.1 Observability: sequential gate calls (opaque) vs. each action observable via Embabel instrumentation
  - [ ] 6.2.2 Testability: integration-heavy (mocked gates) vs. unit-friendly (independent typed actions)
  - [ ] 6.2.3 Error handling: boolean pass/fail vs. structured result types with failure context
  - [ ] 6.2.4 Code clarity: straightforward sequential flow vs. multi-action abstraction layer
  - [ ] 6.2.5 Refactoring scope: none vs. HIGH (side-effect model transformation)
  - [ ] 6.2.6 Type safety: loose (boolean returns, zone checks) vs. strict (typed action chaining)
- [ ] 6.3 Identify winner per dimension with honest reasoning (expect imperative to win on clarity and scope)

## 7. Assess Chatbot Modes

- [ ] 7.1 Document current utility mode: `AgentProcessChatbot.utilityFromPlatform(agentPlatform)` in `ChatConfiguration.java:27`; single action per message; no orchestration; reference to `ChatActions.respond()`
- [ ] 7.2 Document GOAP mode alternative: `new AgentProcessChatbot(agentPlatform, agentSource, conversationFactory)`; multi-action per message; `@AchievesGoal` completion; `@Action(cost)` path selection
- [ ] 7.3 Address the fixed gate order question: Does the trust pipeline benefit from GOAP planning when gate order is predetermined? (Expected answer: no -- GOAP adds value when the planner can choose between alternative action paths)
- [ ] 7.4 Write recommendation for chatbot mode (expected: utility mode remains appropriate for dice-anchors' single-action-per-message pattern)

## 8. Document Blackboard State Opportunity

- [ ] 8.1 Document current state management: string `contextId` extracted from `ActionContext.getProcessContext().getProcessOptions()` (see `ChatActions.resolveContextId()`)
- [ ] 8.2 Document blackboard alternative: `agentProcess.bindProtected(key, value)` for persistent state, `agentProcess.addObject(value)` for type-matched inputs
- [ ] 8.3 Describe potential typed bindings: `AnchorEngine`, `TrustPipeline`, `SimulationRunContext` as blackboard entries
- [ ] 8.4 Mark section explicitly as "DEFERRED to future change" with rationale (requires multi-action refactoring that is not justified until goal-directed patterns are adopted)

## 9. Write Recommendation

- [ ] 9.1 Select one of: adopt, adopt-deferred, defer
- [ ] 9.2 Write rationale grounded in the comparison table and side-effect analysis
- [ ] 9.3 State conditions for re-evaluation (e.g., "revisit if multi-action patterns become standard in the codebase" or "revisit when Embabel provides side-effect handling examples")
- [ ] 9.4 If adopting or adopt-deferred, include a high-level refactoring plan (scope, timeline, dependencies)

## 10. Integrate into DEVELOPING.md

- [ ] 10.1 Add reference to `docs/dev/embabel-goal-modeling.md` in DEVELOPING.md under an appropriate section (e.g., "Developer References" or "Architecture Decisions")
- [ ] 10.2 Verify the link resolves correctly from the repository root

## 11. Verification

- [ ] 11.1 Confirm all five gates are documented in the Current Anchor Lifecycle section
- [ ] 11.2 Confirm all three side-effect handling options are documented with trade-offs
- [ ] 11.3 Confirm comparison table has at least six dimensions with winner identification
- [ ] 11.4 Confirm chatbot mode assessment addresses the fixed gate order question
- [ ] 11.5 Confirm blackboard section is marked "DEFERRED"
- [ ] 11.6 Confirm recommendation is exactly one of: adopt / adopt-deferred / defer
- [ ] 11.7 Confirm file:line references in the document point to correct locations
- [ ] 11.8 Confirm document is reachable from DEVELOPING.md
- [ ] 11.9 Read document end-to-end for clarity, completeness, and balanced assessment

## Definition of Done

- Evaluation document exists at `docs/dev/embabel-goal-modeling.md`
- All 5 trust pipeline gates documented with Embabel pattern mapping
- All 3 side-effect handling options documented with trade-offs
- Comparison table covers 6+ dimensions with winner per dimension
- Chatbot mode assessment with recommendation
- Blackboard opportunity documented and marked DEFERRED
- Recommendation is one of adopt / adopt-deferred / defer with rationale
- DEVELOPING.md links to evaluation document
- File:line references verified against current source
