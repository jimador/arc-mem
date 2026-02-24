## 1. Data Loading Extraction

- [x] 1.1 Create `FactSurvivalLoader` in `sim.report` package. Extract the per-fact survival loading logic from `FactDrillDownPanel` into this class: `loadFactSurvival(ExperimentReport report, RunHistoryStore store, ScenarioLoader scenarioLoader)` returning `Map<String, List<FactSurvivalRow>>` (keyed by scenarioId). Include ground truth text resolution, run record loading, contradiction verdict extraction, and first-drift turn computation. (design D2, resilience-report spec: FactSurvivalRow)
- [x] 1.2 Refactor `FactDrillDownPanel` to delegate per-fact data loading to `FactSurvivalLoader`. Replace inline loading logic with calls to the shared loader. Verify no behavioral change in the drill-down UI. (design D2)

## 2. Resilience Score

- [x] 2.1 Create `ResilienceScore` record in `sim.report` with fields: `overall`, `survivalComponent`, `driftResistanceComponent`, `contradictionPenalty`, `strategyResistanceComponent` (all double). Add `interpretation()` method returning "Excellent"/"Good"/"Moderate"/"Weak"/"Poor" based on overall score thresholds. (resilience-score spec)
- [x] 2.2 Create `ResilienceScoreCalculator` in `sim.report` with static methods `compute(ExperimentReport report, String referenceCondition)` and `computeComparative(ExperimentReport report)`. Implement weighted formula: survival=0.40, drift=0.25, contradiction=0.20, strategy=0.15. Handle missing metrics with conservative defaults (0.0). (resilience-score spec, design D6)

## 3. Report Data Model

- [x] 3.1 Create report records in `sim.report`: `ConditionSummary(String conditionName, Map<String, BenchmarkStatistics> metrics, int runCount)`, `EffectSizeSummary(String conditionA, String conditionB, String metricKey, double cohensD, String interpretation, boolean lowConfidence)`, `FactConditionResult(int survived, int total, OptionalInt firstDriftTurn)`, `FactSurvivalRow(String factId, String factText, Map<String, FactConditionResult> conditionResults)`, `StrategySection(Map<String, Map<String, Double>> strategies)`. (resilience-report spec)
- [x] 3.2 Create `ScenarioSection` record in `sim.report` with fields: `scenarioId`, `scenarioTitle`, `conditionSummaries` (List), `effectSizes` (List), `factSurvivalTable` (List), `narrative` (String). (resilience-report spec)
- [x] 3.3 Create `ResilienceReport` record in `sim.report` with fields: `title`, `generatedAt`, `experimentName`, `conditions`, `scenarioIds`, `repetitionsPerCell`, `cancelled`, `overallScore` (ResilienceScore), `scenarioSections` (List), `strategySection`, `positioning`. (resilience-report spec)

## 4. Report Builder

- [x] 4.1 Create `ResilienceReportBuilder` Spring service in `sim.report`. Constructor-inject `RunHistoryStore` and `ScenarioLoader`. Implement `build(ExperimentReport report)` returning `ResilienceReport`. Wire scenario section construction: for each scenarioId, build `ConditionSummary` from cell reports, extract `EffectSizeSummary` from effect size matrix, load `FactSurvivalRow` via `FactSurvivalLoader`. (resilience-report spec: ResilienceReportBuilder)
- [x] 4.2 Implement narrative generation in `ResilienceReportBuilder`. For each scenario section, generate a plain-English paragraph summarizing: best condition on survival, effect size magnitude, universally contradicted facts, high-variance warnings. (resilience-report spec: narrative generation)
- [x] 4.3 Add static positioning statement constant to `ResilienceReportBuilder`. Reference MemGPT/Letta, Zep/Graphiti, ShardMemo, Core Anchor Memory. Emphasize trust/authority-governed working memory and adversarial drift resistance. (resilience-report spec: positioning statement, design D5)

## 5. Markdown Renderer

- [x] 5.1 Create `MarkdownReportRenderer` in `sim.report` with static method `render(ResilienceReport report)` returning Markdown string. Render: title header, metadata table (experiment name, date, conditions, scenarios, reps), cancellation warning if applicable, overall resilience score with component breakdown. (resilience-report spec: MarkdownReportRenderer)
- [x] 5.2 Render per-scenario sections: scenario heading, condition comparison table (metrics as rows, conditions as columns), effect size annotations, per-fact survival table (fact text truncated to 60 chars, full text in row). (resilience-report spec: MarkdownReportRenderer)
- [x] 5.3 Render strategy effectiveness table, positioning section, and footer with generation timestamp. (resilience-report spec: MarkdownReportRenderer)

## 6. UI Integration

- [x] 6.1 Add "Generate Report" button to `ConditionComparisonPanel`. Button visible only when a report is displayed. On click: disable button with "Generating..." text, launch `ResilienceReportBuilder.build()` via `CompletableFuture`, render to Markdown via `MarkdownReportRenderer`, create `StreamResource` for download, trigger download via hidden `Anchor` element, re-enable button. (benchmark-view-routing spec: Generate Report action, design D3/D4)
- [x] 6.2 Wire `ResilienceReportBuilder` into `BenchmarkView` constructor (Spring-injected). Pass to `ConditionComparisonPanel` via setter or constructor parameter. (design D1)

## 7. Verification

- [x] 7.1 Run `./mvnw.cmd clean compile -DskipTests` — verify clean compilation with all new types.
- [x] 7.2 Run `./mvnw.cmd test` — verify all existing tests pass (no regressions from FactSurvivalLoader extraction).
- [x] 7.3 Manual verification: start the app, run an experiment, click "Generate Report", verify Markdown file downloads with all expected sections.
