## ADDED Requirements

### Requirement: Composable scoring for benchmark aggregation

`ScoringService` SHALL accept pre-collected turn snapshots via `score(List<TurnSnapshot>, List<GroundTruth>)`, enabling external callers such as `BenchmarkAggregator` to invoke scoring without coupling to `SimulationService` lifecycle. The method SHALL be stateless and side-effect-free, producing a self-contained `ScoringResult` suitable for collection and aggregation across multiple benchmark runs.

#### Scenario: Benchmark aggregator scores multiple runs independently
- **WHEN** `BenchmarkRunner` collects turn snapshots from N independent simulation runs
- **THEN** each run's snapshots can be passed to `ScoringService.score()` independently, producing N `ScoringResult` instances with no shared mutable state between invocations

#### Scenario: Scoring result is self-contained for aggregation
- **WHEN** `ScoringService.score()` returns a `ScoringResult`
- **THEN** the result contains all seven metrics (factSurvivalRate, contradictionCount, majorContradictionCount, driftAbsorptionRate, meanTurnsToFirstDrift, anchorAttributionCount, strategyEffectiveness) as numeric values directly usable for statistical computation without additional lookups

### Requirement: Verdict-based attribution counting

Attribution counting SHALL use verdict-based matching: a ground truth fact is attributed when it receives at least one CONFIRMED verdict (via `factId`) across all turn snapshots. Attribution SHALL NOT use text-based fuzzy matching or normalized substring comparison. This ensures deterministic, repeatable attribution counts across benchmark runs regardless of anchor text formatting variations.

#### Scenario: Attribution uses factId CONFIRMED verdicts
- **WHEN** a ground truth fact with `factId` "fact-1" receives a CONFIRMED verdict in any turn snapshot
- **THEN** the fact is counted as attributed via the CONFIRMED verdict, not via text similarity

#### Scenario: Attribution is deterministic across repeated runs
- **WHEN** the same scenario is run multiple times producing identical verdict sequences
- **THEN** `anchorAttributionCount` is identical across all runs, enabling meaningful statistical aggregation

#### Scenario: No attribution without CONFIRMED verdict
- **WHEN** a ground truth fact receives only NOT_MENTIONED verdicts across all turns (even if an anchor with matching text was injected)
- **THEN** the fact is NOT counted as attributed
