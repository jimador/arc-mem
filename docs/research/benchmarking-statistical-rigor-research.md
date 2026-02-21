# Research: Benchmarking and Statistical Rigor for Demo Validation

## Objective

Define a proposal-ready evaluation framework that makes demo claims statistically defensible, reproducible, and comparable across scenarios and model variants.

This research track is a nice-to-have feature with high demo value.

## Why This Matters

Single-run LLM outcomes are noisy. Without repeated runs and confidence intervals, "improved drift resistance" is hard to trust.

Current known limitation in repo docs:

1. run-level analysis is non-statistical,
2. no automated batch analysis in the demo workflow.

## Parent Objective Dependencies

Implementation should follow completion of core `P0` correctness work.

Reason: benchmarking unstable/fail-open logic generates misleading statistics.

## Scope

1. Batch execution protocol.
2. Statistical analysis protocol.
3. Reproducibility contracts.
4. Demo reporting format.

## External Findings

1. LongBench provides multi-task long-context evaluation framing.
   Source: https://arxiv.org/abs/2308.14508
2. LoCoMo emphasizes long-term conversational memory evaluation requirements.
   Source: https://arxiv.org/abs/2402.17753
3. Multi-turn reliability degradation is substantial and variable.
   Source: https://arxiv.org/abs/2505.06120

## Benchmark Design

### Benchmark tiers

1. Internal scenario tier.
   Existing simulation scenarios and adversarial modes.
2. Long-horizon stress tier.
   Long-turn recall/consistency probes derived from LoCoMo-style patterns.
3. Cross-profile tier.
   BALANCED, SECURE, NARRATIVE profile comparisons.

### Required experiment structure

1. `N` repeated runs per scenario/profile/model.
2. Fixed config manifest per experiment.
3. Stored raw traces and aggregate summaries.

Suggested starting values:

1. `N=20` for demo runs,
2. `N=50` for high-confidence proposal evidence.

## Statistical Protocol

### Primary metrics

1. Drift verdict rates.
2. Conflict precision/recall (where labeled truth exists).
3. False promote / false replace rates.
4. Latency (`p50`, `p95`).

### Reporting requirements

1. Mean and median per metric.
2. 95% confidence interval (bootstrap preferred).
3. Effect size vs baseline.
4. Outlier policy and run exclusion criteria.

### Decision thresholds (proposal seed)

1. Any claim of improvement requires CI separation from baseline for primary metric.
2. No-go if false replace rate worsens beyond tolerance even when drift improves.

## Reproducibility Contract

Record per run:

1. model identifier and version,
2. temperature and generation params,
3. prompts/templates hash,
4. scenario version hash,
5. seed or deterministic mode flags where available,
6. code revision.

## Demo-Oriented Outputs

1. "stability scoreboard" across 3-5 flagship scenarios.
2. confidence interval plot per scenario.
3. side-by-side baseline vs candidate summary.

This makes results credible and easier to convert into an upstream proposal.

## Integration Plan

### Phase 1: Batch runner

1. Add headless multi-run execution command.
2. Persist run metadata and metrics.

### Phase 2: Analysis layer

1. Add aggregate stats and CI calculations.
2. Produce markdown/json summary artifacts.

### Phase 3: Demo view

1. Add concise benchmark dashboard panel.
2. Include baseline comparison mode.

## Risks

1. Cost and latency from repeated runs.
2. Metric overfitting to scenario set.
3. Misleading significance due to poor labeling for some metrics.

## Proposal-Ready Deliverables

1. Benchmark protocol spec.
2. Statistical analysis spec.
3. Reporting template for DICE proposal appendix.
4. Initial benchmark baseline report.

## Sources

1. LongBench: https://arxiv.org/abs/2308.14508
2. LoCoMo: https://arxiv.org/abs/2402.17753
3. LLMs Get Lost in Multi-Turn Conversation: https://arxiv.org/abs/2505.06120
4. Repo limitations: `docs/known-limitations.md`
