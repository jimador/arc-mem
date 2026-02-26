# Feature: Experiment Framework

## Feature ID

`F01`

## Summary

Build the controlled ablation experiment infrastructure that runs multiple anchor conditions against the existing simulation scenario corpus, computes cross-condition statistical comparisons, and persists experiment-level reports. This is the **critical path** feature: without declarative condition definitions, paired execution, and effect-size computation, no credible empirical evaluation of anchor drift resistance is possible.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: The existing `sim.benchmark` package (BenchmarkRunner, BenchmarkAggregator, BenchmarkStatistics, BenchmarkReport) supports multi-run execution and descriptive statistics for a *single* configuration. It cannot compare conditions (e.g., anchors-on vs. anchors-off), compute effect sizes between conditions, or run a full experiment matrix. The `injectionStateSupplier` toggle in SimulationService is the only ablation mechanism, and it is a UI checkbox -- not a declarative, reproducible experimental condition.
2. **Value delivered**: Enables rigorous, reproducible ablation studies that quantify the marginal contribution of each anchor subsystem (authority hierarchy, rank differentiation, budget enforcement) to drift resistance. This is the empirical foundation for the tech report (F03).
3. **Why now**: This is Wave 1. The benchmarking infrastructure from the `benchmarking-and-statistical-rigor` change is complete. The experiment framework extends it with condition definitions, matrix execution, and cross-condition statistics. F02 (UI) and F03 (paper) depend on this feature.

## Scope

### In Scope

1. **Ablation condition model**: Declarative definitions for at least four experimental conditions that configure anchor subsystem behavior per-run.
2. **Experiment definition**: A structured record specifying condition set, scenario set, repetitions per cell, and optional evaluator model override.
3. **Experiment execution engine**: Matrix execution (conditions x scenarios x repetitions) with progress reporting and experiment-level cancellation.
4. **Cross-condition statistical comparison**: Cohen's d effect sizes, 95% confidence intervals, and per-strategy effectiveness deltas between condition pairs.
5. **Experiment persistence**: Save and load ExperimentReport via RunHistoryStore using the existing Neo4j JSON serialization pattern.

### Out of Scope

1. UI for experiment configuration and result display (deferred to F02).
2. Formal hypothesis testing (t-tests, ANOVA) -- descriptive statistics and effect sizes are sufficient for this wave.
3. Modifications to the core anchor engine (`anchor/` package) -- conditions configure existing engine behavior, not extend it.
4. New scenario authoring -- the experiment runs against existing YAML scenarios.
5. Cross-model evaluation infrastructure (running the same experiment across multiple LLM backends).

## Dependencies

1. **benchmarking-and-statistical-rigor** (completed): Provides BenchmarkRunner, BenchmarkAggregator, BenchmarkStatistics, BenchmarkReport, BenchmarkProgress.
2. **sim.engine.SimulationService**: The `injectionStateSupplier` and scenario execution loop. Conditions MUST be applicable without modifying SimulationService's public API beyond adding a condition parameter or using the existing supplier mechanism.
3. **sim.engine.RunHistoryStore**: The persistence SPI. ExperimentReport persistence MUST follow the same pattern as BenchmarkReport (Neo4j JSON node).
4. **Priority**: MUST (critical path).
5. **OpenSpec change slug**: `experiment-framework`.

## Research Requirements (Optional)

| Task ID | Question | Relevance | Status |
|---------|----------|-----------|--------|
| R01 | How reliable is LLM-as-judge drift evaluation, and what calibration methodology ensures credible results? | Experiment credibility depends on evaluator reliability. Results from this framework are only as trustworthy as the evaluation verdicts. | Pending |

## Impacted Areas

1. **`sim.benchmark` package (primary)**: New types -- AblationCondition (enum or sealed interface), ExperimentDefinition (record), ExperimentRunner (service), ExperimentReport (record), EffectSizeCalculator (utility).
2. **`sim.engine` package (minor)**: SimulationService MAY need a condition-application hook to override anchor authority/rank seeding per condition. The existing `injectionStateSupplier` handles the NO_ANCHORS condition; other conditions require per-run configuration of anchor seeding behavior.
3. **`sim.engine.RunHistoryStore`**: Two new methods -- `saveExperimentReport(ExperimentReport)` and `loadExperimentReport(String)` and `listExperimentReports()`.
4. **Persistence (Neo4j)**: New node type for ExperimentReport JSON storage, following the existing BenchmarkReport pattern.

