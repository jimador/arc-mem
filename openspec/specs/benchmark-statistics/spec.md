## ADDED Requirements

### Requirement: BenchmarkStatistics record

The system SHALL provide an immutable `BenchmarkStatistics` record in the `sim.benchmark` package with the following fields: `mean` (double), `stddev` (double), `min` (double), `max` (double), `median` (double), `p95` (double), and `sampleCount` (int).

#### Scenario: Record field access

- **GIVEN** a `BenchmarkStatistics` constructed with `mean=0.85, stddev=0.05, min=0.75, max=0.95, median=0.86, p95=0.93, sampleCount=10`
- **WHEN** each accessor is called
- **THEN** `mean()` SHALL return `0.85`, `stddev()` SHALL return `0.05`, `min()` SHALL return `0.75`, `max()` SHALL return `0.95`, `median()` SHALL return `0.86`, `p95()` SHALL return `0.93`, and `sampleCount()` SHALL return `10`

### Requirement: BenchmarkAggregator service

The system SHALL provide a `BenchmarkAggregator` service in the `sim.benchmark` package annotated with `@Service`. The service SHALL be stateless with no injected dependencies. It SHALL expose a method `aggregate(List<ScoringResult> results, String scenarioId)` returning a `BenchmarkReport`.

#### Scenario: Service is stateless

- **GIVEN** two successive calls to `aggregate()` with different inputs
- **WHEN** each call completes
- **THEN** the results SHALL be independent with no shared state between calls

### Requirement: Per-metric statistics for all ScoringResult fields

`BenchmarkAggregator.aggregate()` SHALL compute a `BenchmarkStatistics` for each of the 7 `ScoringResult` fields: `factSurvivalRate`, `contradictionCount`, `majorContradictionCount`, `driftAbsorptionRate`, `meanTurnsToFirstDrift`, `unitAttributionCount`, and `strategyEffectiveness`. The resulting `BenchmarkReport.metricStatistics()` map SHALL contain exactly these 7 keys (excluding `strategyEffectiveness`, which is handled separately).

#### Scenario: Six metric keys in report

- **GIVEN** a `List<ScoringResult>` with 5 entries
- **WHEN** `aggregate()` is called
- **THEN** `report.metricStatistics()` SHALL contain exactly the keys: `"factSurvivalRate"`, `"contradictionCount"`, `"majorContradictionCount"`, `"driftAbsorptionRate"`, `"meanTurnsToFirstDrift"`, `"unitAttributionCount"`

#### Scenario: Mean computed correctly

- **GIVEN** 3 scoring results with `factSurvivalRate` values `[0.80, 0.85, 0.90]`
- **WHEN** `aggregate()` is called
- **THEN** `metricStatistics.get("factSurvivalRate").mean()` SHALL equal `0.85`

#### Scenario: Standard deviation computed correctly

- **GIVEN** 3 scoring results with `factSurvivalRate` values `[0.80, 0.85, 0.90]`
- **WHEN** `aggregate()` is called
- **THEN** `metricStatistics.get("factSurvivalRate").stddev()` SHALL equal the population standard deviation of `[0.80, 0.85, 0.90]`

### Requirement: Median computation

`BenchmarkAggregator` SHALL compute the median as the middle value for odd-length samples and the average of the two middle values for even-length samples, using a sorted copy of the input values.

#### Scenario: Odd sample count median

- **GIVEN** 5 scoring results with `contradictionCount` values `[1, 3, 2, 5, 4]`
- **WHEN** `aggregate()` is called
- **THEN** `metricStatistics.get("contradictionCount").median()` SHALL equal `3.0`

#### Scenario: Even sample count median

- **GIVEN** 4 scoring results with `contradictionCount` values `[1, 4, 2, 3]`
- **WHEN** `aggregate()` is called
- **THEN** `metricStatistics.get("contradictionCount").median()` SHALL equal `2.5`

### Requirement: P95 computation

`BenchmarkAggregator` SHALL compute the 95th percentile using the nearest-rank method on sorted values. For sample sizes where the exact 95th percentile index is non-integer, the system SHALL use linear interpolation between adjacent sorted values.

#### Scenario: P95 with 10 samples

- **GIVEN** 10 scoring results with `driftAbsorptionRate` values `[0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]`
- **WHEN** `aggregate()` is called
- **THEN** `metricStatistics.get("driftAbsorptionRate").p95()` SHALL be approximately `0.955` (interpolated between 0.9 and 1.0)

### Requirement: Per-strategy statistics from strategyEffectiveness

`BenchmarkAggregator` SHALL extract the `strategyEffectiveness` map from each `ScoringResult`, collect values by strategy name across all runs, and compute a `BenchmarkStatistics` for each strategy. The resulting `BenchmarkReport.strategyStatistics()` map SHALL be keyed by strategy name.

#### Scenario: Strategy statistics aggregated

- **GIVEN** 3 scoring results where each has `strategyEffectiveness = {"SUBTLE_REFRAME": 0.6, "AUTHORITY_CHALLENGE": 0.3}`, `{"SUBTLE_REFRAME": 0.7, "AUTHORITY_CHALLENGE": 0.4}`, and `{"SUBTLE_REFRAME": 0.8, "AUTHORITY_CHALLENGE": 0.5}`
- **WHEN** `aggregate()` is called
- **THEN** `report.strategyStatistics()` SHALL contain keys `"SUBTLE_REFRAME"` and `"AUTHORITY_CHALLENGE"` with means `0.7` and `0.4` respectively

