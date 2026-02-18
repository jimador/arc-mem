## MODIFIED Requirements

### Requirement: Attribution accuracy and absorption rate metrics

The attribution accuracy SHALL measure how often injected anchors match ground truth facts via normalized text similarity. The computation SHALL be: `(number of ground truth facts with at least one matching injected anchor) / (total ground truth facts) * 100`. Anchor-to-fact matching SHALL normalize both texts (lowercase, strip non-alphanumeric) and check bidirectional substring containment. The absorption rate SHALL measure the percentage of evaluated turns where the DM's response had zero contradictions, computed as `(evaluated turns without contradictions / total evaluated turns) * 100`. Both values SHALL be displayed as percentages with one decimal place.

#### Scenario: High attribution accuracy
- **WHEN** 4 out of 5 ground truth facts have at least one matching injected anchor
- **THEN** the attribution accuracy displays "80.0%"

#### Scenario: High absorption rate
- **WHEN** 8 out of 10 evaluated turns produced no contradictions
- **THEN** the absorption rate displays "80.0%"

## ADDED Requirements

### Requirement: Major contradiction count metric

The DriftSummaryPanel SHALL display a `majorContradictionCount` metric showing the total number of MAJOR severity contradictions across all evaluated turns. This SHALL be displayed separately from the total contradiction count to distinguish severe drift from minor ambiguity.

#### Scenario: Major vs total contradictions
- **WHEN** a simulation has 5 total contradictions of which 2 are MAJOR severity
- **THEN** the DriftSummaryPanel displays contradictionCount=5 and majorContradictionCount=2

### Requirement: Strategy effectiveness display

The DriftSummaryPanel SHALL display a strategy effectiveness breakdown when adversarial strategies were used. Each strategy SHALL show its contradiction rate as a percentage. The breakdown SHALL only appear for adversarial scenarios.

#### Scenario: Strategy breakdown shown
- **WHEN** a completed adversarial simulation used 3 different attack strategies
- **THEN** the DriftSummaryPanel shows each strategy name with its contradiction rate percentage

#### Scenario: No strategy breakdown for baseline
- **WHEN** a completed baseline (non-adversarial) simulation is viewed
- **THEN** the strategy effectiveness section is not displayed

### Requirement: ScoringService integration

The DriftSummaryPanel SHALL consume metrics from `ScoringService` rather than computing them inline. The panel SHALL call `ScoringService.score(turnResults, groundTruthFacts)` and display the returned `ScoringResult` fields. The `SimulationRunRecord` SHALL include the `ScoringResult` for historical comparison.

#### Scenario: Metrics from ScoringService
- **WHEN** a simulation completes and the DriftSummaryPanel updates
- **THEN** all displayed metrics match the values computed by ScoringService
