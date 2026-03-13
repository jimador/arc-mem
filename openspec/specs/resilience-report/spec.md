## ADDED Requirements

### Requirement: ResilienceReport record

The system SHALL provide an immutable `ResilienceReport` record in the `sim.report` package containing: `title` (String), `generatedAt` (Instant), `experimentName` (String), `conditions` (List of String), `scenarioIds` (List of String), `repetitionsPerCell` (int), `cancelled` (boolean), `overallScore` (ResilienceScore), `scenarioSections` (List of ScenarioSection), `strategySection` (StrategySection), and `positioning` (String).

#### Scenario: ResilienceReport construction

- **GIVEN** a completed experiment with 2 conditions and 3 scenarios
- **WHEN** a `ResilienceReport` is constructed
- **THEN** all fields SHALL be present, `scenarioSections` SHALL contain 3 entries, and `conditions` SHALL contain 2 entries

### Requirement: ScenarioSection record

The system SHALL provide a `ScenarioSection` record containing: `scenarioId` (String), `scenarioTitle` (String), `conditionSummaries` (List of ConditionSummary), `effectSizes` (List of EffectSizeSummary), `factSurvivalTable` (List of FactSurvivalRow), `contradictionDetails` (List of FactContradictionGroup), and `narrative` (String, human-readable interpretation of results for this scenario).

#### Scenario: ScenarioSection per scenario

- **GIVEN** an experiment with scenario "adversarial-contradictory"
- **WHEN** the report is built
- **THEN** a `ScenarioSection` with `scenarioId = "adversarial-contradictory"` SHALL exist with condition summaries, contradiction details, and fact survival data for each condition in the experiment

### Requirement: ConditionSummary record

The system SHALL provide a `ConditionSummary` record containing: `conditionName` (String), `metrics` (Map of metric key to `BenchmarkStatistics`), and `runCount` (int). This SHALL directly surface the per-cell `BenchmarkReport.metricStatistics` for a given condition-scenario pair.

#### Scenario: ConditionSummary from cell report

- **GIVEN** a cell report for FULL_ANCHORS x adversarial-contradictory with factSurvivalRate mean=0.90 and 5 runs
- **WHEN** the condition summary is built
- **THEN** `conditionName` SHALL be "FULL_ANCHORS", `runCount` SHALL be 5, and `metrics.get("factSurvivalRate").mean()` SHALL be 0.90

### Requirement: EffectSizeSummary record

The system SHALL provide an `EffectSizeSummary` record containing: `conditionA` (String), `conditionB` (String), `metricKey` (String), `cohensD` (double), `interpretation` (String — "negligible", "small", "medium", or "large"), and `lowConfidence` (boolean).

#### Scenario: EffectSizeSummary from effect matrix

- **GIVEN** an effect size entry for FULL_ANCHORS vs NO_ANCHORS on factSurvivalRate with d=1.8 and interpretation "large"
- **WHEN** the effect size summary is built
- **THEN** `conditionA` SHALL be "FULL_ANCHORS", `conditionB` SHALL be "NO_ANCHORS", `cohensD` SHALL be 1.8, and `interpretation` SHALL be "large"

### Requirement: FactSurvivalRow record

The system SHALL provide a `FactSurvivalRow` record containing: `factId` (String), `factText` (String), and `conditionResults` (Map of condition name to FactConditionResult). `FactConditionResult` SHALL contain: `survived` (int), `total` (int), and `firstDriftTurn` (OptionalInt — empty if no drift).

#### Scenario: FactSurvivalRow with mixed results

- **GIVEN** a fact "The king is alive" that survived 3/5 runs under FULL_ANCHORS (first drift at turn 8) and 1/5 under NO_ANCHORS (first drift at turn 3)
- **WHEN** the row is built
- **THEN** FULL_ANCHORS result SHALL have survived=3, total=5, firstDriftTurn=8; NO_ANCHORS result SHALL have survived=1, total=5, firstDriftTurn=3

### Requirement: FactContradictionGroup record

The system SHALL provide a `FactContradictionGroup` record in `sim.report` containing: `factId` (String), `factText` (String), and `details` (List of ContradictionDetail). Each group represents all contradiction events for a single ground truth fact across all conditions and runs.

#### Scenario: FactContradictionGroup per contradicted fact

- **GIVEN** fact "stone-guardian" was contradicted in 2 runs under NO_ANCHORS and 0 runs under FULL_ANCHORS
- **WHEN** contradiction details are loaded
- **THEN** a `FactContradictionGroup` with `factId = "stone-guardian"` SHALL contain 2 `ContradictionDetail` entries, all with condition "NO_ANCHORS"

