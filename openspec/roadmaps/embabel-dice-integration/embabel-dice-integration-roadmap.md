# Embabel & DICE Integration Roadmap

## Initiative

Audit and document Embabel Agent usage across dice-anchors. Leverage framework capabilities more idiomatically, restructure tools following CQS principles, and establish clear integration contracts with DICE for maintainability and observability. This roadmap sequences four features that collectively deepen the project's foundation for long-horizon consistency and trust management.

## Scope & Sequencing

**Timeline**: Parallel implementation with sequencing dependencies.

**Sequence**:
1. **[F1] Embabel API Inventory** (foundation) — Catalog current usage vs. available capabilities; document framework patterns and recommendations.
2. **[F2] Tool Restructuring** (code) — Split `AnchorTools` into query and mutation groups; consolidate retrieval tools; update tool registration.
3. **[F3] DICE Integration Documentation** (docs, parallel with F2) — Document integration surface, fragile points, monitoring strategy.
4. **[F4] Goal Modeling Evaluation** (docs, depends on F1) — Evaluate goal-directed orchestration for anchor lifecycle; document trade-offs and recommendation.

**Dependency Graph**:
```
F1 (API Inventory) ──> F4 (Goal Modeling Evaluation)
F1 ──> F2 (Tool Restructuring)
F1 ──> F3 (DICE Integration Docs)
F2, F3, F4 all depend on F1; F2 and F3 can run in parallel
```

## Features

### F1: Embabel API Inventory & Patterns

**Slug**: `embabel-api-inventory`

**Why**: dice-anchors uses Embabel's basic capabilities (single action, tools, template rendering) but has unexplored patterns (`@Goal`, `@Condition`, `@AchievesGoal`, Blackboard). Inventory provides the foundation for evaluating where idioms could improve observability and testability without requiring major refactoring.

**Visibility**:
- **UI**: None (documentation-only feature)
- **Observability**: Catalog serves as living documentation for OTEL span instrumentation roadmap

**Acceptance Criteria**:
- [ ] Comprehensive inventory document created at `docs/dev/embabel-api-inventory.md`
- [ ] Current usage catalog complete with file:line references for all Embabel annotations used
- [ ] Available but unused capabilities documented with explanations
- [ ] Recommended patterns section aligned with framework documentation
- [ ] Tool restructuring rationale (CQS principle, read-only vs. full access) documented

**Key Decisions**:
- API inventory is read-only documentation; no code changes in this feature
- Inventory documents current state as foundation for F2, F3, F4
- Recommendations in inventory are informational; actual adoption deferred to feature implementation

**Estimated Scope**: Small (documentation-only)

---

### F2: Tool Restructuring (Query/Mutation Separation)

**Slug**: `anchor-llm-tools`

**Why**: Current `AnchorTools` mixes read operations (queryFacts, listAnchors) with mutations (pinFact, unpinFact, demoteAnchor). CQS principle separates these into distinct tool groups, enabling selective registration for read-only contexts (simulation, audit) vs. full access (chat).

**Visibility**:
- **UI**: Chat tool availability changes; may affect LLM tool selection behavior
- **Observability**: Tool calls logged with source (query vs. mutation); OTEL spans updated

**Acceptance Criteria**:
- [ ] `AnchorQueryTools` (@MatryoshkaTools) created with: `queryFacts`, `listAnchors`, `retrieveAnchors`
- [ ] `AnchorMutationTools` (@MatryoshkaTools) created with: `pinFact`, `unpinFact`, `demoteAnchor`
- [ ] `AnchorRetrievalTools` consolidated into `AnchorQueryTools`; conditional registration preserved (HYBRID/TOOL modes)
- [ ] `ChatActions` updated for dual-group registration in chat; single-group in read-only contexts
- [ ] All tests passing: `./mvnw test`
- [ ] Tool descriptions verified in chat integration test; LLM tool usage smoke test

**Key Decisions**:
- Query tools always available; mutation tools context-conditional
- Consolidate retrieval tools into query group (all are read operations)
- Preserve OTEL instrumentation and retrieval-mode conditional logic
- Delete old `AnchorTools.java` and `AnchorRetrievalTools.java`

**Estimated Scope**: Medium (code + tests)

---

### F3: DICE Integration Surface Documentation

**Slug**: `dice-integration-review-docs`

**Why**: dice-anchors couples tightly with DICE 0.1.0-SNAPSHOT for proposition extraction, revision, and promotion. Integration surface is complex and fragile; explicit documentation provides reference for future API changes and monitoring strategy.

**Visibility**:
- **UI**: None (documentation-only)
- **Observability**: Integration tests serve as API compatibility monitors

**Acceptance Criteria**:
- [ ] `docs/dev/dice-integration.md` created with end-to-end integration flow
- [ ] Component usage documented for: `LlmPropositionExtractor`, `LlmPropositionReviser`, `PropositionPipeline`, `PropositionIncrementalAnalyzer`
- [ ] Current SNAPSHOT API signatures documented (method names, parameters, return types)
- [ ] Fragile coupling points identified: 13-param `Proposition.create()`, `DiceAnchorsChunkHistoryStore` wrapper, windowed analysis parameters
- [ ] Monitoring strategy documented: SNAPSHOT release tracking, integration test suite for API alignment
- [ ] DICE vs. Anchors responsibility boundaries clarified (what each project implements)
- [ ] Documentation integrated into DEVELOPING.md or README

