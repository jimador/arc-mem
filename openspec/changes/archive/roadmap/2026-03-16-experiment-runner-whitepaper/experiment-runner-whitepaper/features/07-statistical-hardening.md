# Feature: Statistical Hardening & Reproducibility

## Feature ID

`F07`

## Summary

Add hypothesis testing, multiple-comparison correction, and reproducibility guarantees to the experiment runner. Currently, the runner computes descriptive statistics (mean, stddev, CI) and effect sizes (Cohen's d) but lacks inferential statistics (p-values or Bayesian equivalents) and doesn't enforce reproducibility standards (run seeding, config hashing, deterministic scenario separation).

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: The whitepaper's evidence guardrails (Section 14) say "keep claim language conservative unless deterministic runs, confidence intervals, and stability checks are complete." Effect sizes alone don't tell reviewers whether observed differences are likely due to chance. Without p-values or Bayesian credible intervals, claims are directional but not statistically grounded.
2. Value delivered: Paper-grade statistical rigor. Reviewers can evaluate whether ARC's effects are likely real vs noise.
3. Why now: This is Wave 4 (rigor layer) — runs after the full matrix is operational. Marked SHOULD because the paper explicitly allows directional findings.

## Scope

### In Scope

1. Hypothesis testing: per-metric comparison between condition pairs (parametric or non-parametric based on normality)
2. Multiple-comparison correction: Bonferroni or Benjamini-Hochberg for the number of metrics × condition pairs tested
3. Deterministic scenario tagging: scenarios marked as `deterministic: true` in YAML get separated in reporting
4. Run manifest enhancement: random seeds (if applicable), JVM version, model temperature settings
5. Stability check: flag metrics where coefficient of variation exceeds threshold across runs

### Out of Scope

1. Bayesian analysis (defer unless R01 research recommends it)
2. Power analysis (pre-hoc sample size calculation)
3. Causal inference methods (beyond ablation comparison)
4. Custom statistical test framework

## Dependencies

1. Feature dependencies: F06 (needs full matrix results to test against)
2. Technical prerequisites: EffectSizeCalculator, BenchmarkStatistics, ExperimentReport
3. Parent objectives: Whitepaper evidence guardrails, Section 7.5 (Reproducibility Requirements)

## Research Requirements

1. Open questions: What statistical methodology is standard for LLM ablation studies? Frequentist vs Bayesian? Which multiple-comparison correction?
2. Required channels: web
3. Research completion gate: Methodology recommendation with 2+ precedent papers cited

## Impacted Areas

1. Packages/components: `benchmark/EffectSizeCalculator` (add p-values), `benchmark/StatisticalTestRunner` (new), `report/MarkdownReportRenderer` (add significance markers), `benchmark/ExperimentReport` (add test results)
2. Data/persistence: Statistical test results stored alongside effect sizes in Neo4j
3. Domain-specific subsystem impacts: None — pure computation on existing metric data

## Visibility Requirements

### Observability Visibility

1. Logs/events/metrics: Statistical test results logged per condition pair per metric
2. Trace/audit payload: Full test output (test statistic, p-value, corrected p-value, effect size, CI)
3. How to verify: Markdown report includes significance markers (* p<0.05, ** p<0.01, *** p<0.001) next to effect sizes

## Acceptance Criteria

1. Each condition-pair comparison MUST include a p-value (or credible interval if Bayesian approach chosen per R04)
2. Multiple-comparison correction MUST be applied when >1 metric or >1 condition pair is tested
3. Markdown report MUST annotate significant results with standard markers
4. Export formats MUST include raw test statistics and corrected p-values
5. Deterministic scenarios MUST be flagged and reported separately from adaptive scenarios
6. Run manifest MUST include all parameters needed to reproduce results
7. High-variance metrics (CV > 0.5) SHOULD be flagged with a warning in the report

## Risks and Mitigations

1. Risk: Small sample sizes (N<10 runs) may produce unreliable p-values
2. Mitigation: Use non-parametric tests (Mann-Whitney U) when normality cannot be assumed; flag sample-size warnings
3. Risk: Multiple-comparison correction may make all results non-significant at small N
4. Mitigation: Report both corrected and uncorrected p-values; use effect sizes as primary evidence per whitepaper's conservative posture

## Proposal Seed

### Suggested OpenSpec Change Slug

`statistical-hardening`

### Proposal Starter Inputs

1. Problem statement: The experiment runner computes effect sizes but no inferential statistics. Whitepaper reviewers expect statistical significance testing or equivalent Bayesian evidence measures.
2. Why now: Wave 4 feature — runs after full matrix is operational. The paper can proceed without it (directional findings) but is stronger with it.
3. Constraints: Must work with the sample sizes the experiment runner produces (typically 5-20 runs per cell). Must handle non-normal distributions gracefully.
4. Outcomes: p-values or credible intervals for all condition-pair comparisons; multiple-comparison correction; significance annotations in reports.

### Suggested Capability Areas

1. Hypothesis testing (parametric + non-parametric)
2. Multiple-comparison correction
3. Reproducibility manifest
4. Stability diagnostics

### Candidate Requirement Blocks

1. Requirement: FULL_ARC vs NO_ACTIVE_MEMORY comparison on factSurvivalRate MUST produce a p-value indicating whether the observed difference is statistically significant
2. Scenario: Experiment with 10 reps per condition; p-value < 0.05 indicates ARC significantly improves survival

## Validation Plan

1. Unit tests: Statistical test functions produce correct results on known distributions
2. Integration: Run small matrix, verify p-values are computed and included in report
3. Sanity check: FULL_ARC vs FULL_ARC (same condition) should produce p ≈ 1.0 (no significant difference)

## Known Limitations

1. Small sample sizes limit statistical power — some real effects may not reach significance
2. LLM-as-judge variance inflates metric variance, potentially masking true effects
3. Multiple-comparison correction is conservative — may need larger N to detect medium effects

## Suggested Command

`/opsx:new statistical-hardening`
