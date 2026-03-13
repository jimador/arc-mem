## Context

context units integrates with DICE 0.1.0-SNAPSHOT for proposition extraction, revision, persistence, and incremental analysis. The integration spans two distinct extraction paths:

- **Chat path**: Event-driven via `ConversationPropositionExtraction` (`@Async @EventListener`), using `PropositionIncrementalAnalyzer` with `WindowConfig` sliding window for deduplication across conversation turns.
- **Simulation path**: Synchronous via `SimulationExtractionService.extract()`, using `PropositionPipeline.process()` directly (one-shot, no windowing).

Both paths share the same pipeline configuration (`PropositionConfiguration`) and persistence layer (`ContextUnitRepository` implementing DICE's `PropositionRepository` SPI).

Research (R02-dice-api-surface.md) revealed that the `Proposition.create()` overload has 15 parameters (not the previously documented 13), with three parameters of unclear semantics (positions 7, 12, 14). This is the highest-risk API coupling point.

## Goals

- Document the complete DICE integration surface in a single canonical reference (`docs/dev/dice-integration.md`)
- Capture accurate file:line references for all DICE integration points
- Identify and assess all fragile coupling points with risk ratings
- Establish clear responsibility boundaries between context units and DICE
- Define a monitoring strategy for SNAPSHOT API evolution
- Integrate the document into development guides for discoverability

## Non-Goals

- Refactoring DICE integration code (no code changes)
- Implementing integration tests (deferred to future maintenance cycle)
- Upgrading DICE version
- Changing extraction pipeline architecture

## Decisions

### D1: Document Location

**Decision**: `docs/dev/dice-integration.md`

**Rationale**: Follows the pattern established by the Embabel API inventory (`docs/dev/embabel-api-inventory.md`). The `docs/dev/` directory houses developer-facing reference documents that complement CLAUDE.md with deep-dive topics.

### D2: Document Structure

**Decision**: Seven-section structure covering overview, end-to-end flows, component API usage, fragile coupling points, API stability assessment, responsibility boundaries, and monitoring strategy.

**Rationale**: Mirrors the feature doc's design sketch. Each section addresses a distinct concern: architecture (flows), reference (component APIs), risk (fragile points + stability), contract (boundaries), and process (monitoring). Mermaid diagrams for data flow visualization.

### D3: Fragile Point Assessment

**Decision**: Use three-tier risk rating (LOW/MEDIUM/HIGH stability) derived from R02 research findings.

**Rationale**: R02 research established stability assessments for each DICE component based on API surface analysis. The 15-param `Proposition.create()` overload is rated LOW stability due to unclear parameters at positions 7, 12, and 14 — suggesting the API is still settling. Stable core models (`PropositionStatus`, `EntityMention`) are rated HIGH.

### D4: Integration Tests Deferred

**Decision**: Document integration test recommendations but defer implementation to a future maintenance cycle.

**Rationale**: This is a documentation-only feature per the prep doc's key decisions. Integration test patterns are documented for future implementation but no code changes are in scope.

### D5: 15-Parameter Correction

**Decision**: Document the corrected 15-parameter `Proposition.create()` signature from R02 research, not the previously documented 13-parameter count.

**Rationale**: R02 research (via Embabel MCP server analysis) confirmed 15 parameters. The actual call site in `PropositionView.toDice()` (lines 81-97) passes 15 arguments. Previous references to "13-param" in feature docs and CLAUDE.md are corrected.

## Document Structure

```
docs/dev/dice-integration.md
|- Overview
|  |- DICE 0.1.0-SNAPSHOT integration summary
|  |- Proposition lifecycle: extract -> revise -> persist -> promote
|
|- End-to-End Integration Flow
|  |- Chat extraction (event-driven, windowed)
|  |  |- Entry: ConversationPropositionExtraction.java:83
|  |  |- WindowConfig: ConversationPropositionExtraction.java:68-72
|  |  |- Analyzer: ConversationPropositionExtraction.java:73-78
|  |- Simulation extraction (synchronous, one-shot)
|  |  |- Entry: SimulationExtractionService.java:66
|  |  |- Pipeline: SimulationExtractionService.java:79
|  |- Mermaid data flow diagrams
|
|- Component API Usage
|  |- LlmPropositionExtractor (PropositionConfiguration.java:107-118)
|  |- LlmPropositionReviser (PropositionConfiguration.java:125-132)
|  |- PropositionPipeline (PropositionConfiguration.java:138-146)
|  |- PropositionIncrementalAnalyzer (ConversationPropositionExtraction.java:73-78)
|  |- SourceAnalysisContext builder pattern
|
|- Fragile Coupling Points
|  |- Proposition.create() 15-param overload (PropositionView.java:81-97) [HIGH risk]
|  |- ArcMemChunkHistoryStore wrapper (ArcMemChunkHistoryStore.java) [MEDIUM risk]
|  |- WindowConfig semantics (ConversationPropositionExtraction.java:68-72) [MEDIUM risk]
|  |- analyze() untyped return (ConversationPropositionExtraction.java:103) [MEDIUM risk]
|
|- API Stability Assessment
|  |- Table: Component / Stability / Risk Description
|  |- Based on R02-dice-api-surface.md research findings
|
|- Responsibility Boundaries
|  |- context units implements: context unit lifecycle, trust pipeline, conflict detection, context assembly
|  |- DICE provides: extraction, revision, chunk history, incremental analysis, entity mentions
|  |- Extension points: PropositionRepository SPI, ChunkHistoryStore SPI, extraction templates
|
|- Monitoring Strategy
   |- SNAPSHOT release tracking cadence
   |- Integration test recommendations (deferred)
   |- Deprecation warning detection
   |- Maintenance calendar
```

## Risks / Trade-offs

**[File:line references become stale]** -- Code changes after documentation is written will invalidate line references. Mitigation: document file references with enough context (method names, code snippets) that line numbers serve as hints rather than brittle pointers. Verification step confirms accuracy at time of writing.

**[DICE API may have changed since R02 research]** -- R02 research was conducted against a specific SNAPSHOT build. Mitigation: verify all API signatures against the current dependency version during documentation authoring.
