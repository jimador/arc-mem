# Prep: DICE Integration Surface Documentation

## Visibility Contract

- **UI Visibility**: None
- **Observability Visibility**: Reference document for integration tests and API compatibility monitoring (future)
- **Acceptance**: Documentation complete, accurate, integrated into development guides; no code changes required

## Key Decisions

1. **No code changes**: Documentation-only feature (no refactoring)
2. **Current snapshot captured**: Docs reflect DICE 0.1.0-SNAPSHOT as of feature creation (API surface may evolve)
3. **Fragile points explicit**: Known API fragility points identified and mitigation strategy documented
4. **Monitoring strategy deferred**: Integration tests are future maintenance task (not in this roadmap)
5. **Responsibility boundaries clear**: Explicit "dice-anchors vs. DICE" contract documented

## Open Questions (Answered by Feature Specification)

1. Should integration tests be added in this feature or deferred?
   - **Decision**: Deferred to next maintenance cycle (outside roadmap scope)
2. Are there other fragile coupling points beyond the three known?
   - **Decision**: Codebase search during documentation phase will identify any additional points
3. What DICE API stability guarantees exist for 0.1.0-SNAPSHOT?
   - **Decision**: Capture in "API Stability" section of documentation

## Acceptance Gates

- [ ] `docs/dev/dice-integration.md` created with complete integration flow
- [ ] End-to-end flow documented with file:line references
- [ ] Component usage documented for all DICE-coupled components
- [ ] Current SNAPSHOT API signatures captured
- [ ] Fragile coupling points identified and explained (with migration path)
- [ ] Monitoring strategy documented (release tracking, integration test plan)
- [ ] Responsibility boundaries explicitly stated
- [ ] Documentation reviewed for accuracy
- [ ] DEVELOPING.md or README updated with reference

## Small-Model Constraints

- **Files touched**: ~4 (codebase inspection, documentation authoring, DEVELOPING.md update)
- **Estimated runtime**: 2-3 hours
- **Verification commands**:
  ```bash
  # Verify all file:line references are current
  grep -n "LlmPropositionExtractor\|PropositionView\|DiceAnchorsChunkHistoryStore" src/main/java/dev/dunnam/diceanchors/extract/*.java
  ```

## Documentation Notes

### Data Flow to Capture

```
Chat Flow:
UserMessage → ChatActions.respond()
  → AssistantMessage (LLM)
  → ConversationPropositionExtraction (event listener)
  → LlmPropositionExtractor (DICE)
  → PropositionView.toDice() [FRAGILE: 13-param create]
  → PropositionPipeline
  → PropositionIncrementalAnalyzer (windowed)
  → AnchorRepository (Neo4j)

Simulation Flow:
SimulationTurnExecutor
  → LlmCallService (LLM response)
  → SimulationExtractionService (synchronous)
  → LlmPropositionExtractor (DICE)
  → PropositionView.toDice() [FRAGILE]
  → PropositionPipeline
  → AnchorRepository (Neo4j)
```

### Fragile Points to Document

1. **PropositionView.toDice()** — 13-param `Proposition.create()` overload
   - File:line reference
   - Parameter list (what's required)
   - Risk: Parameter addition/removal breaks immediately
   - Mitigation: Integration test verifying method signature

2. **DiceAnchorsChunkHistoryStore** — Delegation wrapper
   - File:line reference
   - Interface contract
   - Risk: Interface evolution forces updates
   - Mitigation: Document interface; verify against DICE version

3. **PropositionIncrementalAnalyzer** — Windowed analysis
   - File:line reference
   - Configuration parameters (window size, overlap, trigger interval)
   - Risk: Parameters undocumented; unclear when to tune
   - Mitigation: Document parameter semantics and default values

### Monitoring Strategy to Document

- **SNAPSHOT release cadence**: Check monthly or on notification
- **Integration tests**: Add to maintenance cycle (not this feature)
- **Deprecation warnings**: Enable in Maven build
- **Maintenance calendar**: Plan SNAPSHOT compatibility fixes quarterly

## Implementation Notes

- Use CLAUDE.md as reference for current architecture
- Search codebase for all DICE imports/usages
- Verify all file:line references before submission
- Include data flow diagrams (Mermaid) if helpful
- Document API stability status (is 0.1.0-SNAPSHOT stable/frozen?)
