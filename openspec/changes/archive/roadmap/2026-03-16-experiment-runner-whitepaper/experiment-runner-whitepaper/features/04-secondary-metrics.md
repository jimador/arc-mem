# Feature: Secondary Metrics & Failure Categorization

## Feature ID

`F04`

## Summary

Add the secondary metrics the whitepaper needs beyond the current primary set: repeated-attack erosion rate, reactivation success rate, compliance recovery rate, and failure-mode categorization by error type. These metrics populate the paper's Table 5 (secondary metrics) and enable Section 8.5 (observability findings).

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: The whitepaper's Section 7.4 defines 6 secondary metrics. ScoringService currently computes none of them. Without these, the paper can only report primary metrics and cannot make claims about erosion resistance, reactivation effectiveness, or compliance recovery.
2. Value delivered: Richer evaluation data enabling mechanism-level analysis (not just aggregate survival rates).
3. Why now: Secondary metrics inform F05 (export pipeline) — the export format should include all metrics from the start.

## Scope

### In Scope

1. Repeated-attack erosion rate: % of facts that degrade after N attacks on the same target
2. Reactivation success rate: % of dormant units successfully reactivated when context makes them relevant again
3. Compliance recovery rate: % of turns where compliance enforcement corrected a potential violation
4. Failure-mode categorization: classify each contradiction by error type (contradiction, false revision, omission, hedging, scoped exception)
5. Per-fact survival traces: turn-by-turn activation score and verdict history per ground-truth fact
6. Integration into ScoringResult and BenchmarkAggregator
7. Display in ConditionComparisonPanel

### Out of Scope

1. Observability dashboard or real-time monitoring
2. Custom metric definition framework
3. Metric alerting or threshold-based flagging

## Dependencies

1. Feature dependencies: none (can be built in parallel with F02 and F03)
2. Technical prerequisites: ScoringService, BenchmarkAggregator, EvalVerdict
3. Parent objectives: Whitepaper Table 5 and Section 8.5

## Impacted Areas

1. Packages/components: `engine/ScoringService`, `benchmark/BenchmarkAggregator`, `benchmark/BenchmarkStatistics`, `report/ResilienceReportBuilder`, `report/MarkdownReportRenderer`, `ui/panels/ConditionComparisonPanel`
2. Data/persistence: Extended ScoringResult fields stored in Neo4j run records
3. Domain-specific subsystem impacts: EvalVerdict needs failure-mode classification; dormancy tracking needs reactivation event capture

## Visibility Requirements

### UI Visibility

1. User-facing surface: ConditionComparisonPanel shows secondary metrics alongside primary metrics
2. What is shown: Erosion rate, reactivation rate, compliance recovery rate per condition
3. Success signal: New metric columns appear with non-trivial values that differ across conditions

### Observability Visibility

1. Logs/events/metrics: Each new metric logged per run with structured fields
2. Trace/audit payload: Per-fact survival trace stored in SimulationRunRecord
3. How to verify: Run with dormancy-revival scenario; reactivation success rate > 0

## Acceptance Criteria

1. ScoringResult MUST include erosionRate, reactivationSuccessRate, complianceRecoveryRate fields
2. Each contradiction MUST be classified by failure mode (contradiction, false revision, omission, hedging, scoped exception)
3. Per-fact survival traces MUST record turn-by-turn activation score and verdict for each ground-truth fact
4. BenchmarkAggregator MUST compute mean/stddev/CI for all new metrics
5. MarkdownReportRenderer MUST include secondary metrics in report output
6. ConditionComparisonPanel SHOULD display new metrics

## Risks and Mitigations

1. Risk: Failure-mode classification requires LLM judgment, adding latency and variance
2. Mitigation: Extend existing drift evaluator prompt to return failure mode alongside verdict — single LLM call, not separate

## Proposal Seed

### Suggested OpenSpec Change Slug

`secondary-metrics`

### Proposal Starter Inputs

1. Problem statement: The whitepaper defines 6 secondary metrics that the experiment runner cannot currently compute. Without them, the paper can only report aggregate survival and contradiction rates.
2. Why now: F05 (export pipeline) should include all metrics from launch. Adding metrics after export is built means reformatting.
3. Constraints: New metrics MUST be computed from existing simulation data (verdicts, unit events, activation scores) — no additional LLM calls except extending the existing evaluator.
4. Outcomes: Complete metric set populating Table 5 and enabling per-mechanism analysis.

### Suggested Capability Areas

1. Erosion tracking across repeated attacks
2. Dormancy/reactivation event capture
3. Compliance enforcement audit trail
4. Failure-mode classification

### Candidate Requirement Blocks

1. Requirement: Erosion rate MUST be computed as the fraction of facts whose activation score decreased after being targeted by ≥2 attacks
2. Scenario: A scenario with 3 attacks on the same fact; the erosion rate captures whether the fact degraded or held

## Validation Plan

1. Unit tests: ScoringService computes correct erosion/reactivation/compliance rates from mock verdict data
2. Integration: Run dormancy-revival scenario, verify reactivation success rate > 0
3. Observability: Markdown report includes new metrics section

## Known Limitations

1. Failure-mode classification accuracy depends on LLM evaluator quality
2. Reactivation rate only meaningful in scenarios with dormancy configuration
3. Compliance recovery rate only meaningful when compliance enforcement is enabled

## Suggested Command

`/opsx:new secondary-metrics`