#### Scenario: Strategy missing from some runs

- **GIVEN** 3 scoring results where run 1 has `strategyEffectiveness = {"A": 0.5, "B": 0.3}`, run 2 has `{"A": 0.7}`, and run 3 has `{"A": 0.6, "B": 0.4}`
- **WHEN** `aggregate()` is called
- **THEN** strategy `"A"` SHALL have `sampleCount = 3` and strategy `"B"` SHALL have `sampleCount = 2`

### Requirement: Coefficient of variation flagging

`BenchmarkStatistics` SHALL provide a method `coefficientOfVariation()` that returns `stddev / mean`. When `mean == 0.0`, the method SHALL return `0.0` to avoid division by zero. The system SHALL consider a metric "high variance" when `coefficientOfVariation() > 0.5`.

#### Scenario: CV below threshold

- **GIVEN** a `BenchmarkStatistics` with `mean = 0.80` and `stddev = 0.10`
- **WHEN** `coefficientOfVariation()` is called
- **THEN** the result SHALL be `0.125` and the metric SHALL NOT be flagged as high variance

#### Scenario: CV above threshold

- **GIVEN** a `BenchmarkStatistics` with `mean = 0.40` and `stddev = 0.30`
- **WHEN** `coefficientOfVariation()` is called
- **THEN** the result SHALL be `0.75` and the metric SHALL be flagged as high variance

#### Scenario: CV with zero mean

- **GIVEN** a `BenchmarkStatistics` with `mean = 0.0` and `stddev = 0.0`
- **WHEN** `coefficientOfVariation()` is called
- **THEN** the result SHALL be `0.0`

### Requirement: Empty input handling

`BenchmarkAggregator.aggregate()` SHALL throw `IllegalArgumentException` when called with an empty `List<ScoringResult>`.

#### Scenario: Empty list rejected

- **GIVEN** an empty `List<ScoringResult>`
- **WHEN** `aggregate()` is called
- **THEN** the method SHALL throw `IllegalArgumentException` with a message indicating at least one result is required

### Requirement: Single run edge case

When `aggregate()` receives a single `ScoringResult`, all `BenchmarkStatistics` instances SHALL have `stddev = 0.0`, `mean == median == min == max == p95`, and `sampleCount = 1`.

#### Scenario: Single run produces zero stddev

- **GIVEN** a single `ScoringResult` with `factSurvivalRate = 0.90`
- **WHEN** `aggregate()` is called
- **THEN** `metricStatistics.get("factSurvivalRate")` SHALL have `mean = 0.90`, `stddev = 0.0`, `min = 0.90`, `max = 0.90`, `median = 0.90`, `p95 = 0.90`, and `sampleCount = 1`

### Requirement: NaN handling for meanTurnsToFirstDrift

When `meanTurnsToFirstDrift` is `NaN` (indicating no drift occurred in a run), `BenchmarkAggregator` SHALL exclude that run's value from the `meanTurnsToFirstDrift` statistics computation. If all runs produce `NaN`, the resulting `BenchmarkStatistics` for `meanTurnsToFirstDrift` SHALL have all fields set to `Double.NaN` and `sampleCount = 0`.

#### Scenario: Some runs have NaN meanTurnsToFirstDrift

- **GIVEN** 4 scoring results with `meanTurnsToFirstDrift` values `[3.0, NaN, 5.0, NaN]`
- **WHEN** `aggregate()` is called
- **THEN** `metricStatistics.get("meanTurnsToFirstDrift")` SHALL have `sampleCount = 2`, `mean = 4.0`, and values computed only from `[3.0, 5.0]`

#### Scenario: All runs have NaN meanTurnsToFirstDrift

- **GIVEN** 3 scoring results where all have `meanTurnsToFirstDrift = NaN`
- **WHEN** `aggregate()` is called
- **THEN** `metricStatistics.get("meanTurnsToFirstDrift")` SHALL have `sampleCount = 0` and all numeric fields SHALL be `NaN`

### Requirement: Baseline comparison deltas

`BenchmarkAggregator` SHALL provide a method `computeDeltas(BenchmarkReport current, BenchmarkReport baseline)` returning a `Map<String, Double>` where each key is a metric name and the value is `current.mean - baseline.mean`. The map SHALL contain entries for all metric keys present in both reports.

#### Scenario: Delta computation

- **GIVEN** a current report with `factSurvivalRate` mean `0.85` and a baseline report with `factSurvivalRate` mean `0.80`
- **WHEN** `computeDeltas(current, baseline)` is called
- **THEN** the result SHALL contain `"factSurvivalRate" -> 0.05`

#### Scenario: Negative delta for regression

- **GIVEN** a current report with `contradictionCount` mean `3.0` and a baseline report with `contradictionCount` mean `2.0`
- **WHEN** `computeDeltas(current, baseline)` is called
- **THEN** the result SHALL contain `"contradictionCount" -> 1.0`

## Invariants

- **BS1**: `BenchmarkStatistics.min()` SHALL always be <= `mean()` <= `max()`.
- **BS2**: `BenchmarkStatistics.median()` SHALL always be >= `min()` and <= `max()`.
- **BS3**: `BenchmarkStatistics.stddev()` SHALL always be >= 0.
- **BS4**: `BenchmarkStatistics.sampleCount()` SHALL always be >= 0.
- **BS5**: `BenchmarkAggregator` SHALL use only standard library math (no external statistics dependencies).
