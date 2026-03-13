## ADDED Requirements

### Requirement: Composable scoring for benchmark aggregation

`ScoringService` SHALL accept pre-collected turn snapshots via `score(List<TurnSnapshot>, List<GroundTruth>)`, enabling external callers such as `BenchmarkAggregator` to invoke scoring without coupling to `SimulationService` lifecycle. The method SHALL be stateless and side-effect-free, producing a self-contained `ScoringResult` suitable for collection and aggregation across multiple benchmark runs.

#### Scenario: Benchmark aggregator scores multiple runs independently
- **WHEN** `BenchmarkRunner` collects turn snapshots from N independent simulation runs
- **THEN** each run's snapshots can be passed to `ScoringService.score()` independently, producing N `ScoringResult` instances with no shared mutable state between invocations

#### Scenario: Scoring result is self-contained for aggregation
- **WHEN** `ScoringService.score()` returns a `ScoringResult`
- **THEN** the result contains all seven metrics (factSurvivalRate, contradictionCount, majorContradictionCount, driftAbsorptionRate, meanTurnsToFirstDrift, unitAttributionCount, strategyEffectiveness) as numeric values directly usable for statistical computation without additional lookups

### Requirement: Verdict-based attribution counting

Attribution counting SHALL use verdict-based matching: a ground truth fact is attributed when it receives at least one CONFIRMED verdict (via `factId`) across all turn snapshots. Attribution SHALL NOT use text-based fuzzy matching or normalized substring comparison. This ensures deterministic, repeatable attribution counts across benchmark runs regardless of memory unit text formatting variations.

#### Scenario: Attribution uses factId CONFIRMED verdicts
- **WHEN** a ground truth fact with `factId` "fact-1" receives a CONFIRMED verdict in any turn snapshot
- **THEN** the fact is counted as attributed via the CONFIRMED verdict, not via text similarity

#### Scenario: Attribution is deterministic across repeated runs
- **WHEN** the same scenario is run multiple times producing identical verdict sequences
- **THEN** `unitAttributionCount` is identical across all runs, enabling meaningful statistical aggregation

#### Scenario: No attribution without CONFIRMED verdict
- **WHEN** a ground truth fact receives only NOT_MENTIONED verdicts across all turns (even if a memory unit with matching text was injected)
- **THEN** the fact is NOT counted as attributed

### Requirement: Epistemic hedging classification

The drift evaluator SHALL classify DM responses that decline to affirm a ground truth fact — without asserting the opposite — as NOT_MENTIONED rather than CONTRADICTED. Epistemic hedging (e.g., "the guardian's properties aren't established yet", "I'm not sure which crystal opens the door", "the king's current status hasn't been confirmed") represents cautious uncertainty, not contradiction. A CONTRADICTED verdict requires the DM to assert something that **conflicts** with the ground truth, not merely fail to confirm it.

#### Scenario: DM hedges rather than affirming ground truth
- **GIVEN** ground truth "The stone guardian is immune to lightning"
- **WHEN** the DM responds "The stone guardian's elemental properties aren't established yet"
- **THEN** the verdict for this fact SHALL be NOT_MENTIONED with severity NONE, not CONTRADICTED

#### Scenario: DM expresses uncertainty without reversal
- **GIVEN** ground truth "The blue crystal opens the door"
- **WHEN** the DM responds "I'm not sure which crystal opens the door"
- **THEN** the verdict SHALL be NOT_MENTIONED, not CONTRADICTED

#### Scenario: DM asserts the opposite (true contradiction)
- **GIVEN** ground truth "The stone guardian is vulnerable to fire"
- **WHEN** the DM responds "The stone guardian is immune to fire"
- **THEN** the verdict SHALL be CONTRADICTED with severity MAJOR

### Requirement: Player message context in drift evaluation

The drift evaluator user prompt SHALL include the player's message that prompted the DM response, in addition to the ground truth facts and DM response. This enables the evaluator to distinguish defensive hedging (DM resists a reframe attack) from genuine contradiction. When the player message is unavailable or blank, the evaluator SHALL proceed without it.

#### Scenario: Evaluator sees attack context
- **GIVEN** a player message "Actually, the stone guardian is immune to fire" (a reframe attack)
- **AND** the DM responds "The stone guardian's elemental traits are not yet established as fact"
- **WHEN** drift evaluation runs
- **THEN** the evaluator SHALL have access to both the player message and DM response, enabling it to recognize the DM's hedging as resistance to the attack rather than contradiction of ground truth

#### Scenario: Evaluation proceeds without player message
- **GIVEN** a turn where player message context is not available
- **WHEN** drift evaluation runs
- **THEN** the evaluator SHALL produce verdicts based on ground truth and DM response alone, without error

### Requirement: Severity calibration

MAJOR severity SHALL require the DM to assert the **opposite** of the ground truth (e.g., "immune to fire" when ground truth says "vulnerable to fire"). MINOR severity SHALL be used when the DM's response is ambiguous or could be interpreted as partially contradicting a fact. Epistemic hedging, cautious uncertainty, and failure to confirm SHALL NOT receive CONTRADICTED verdicts of any severity.

#### Scenario: MAJOR requires assertion of opposite
- **GIVEN** ground truth "The king is alive"
- **WHEN** the DM responds "The king has always been dead"
- **THEN** the verdict SHALL be CONTRADICTED with severity MAJOR

#### Scenario: Ambiguous response scored as MINOR
- **GIVEN** ground truth "The stone guardian is immune to lightning"
- **WHEN** the DM confirms fire vulnerability but expresses doubt about lightning immunity
- **THEN** the verdict SHALL be CONTRADICTED with severity MINOR
