## 1. Configuration Properties

- [x] 1.1 Add `RetrievalMode` enum (`BULK`, `TOOL`, `HYBRID`) to `assembly/` package
- [x] 1.2 Add `RetrievalConfig` record to `DiceAnchorsProperties` with `mode` (HYBRID), `minRelevance` (0.0), `baselineTopK` (5), `toolTopK` (5), and nested `ScoringConfig` with `authorityWeight` (0.4), `tierWeight` (0.3), `confidenceWeight` (0.3)
- [x] 1.3 Add `dice-anchors.retrieval.*` defaults to `application.yml`
- [x] 1.4 Add retrieval config validation to `AnchorConfiguration.validateConfiguration()`: scoring weights sum to 1.0 (with epsilon tolerance), thresholds in [0.0, 1.0], counts > 0

## 2. Relevance Scoring

- [x] 2.1 Add `ScoredAnchor` record to `assembly/` package: `id`, `text`, `rank`, `authority`, `confidence`, `memoryTier`, `relevanceScore`
- [x] 2.2 Add `RelevanceScorer` service with `computeHeuristicScore(Anchor, ScoringConfig)` method using authority_weight * tier_weight * confidence formula
- [x] 2.3 Add `scoreByRelevance(String query, List<Anchor>, RetrievalConfig)` method to `RelevanceScorer` for LLM-based relevance scoring (batch prompt via ChatModel)
- [x] 2.4 Add relevance scoring prompt template to `src/main/resources/prompts/`
- [x] 2.5 Wire `RelevanceScorer` bean in `AnchorConfiguration`

## 3. Assembly Pipeline Integration

- [x] 3.1 Modify `AnchorsLlmReference` constructor to accept `RetrievalConfig` (nullable for backward compatibility)
- [x] 3.2 Implement BULK mode path in `AnchorsLlmReference.ensureAnchorsLoaded()`: current behavior unchanged when mode is BULK or config is null
- [x] 3.3 Implement HYBRID mode path: score anchors via heuristic, include all CANON + top-N by score, apply minRelevance quality gate
- [x] 3.4 Implement TOOL mode path: skip baseline injection, return empty anchor list
- [x] 3.5 Update `AnchorConfiguration` bean wiring to pass `RetrievalConfig` to `AnchorsLlmReference`

## 4. Retrieval Tool

- [x] 4.1 Add `AnchorRetrievalTools` record with `@MatryoshkaTools` annotation and `retrieveAnchors(String query)` `@LlmTool` method
- [x] 4.2 Implement `retrieveAnchors`: load active anchors, call `RelevanceScorer.scoreByRelevance()`, apply quality gate, return top-k `ScoredAnchor` list
- [x] 4.3 Modify `ChatActions` to conditionally register `AnchorRetrievalTools` when mode is HYBRID or TOOL
- [x] 4.4 Modify `SimulationTurnExecutor` to conditionally register `AnchorRetrievalTools` when mode is HYBRID or TOOL

## 5. OTEL Observability

- [x] 5.1 Add retrieval span attributes in `AnchorsLlmReference.ensureAnchorsLoaded()`: `retrieval.mode`, `retrieval.baseline_count`, `retrieval.filtered_count`, `retrieval.avg_relevance_score`
- [x] 5.2 Add `retrieval.tool_call_count` tracking in `AnchorRetrievalTools.retrieveAnchors()` via span attribute increment

## 6. Test Updates

- [x] 6.1 Add `RelevanceScorerTest`: heuristic scoring for all authority/tier combinations, CANON highest, PROVISIONAL COLD lowest
- [x] 6.2 Add `AnchorsLlmReferenceRetrievalTest`: BULK mode identical to current, HYBRID reduces baseline, TOOL produces empty, CANON always included, quality gate filters below threshold
- [x] 6.3 Add `RetrievalConfigValidationTest`: valid config passes, invalid weight sum rejected, invalid thresholds rejected, defaults backward compatible
- [x] 6.4 Update any existing test files that construct `DiceAnchorsProperties` to include retrieval config parameter (null for backward compat)

## 7. Build Verification

- [x] 7.1 Run full test suite — all tests pass
- [x] 7.2 Verify backward compatibility: BULK mode with default config produces identical behavior to pre-change baseline
