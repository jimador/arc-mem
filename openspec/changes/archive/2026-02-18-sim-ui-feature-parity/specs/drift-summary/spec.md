## ADDED Requirements

### Requirement: Six-stat CSS grid layout

The `DriftSummaryPanel` SHALL render a 6-cell CSS grid displaying aggregate drift metrics. The grid SHALL use a 3-column, 2-row layout. Each cell SHALL display a metric label and its computed value. The grid SHALL be visible after a simulation completes or when reviewing a completed run.

#### Scenario: Grid renders after simulation completion
- **WHEN** a simulation reaches the COMPLETE phase
- **THEN** the DriftSummaryPanel displays a 3x2 grid with all six metrics populated

#### Scenario: Grid hidden during simulation
- **WHEN** a simulation is in the RUNNING phase
- **THEN** the DriftSummaryPanel is either hidden or shows a placeholder message

### Requirement: Survival rate metric

The survival rate SHALL be computed as the percentage of ground truth facts that were never contradicted across all evaluated turns. The formula SHALL be: `(facts with zero CONTRADICTED verdicts / total ground truth facts) * 100`. The value SHALL be displayed as a percentage with one decimal place.

#### Scenario: All facts survive
- **WHEN** a simulation completes with 5 ground truth facts and none were contradicted in any turn
- **THEN** the survival rate displays "100.0%"

#### Scenario: Partial survival
- **WHEN** 3 out of 5 ground truth facts were contradicted at least once
- **THEN** the survival rate displays "40.0%"

### Requirement: Contradiction count metric

The contradiction count SHALL be the total number of CONTRADICTED verdicts across all evaluated turns. This counts each individual contradiction event, not unique facts. The value SHALL be displayed as an integer.

#### Scenario: Multiple contradictions across turns
- **WHEN** fact-1 is contradicted in turns 5 and 8, and fact-2 is contradicted in turn 6
- **THEN** the contradiction count displays "3"

### Requirement: Major drift count metric

The major drift count SHALL be the number of turns where at least one ground truth fact was contradicted. This measures how many turns experienced drift events. The value SHALL be displayed as an integer with the total evaluated turns as denominator (e.g., "3 / 10").

#### Scenario: Drift in multiple turns
- **WHEN** contradictions occur in turns 5, 8, and 12 out of 15 evaluated turns
- **THEN** the major drift count displays "3 / 15"

### Requirement: Mean first drift turn metric

The mean first drift turn SHALL be the average turn number at which each ground truth fact was first contradicted. Facts that were never contradicted SHALL be excluded from the average. If no facts were contradicted, the value SHALL display "N/A". The value SHALL be displayed as a decimal with one decimal place.

#### Scenario: First contradictions at varying turns
- **WHEN** fact-1 is first contradicted at turn 5 and fact-2 at turn 9
- **THEN** the mean first drift turn displays "7.0"

#### Scenario: No contradictions
- **WHEN** no ground truth facts were contradicted
- **THEN** the mean first drift turn displays "N/A"

### Requirement: Attribution accuracy and absorption rate metrics

The attribution accuracy SHALL measure how often the evaluator correctly identified which specific anchor was relevant to a verdict, computed as a ratio of verdicts with correct anchor attribution to total verdicts. The absorption rate SHALL measure the percentage of adversarial turns where the DM's response successfully maintained consistency (i.e., no contradictions), computed as `(adversarial turns without contradictions / total adversarial turns) * 100`. Both values SHALL be displayed as percentages.

#### Scenario: High absorption rate
- **WHEN** 8 out of 10 adversarial turns produced no contradictions
- **THEN** the absorption rate displays "80.0%"
