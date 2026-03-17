# Feature: Automated Experiment Matrix Runner

## Feature ID

`F06`

## Summary

Create a config-driven full-matrix experiment runner that executes all conditions × all scenario packs × N repetitions in a single automated run, produces a complete data package (JSON + CSV + Markdown + traces), and generates a reproducibility manifest documenting exact configuration, versions, and timestamps.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: Running the full whitepaper evaluation matrix currently requires manual configuration in the UI — selecting conditions, scenarios, setting repetition count, then manually exporting. For a paper with 7 conditions × ~30 scenarios × 10+ reps, this is prohibitive.
2. Value delivered: One command produces all whitepaper data. Reproducible, auditable, automatable.
3. Why now: This is the capstone feature that ties F02 (conditions), F03 (scenarios), and F05 (export) into a single automated workflow.

## Scope

### In Scope

1. YAML/JSON experiment definition file: conditions, scenario packs (or "all"), repetition count, model, export formats
2. CLI or Spring Boot runner entry point that executes the full matrix without UI interaction
3. Automatic export of all formats (JSON, CSV, Markdown, traces) on completion
4. Run manifest: config hash, git commit, timestamp, model version, condition list, scenario list, rep count
5. Progress logging suitable for unattended execution (% complete, ETA, errors)
6. Graceful handling of individual cell failures (continue matrix, flag failures)

### Out of Scope

1. Distributed execution across multiple machines
2. Cloud-based experiment scheduling
3. Experiment comparison UI (existing ConditionComparisonPanel is sufficient)
4. Automatic whitepaper prose generation from results

## Dependencies

1. Feature dependencies: F02 (all conditions), F03 (cross-domain scenarios), F05 (export pipeline)
2. Technical prerequisites: ExperimentRunner, BenchmarkRunner, export pipeline
3. Parent objectives: Automated data gathering for whitepaper Sections 8-9

## Impacted Areas

1. Packages/components: New `benchmark/ExperimentMatrixRunner` or extension of `ExperimentRunner`; new `benchmark/ExperimentDefinition` config record; CLI entry point
2. Data/persistence: All results persisted to Neo4j; exports written to configurable output directory
3. Domain-specific subsystem impacts: None — orchestration only

## Visibility Requirements

### UI Visibility

1. User-facing surface: Optional — existing BenchmarkView can trigger matrix runs. CLI is primary interface.
2. What is shown: Progress log, cell completion count, estimated time remaining
3. Success signal: All cells complete, all exports written, manifest generated

### Observability Visibility

1. Logs/events/metrics: Per-cell progress, timing, error counts logged
2. Trace/audit payload: Run manifest with full reproducibility metadata
3. How to verify: Re-run with same config and manifest → results are statistically consistent (within CI bounds)

## Acceptance Criteria

1. A YAML experiment definition MUST specify conditions, scenarios, reps, model, and output directory
2. The runner MUST execute all cells (condition × scenario × reps) without manual intervention
3. On completion, the runner MUST export JSON, CSV, Markdown, and trace files to the output directory
4. A run manifest MUST be generated with: config hash, git commit SHA, timestamp, model ID, total cells, total runs, wall-clock time
5. Individual cell failures MUST NOT abort the entire matrix — failed cells are logged and flagged in the report
6. The runner SHOULD support a `--dry-run` mode that validates the config without executing experiments
7. Repetition count MUST be ≥ 5 for paper-quality data (configurable, with warning if < 5)

## Risks and Mitigations

1. Risk: Full matrix is very expensive (7 conditions × 30 scenarios × 10 reps = 2,100 simulation runs, each with multiple LLM calls)
2. Mitigation: Support subset selection (specific conditions/scenarios), parallelism tuning, cost estimation in dry-run mode
3. Risk: Long-running execution may fail partway through
4. Mitigation: Checkpoint after each cell; resume from last completed cell

## Proposal Seed

### Suggested OpenSpec Change Slug

`automated-experiment-matrix`

### Proposal Starter Inputs

1. Problem statement: Producing whitepaper data requires executing hundreds to thousands of simulation runs across multiple conditions and scenarios. Manual UI-driven execution is not feasible at this scale.
2. Why now: This is the capstone feature — it connects all infrastructure (conditions, scenarios, metrics, export) into a single automated workflow.
3. Constraints: Must use existing ExperimentRunner/BenchmarkRunner infrastructure. Must support graceful failure and resumption.
4. Outcomes: One-command execution producing a complete, reproducible data package for the whitepaper.

### Suggested Capability Areas

1. Config-driven experiment definition
2. Full-matrix orchestration
3. Multi-format export automation
4. Reproducibility manifest generation
5. Checkpoint and resume

### Candidate Requirement Blocks

1. Requirement: Running `./run-experiment.sh paper-matrix.yml` MUST produce a complete data package in the output directory
2. Scenario: Researcher runs the matrix overnight, returns to find all exports ready for analysis

## Validation Plan

1. Unit tests: ExperimentDefinition parsing, manifest generation
2. Integration: Run a small matrix (2 conditions × 2 scenarios × 2 reps) end-to-end
3. Reproducibility: Two runs with same config produce statistically consistent results (within CI bounds)

## Known Limitations

1. No distributed execution — single machine only
2. Cost estimation is approximate (depends on LLM response variability)
3. Resume from checkpoint requires Neo4j persistence to be intact

## Suggested Command

`/opsx:new automated-experiment-matrix`