## Visibility Requirements

### UI Visibility

1. The experiment framework MUST expose progress callbacks suitable for UI consumption (F02 will wire these).
2. Progress granularity MUST include: current cell (condition x scenario), current run within cell, and overall experiment progress.
3. Progress reporting format: "Cell {n}/{total}: {conditionName} x {scenarioId}, Run {r}/{reps}".

### Observability Visibility

1. Experiment execution MUST emit OpenTelemetry spans (`experiment.run`) with attributes: `experiment.name`, `experiment.condition_count`, `experiment.scenario_count`, `experiment.total_cells`, `experiment.repetitions`.
2. Per-cell execution MUST emit child spans (`experiment.cell`) with attributes: `cell.condition`, `cell.scenario_id`, `cell.run_index`.
3. Effect size computation SHOULD emit a span (`experiment.effect_size`) recording the comparison pair and resulting Cohen's d.
4. Experiment completion MUST log a structured summary: total duration, cells completed, cells cancelled, and mean effect size across primary metric (factSurvivalRate).

## Acceptance Criteria

1. The framework MUST support at least four ablation conditions:
   - `FULL_ANCHORS` -- injection ON, full authority hierarchy (PROVISIONAL through CANON), rank differentiation, budget enforcement.
   - `NO_ANCHORS` -- injection OFF (formalizes the existing UI toggle).
   - `FLAT_AUTHORITY` -- injection ON, all anchors seeded at RELIABLE authority, no promotion occurs during the run.
   - `NO_RANK_DIFFERENTIATION` -- injection ON, all anchors seeded at rank 500, no rank changes during the run.
2. Conditions MUST be declarative (record, enum, or sealed interface with configuration fields), not ad-hoc boolean toggles.
3. The experiment matrix MUST be `conditions.size() x scenarios.size() x repetitionsPerCell` total simulation runs.
4. The framework MUST compute Cohen's d effect sizes between every pair of conditions for each metric (factSurvivalRate, driftAbsorptionRate, contradictionCount, majorContradictionCount, meanTurnsToFirstDrift, anchorAttributionCount).
5. The framework MUST compute 95% confidence intervals for each metric in each condition cell.
6. The framework MUST compute per-strategy effectiveness comparison across conditions, enabling statements like "SUBTLE_REFRAME succeeded 60% against NO_ANCHORS but only 15% against FULL_ANCHORS."
7. The framework MUST support cancellation at the experiment level; cancellation MUST complete the current cell (all remaining runs in that cell) before stopping.
8. The framework MUST persist ExperimentReport to Neo4j via RunHistoryStore.
9. All runs within a single cell MUST use the same scenario, condition configuration, and evaluator model (reproducibility).
10. The framework SHOULD support a configurable evaluator model (model used for drift evaluation), defaulting to the scenario's model field. This enables evaluator independence experiments where a different model judges drift than the one generating responses.
11. ExperimentReport MUST include: experiment name, per-cell BenchmarkReports, cross-condition effect size matrix, per-strategy cross-condition deltas, total duration, and metadata (conditions used, scenario IDs, repetition count).
12. The framework MUST NOT modify the core anchor engine (`anchor/` package). Condition application MUST work through configuration of the simulation service layer.

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **LLM API cost for full matrix** | High | Medium | A 4-condition x 5-scenario x 5-rep matrix = 100 runs at ~20 turns each = ~2000 LLM calls. Start with 2 scenarios x 3 reps for calibration runs. ExperimentDefinition SHOULD support subset execution. |
| **High variance washes out effect sizes** | Medium | High | If initial results show CV > 0.5 on key metrics, increase N beyond 5. BenchmarkStatistics already flags high-variance results via `isHighVariance()`. The framework SHOULD warn when effect sizes are computed from high-variance cells. |
| **Condition application requires SimulationService changes** | Medium | Medium | Prefer a condition-applicator strategy pattern that configures the existing `injectionStateSupplier` and seed-anchor behavior rather than modifying SimulationService internals. If SimulationService changes are unavoidable, they MUST be backward-compatible. |
| **FLAT_AUTHORITY and NO_RANK conditions alter seed anchors** | Low | Low | Conditions that override authority or rank MUST do so at the seed-anchor level before the simulation loop starts, not by mutating live anchor state mid-run. This preserves the existing anchor lifecycle invariants. |
| **Experiment duration** | Medium | Low | A 100-run experiment at ~60s/run = ~100 minutes. Progress reporting and cancellation are essential. The framework SHOULD estimate total duration before starting and display it. |

