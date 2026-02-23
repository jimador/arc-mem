## ADDED Requirements

### Requirement: Cohen's d effect size computation

The system SHALL provide an `EffectSizeCalculator` in the `sim.benchmark` package that computes Cohen's d effect size between every pair of conditions for each of the 6 scoring metrics: `factSurvivalRate`, `driftAbsorptionRate`, `contradictionCount`, `majorContradictionCount`, `meanTurnsToFirstDrift`, and `anchorAttributionCount`. The formula SHALL be: `d = (mean1 - mean2) / pooled_sd`, where `pooled_sd` is the pooled standard deviation using sample standard deviation (N-1 denominator, Bessel's correction).

#### Scenario: Cohen's d between two conditions

- **GIVEN** condition A with factSurvivalRate values [0.90, 0.85, 0.88, 0.92, 0.87] and condition B with values [0.60, 0.55, 0.58, 0.62, 0.57]
- **WHEN** Cohen's d is computed for factSurvivalRate between conditions A and B
- **THEN** the result SHALL be a positive value indicating condition A has higher survival rate
- **AND** the computation SHALL use sample stddev (N-1 denominator) for the pooled SD

#### Scenario: Cohen's d computed for all 6 metrics

- **GIVEN** two conditions each with completed `BenchmarkReport` records
- **WHEN** effect sizes are computed between the two conditions
- **THEN** Cohen's d SHALL be computed for all 6 metrics: factSurvivalRate, driftAbsorptionRate, contradictionCount, majorContradictionCount, meanTurnsToFirstDrift, anchorAttributionCount

#### Scenario: Pooled SD uses Bessel's correction

- **GIVEN** condition A with values [10, 20] and condition B with values [30, 40]
- **WHEN** pooled SD is computed
- **THEN** the sample variance for each group SHALL use N-1 denominator (not N)

### Requirement: Effect size interpretation labels

Cohen's d values SHALL be annotated with standard interpretation labels based on absolute value: `|d| < 0.2` = "negligible", `0.2 <= |d| < 0.5` = "small", `0.5 <= |d| < 0.8` = "medium", `|d| >= 0.8` = "large".

#### Scenario: Negligible effect size

- **GIVEN** Cohen's d = 0.15 for a metric
- **WHEN** the interpretation label is computed
- **THEN** the label SHALL be "negligible"

#### Scenario: Small effect size

- **GIVEN** Cohen's d = 0.35 for a metric
- **WHEN** the interpretation label is computed
- **THEN** the label SHALL be "small"

#### Scenario: Medium effect size

- **GIVEN** Cohen's d = 0.65 for a metric
- **WHEN** the interpretation label is computed
- **THEN** the label SHALL be "medium"

#### Scenario: Large effect size

- **GIVEN** Cohen's d = 1.2 for a metric
- **WHEN** the interpretation label is computed
- **THEN** the label SHALL be "large"

#### Scenario: Negative d uses absolute value for interpretation

- **GIVEN** Cohen's d = -0.75 for a metric
- **WHEN** the interpretation label is computed
- **THEN** the label SHALL be "medium" (based on |d| = 0.75)

### Requirement: 95% confidence intervals

The system SHALL compute 95% confidence intervals for each metric in each condition cell. The confidence interval SHALL be computed as: `CI = mean +/- (1.96 x stddev / sqrt(n))`, where `stddev` is the sample standard deviation and `n` is the number of runs in the cell. The CI SHALL be represented as a pair of values (lower bound, upper bound).

#### Scenario: Confidence interval computation

- **GIVEN** a cell with 10 runs, mean factSurvivalRate = 0.85, and stddev = 0.05
- **WHEN** the 95% CI is computed
- **THEN** the CI SHALL be approximately (0.819, 0.881), computed as 0.85 +/- (1.96 x 0.05 / sqrt(10))

#### Scenario: Wider CI with fewer runs

- **GIVEN** a cell with 3 runs versus a cell with 30 runs, both having the same mean and stddev
- **WHEN** 95% CIs are computed for both
- **THEN** the cell with 3 runs SHALL have a wider confidence interval than the cell with 30 runs

#### Scenario: CI computed for each metric

- **GIVEN** a completed cell with aggregated statistics
- **WHEN** confidence intervals are computed
- **THEN** a 95% CI SHALL be produced for each of the 6 scoring metrics

### Requirement: Per-strategy effectiveness comparison

The system SHALL compute per-strategy effectiveness deltas across conditions. For each adversarial strategy present in the scenario results, the system SHALL compare the strategy's success rate (or related effectiveness metric) across conditions, enabling comparisons such as "SUBTLE_REFRAME succeeded 60% against NO_ANCHORS but 15% against FULL_ANCHORS."

