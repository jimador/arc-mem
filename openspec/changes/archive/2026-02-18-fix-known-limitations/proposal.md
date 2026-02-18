## Why

The [known-limitations.md](../../docs/known-limitations.md) document catalogs concrete gaps in drift evaluation, conflict detection, compaction validation, trust evaluation, and persistence. These aren't theoretical — they cause observable problems: inflated NOT_MENTIONED rates from a weak evaluation prompt, missed semantic contradictions from lexical-only conflict detection, and lost run history on restart. The tor project has already solved several of these problems. This change ports those solutions and addresses the remaining gaps to make dice-anchors a credible anchor resilience testbed.

## What Changes

### Drift Evaluation Overhaul
- Replace the minimal `evaluateDrift()` prompt with tor-style structured system+user prompt pair, including the critical distinction: "story progression ≠ contradiction"
- Replace fragile line-by-line regex parsing with JSON-structured output using a `DriftVerdict` DTO (instruct JSON format in prompt → strip markdown fences → Jackson parse → fallback keyword heuristic)
- Add severity levels (NONE, MINOR, MAJOR) to contradiction tracking
- Add true attribution tracking: match injected anchors to ground truth via normalized text similarity
- Add `ScoringService` with metrics: factSurvivalRate, meanTurnsToFirstDrift, contradictionCount, driftAbsorptionRate, recallAccuracy, anchorAttributionCount, strategyEffectiveness

### LLM-Based Conflict Detection
- Replace `NegationConflictDetector` (lexical negation + word overlap) with an LLM-based conflict detector using a cheap, fast model (gpt-4o-nano or equivalent)
- Keep the `ConflictDetector` SPI interface so the lexical detector remains available as a fallback
- New detector sends anchor pairs to the LLM with a focused "are these contradictory?" prompt

### Compaction Summary Validation
- Add post-compaction validation that checks whether protected facts survived in the summary
- Use `ProtectedContentProvider` output as the checklist; LLM or keyword match to verify presence in the compacted summary

### Trust Evaluation Improvements
- Enrich trust signal implementations beyond the current minimal stubs
- Add concrete signal implementations that produce meaningful scores for simulation scenarios

### Run History Persistence
- Define a `RunHistoryStore` interface with clear API contract (save, load, list, delete)
- Provide in-memory implementation (current behavior) and Neo4j implementation
- Run records survive application restarts when using Neo4j store

### DICE Extraction Fixes
- Fix minor issues in the working extraction pipeline: replace in-memory `ChunkHistoryStore` with persistent backing, improve error handling granularity
- Verify `ConversationPropositionExtraction` event flow end-to-end

### Known Limitations Doc Updates
- Check off each limitation as its fix lands
- Update or remove sections that no longer apply

## Capabilities

### New Capabilities
- `drift-evaluation`: Structured drift verdict DTOs, evaluation prompts, attribution tracking, and scoring service
- `conflict-detection`: LLM-based semantic conflict detection replacing lexical approach
- `run-history-persistence`: Persistent run history with pluggable store interface

### Modified Capabilities
- `compaction`: Add post-compaction fact survival validation
- `trust-scoring`: Enrich signal implementations with concrete scoring logic
- `drift-summary`: Update to consume new ScoringService metrics and severity levels

## Impact

- **sim/engine/**: `SimulationTurnExecutor` — drift eval prompt rewrite, JSON parsing, attribution. `SimulationService` — scoring integration. New `ScoringService` and `DriftVerdict` records.
- **anchor/**: `NegationConflictDetector` replaced by `LlmConflictDetector` behind `ConflictDetector` SPI. `AnchorEngine` wiring updated.
- **persistence/**: New `RunHistoryStore` interface + Neo4j implementation. `ChunkHistoryStore` persistence.
- **sim/views/**: `DriftSummaryPanel` updated for new metrics. `ConversationPanel` updated for severity badges.
- **application.yml**: New config for conflict detection model, scoring thresholds.
- **Dependencies**: No new dependencies — uses existing Spring AI ChatModel and Jackson.

### Constitutional Alignment

This change aligns with the project constitution:
- **Data over strings**: DriftVerdict and ScoringResult are records, not formatted strings
- **SPI architecture**: ConflictDetector remains an SPI; RunHistoryStore is a new SPI
- **Constructor injection only**: All new services use constructor injection
- **Immutable DTOs**: All new data carriers are Java records
