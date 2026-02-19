## ADDED Requirements

### Requirement: Batch trust evaluation API
The trust pipeline SHALL provide a `batchEvaluate(List<TrustContext> contexts)` method that scores multiple propositions in a single pass. Each `TrustContext` SHALL contain the proposition text, source metadata, and extraction confidence. Non-LLM signals (SourceAuthoritySignal, ExtractionConfidenceSignal, GraphConsistencySignal, CorroborationSignal) SHALL be computed independently per proposition. If any future LLM-based trust signals are added, they SHALL be batched.

#### Scenario: Batch evaluation with current signal set
- **GIVEN** the current 4 trust signals (all non-LLM)
- **WHEN** `batchEvaluate()` is called with 5 propositions
- **THEN** each proposition SHALL receive scores from all 4 signals
- **AND** no LLM calls SHALL be made (current signals are all computational)
- **AND** the result SHALL be a `Map<String, TrustScore>`

#### Scenario: Future LLM-based signal batching
- **GIVEN** an LLM-based trust signal is configured
- **WHEN** `batchEvaluate()` is called with multiple propositions
- **THEN** the LLM-based signal SHALL batch all propositions into a single LLM call
- **AND** non-LLM signals SHALL still compute independently