#### Scenario: Strategy effectiveness delta between conditions

- **GIVEN** condition FULL_ANCHORS where SUBTLE_REFRAME achieved 15% effectiveness and condition NO_ANCHORS where SUBTLE_REFRAME achieved 60% effectiveness
- **WHEN** strategy deltas are computed
- **THEN** the delta for SUBTLE_REFRAME between NO_ANCHORS and FULL_ANCHORS SHALL indicate a 45 percentage-point difference

#### Scenario: Multiple strategies compared across conditions

- **GIVEN** three adversarial strategies (SUBTLE_REFRAME, DIRECT_CONTRADICTION, AUTHORITY_IMPERSONATION) and two conditions
- **WHEN** strategy deltas are computed
- **THEN** deltas SHALL be computed for each of the three strategies

### Requirement: Effect size matrix in ExperimentReport

The `ExperimentReport` SHALL contain an effect size matrix structured as a Map from condition-pair keys to per-metric Cohen's d values. Every unique pair of conditions SHALL have an entry in the matrix. For N conditions, the matrix SHALL contain N*(N-1)/2 entries (each unordered pair represented once).

#### Scenario: Matrix size for 4 conditions

- **GIVEN** an experiment with 4 conditions
- **WHEN** the effect size matrix is assembled
- **THEN** the matrix SHALL contain 6 entries (4*3/2 unique pairs)

#### Scenario: Matrix entry contains per-metric effect sizes

- **GIVEN** a condition pair (FULL_ANCHORS, NO_ANCHORS)
- **WHEN** the matrix entry for this pair is inspected
- **THEN** it SHALL contain Cohen's d values for all 6 scoring metrics along with their interpretation labels

### Requirement: High-variance warning

When computing effect sizes from cells where the coefficient of variation (CV = stddev / mean) exceeds 0.5 for any metric, the system SHOULD flag the corresponding effect size result as low-confidence. The flag SHOULD be included in the effect size matrix entry for that metric and condition pair.

#### Scenario: High CV triggers low-confidence flag

- **GIVEN** a cell where factSurvivalRate has mean = 0.40 and stddev = 0.25 (CV = 0.625)
- **WHEN** effect sizes involving this cell are computed
- **THEN** the factSurvivalRate effect size SHOULD be flagged as low-confidence

#### Scenario: Normal CV does not trigger flag

- **GIVEN** a cell where factSurvivalRate has mean = 0.85 and stddev = 0.05 (CV = 0.059)
- **WHEN** effect sizes involving this cell are computed
- **THEN** the factSurvivalRate effect size SHALL NOT be flagged as low-confidence

### Requirement: Edge cases in effect size computation

Cohen's d SHALL return 0.0 when both conditions have zero variance (pooled SD = 0). When a metric value is NaN (e.g., `meanTurnsToFirstDrift` when no drift occurs in any run), that metric SHALL be excluded from effect size computation for the affected condition pair. The system SHALL NOT produce NaN or Infinity in any effect size result.

#### Scenario: Zero variance in both conditions

- **GIVEN** condition A with factSurvivalRate values [1.0, 1.0, 1.0] and condition B with values [1.0, 1.0, 1.0]
- **WHEN** Cohen's d is computed
- **THEN** the result SHALL be 0.0

#### Scenario: Zero variance with different means

- **GIVEN** condition A with values [0.8, 0.8, 0.8] and condition B with values [0.6, 0.6, 0.6]
- **WHEN** Cohen's d is computed (pooled SD = 0)
- **THEN** the result SHALL be 0.0 (not Infinity)

#### Scenario: NaN metric excluded from computation

- **GIVEN** condition A where meanTurnsToFirstDrift is NaN (no drift occurred) and condition B where it is 3.5
- **WHEN** effect sizes are computed
- **THEN** meanTurnsToFirstDrift SHALL be excluded from the effect size result for this condition pair

#### Scenario: No NaN or Infinity in results

- **GIVEN** any combination of condition results
- **WHEN** effect sizes are computed
- **THEN** no effect size value in the matrix SHALL be NaN or Infinity

## Invariants

- **CS1**: Effect size computation SHALL be symmetric in magnitude: `|d(A,B)| == |d(B,A)|`. The sign indicates direction.
- **CS2**: The effect size matrix SHALL contain exactly N*(N-1)/2 entries for N conditions.
- **CS3**: All statistical computations SHALL use sample statistics (N-1 denominator), not population statistics.
