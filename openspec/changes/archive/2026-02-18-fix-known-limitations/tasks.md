## 1. Drift Evaluation — Records and Parsing

- [x] 1.1 Create `Verdict` enum (CONTRADICTED, CONFIRMED, NOT_MENTIONED) and `Severity` enum (NONE, MINOR, MAJOR) in `sim.engine` package
- [x] 1.2 Create `FactVerdict` record (factId, verdict, severity, explanation) and `DriftEvaluationResult` record (List<FactVerdict>)
- [x] 1.3 Write drift evaluation system prompt template in `src/main/resources/prompts/` with critical distinction ("story progression ≠ contradiction"), concrete examples, and JSON output schema
- [x] 1.4 Rewrite `SimulationTurnExecutor.evaluateDrift()` to use new prompt, strip markdown fences, parse JSON with Jackson into `DriftEvaluationResult`
- [x] 1.5 Add fallback keyword heuristic parser — scan response for CONTRADICTED/CONFIRMED/NOT_MENTIONED per fact ID when JSON parsing fails
- [x] 1.6 Update `TurnResult` (or equivalent turn data record) to carry `DriftEvaluationResult` instead of raw verdict maps
- [x] 1.7 Unit tests for JSON parsing, fence stripping, fallback heuristic, and severity classification

## 2. Drift Evaluation — Attribution and Scoring

- [x] 2.1 Add `computeAttribution()` method — normalize anchor and ground truth texts (lowercase, strip non-alphanumeric), bidirectional substring match
- [x] 2.2 Create `ScoringResult` record with fields: factSurvivalRate, contradictionCount, majorContradictionCount, driftAbsorptionRate, meanTurnsToFirstDrift, anchorAttributionCount, strategyEffectiveness (Map<String, Double>)
- [x] 2.3 Create `ScoringService` — stateless service that takes List<TurnResult> and List<GroundTruthFact>, returns ScoringResult
- [x] 2.4 Integrate `ScoringService` into `SimulationService` — compute ScoringResult at run end, include in SimulationRunRecord
- [x] 2.5 Unit tests for ScoringService metric computations (perfect run, partial drift, strategy effectiveness)

## 3. Conflict Detection — LLM-Based

- [x] 3.1 Create `LlmConflictDetector` implementing `ConflictDetector` interface — sends proposition+anchor pairs to LLM, parses JSON response (contradicts: boolean, explanation: String)
- [x] 3.2 Write conflict detection prompt — focused on factual contradiction, temporal progression distinction, JSON output
- [x] 3.3 Add `dice-anchors.conflict-detection.strategy` config property (lexical | llm, default: llm) and `dice-anchors.conflict-detection.model` (default: gpt-4o-nano)
- [x] 3.4 Wire conditional bean selection — `@ConditionalOnProperty` for LlmConflictDetector vs NegationConflictDetector
- [x] 3.5 Update `DiceAnchorsProperties` with conflict detection config section
- [x] 3.6 Unit test for LlmConflictDetector with mocked ChatModel

## 4. Compaction — Fact Survival Validation

- [x] 4.1 Create `CompactionLossEvent` record (anchorId, anchorText, authority, rank)
- [x] 4.2 Add post-compaction validation in compaction flow — after summary generation, check each protected content item against summary text via normalized keyword matching
- [x] 4.3 Report CompactionLossEvents on turn result; surface in Compaction tab of ContextInspectorPanel
- [x] 4.4 Unit test for validation logic — matching, partial matching, and loss detection

## 5. Trust Signal Enrichment

- [x] 5.1 Update `GraphConsistencySignal` — replace word overlap with Jaccard similarity (lowercased tokens, stop words removed)
- [x] 5.2 Update `CorroborationSignal` — implement weighted source diversity (DM+PLAYER mixed = 0.7, 3+ sources = 0.9)
- [x] 5.3 Unit tests for enriched signal implementations

## 6. Run History Persistence

- [x] 6.1 Create `RunHistoryStore` interface with methods: save, load, list, listByScenario, delete
- [x] 6.2 Create `InMemoryRunHistoryStore` implementation using ConcurrentHashMap
- [x] 6.3 Create `Neo4jRunHistoryStore` implementation — SimulationRun node with runId, scenarioId, timestamps as properties and full JSON payload
- [x] 6.4 Add Cypher queries for Neo4j store (save, load, list, listByScenario, delete)
- [x] 6.5 Add `dice-anchors.run-history.store` config property (memory | neo4j, default: memory)
- [x] 6.6 Wire conditional bean selection and update SimulationService/RunHistoryPanel to use RunHistoryStore interface
- [x] 6.7 Unit tests for InMemoryRunHistoryStore; integration test marker for Neo4jRunHistoryStore

## 7. DICE Extraction Fixes

- [x] 7.1 Review and verify end-to-end event flow: ChatActions → ConversationAnalysisRequestEvent → ConversationPropositionExtraction → PropositionPipeline → AnchorPromoter
- [x] 7.2 Replace broad `catch (Exception e)` in ConversationPropositionExtraction with specific exception handling
- [x] 7.3 Add persistent backing for ChunkHistoryStore behind an interface (same pattern as RunHistoryStore — in-memory default, Neo4j option)

## 8. UI Updates — Drift Summary Panel

- [x] 8.1 Update DriftSummaryPanel to consume ScoringResult from ScoringService instead of inline computation
- [x] 8.2 Add major contradiction count metric display
- [x] 8.3 Add strategy effectiveness breakdown (show per-strategy contradiction rate for adversarial scenarios)
- [x] 8.4 Update attribution accuracy to use real attribution from ScoringService

## 9. Configuration and Wiring

- [x] 9.1 Update `DiceAnchorsProperties` with all new config sections (conflict-detection, run-history)
- [x] 9.2 Update `application.yml` with default values for new properties
- [x] 9.3 Verify compile and existing tests pass with all changes

## 10. Documentation

- [x] 10.1 Update `docs/known-limitations.md` — check off fixed items, update or remove resolved sections
- [x] 10.2 Update `docs/simulation-harness.md` — document new metrics, severity levels, attribution tracking
- [x] 10.3 Update `docs/architecture.md` — document RunHistoryStore SPI, LlmConflictDetector, ScoringService
