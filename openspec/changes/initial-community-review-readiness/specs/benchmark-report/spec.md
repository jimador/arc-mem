## MODIFIED Requirements

### Requirement: BenchmarkReport record structure

`BenchmarkReport` SHALL include model identifier and degraded-run counts so reviewers can assess evidence quality at a glance.

#### Scenario: Report contains model identifier and degraded-run counts
- **GIVEN** a completed benchmark run cohort
- **WHEN** the benchmark report is assembled
- **THEN** the report SHALL include the model identifier used and the count of runs where conflict evaluation was degraded

#### Scenario: Non-zero degraded-run count is visible in rendered output
- **GIVEN** a benchmark report with one or more degraded runs
- **WHEN** the report is rendered
- **THEN** the degraded-run count SHALL appear in the rendered output so reviewers notice it

## Invariants

- **BMR1**: Benchmark reports SHALL surface model identifier and degraded-run counts so evidence quality is assessable.