### Requirement: ContradictionDetail record

The system SHALL provide a `ContradictionDetail` record in `sim.report` containing: `factId` (String), `condition` (String), `runIndex` (int), `turnNumber` (int), `attackStrategies` (List of String), `playerMessage` (String), `dmResponse` (String), `severity` (EvalVerdict.Severity), and `explanation` (String). This captures the full context of a single contradiction event for diagnostic review.

#### Scenario: ContradictionDetail captures attack context

- **GIVEN** a contradiction at turn 9 in run 2 under NO_ANCHORS where the player used SUBTLE_REFRAME
- **WHEN** the detail is built
- **THEN** `turnNumber` SHALL be 9, `runIndex` SHALL be 2, `condition` SHALL be "NO_ANCHORS", `attackStrategies` SHALL contain "SUBTLE_REFRAME", and `playerMessage` and `dmResponse` SHALL contain the actual message texts

### Requirement: ContradictionDetailLoader

The system SHALL provide a `ContradictionDetailLoader` utility class in `sim.report` with a static method `loadContradictionDetails(ExperimentReport report, RunHistoryStore store, ScenarioLoader scenarioLoader)` returning `Map<String, List<FactContradictionGroup>>` (keyed by scenarioId). The loader SHALL extract per-fact contradiction events from run records, including turn context (player message, DM response, attack strategy, severity, explanation). The loader SHALL delegate to `FactSurvivalLoader.buildGroundTruthIndex()` for ground truth resolution.

#### Scenario: Contradiction details loaded across conditions

- **GIVEN** an experiment with FULL_ANCHORS (0 contradictions) and NO_ANCHORS (5 contradictions across 2 facts)
- **WHEN** `ContradictionDetailLoader.loadContradictionDetails()` is called
- **THEN** the result SHALL contain 2 `FactContradictionGroup` entries with a total of 5 `ContradictionDetail` records

### Requirement: Contradiction detail rendering in Markdown

`MarkdownReportRenderer` SHALL render a "Contradiction Detail" section per scenario containing per-fact subsections. Each subsection SHALL display the fact ID and text as a subheader, followed by a table with columns: counter, condition, run, turn, strategy, attack (player message), response (DM response), severity, and explanation. Facts with no contradictions SHALL be omitted from this section.

#### Scenario: Contradiction detail table in Markdown

- **GIVEN** a scenario where fact "iron-door" has 2 contradiction events
- **WHEN** the Markdown is rendered
- **THEN** a "#### iron-door:" subheader SHALL appear followed by a table with 2 data rows containing the full context of each contradiction

### Requirement: StrategySection record

The system SHALL provide a `StrategySection` record containing: `strategies` (Map of strategy name to Map of condition name to Double representing mean effectiveness). This SHALL directly surface `ExperimentReport.strategyDeltas`.

### Requirement: ResilienceReportBuilder service

The system SHALL provide a `ResilienceReportBuilder` Spring service in `sim.report` that transforms an `ExperimentReport` into a `ResilienceReport`. The builder SHALL:

1. Compute the overall `ResilienceScore` from experiment metrics.
2. Build one `ScenarioSection` per scenario in the experiment.
3. Extract per-fact survival data by loading `SimulationRunRecord` instances via `RunHistoryStore` for each cell's `runIds`, iterating turn snapshots for contradiction verdicts (same logic as `FactDrillDownPanel`).
4. Extract effect size summaries from `ExperimentReport.effectSizeMatrix`.
5. Build strategy section from `ExperimentReport.strategyDeltas`.
6. Generate per-scenario narrative text summarizing key findings.
7. Set the positioning statement from a configurable template.

#### Scenario: Builder produces complete report

- **GIVEN** a completed experiment with 2 conditions, 1 scenario, 5 reps
- **WHEN** `ResilienceReportBuilder.build(report)` is called
- **THEN** the returned `ResilienceReport` SHALL have 1 scenario section, effect sizes for the condition pair, a non-null overall score, and a non-empty positioning string

#### Scenario: Builder handles cancelled experiment

- **GIVEN** a cancelled experiment with 3 of 8 cells completed
- **WHEN** `ResilienceReportBuilder.build(report)` is called
- **THEN** the returned `ResilienceReport` SHALL have `cancelled = true` and scenario sections only for scenarios with at least one completed cell

### Requirement: Narrative generation

