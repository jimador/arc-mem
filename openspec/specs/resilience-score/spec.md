## ADDED Requirements

### Requirement: ResilienceScore record

The system SHALL provide an immutable `ResilienceScore` record in the `sim.report` package containing: `overall` (double, 0.0–100.0), `survivalComponent` (double, 0.0–100.0), `driftResistanceComponent` (double, 0.0–100.0), `contradictionPenalty` (double, 0.0–100.0), and `strategyResistanceComponent` (double, 0.0–100.0).

#### Scenario: ResilienceScore with perfect results

- **GIVEN** 100% survival rate, no contradictions, no drift, and 0% strategy effectiveness
- **WHEN** the score is computed
- **THEN** `overall` SHALL be 100.0, `survivalComponent` SHALL be 100.0, `contradictionPenalty` SHALL be 0.0

#### Scenario: ResilienceScore with poor results

- **GIVEN** 30% survival rate, 5 contradictions per run, first drift at turn 2, and 80% strategy effectiveness
- **WHEN** the score is computed
- **THEN** `overall` SHALL be significantly below 50.0

### Requirement: ResilienceScore computation

The system SHALL provide a `ResilienceScoreCalculator` class in `sim.report` with a static method `compute(ExperimentReport report, String referenceCondition)` that computes a `ResilienceScore` for the specified condition across all scenarios. The computation SHALL use the following weighted formula:

- **survivalComponent** (weight 0.40): Mean `factSurvivalRate` across all cells for the reference condition, scaled to 0–100.
- **driftResistanceComponent** (weight 0.25): Based on `driftAbsorptionRate` mean. Higher absorption = higher score. Scaled to 0–100.
- **contradictionPenalty** (weight 0.20): Based on mean `contradictionCount`. Zero contradictions = 100, penalty increases with contradiction count. Formula: `max(0, 100 - (meanContradictions * 20))`.
- **strategyResistanceComponent** (weight 0.15): Inverse of mean strategy effectiveness across all strategies for the reference condition. Lower strategy effectiveness (i.e., attacks are less effective) = higher resistance score. Formula: `max(0, 100 - (meanStrategyEffectiveness * 100))`.
- **overall**: Weighted sum of the four components.

#### Scenario: Compute from experiment with two conditions

- **GIVEN** an experiment with FULL_ANCHORS (survivalRate=0.90, absorption=0.95, contradictions=0.5, strategyEffectiveness=0.10) and NO_ANCHORS
- **WHEN** `ResilienceScoreCalculator.compute(report, "FULL_ANCHORS")` is called
- **THEN** survivalComponent SHALL be 90.0, driftResistanceComponent SHALL be 95.0, contradictionPenalty SHALL be 90.0, strategyResistanceComponent SHALL be 90.0, and overall SHALL be approximately 91.25

#### Scenario: Compute handles missing metrics gracefully

- **GIVEN** a cell report missing the `driftAbsorptionRate` metric
- **WHEN** the score is computed
- **THEN** `driftResistanceComponent` SHALL default to 0.0 (conservative) and the overall score SHALL still be computed

#### Scenario: Compute handles cancelled experiment

- **GIVEN** a cancelled experiment with only 2 of 6 cells completed for FULL_ANCHORS
- **WHEN** `ResilienceScoreCalculator.compute(report, "FULL_ANCHORS")` is called
- **THEN** the score SHALL be computed from the 2 available cells only

### Requirement: Score interpretation

`ResilienceScore` SHALL provide a method `interpretation()` returning a String with the following thresholds:

- overall >= 90: "Excellent"
- overall >= 75: "Good"
- overall >= 50: "Moderate"
- overall >= 25: "Weak"
- overall < 25: "Poor"

#### Scenario: Score interpretation at boundary

- **GIVEN** a `ResilienceScore` with `overall = 75.0`
- **THEN** `interpretation()` SHALL return "Good"

#### Scenario: Score interpretation below threshold

- **GIVEN** a `ResilienceScore` with `overall = 49.9`
- **THEN** `interpretation()` SHALL return "Moderate"

### Requirement: Comparative score

`ResilienceScoreCalculator` SHALL provide a method `computeComparative(ExperimentReport report)` returning a Map of condition name to `ResilienceScore`. This computes the resilience score for every condition in the experiment, enabling side-by-side comparison.

#### Scenario: Comparative scores for all conditions

- **GIVEN** an experiment with 3 conditions: FULL_ANCHORS, NO_ANCHORS, FLAT_AUTHORITY
- **WHEN** `computeComparative(report)` is called
- **THEN** the result SHALL contain 3 entries, one per condition, each with a valid `ResilienceScore`

## Invariants

- **RS1**: `ResilienceScore.overall` SHALL always be in the range [0.0, 100.0].
- **RS2**: All component scores SHALL be in the range [0.0, 100.0].
- **RS3**: `overall` SHALL equal `survivalComponent * 0.40 + driftResistanceComponent * 0.25 + contradictionPenalty * 0.20 + strategyResistanceComponent * 0.15` (within floating-point tolerance).