**Key Decisions**:
- Documentation captures current API surface (0.1.0-SNAPSHOT as of roadmap creation)
- Monitoring strategy is future-facing; integration tests added in later maintenance cycles
- No code changes in this feature; documentation-only

**Estimated Scope**: Medium (documentation)

---

### F4: Goal-Directed Orchestration Evaluation

**Slug**: `embabel-goal-modeling`

**Why**: Anchor lifecycle (confidence → dedup → conflict → trust → promote) is a natural fit for Embabel's goal-directed orchestration with typed action chaining and conditions. Evaluation assesses architectural feasibility, trade-offs, and recommendation without requiring refactoring.

**Visibility**:
- **UI**: None (documentation-only)
- **Observability**: Evaluation document guides future OTEL span design for multi-action flows

**Acceptance Criteria**:
- [ ] `docs/dev/embabel-goal-modeling.md` created with evaluation
- [ ] Trust pipeline analysis: current 5-gate sequence mapped to Embabel patterns (condition predicates, goal statements)
- [ ] Architectural trade-offs documented: side-effect handling (Neo4j mutations vs. typed data flow), refactoring scope, implementation cost
- [ ] Comparison table: imperative (current) vs. goal-directed covering observability, testability, error handling, complexity
- [ ] Chatbot mode assessment: utility mode vs. goal-directed alternatives with recommendation
- [ ] Blackboard state opportunity documented with "deferred to future change" marker
- [ ] Clear recommendation: adopt now, adopt with deferred refactoring, or defer entirely

**Key Decisions**:
- Evaluation is document-only; no code implementation
- Chatbot mode assessment informs future decisions but doesn't require changes
- Blackboard opportunity explicitly deferred
- Recommendation shapes future roadmap planning

**Estimated Scope**: Medium (documentation)

---

## Non-Goals

- Upgrading Embabel Agent or DICE versions
- Implementing goal-directed orchestration in this roadmap (evaluation only)
- Adopting Blackboard state management (documented as deferred)
- Changing DICE extraction pipeline architecture
- Adding new anchor capabilities or trust/authority semantics

## Implementation Order Rationale

1. **F1 first**: Foundation for all other features. Inventory informs F2 (which tools to split), F3 (how tools are used), and F4 (goal patterns available).
2. **F2 & F3 in parallel**: No dependency between them; both depend on F1. Parallel execution maximizes velocity.
3. **F4 last**: Benefits from completed F1 inventory and can reference F2, F3 decisions in evaluation.

## Success Metrics

- [ ] All four features complete and archived
- [ ] Documentation integrated into project guides (DEVELOPING.md or README)
- [ ] Tool restructuring verified in chat integration test (no LLM regression)
- [ ] DICE integration surface documented as reference for future maintenance
- [ ] Clear recommendation on goal modeling (informs next roadmap/change decision)

## Open Questions

- Should `retrieveAnchors` remain conditional (based on retrieval mode), or be always available? (Answered in F2 task design)
- Is `AgentProcessChatbot.utilityFromPlatform()` the right chatbot choice? (Answered in F4 evaluation)
- What specific architectural benefits does multi-action orchestration provide for dice-anchors? (Answered in F4 evaluation)
- Should integration tests be added in this roadmap or deferred to maintenance cycle? (Deferred per "Non-Goals")

## Research

Research docs grounding each feature in concrete API details:

| Doc | Contents |
|-----|----------|
| `research/R01-embabel-api-surface.md` | Full annotation inventory (used vs. unused), parameter details, action chaining mechanics, chatbot modes, blackboard binding, template integration |
| `research/R02-dice-api-surface.md` | Proposition.create() 15-param signature, pipeline architecture, ChunkHistoryStore contract, API stability assessment |

### Key Research Findings

1. **Proposition.create() has 15 parameters** (not 13 as previously documented) — positions 7, 12, 14 have unclear semantics
2. **@MatryoshkaTools has `removeOnInvoke`** (default true) — we SHOULD set to `false` for persistent tool availability
3. **@LlmTool.Param** exists for rich parameter descriptions — we use none currently
4. **@Action(toolGroups)** enables declarative tool binding — we build arrays manually
5. **@Action(cost)** influences GOAP planning path selection — relevant for goal modeling evaluation
6. **GOAP mode** is default `AgentProcessChatbot`; we explicitly use utility mode
7. **Blackboard binding** (`bindProtected`, `addObject`) provides typed state — we use string context IDs
8. **ChatTrigger** pattern — ephemeral actions that don't clutter conversation history

## References

- **Current Change**: `openspec/changes/embabel-dice-integration-review/` (seeds this roadmap)
- **CLAUDE.md**: RFC 2119 keywords, project architecture, Embabel integration patterns
- **Embabel API Docs**: https://docs.embabel.com/embabel-agent/api-docs/0.3.5-SNAPSHOT/index.html
- **DICE Docs**: https://github.com/embabel/dice (0.1.0-SNAPSHOT)