`ResilienceReportBuilder` SHALL generate a per-scenario narrative string summarizing: which condition performed best on survival rate, the magnitude of effect sizes, whether any facts were universally contradicted, and whether high variance was observed. The narrative SHALL be plain English, suitable for inclusion in a tech report.

#### Scenario: Narrative mentions dominant condition

- **GIVEN** FULL_ANCHORS has factSurvivalRate mean=0.92 and NO_ANCHORS has mean=0.48 with Cohen's d=1.8 (large)
- **WHEN** the narrative is generated
- **THEN** it SHALL mention that FULL_ANCHORS significantly outperformed NO_ANCHORS on fact survival with a large effect size

### Requirement: Positioning statement

The `ResilienceReport` SHALL include a `positioning` field containing a statement that positions the memory unit system relative to: MemGPT/Letta (OS-style paging), Zep/Graphiti (temporal knowledge graphs), ShardMemo (sharded retrieval), and Core Anchor Memory (static context anchoring). The statement SHALL emphasize that this system focuses on trust/authority-governed working memory and adversarial drift resistance.

#### Scenario: Positioning references related systems

- **WHEN** a `ResilienceReport` is generated
- **THEN** the `positioning` field SHALL be non-empty and SHALL contain references to at least MemGPT, Zep/Graphiti, and the system's focus on adversarial drift

### Requirement: MarkdownReportRenderer

The system SHALL provide a `MarkdownReportRenderer` class in `sim.report` with a static method `render(ResilienceReport report)` returning a `String` containing a complete Markdown document. The document SHALL include:

1. Title and metadata (experiment name, date, conditions, scenarios, reps).
2. Overall resilience score with breakdown.
3. Per-scenario sections with condition comparison tables, effect sizes, and per-fact survival tables.
4. Strategy effectiveness table.
5. Positioning statement.
6. "Generated by arc-mem" footer with timestamp.

#### Scenario: Markdown contains all sections

- **GIVEN** a `ResilienceReport` with 2 scenarios
- **WHEN** `MarkdownReportRenderer.render(report)` is called
- **THEN** the returned Markdown SHALL contain headers for each scenario, a "Resilience Score" section, a "Strategy Effectiveness" section, and a "Positioning" section

#### Scenario: Markdown tables are valid

- **GIVEN** a scenario with 3 facts and 2 conditions
- **WHEN** the Markdown is rendered
- **THEN** the per-fact survival table SHALL be valid Markdown table syntax with a header row, separator row, and 3 data rows

#### Scenario: Cancelled experiment noted in Markdown

- **GIVEN** a cancelled experiment report
- **WHEN** the Markdown is rendered
- **THEN** a warning note SHALL appear near the top: "Note: This experiment was cancelled. Results are partial."

## Invariants

- **RR1**: `ResilienceReport` SHALL be fully constructible from an `ExperimentReport` without any additional user input.
- **RR2**: `MarkdownReportRenderer.render()` SHALL be a pure function with no side effects.
- **RR3**: Per-fact survival data in the report SHALL be consistent with the data shown by `FactDrillDownPanel` for the same experiment.
- **RER1**: Resilience report narrative SHALL NOT overstate conclusions beyond validated evidence grade.

## Added Requirements (initial-community-review-readiness)

### Requirement: Narrative generation (evidence-bound)

Narrative generation SHALL remain evidence-bound and SHALL include confidence qualifiers. When stability gates are unmet, required ablations are missing, or degraded runs materially affect results, the report SHALL use provisional language and SHALL explicitly block material-robustness claims.

#### Scenario: Missing ablation forces provisional narrative
- **GIVEN** a resilience report generated without all required ablations
- **WHEN** narrative text is composed
- **THEN** the narrative SHALL state that results are provisional
- **AND** material robustness claims SHALL be marked unsupported

#### Scenario: Stable claim-grade evidence allows non-provisional summary
- **GIVEN** a resilience report with required ablations, stability checks, and no blocking degradations
- **WHEN** narrative text is composed
- **THEN** the summary SHALL indicate claim-grade readiness

### Requirement: MarkdownReportRenderer (evidence grade metadata)

Markdown rendering SHALL include evidence-grade metadata and degraded-run indicators in the report header or summary blocks so reviewers can quickly assess evidence quality.

#### Scenario: Rendered report shows evidence grade and degraded-run indicators
- **GIVEN** a generated resilience report with degraded runs
- **WHEN** markdown is rendered
- **THEN** the output SHALL show evidence grade and degraded-run indicators in summary sections
