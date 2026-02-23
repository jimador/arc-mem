## Why

The existing `sim.benchmark` package (BenchmarkRunner, BenchmarkAggregator, BenchmarkStatistics, BenchmarkReport) supports multi-run execution and descriptive statistics for a single configuration. Evaluating the marginal contribution of individual anchor subsystems — authority hierarchy, rank differentiation, budget enforcement — requires controlled ablation experiments that the current infrastructure cannot express or execute. The `injectionStateSupplier` toggle in SimulationService is the only ablation mechanism, and it is a UI checkbox, not a declarative, reproducible experimental condition. Without a proper experiment framework, the tech report (F03) cannot make credible claims about anchor effectiveness.

## What Changes

- Add a **declarative ablation condition model** (enum or sealed interface) that configures anchor injection, authority seeding, rank seeding, and rank mutation behavior per simulation run. At least four conditions: `FULL_ANCHORS`, `NO_ANCHORS`, `FLAT_AUTHORITY`, `NO_RANK_DIFFERENTIATION`.
- Add an **experiment definition** record specifying condition set, scenario set, repetitions per cell, and optional evaluator model override.
- Add an **experiment execution engine** that runs the full experiment matrix (conditions × scenarios × repetitions) with cell-level progress reporting and experiment-level cancellation.
- Add **cross-condition statistical comparison**: Cohen's d effect sizes between condition pairs, 95% confidence intervals per metric per condition, and per-strategy effectiveness deltas across conditions.
- Add **experiment persistence**: save/load ExperimentReport via RunHistoryStore using the existing Neo4j JSON serialization pattern.
- Add **OTEL spans** for experiment and cell execution.

## Capabilities

### New Capabilities

- `ablation-conditions`: Declarative condition definitions that configure anchor subsystem behavior per-run. Each condition specifies injection state, authority override, rank override, and rank mutation policy. Applied at the seed-anchor level before the simulation loop, not by mutating live anchor state.
- `experiment-execution`: Matrix execution engine orchestrating conditions × scenarios × repetitions. Extends BenchmarkRunner with cell-level orchestration, experiment-level cancellation, and structured progress reporting suitable for UI consumption.
- `cross-condition-statistics`: Cohen's d effect sizes between condition pairs, 95% confidence intervals, and per-strategy effectiveness comparison across conditions. Produces an ExperimentReport with the full comparison matrix.
- `experiment-persistence`: Save, load, list, and delete ExperimentReport via RunHistoryStore. Follows the existing BenchmarkReport Neo4j JSON node pattern.

### Modified Capabilities

- `benchmark-runner`: BenchmarkRunner gains the ability to accept an AblationCondition that is applied before each run begins. The existing no-condition behavior MUST be preserved as the default (backward-compatible).
- `run-history`: RunHistoryStore adds `saveExperimentReport`, `loadExperimentReport`, and `listExperimentReports` methods alongside the existing benchmark report methods.

## Impact

- **Packages**: New types in `sim.benchmark` — AblationCondition, ExperimentDefinition, ExperimentRunner, ExperimentReport, EffectSizeCalculator. Modifications to BenchmarkRunner (condition parameter).
- **Persistence**: New `ExperimentReport` Neo4j JSON node. RunHistoryStore interface extended with 3 methods; Neo4jRunHistoryStore implementation.
- **Observability**: OTEL spans `experiment.run` and `experiment.cell` with structured attributes.
- **Dependencies**: No new external dependencies. Cohen's d and confidence intervals computed with standard library math (same approach as BenchmarkAggregator).
- **Testing**: New unit tests for EffectSizeCalculator, AblationCondition application, ExperimentRunner orchestration, and ExperimentReport serialization.
- **UI**: No UI changes in this feature — experiment results are exposed via callbacks and persisted reports for F02 to consume.

## Constitutional Alignment

- **Article II (Neo4j-only)**: ExperimentReport persistence uses Neo4j JSON nodes via Drivine, consistent with BenchmarkReport.
- **Article III (Constructor injection)**: All new services use constructor injection.
- **Article IV (Records)**: ExperimentDefinition, ExperimentReport, AblationCondition use records.
- **Article V (Anchor invariants)**: Conditions do NOT modify the anchor engine. FLAT_AUTHORITY and NO_RANK override seed-anchor configuration, not live anchor state. Budget enforcement (A1), rank clamping (A2), explicit promotion (A3), and upgrade-only authority (A4) remain intact.
- **Article VI (Sim isolation)**: Each run in the experiment matrix gets its own `sim-{uuid}` contextId.
