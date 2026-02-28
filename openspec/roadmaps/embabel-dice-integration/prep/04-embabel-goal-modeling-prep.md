# Prep: Embabel Goal-Directed Orchestration Evaluation

## Visibility Contract

- **UI Visibility**: None
- **Observability Visibility**: Future reference for OTEL span design (multi-action flows)
- **Acceptance**: Evaluation complete with clear recommendation; documented thoroughly; integrated into development guides

## Key Decisions

1. **Evaluation-only feature**: No code implementation (document analysis only)
2. **Current vs. goal-directed comparison**: Balanced assessment of both approaches
3. **Clear recommendation required**: No ambiguity; guides next roadmap/feature decision
4. **Blackboard deferred**: Explicitly marked as "deferred to future change"
5. **Chatbot mode assessment included**: Utility vs. goal-directed evaluation
6. **Trade-off analysis thorough**: Side-effects, refactoring scope, implementation cost all addressed

## Open Questions (Answered by Feature Specification)

1. Are there other Neo4j mutation points in anchor lifecycle?
   - **Decision**: Audit all gates during evaluation
2. Would a hybrid approach (incremental @Action wrappers) be practical?
   - **Decision**: Document as Option 2 in recommendation section
3. Are there Embabel examples of handling side-effects?
   - **Decision**: Research Embabel patterns; document findings

## Acceptance Gates

- [ ] `docs/dev/embabel-goal-modeling.md` created with complete evaluation
- [ ] Trust pipeline analysis: current sequence + Embabel pattern mapping
- [ ] Architectural trade-offs: side-effect handling, refactoring scope, implementation cost
- [ ] Comparison table: current vs. goal-directed (observability, testability, error handling, complexity)
- [ ] Chatbot mode assessment: utility mode vs. goal-directed with recommendation
- [ ] Blackboard state opportunity documented (explicitly marked "deferred")
- [ ] **Clear recommendation**: No ambiguity; recommends one of:
     - [ ] Adopt goal-directed orchestration (with refactoring plan)
     - [ ] Adopt with deferred refactoring (hybrid approach)
     - [ ] Defer goal-directed patterns entirely
- [ ] Documentation reviewed for accuracy and reasoning
- [ ] DEVELOPING.md or README updated with reference
- [ ] Recommendation is actionable (informs next roadmap decision)

## Small-Model Constraints

- **Files touched**: ~4 (codebase inspection, documentation authoring, DEVELOPING.md update)
- **Estimated runtime**: 3-4 hours
- **Verification commands**:
  ```bash
  # Verify trust pipeline gates in AnchorPromoter
  grep -n "gate\|Condition\|confidence\|dedup\|conflict\|trust\|promote" src/main/java/dev/dunnam/diceanchors/extract/AnchorPromoter.java
  ```

## Evaluation Framework

### Trust Pipeline Analysis

Current 5-gate sequence to analyze:
1. **Confidence gate** → Map to `@Condition: extractionConfidenceAboveThreshold`
2. **Dedup gate** → Map to `@Condition: notDuplicate`
3. **Conflict gate** → Map to `@Condition: noConflict`
4. **Trust gate** → Map to `@Condition: trustThresholdMet`
5. **Promote gate** → Map to `@Action: promoteToAnchor()`

### Side-Effect Handling Trade-Off

Key question: How do Embabel's typed data-flow assumptions interact with Neo4j mutations?

**Current approach**: Gates return boolean; `AnchorPromoter` applies mutations sequentially
**Goal-directed approach**: Actions return typed results; mutations could be:
- Option A: Apply mutations inside each action (side-effects)
- Option B: Return side-effect descriptors; apply separately (functional style)
- Option C: Hybrid (gates return results; final action applies mutations)

Evaluate cost/benefit of each.

### Comparison Dimensions

| Aspect | Current (Imperative) | Goal-Directed | Winner | Why |
|--------|----------------------|---------------|---------|------|
| Observability | | | | |
| Testability | | | | |
| Error Handling | | | | |
| Code Clarity | | | | |
| Refactoring Scope | | | | |
| Type Safety | | | | |
| Long-term Maintainability | | | | |

Fill in for current + goal-directed; clearly indicate winner and reasoning.

### Chatbot Mode Assessment

Evaluate:
- `AgentProcessChatbot.utilityFromPlatform()` — utility mode (current)
  - Single action per message
  - No orchestration
  - Suitable for simple request-response

- Goal-directed alternative
  - Multi-action per message
  - Condition-based branching
  - Suitable for complex orchestration

**Question**: Is utility mode sufficient for dice-anchors, or should we explore goal-directed?

### Recommendation Template

**Recommendation**: [Adopt / Adopt with Deferred Refactoring / Defer]

**Rationale**: [Clear explanation of why this option is best for dice-anchors]

**Conditions**: [If applicable, state conditions for re-evaluation]

**Refactoring Plan** (if adopting): [Scope, timeline, dependencies]

## Implementation Notes

- Use Embabel 0.3.5-SNAPSHOT API docs as primary reference
- Research Embabel examples (GitHub repos, documentation)
- Analyze `AnchorPromoter` code to understand current gate sequencing
- Assess Neo4j mutation patterns (where mutations happen, impact on typing)
- Interview project leadership on observability/testability priorities
- Document balanced assessment (don't pre-judge result)
- Write recommendation only after thorough analysis
