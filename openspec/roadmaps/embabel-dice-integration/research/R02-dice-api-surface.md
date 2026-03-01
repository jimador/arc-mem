# Research: DICE API Surface (0.1.0-SNAPSHOT)

## Source

Embabel MCP server (class/package lookups) + codebase analysis of dice-anchors DICE imports and usage.

## Proposition.create() Signature

Actually **15 parameters** (not 13 as previously documented in CLAUDE.md):

```java
Proposition.create(
    id,            // String — UUID
    contextId,     // String — isolation context
    text,          // String — proposition statement
    mentions,      // List<EntityMention> — entity references
    confidence,    // double — LLM certainty (0.0-1.0)
    decay,         // double — staleness rate (0.0-1.0)
    0.0,           // double — unknown (possibly importance/weight)
    reasoning,     // String — LLM explanation
    grounding,     // List<String> — supporting chunk IDs
    created,       // Instant — extraction timestamp
    revised,       // Instant — last update
    revised,       // Instant — repeated (possibly "accessed")
    status,        // PropositionStatus — lifecycle status
    0,             // int — unknown (possibly version counter)
    sourceIds      // List<String> — source lineage
)
```

**Fragility**: Parameters at positions 7 (double) and 14 (int) have unclear semantics. The duplicated `revised` at position 12 suggests an API that's still settling.

## PropositionPipeline

**Builder pattern** (three-stage flow):
```java
PropositionPipeline
    .withExtractor(LlmPropositionExtractor)
    .withRevision(PropositionReviser, PropositionRepository)
```

**Key method**: `process(List<Chunk>, SourceAnalysisContext)` -> `PropositionResults`

**PropositionResults contract**:
- `propositionsToPersist()` — deduplicated propositions ready for storage
- `persist(PropositionRepository, NamedEntityDataRepository)` — full persist with entity linking
  - Can throw `RuntimeException` on entity link failure
  - dice-anchors uses fallback: `propositionRepository.saveAll(propositionsToPersist)`

## Two Extraction Paths

### Path 1: Simulation (One-Shot)
```
SimulationExtractionService.extract()
  → pipeline.process(chunks, context)
  → PropositionResults.persist() [with fallback]
```

### Path 2: Chat (Windowed, Incremental)
```
ConversationPropositionExtraction
  → PropositionIncrementalAnalyzer.analyze()
  → WindowConfig-driven sliding window
  → pipeline.process() [under the hood]
  → PropositionResults.persist() [with fallback]
```

## PropositionIncrementalAnalyzer

**Constructor**:
```java
new PropositionIncrementalAnalyzer<>(
    pipeline,           // PropositionPipeline
    chunkHistoryStore,  // ChunkHistoryStore
    MessageFormatter.INSTANCE,
    windowConfig        // WindowConfig
)
```

**WindowConfig fields**:
- `windowSize` — messages in sliding window
- `windowOverlap` — messages carried forward between windows
- `triggerInterval` — messages before window advances

**Return type issue**: `analyze()` returns untyped `Object`, requiring cast to `ChunkPropositionResult`:
```java
if (!(rawResult instanceof ChunkPropositionResult result)) {
    logger.error("Unexpected analysis result type...");
    return;
}
```

## ChunkHistoryStore Interface

**Contract**: Manages history of chunks analyzed in incremental pipeline; prevents re-extraction.

**dice-anchors wrapper**: `DiceAnchorsChunkHistoryStore`
- `delegate()` — returns underlying DICE `ChunkHistoryStore`
- `clearByContext(String contextId)` — per-simulation cleanup
- `clearAll()` — full reset (startup if `persistence.clearOnStart=true`)

**Implementation**: `InMemoryDiceAnchorsChunkHistoryStore` wraps DICE's `InMemoryChunkHistoryStore`
- Thread-safe via synchronized lifecycle methods
- Limitation: Cannot clear per-context in in-memory store; resets entire store

## EntityMention Bridging

**DICE side**: `EntityMention` — `span`, `type`, `resolvedId`, `role`
**dice-anchors side**: `Mention` + `MentionRole` enum — 1:1 bidirectional conversion via `fromDice()` / `toDice()`

## LlmPropositionExtractor Builder

```java
LlmPropositionExtractor
    .withLlm(String llmName)
    .withAi(Ai ai)
    .withPropositionRepository(PropositionRepository)
    .withSchemaAdherence(SchemaAdherence.DEFAULT)
    .withTemplate("dice/extract_dnd_propositions")
```

## SourceAnalysisContext Builder

```java
SourceAnalysisContext
    .withContextId(String)
    .withEntityResolver(EntityResolver)
    .withSchema(DataDictionary)
```

## API Points Likely to Change in SNAPSHOT

| Component | Risk | Reason |
|-----------|------|--------|
| `Proposition.create()` 15-param overload | HIGH | Unclear parameters at positions 7, 14; duplicated `revised` at 12 |
| `PropositionResults.persist()` fallback | MEDIUM | May evolve to structured error handling |
| `WindowConfig` semantics | MEDIUM | Overlap/trigger intervals may be refined |
| `analyze()` untyped return | MEDIUM | Type erasure issue; may get generic type parameter |
| `PropositionStatus` enum | LOW | May add ARCHIVED, SUPERSEDED values |
| `EntityMention` fields | LOW | Core model, likely stable |

## PropositionRepository Interface (DICE SPI, implemented by AnchorRepository)

Key methods:
- `saveAll(List<Proposition>)` — bulk persist
- `assignContextIds(List<String> propositionIds, String contextId)` — isolation
- `tagSourceIds(List<String> propositionIds, String source)` — lineage tracking
