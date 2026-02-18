## ADDED Requirements

### Requirement: DriftEvaluationResult structured output

The drift evaluator SHALL return results as a `DriftEvaluationResult` record containing a list of `FactVerdict` records. Each `FactVerdict` SHALL contain: `factId` (String), `verdict` (Verdict enum: CONTRADICTED, CONFIRMED, NOT_MENTIONED), `severity` (Severity enum: NONE, MINOR, MAJOR), and `explanation` (String). The evaluator SHALL instruct the LLM to produce JSON output matching this schema, strip markdown code fences from the response, and parse with Jackson ObjectMapper.

#### Scenario: Successful JSON parsing
- **WHEN** the LLM returns a valid JSON response with fact verdicts
- **THEN** the evaluator parses it into a `DriftEvaluationResult` containing one `FactVerdict` per ground truth fact

#### Scenario: Markdown fence stripping
- **WHEN** the LLM wraps its JSON response in ````json ... ``` fences
- **THEN** the evaluator strips the fences before parsing

#### Scenario: Fallback on parse failure
- **WHEN** the LLM returns malformed JSON that cannot be parsed
- **THEN** the evaluator falls back to keyword heuristic parsing, scanning for CONTRADICTED, CONFIRMED, and NOT_MENTIONED tokens per fact ID
- **AND** severity defaults to MAJOR for CONTRADICTED verdicts found via fallback

### Requirement: Structured evaluation prompt with critical distinctions

The drift evaluation system prompt SHALL include the critical instruction: "A contradiction means the DM denies or reverses an established fact — NOT that the world has moved forward from it." The prompt SHALL include concrete examples distinguishing world progression from contradiction (e.g., "the tavern burning down is progression, not contradiction of 'there is a tavern'"). The prompt SHALL instruct the LLM to output JSON matching the `DriftEvaluationResult` schema with one entry per ground truth fact.

#### Scenario: World progression not marked as contradiction
- **WHEN** a ground truth states "Baron Krell rules the Northern Province" and the DM describes Baron Krell being assassinated through narrative events
- **THEN** the evaluator classifies this as NOT_MENTIONED or CONFIRMED (narrative progression), not CONTRADICTED

#### Scenario: Direct contradiction detected
- **WHEN** a ground truth states "the bridge is intact" and the DM states "there was never a bridge here"
- **THEN** the evaluator classifies this as CONTRADICTED with severity MAJOR

#### Scenario: Implicit confirmation recognized
- **WHEN** a ground truth states "the East Gate is guarded" and the DM describes guards challenging the party at the East Gate
- **THEN** the evaluator classifies this as CONFIRMED

### Requirement: Severity classification

Each CONTRADICTED verdict SHALL include a severity level. MAJOR severity SHALL be assigned when the DM directly denies or reverses a fact. MINOR severity SHALL be assigned when the DM's response is ambiguous or could be interpreted as partially contradicting a fact. NONE severity SHALL be assigned for CONFIRMED and NOT_MENTIONED verdicts.

#### Scenario: Direct denial is MAJOR
- **WHEN** a ground truth states "the king is alive" and the DM states "the king has always been dead"
- **THEN** severity is MAJOR

#### Scenario: Ambiguous contradiction is MINOR
- **WHEN** a ground truth states "the sword is cursed" and the DM describes the sword as "unusual" without confirming the curse
- **THEN** severity is MINOR

### Requirement: Attribution tracking

The drift evaluator SHALL compute attribution by matching injected anchor texts to ground truth fact texts using normalized text similarity. Normalization SHALL lowercase both texts and strip non-alphanumeric characters. A match occurs when the normalized anchor text contains the normalized fact text or vice versa. The attribution count SHALL be the number of ground truth facts that have at least one matching injected anchor.

#### Scenario: Anchor matches ground truth
- **WHEN** an injected anchor has text "Baron Krell rules the Northern Province" and a ground truth fact states "Baron Krell rules the Northern Province"
- **THEN** the fact is counted as attributed

#### Scenario: Partial text match
- **WHEN** an injected anchor has text "Baron Krell rules the Northern Province with an iron fist" and a ground truth fact states "Baron Krell rules the Northern Province"
- **THEN** the fact is counted as attributed (normalized substring match)

#### Scenario: No matching anchor
- **WHEN** no injected anchor text matches a ground truth fact text after normalization
- **THEN** the fact is NOT counted as attributed

### Requirement: ScoringService

A `ScoringService` SHALL compute aggregate metrics from a completed simulation run. The service SHALL accept a list of turn results and ground truth facts. It SHALL return a `ScoringResult` record containing:

- `factSurvivalRate` (double): percentage of ground truth facts never contradicted
- `contradictionCount` (int): total CONTRADICTED verdicts across all turns
- `majorContradictionCount` (int): total MAJOR severity contradictions
- `driftAbsorptionRate` (double): percentage of evaluated turns with zero contradictions
- `meanTurnsToFirstDrift` (double): average turn number of first contradiction per fact (NaN if no contradictions)
- `anchorAttributionCount` (int): number of facts with at least one matching injected anchor
- `strategyEffectiveness` (Map<String, Double>): contradiction rate per attack strategy

#### Scenario: Perfect run scoring
- **WHEN** a simulation completes with zero contradictions across 10 evaluated turns
- **THEN** factSurvivalRate is 100.0, contradictionCount is 0, driftAbsorptionRate is 100.0, meanTurnsToFirstDrift is NaN

#### Scenario: Strategy effectiveness computation
- **WHEN** CONFIDENT_ASSERTION strategy was used in 4 turns and caused contradictions in 2 of them
- **THEN** strategyEffectiveness contains entry "CONFIDENT_ASSERTION" → 0.5
