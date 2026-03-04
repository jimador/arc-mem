# Implementation Tasks

## 1. Configuration

- [x] 1.1 Add `QualityScoringConfig` record to `DiceAnchorsProperties` with `enabled` (default: false) field
- [x] 1.2 Update `application.yml` with `dice-anchors.anchor.quality-scoring.enabled: false` default

## 2. Novelty Signal

- [x] 2.1 Create `NoveltySignal` class in `anchor/` package implementing `TrustSignal`
  - [x] 2.1.1 Implement `name()` returning `"novelty"`
  - [x] 2.1.2 Implement `evaluate()` with Jaccard similarity against active anchors via `AnchorEngine.inject(contextId)`
  - [x] 2.1.3 Implement `tokenize()` helper: lowercase, split on `\\W+`, remove stop words, return `Set<String>`
  - [x] 2.1.4 Implement `jaccardSimilarity(Set<String>, Set<String>)` helper
  - [x] 2.1.5 Return `OptionalDouble.empty()` when quality scoring is disabled
  - [x] 2.1.6 Return novelty 1.0 when no active anchors exist
- [x] 2.2 Define static `STOP_WORDS` constant (`Set.of(...)`) with common English stop words
- [x] 2.3 Write unit tests for `NoveltySignal`
  - [x] 2.3.1 Test identical proposition and anchor yields novelty 0.0
  - [x] 2.3.2 Test completely novel proposition yields novelty 1.0
  - [x] 2.3.3 Test partial overlap yields score between 0.0 and 1.0
  - [x] 2.3.4 Test empty anchor list yields novelty 1.0
  - [x] 2.3.5 Test disabled config returns `OptionalDouble.empty()`
  - [x] 2.3.6 Test stop-word filtering (only content words contribute to similarity)

## 3. Importance Signal

- [x] 3.1 Create `ImportanceSignal` class in `anchor/` package implementing `TrustSignal`
  - [x] 3.1.1 Implement `name()` returning `"importance"`
  - [x] 3.1.2 Implement `evaluate()` with keyword overlap ratio against combined active anchor tokens
  - [x] 3.1.3 Reuse tokenization from shared utility (extract to package-private `TextTokenizer` if needed, or share stop words)
  - [x] 3.1.4 Return `OptionalDouble.empty()` when quality scoring is disabled
  - [x] 3.1.5 Return importance 0.5 when no active anchors exist
- [x] 3.2 Write unit tests for `ImportanceSignal`
  - [x] 3.2.1 Test proposition with full keyword overlap yields importance ~1.0
  - [x] 3.2.2 Test proposition with no keyword overlap yields importance 0.0
  - [x] 3.2.3 Test empty anchor list yields importance 0.5
  - [x] 3.2.4 Test disabled config returns `OptionalDouble.empty()`

## 4. DomainProfile Updates

- [x] 4.1 Update `DomainProfile.NARRATIVE` weights to include `novelty: 0.05` and `importance: 0.05`
- [x] 4.2 Update `DomainProfile.SECURE` weights to include `novelty: 0.05` and `importance: 0.05`
- [x] 4.3 Update `DomainProfile.BALANCED` weights to include `novelty: 0.06` and `importance: 0.06`
- [x] 4.4 Verify all three profiles still sum to 1.0
- [x] 4.5 Update existing tests that reference hardcoded profile weights

## 5. Spring Configuration Wiring

- [x] 5.1 Add `NoveltySignal` bean to `TrustConfiguration` (injecting `AnchorEngine` and `DiceAnchorsProperties`)
- [x] 5.2 Add `ImportanceSignal` bean to `TrustConfiguration` (injecting `AnchorEngine` and `DiceAnchorsProperties`)

## 6. Verification

- [x] 6.1 Run full test suite: `./mvnw test` -- all existing tests MUST pass
- [x] 6.2 Verify trust audit records include novelty/importance entries when enabled
- [x] 6.3 Verify weight redistribution produces equivalent scores when quality scoring is disabled vs. prior profile weights

## Definition of Done

- All existing tests pass with no regressions
- `NoveltySignal` and `ImportanceSignal` produce scores in [0.0, 1.0] when enabled
- Quality scoring disabled by default; trust scores unchanged from prior behavior
- Scores appear in `TrustAuditRecord.signalAudit` when enabled
- No LLM calls introduced by quality scoring
- Code follows project style (records, constructor injection, modern Java 25, no comment slop)
