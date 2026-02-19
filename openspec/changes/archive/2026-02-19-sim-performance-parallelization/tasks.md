## 1. Java Version & Configuration

- [x] 1.1 Update `pom.xml` Java version from 21 to 25 and verify compilation with `./mvnw.cmd clean compile -DskipTests`
- [x] 1.2 Add `SimConfig` record to `DiceAnchorsProperties` with `llmCallTimeoutSeconds` (default 30), `batchMaxSize` (default 10), `parallelPostResponse` (default true)
- [x] 1.3 Add `dice-anchors.sim.*` entries to `application.yml`
- [x] 1.4 Verify: all 342 existing tests still pass after Java 25 upgrade

## 2. LlmCallService Foundation

- [x] 2.1 Create `LlmCallService` in `sim/engine/` package with constructor-injected `ChatModelHolder` and `DiceAnchorsProperties` — Spec: `parallel-llm-execution`
- [x] 2.2 Implement `call(systemPrompt, userPrompt)` method with per-call timeout enforcement
- [x] 2.3 Implement `callBatched(systemPrompt, userPrompt)` method with extended timeout for batch calls
- [x] 2.4 Write unit tests for `LlmCallService`: timeout behavior, error propagation, thread-safety under concurrent invocations

## 3. Batch Prompt Templates

- [x] 3.1 Create `batch-duplicate-system.jinja` and `batch-duplicate-user.jinja` prompt templates with structured JSON output format — Spec: `batched-promotion-gates`
- [x] 3.2 Create `batch-conflict-detection.jinja` prompt template with per-candidate conflict verdicts
- [x] 3.3 Create `batch-trust-scoring.jinja` prompt template with per-candidate trust scores
- [x] 3.4 Create batch result records: `BatchDedupResult`, `BatchConflictResult`, `BatchTrustResult`

## 4. Batched Duplicate Detection

- [x] 4.1 Add `batchIsDuplicate(contextId, List<String>)` method to `DuplicateDetector` — Spec: `normalized-string-dedup`
- [x] 4.2 Implement fast-path/LLM-path split: run `NormalizedStringDuplicateDetector` per candidate first, batch remaining for LLM fallback
- [x] 4.3 Implement LLM batch dedup using `LlmCallService.callBatched()` and `batch-duplicate-*.jinja` templates
- [x] 4.4 Add fallback: if batch LLM call fails, fall back to individual per-candidate `isDuplicate()` calls — Spec: `batched-promotion-gates` I3
- [x] 4.5 Ensure existing `isDuplicate(contextId, candidateText)` API remains unchanged — Spec: `normalized-string-dedup` backward compat
- [x] 4.6 Write unit tests for batch dedup: all-fast-path, mixed, all-LLM, empty list, fallback on failure

## 5. Batched Conflict Detection

- [x] 5.1 Add `batchDetect(List<String>, List<Anchor>)` default method to `ConflictDetector` interface that delegates to individual `detect()` — Spec: `conflict-detection`
- [x] 5.2 Override `batchDetect` in `LlmConflictDetector` with single batched LLM call using `batch-conflict-detection.jinja`
- [x] 5.3 Override `batchDetect` in `NegationConflictDetector` with parallel lexical evaluation (virtual threads or parallel stream)
- [x] 5.4 Add fallback: if batch LLM call fails, fall back to individual `detect()` calls
- [x] 5.5 Write unit tests for batch conflict detection: no conflicts, mixed, batch failure fallback

## 6. Batched Trust Scoring

- [x] 6.1 Add `batchEvaluate(List<TrustContext>)` method to trust pipeline — Spec: `trust-scoring`
- [x] 6.2 Implement per-proposition non-LLM signal computation (SourceAuthoritySignal, ExtractionConfidenceSignal, GraphConsistencySignal, CorroborationSignal)
- [x] 6.3 Write unit tests for batch trust evaluation

## 7. Batched Promotion Funnel

- [x] 7.1 Add `batchEvaluateAndPromote(contextId, List<Proposition>)` method to `AnchorPromoter` — Spec: `batched-promotion-gates`
- [x] 7.2 Implement batch gate pipeline: confidence filter → batch dedup → batch conflict → batch trust → sequential promote
- [x] 7.3 Preserve gate ordering invariant I1 and batch size cap invariant I2
- [x] 7.4 Keep sequential `promote()` calls to preserve budget enforcement invariant A1
- [x] 7.5 Wire `SimulationExtractionService.extract()` to call `batchEvaluateAndPromote` instead of per-proposition `evaluateAndPromote`
- [x] 7.6 Write unit tests for batched promotion: full pipeline, all-filtered-at-confidence, fallback behavior

## 8. Parallel Turn Pipeline

- [x] 8.1 Refactor `SimulationTurnExecutor.executeTurnFull()` to use `StructuredTaskScope` for post-response fork-join — Spec: `parallel-turn-pipeline`
- [x] 8.2 Implement Branch A (drift evaluation) and Branch B (extraction + promotion) as forked subtasks
- [x] 8.3 Implement join point: collect drift verdicts + extraction result before sequential post-join operations
- [x] 8.4 Handle branch failure via `ShutdownOnFailure`: cancel sibling, propagate exception, mark turn as failed
- [x] 8.5 Implement `parallelPostResponse` feature flag: when false, revert to sequential execution
- [x] 8.6 Update `SimulationExtractionService` for concurrency safety — Spec: `sim-extraction-lifecycle`
- [x] 8.7 Ensure ContextTrace assembly merges results from both branches after join — Spec: `parallel-turn-pipeline` I3
- [x] 8.8 Write unit tests for parallel pipeline: ATTACK turn, ESTABLISH turn, extraction disabled, branch failure

## 9. Integration Verification

- [x] 9.1 Run full test suite: `./mvnw.cmd test` — all tests must pass
- [ ] 9.2 Run a simulation scenario end-to-end and verify turn times are reduced
- [ ] 9.3 Verify Entity Mention Network view still loads (no regressions from concurrency changes)
- [ ] 9.4 Check logs for any thread-safety warnings or race condition indicators