## Proposal Seed

### Change Slug

`experiment-framework`

### Proposal Starter Inputs

1. **Problem statement**: The existing benchmark infrastructure supports single-condition multi-run execution. Evaluating the marginal contribution of individual anchor subsystems (authority hierarchy, rank differentiation) requires controlled ablation experiments that the current infrastructure cannot express or execute.
2. **Why now**: The benchmarking foundation (BenchmarkRunner, BenchmarkAggregator) is complete. The tech report (F03) requires ablation results. The experiment framework is the prerequisite for both the benchmarking UI (F02) and the paper.
3. **Constraints/non-goals**: No changes to the core anchor engine. No formal hypothesis testing (descriptive stats and effect sizes only). No UI (deferred to F02). No new scenarios (runs against existing corpus).
4. **Visible outcomes**: Operators MUST be able to run a named experiment, see cell-by-cell progress, cancel mid-run, and retrieve persisted experiment reports with cross-condition effect sizes.

### Suggested Capability Areas

1. **Ablation condition model and application**: Enum/sealed-interface conditions with per-condition configuration of injection, authority, rank, and budget behavior.
2. **Experiment matrix execution**: Extension of BenchmarkRunner to orchestrate condition x scenario x rep cells with progress and cancellation.
3. **Cross-condition statistical comparison**: Cohen's d, confidence intervals, and strategy-level deltas.
4. **Experiment persistence**: ExperimentReport save/load via RunHistoryStore.

### Candidate Requirement Blocks

1. **REQ-COND**: The system SHALL define ablation conditions as declarative configuration objects that control anchor injection, authority seeding, rank seeding, and rank mutation behavior per simulation run.
2. **REQ-MATRIX**: The system SHALL execute a full experiment matrix (conditions x scenarios x repetitions) with per-cell aggregation and experiment-level reporting.
3. **REQ-EFFECT**: The system SHALL compute Cohen's d effect sizes between all condition pairs for each scoring metric.
4. **REQ-PERSIST**: The system SHALL persist ExperimentReport to Neo4j and support retrieval by experiment name or report ID.
5. **REQ-CANCEL**: The system SHALL support experiment-level cancellation that completes the current cell before stopping.

## Validation Plan

1. **Unit tests** MUST verify Cohen's d computation against known inputs (e.g., two distributions with known means and standard deviations).
2. **Unit tests** MUST verify that each AblationCondition correctly configures the simulation service layer (injection off, flat authority, flat rank).
3. **Unit tests** MUST verify that ExperimentReport serialization/deserialization preserves all fields.
4. **Integration test** SHOULD verify a minimal experiment (2 conditions x 1 scenario x 2 reps) executes end-to-end and produces a valid ExperimentReport with effect sizes.
5. **Observability validation** MUST confirm that experiment spans appear in OTEL traces with correct attributes.
6. **Regression**: Existing BenchmarkRunner behavior MUST NOT change. The experiment framework extends, not replaces, the benchmark infrastructure.

## Known Limitations

1. **No formal hypothesis testing**: Cohen's d and confidence intervals provide practical significance measures. Formal p-values (t-test, ANOVA) require larger sample sizes and are deferred.
2. **Population standard deviation**: BenchmarkStatistics uses population stddev (N, not N-1). Cohen's d computation SHOULD use pooled sample stddev (N-1) for correctness. This divergence MUST be documented.
3. **Sequential execution**: BenchmarkRunner invariant requires sequential runs to avoid Neo4j context collisions. Experiment execution inherits this constraint. Parallelism across conditions is a future optimization.
4. **Single evaluator model per experiment**: The evaluator model override applies to the entire experiment, not per-condition. Per-condition evaluator variation is a candidate extension.
5. **Condition interaction effects**: The four conditions test individual subsystems. Interaction effects (e.g., authority + rank together vs. separately) are not captured by pairwise Cohen's d. Factorial designs are a future extension.

## Suggested Command

```
/opsx:ff experiment-framework
```
