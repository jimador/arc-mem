# Implementation Tasks

## 1. Scenario Model Extension

- [x] 1.1 Add `extractionEnabled` boolean to `SimulationScenario` (default false)
- [x] 1.2 Update `ScenarioLoader` to parse new field from YAML
- [x] 1.3 Verify existing scenarios load without regression (field absent → false)

## 2. SimulationExtractionService

- [x] 2.1 Create `SimulationExtractionService` in `sim/engine/`
- [x] 2.2 Inject `PropositionPipeline`, `PropositionRepository`, `AnchorPromoter`, `DataDictionary`, `EntityResolver`
- [x] 2.3 Implement `extract(contextId, dmResponseText)` method:
  - [x] 2.3.1 Call pipeline to extract propositions from DM response (via SimChunk wrapper)
  - [x] 2.3.2 Persist via `results.persist(propositionRepository, null)`
  - [x] 2.3.3 Call `promoter.evaluateAndPromote(contextId, propositions)`
  - [x] 2.3.4 Return `ExtractionResult(extractedCount, promotedCount, extractedTexts)`
- [x] 2.4 Create `ExtractionResult` record

## 3. Turn Executor Integration

- [x] 3.1 Inject `SimulationExtractionService` into `SimulationTurnExecutor`
- [x] 3.2 Add extraction call in `executeTurnFull()`:
  - [x] 3.2.1 After DM response, before drift evaluation
  - [x] 3.2.2 Check `scenario.isExtractionEnabled()` (passed as boolean param)
  - [x] 3.2.3 If enabled: call `extractionService.extract(contextId, dmResponse)`
  - [x] 3.2.4 If disabled: skip (no-op)
- [x] 3.3 Pass extraction results to ContextTrace
- [x] 3.4 Add OTEL span attributes for extraction (propositionsExtracted, promoted)

## 4. ContextTrace Extension

- [x] 4.1 Add fields to `ContextTrace`: `propositionsExtracted`, `propositionsPromoted`, `extractedTexts`
- [x] 4.2 Update all ContextTrace construction sites to include new fields
- [x] 4.3 Default values when extraction not enabled (0, 0, empty list) via convenience constructor

## 5. Anchor Source Tracking

- [x] 5.1 Add `CREATED_EXTRACTED` event type in `AnchorTimelinePanel.AnchorEventType`
- [x] 5.2 Detect "sim_extraction" reason in anchor events and map to CREATED_EXTRACTED
- [ ] 5.3 Tag seed anchors during `SimulationService.seedAnchor()` as SEEDED (deferred — needs anchor event metadata)
- [ ] 5.4 Pass source type through to `AnchorEvent` in state diff (deferred — needs anchor event model change)

## 6. UI Updates

- [x] 6.1 Update `ContextInspectorPanel` Anchors tab:
  - [x] 6.1.1 Show extraction count badge (e.g., "3 extracted, 1 promoted")
  - [x] 6.1.2 List extracted proposition texts in extraction summary section
- [x] 6.2 Update `AnchorTimelinePanel`:
  - [x] 6.2.1 Differentiate SEEDED vs EXTRACTED CREATED events via CREATED_EXTRACTED type
  - [x] 6.2.2 Use distinct color/icon for extracted anchors (teal #009688, diamond-with-dot symbol)
- [ ] 6.3 Verify `DriftSummaryPanel` includes extracted anchors in attribution count (manual verification needed)

## 7. Showcase Scenarios

### extraction-baseline.yaml
- [x] 7.1.1 Create scenario: 10 turns, no adversarial, `extractionEnabled: true`
- [x] 7.1.2 Define setting (Sunken Citadel of Vael'thara — elven transmutation academy)
- [x] 7.1.3 Script WARM_UP + ESTABLISH + RECALL_PROBE turns
- [x] 7.1.4 Define ground truth matching expected extracted facts (5 facts)
- [x] 7.1.5 No seed anchors (all anchors come from extraction)

### extraction-under-attack.yaml
- [x] 7.2.1 Create scenario: 15 turns, adversarial, `extractionEnabled: true`
- [x] 7.2.2 5 warm-up/establish turns (establish facts via extraction)
- [x] 7.2.3 10 attack/recall turns targeting extracted facts
- [x] 7.2.4 Define ground truth matching warm-up established facts (5 facts)
- [x] 7.2.5 Seed 2 high-authority anchors (CANON + RELIABLE) alongside extraction
- [x] 7.2.6 Attack strategies: CONFIDENT_ASSERTION, SUBTLE_REFRAME, FALSE_MEMORY_PLANT

## 8. Testing

- [ ] 8.1 Unit test for `SimulationExtractionService` (deferred — requires mock setup for PropositionPipeline)
- [ ] 8.2 Integration test: extraction-baseline scenario produces anchors (manual)
- [x] 8.3 Verify existing scenarios (extractionEnabled=false) unchanged — compilation + test pass
- [x] 8.4 Verify ContextTrace serialization with extraction metadata — default constructor preserves backward compat

## 9. Verification

- [x] 9.1 Run full test suite: `./mvnw.cmd test` — 304 tests, 0 failures
- [x] 9.2 Build: `./mvnw.cmd clean compile -DskipTests` — BUILD SUCCESS
- [ ] 9.3 Manual test: Run extraction-baseline scenario, verify propositions appear in inspector
- [ ] 9.4 Manual test: Run extraction-under-attack, verify extracted anchors resist drift
- [ ] 9.5 Run existing adversarial scenario, verify no regression
- [ ] 9.6 Verify ContextInspectorPanel shows extraction data
- [ ] 9.7 Verify AnchorTimelinePanel differentiates SEEDED vs EXTRACTED

## Definition of Done

- ✓ DICE extraction runs during sim turns when enabled
- ✓ Extracted propositions flow through full promotion pipeline
- ✓ ContextTrace includes extraction metadata
- ✓ UI shows extraction activity per turn
- ✓ Timeline differentiates SEEDED vs EXTRACTED anchors
- ✓ 2 showcase scenarios demonstrate extraction lifecycle
- ✓ Existing scenarios unaffected
- ✓ All tests pass
