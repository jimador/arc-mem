# Feature: DICE Integration Surface Documentation

## Context

dice-anchors couples tightly with DICE 0.1.0-SNAPSHOT for proposition extraction, revision, and promotion. Integration surface includes:

- **Extraction**: `LlmPropositionExtractor` (prompt-based extraction)
- **Revision**: `LlmPropositionReviser` (validates extracted propositions)
- **Pipeline**: `PropositionPipeline` (coordinates extract → revise → persist flow)
- **Analysis**: `PropositionIncrementalAnalyzer` (windowed analysis across turns)
- **History**: `DiceAnchorsChunkHistoryStore` (delegation wrapper around DICE's `ChunkHistoryStore`)
- **Conversion**: `PropositionView.toDice()` (converts internal representation to DICE)

**Known fragile points**:
- `PropositionView.toDice()` uses 13-param `Proposition.create()` overload (vulnerable to API evolution)
- `DiceAnchorsChunkHistoryStore` wraps DICE interface; requires maintenance on SNAPSHOT changes
- Windowed analysis parameters (window size, overlap, trigger interval) are configurable but undocumented

**Goal**: Document integration surface, fragile coupling points, responsibility boundaries, and establish monitoring strategy for DICE 0.1.0-SNAPSHOT API evolution.

## Proposal Seed

### Why This Feature

Explicit DICE integration documentation provides:
1. **Reference**: Clear contract between dice-anchors and DICE components (who does what)
2. **Fragile point catalog**: Known API fragility points tracked for future maintenance
3. **Monitoring strategy**: Process for tracking SNAPSHOT releases and catching API misalignments
4. **Onboarding**: New contributors understand integration architecture without digging through code

### What Changes

**Documentation** (no code changes):
- Create `docs/dev/dice-integration.md` with end-to-end integration flow
- Document each component: `LlmPropositionExtractor`, `LlmPropositionReviser`, `PropositionPipeline`, `PropositionIncrementalAnalyzer`
- Document current SNAPSHOT API signatures (method names, parameters, return types)
- Identify fragile coupling points: 13-param `Proposition.create()`, `DiceAnchorsChunkHistoryStore` wrapper, windowed analysis
- Establish monitoring strategy: SNAPSHOT release tracking, integration test suite for API compatibility
- Clarify responsibility boundaries: what dice-anchors implements vs. what DICE provides
- Integrate documentation into DEVELOPING.md or README

### Success Criteria

- [ ] `docs/dev/dice-integration.md` created with complete integration flow
- [ ] End-to-end flow documented with file:line references
- [ ] Component usage documented for all DICE-coupled components
- [ ] Current SNAPSHOT API signatures captured (0.1.0-SNAPSHOT as of feature creation)
- [ ] Fragile coupling points identified and explained
- [ ] Monitoring strategy documented (release tracking, integration tests)
- [ ] Responsibility boundaries explicitly stated (local vs. upstream implementations)
- [ ] Documentation referenced from DEVELOPING.md or README
- [ ] No code changes required (documentation-only feature)

### Visibility

- **UI**: None (documentation-only)
- **Observability**: Integration tests serve as API compatibility monitors (future maintenance task)

## DICE API Research Findings

*Source: Embabel MCP server + codebase analysis. See `research/R02-dice-api-surface.md` for full details.*

### Proposition.create() — Actually 15 Parameters (Not 13)

Previous documentation stated 13 parameters. Research confirms **15 parameters** with two unclear positions:

```java
Proposition.create(
    id,            // 1.  String — UUID
    contextId,     // 2.  String — isolation context
    text,          // 3.  String — proposition statement
    mentions,      // 4.  List<EntityMention> — entity references
    confidence,    // 5.  double — LLM certainty (0.0-1.0)
    decay,         // 6.  double — staleness rate (0.0-1.0)
    0.0,           // 7.  double — UNKNOWN (importance/weight?)
    reasoning,     // 8.  String — LLM explanation
    grounding,     // 9.  List<String> — supporting chunk IDs
    created,       // 10. Instant — extraction timestamp
    revised,       // 11. Instant — last update
    revised,       // 12. Instant — DUPLICATED (accessed?)
    status,        // 13. PropositionStatus — lifecycle status
    0,             // 14. int — UNKNOWN (version counter?)
    sourceIds      // 15. List<String> — source lineage
)
```

**Fragility assessment**: Positions 7 (double), 12 (duplicated Instant), and 14 (int) have unclear semantics. This is the highest-risk API point.

### PropositionIncrementalAnalyzer — Untyped Return

`analyze()` returns `Object`, requiring unsafe cast:
```java
if (!(rawResult instanceof ChunkPropositionResult result)) {
    logger.error("Unexpected analysis result type...");
    return;
}
```

This MAY get a generic type parameter in a future SNAPSHOT.

### ChunkHistoryStore — Per-Context Limitation

`InMemoryDiceAnchorsChunkHistoryStore` cannot clear per-context (only full reset). This is acceptable for short-lived simulation contexts but could be a problem for long-running production deployments.

### Two Extraction Paths to Document

| Path | Entry Point | Pipeline | Windowing |
|------|------------|----------|-----------|
| Simulation | `SimulationExtractionService.extract()` | Direct `pipeline.process()` | None (one-shot) |
| Chat | `ConversationPropositionExtraction` | Via `PropositionIncrementalAnalyzer` | `WindowConfig` sliding window |

### API Stability Assessment

| Component | Stability | Risk |
|-----------|-----------|------|
| `Proposition.create()` 15-param | LOW | Unclear params suggest incomplete API |
| `PropositionResults.persist()` fallback | MEDIUM | May evolve error handling |
| `WindowConfig` semantics | MEDIUM | Overlap/trigger may be refined |
| `analyze()` untyped return | MEDIUM | May get generic type parameter |
| `PropositionStatus` enum | HIGH | Core model, likely stable |
| `EntityMention` fields | HIGH | Core model, likely stable |
| `PropositionRepository` SPI | HIGH | Interface contract stable |

## Design Sketch

### DICE Integration Surface Document Structure

```
docs/dev/dice-integration.md
├── Overview
│   └── Proposition lifecycle in dice-anchors (extract → revise → persist → promote)
├── End-to-End Integration Flow
│   ├── Chat extraction (event-driven)
│   ├── Simulation extraction (synchronous)
│   ├── Proposition revision
│   ├── Persistence
│   └── Anchor promotion
├── Component API Usage
│   ├── LlmPropositionExtractor (with method signatures)
│   ├── LlmPropositionReviser (with method signatures)
│   ├── PropositionPipeline (with method signatures)
│   ├── PropositionIncrementalAnalyzer (with method signatures)
│   └── File:line references for each
├── Fragile Coupling Points
│   ├── PropositionView.toDice() — 13-param Proposition.create()
│   ├── DiceAnchorsChunkHistoryStore — delegation wrapper
│   ├── Windowed analysis parameters (configurable, undocumented)
│   └── Migration path / deprecation strategy
├── Responsibility Boundaries
│   ├── What dice-anchors implements
│   │   ├── Anchor lifecycle (rank, authority, promotion)
│   │   ├── Trust pipeline (confidence → conflict → promote)
│   │   ├── Extraction integration (DICE → Neo4j)
│   │   └── Conflict resolution & dedup
│   └── What DICE provides
│       ├── Proposition extraction (LLM-based)
│       ├── Proposition revision (validation)
│       ├── Chunk history management
│       └── Incremental analysis
├── Monitoring Strategy
│   ├── SNAPSHOT release tracking (process + cadence)
│   ├── Integration tests for API compatibility
│   ├── Deprecation warning checks
│   └── Maintenance calendar
└── Data Flow Diagrams
    └── Mermaid diagrams showing extraction → revision → persistence → promotion
```

### Key Sections to Document

#### 1. Current Integration Flow (Chat)
```
UserMessage
  → ChatActions.respond()
  → AssistantMessage (LLM response)
  → ConversationPropositionExtraction (event listener)
  → LlmPropositionExtractor (DICE)
  → LlmPropositionReviser (DICE)
  → PropositionPipeline
  → PropositionIncrementalAnalyzer (windowed)
  → AnchorRepository (Neo4j)
  → AnchorPromoter (trust pipeline)
```

#### 2. Fragile Points Explanation

**Problem**: `PropositionView.toDice()` hard-codes 13-param `Proposition.create()` overload
```java
Proposition.create(
  content, type, groundTruth, isFromKb, isRefined,
  createdAt, source, confidence, semanticId, id,
  chunkIds, contexts, metadata
)
```
**Risk**: If DICE adds/removes parameters, this breaks immediately.
**Mitigation**: Monitor DICE releases; add integration test verifying method exists and signature unchanged.

**Problem**: `DiceAnchorsChunkHistoryStore` implements DICE's `ChunkHistoryStore` interface
**Risk**: Interface contract changes force updates to our delegation wrapper.
**Mitigation**: Document interface contract; test against actual DICE version.

#### 3. Monitoring Strategy

- **Release cadence**: Check DICE releases monthly (or on notification)
- **Integration tests**: Compile-time checks for method existence; runtime checks for signature compatibility
- **Deprecation warnings**: Enable DICE deprecation warnings in Maven build
- **Maintenance window**: Plan SNAPSHOT API fixes in quarterly maintenance cycle

## Dependencies

- **Depends on**: F1 (Embabel API Inventory) — provides framework reference context
- **No code dependencies** between features

## Open Questions

1. Should integration tests be added in this feature or deferred to next maintenance cycle?
   - **Recommendation**: Defer to maintenance cycle (outside roadmap scope); document test plan
2. Are there other fragile coupling points beyond the three identified?
   - **Recommendation**: Codebase search for other DICE imports/usages during documentation phase
3. What DICE API stability guarantees exist for 0.1.0-SNAPSHOT?
   - **Recommendation**: Capture in "API Stability" section of documentation

## Acceptance Gates

1. **Documentation completeness**: All sections populated with current codebase data
2. **File:line accuracy**: All code references verified against actual source
3. **Data flow clarity**: Diagrams and textual flow match actual implementation
4. **Fragile point accuracy**: Known fragile points validated; no false positives
5. **Responsibility boundaries**: Unambiguous contract between dice-anchors and DICE
6. **DEVELOPING.md integration**: Documentation linked from project development guide

## Next Steps

Once feature is approved:
1. Create OpenSpec change: `/opsx:new` with slug `dice-integration-review-docs`
2. Work through proposal → spec → design → tasks
3. Document any new fragile points discovered during codebase analysis
4. Archive change and sync specs via `/opsx:sync` or `/opsx:archive`

Maintenance team can use this documentation as foundation for future DICE API compatibility monitoring.
