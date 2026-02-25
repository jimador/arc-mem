# SYSTEM MAP

## 1) Runtime Topology

Execution path:
`ScenarioLoader` -> `SimulationService` -> `SimulationTurnExecutor` -> (`ChatModelHolder`, `SimulationExtractionService`, `AnchorPromoter`, `ScoringService`) -> `RunHistoryStore`

Benchmark/report path:
`ExperimentRunner` -> `BenchmarkRunner` -> `ExperimentReport` -> `ResilienceReportBuilder` -> (`ResilienceScoreCalculator`, `FactSurvivalLoader`, `ContradictionDetailLoader`) -> `MarkdownReportRenderer`

Supporting subsystems:
- Anchor lifecycle: `AnchorEngine`
- Persistence: `AnchorRepository` + `PropositionNode` (Neo4j/Drivine)
- Prompt assembly: `AnchorsLlmReference`, `PropositionsLlmReference`, templates in `src/main/resources/prompts/`

## 2) Module Responsibilities

- `anchor/`: lifecycle, budget enforcement, authority transitions, conflict resolution, trust re-evaluation, lifecycle events.
- `extract/`: promotion gate sequence (confidence -> dedup -> conflict -> trust -> promote).
- `assembly/`: anchor selection/injection, formatting, token budgeting.
- `sim/engine/`: turn orchestration, adversarial message generation, drift evaluation, run scoring, run persistence.
- `sim/benchmark/`: condition/scenario matrix orchestration, aggregate stats, CIs/effect sizes.
- `sim/report/`: resilience score composition, per-fact survival tables, contradiction drill-down tables, markdown rendering.

## 3) Core Anchor Logic Locations

- Extraction from DM output: `SimulationExtractionService.extract(...)`
- Promotion path: `AnchorPromoter.evaluateAndPromote(...)`, `batchEvaluateAndPromote(...)`
- Ranking/budget: `AnchorEngine` + `ReinforcementPolicy` + `DecayPolicy`; eviction via repository calls
- Authority tiers and transitions: `Authority`, `AnchorEngine`, `AuthorityConflictResolver`
- Trust gates: `TrustPipeline`, `TrustEvaluator`, `TrustSignal`

## 4) Conflict Representation and Resolution

- Conflict object: `ConflictDetector.Conflict(existingAnchor, incomingText, confidence, reason)`
- Detectors: `LlmConflictDetector`, `NegationConflictDetector`, `CompositeConflictDetector`
- Resolution outcomes: `KEEP_EXISTING`, `REPLACE`, `DEMOTE_EXISTING`, `COEXIST`
- Application point: `AnchorPromoter.resolveConflicts(...)`

## 5) Context Injection Strategy

- Format: `AnchorsLlmReference.getContent()` renders authority-grouped anchor text from `prompts/anchors-reference.jinja`
- Placement: `SimulationTurnExecutor.buildSystemPrompt(...)` injects anchors into system prompt under established-facts block
- Effect: injected anchors are in system-level instructions, ahead of generated response turn

## 6) Experiment Configuration and Execution Flow

- Scenario source: `src/main/resources/simulations/*.yml` -> `SimulationScenario`
- Per run flow:
1. `SimulationService` allocates `contextId = sim-{uuid}` and seeds scenario anchors.
2. Turn loop executes through `SimulationTurnExecutor`.
3. Evaluated turns produce verdicts (`CONTRADICTED`/`CONFIRMED`/`NOT_MENTIONED`).
4. `ScoringService` computes run metrics and persists `SimulationRunRecord`.
5. Context cleanup runs in `finally`.
- Matrix flow:
1. `BenchmarkRunner` executes repetitions for one `(condition, scenario)` cell.
2. `ExperimentRunner` iterates the full condition x scenario matrix.
3. `ResilienceReportBuilder` materializes markdown report artifacts.

## 7) Adversarial Scenario Generation Paths

- Scripted: scenario `turns[]` with explicit `type`/`strategy`/`targetFact`
- Adaptive: `adversaryMode: adaptive` with `TieredEscalationStrategy` + `AdaptiveAttackPrompter`
- Unscripted fallback: `SimulationService.generateAdversarialMessage(...)` samples fact and strategy, then asks the model to generate attack text

## 8) Important Reporting Semantics

- Run-level `factSurvivalRate` in `ScoringService` is based on "confirmed and never contradicted" facts.
- Per-fact survival in report tables (`FactSurvivalLoader`) uses the same survival criterion, but is presented as per-fact counts across runs.
- Composite resilience uses weighted components in `ResilienceScoreCalculator`; contradiction component is a transformed mean contradiction count, not a direct probability.
