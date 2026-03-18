# ARC Whitepaper: Statistical Analysis

**Date**: 2026-03-17
**For**: Preliminary statistical review
**Status**: Pre-submission — not for distribution

---

## Experiment Design

### Conditions

6 ablation conditions:

| Condition | Description |
|-----------|-------------|
| FULL_AWMU | Full ARC-Mem: trust pipeline, authority hierarchy, lifecycle decay/reinforcement, rank differentiation |
| NO_AWMU | No ARC-Mem: baseline LLM with no working memory governance |
| FLAT_AUTHORITY | ARC-Mem with all memory units at equal authority (no hierarchy) |
| NO_TRUST | ARC-Mem without trust pipeline evaluation |
| NO_LIFECYCLE | ARC-Mem without decay or reinforcement |
| NO_RANK_DIFFERENTIATION | ARC-Mem without rank-based eviction priority |

### Scenarios

10 scenarios across 3 domains:

**Narrative / D&D (adversarial focus):**
- adversarial-contradictory
- adversarial-displacement
- adversarial-poisoned-player
- cursed-blade
- balanced-campaign
- trust-evaluation-basic

**Operations:**
- ops-incident-response
- ops-incident-adaptive

**Compliance:**
- compliance-fraud-review
- compliance-policy-adaptive

### Sample Size

- 10 repetitions per condition × scenario cell
- 60 cells total (6 conditions × 10 scenarios)
- 600 simulation runs analyzed

### Models

- **DM responses**: gpt-4.1-nano
- **Drift evaluation (judge)**: gpt-4.1-mini

---

## Statistical Methods

### Descriptive Statistics

- Mean and population standard deviation (N denominator) as reported by `BenchmarkStatistics`
- 95% confidence intervals computed as: mean ± 1.96 × (sample stddev / √N), where sample stddev uses Bessel's correction (N-1 denominator)
- Population stddev is converted to sample stddev before CI and Cohen's d calculation: s = σ × √(N/(N-1))

### Effect Sizes

Cohen's d with pooled sample standard deviation:

```
d = (μ₁ - μ₂) / s_pooled
s_pooled = √(((n₁-1)s₁² + (n₂-1)s₂²) / (n₁+n₂-2))
```

Interpretation thresholds:
- |d| < 0.2: negligible
- 0.2 ≤ |d| < 0.5: small
- 0.5 ≤ |d| < 0.8: medium
- |d| ≥ 0.8: large

### Hypothesis Tests

Mann-Whitney U with normal approximation (two-tailed). U statistic converted to z-score:

```
z = (U - μ_U) / σ_U
μ_U = n₁n₂/2
σ_U = √(n₁n₂(n₁+n₂+1)/12)
p = 2 × (1 - Φ(|z|))
```

n₁ = n₂ = 10 for all comparisons.

### Multiple Comparison Correction

Benjamini-Hochberg false discovery rate (FDR) procedure. Hypotheses ranked by p-value; adjusted threshold = (rank/m) × Q where Q = 0.05.

---

## Primary Comparison: FULL_AWMU vs NO_AWMU

### adversarial-contradictory

The highest-discrimination scenario. Sustained, targeted contradiction pressure over multiple turns.

| Metric | FULL_AWMU mean ± sd | NO_AWMU mean ± sd | Cohen's d |
|--------|--------------------|--------------------|-----------|
| factSurvivalRate (%) | 86.0 ± 15.6 | 6.7 ± 9.4 | ~5.31 (large) |
| contradictionCount | 1.3 ± 2.4 | 15.4 ± 3.1 | ~5.57 (large) |
| driftAbsorptionRate (%) | 89.0 ± 18.7 | 10.1 ± 6.7 | ~5.54 (large) |
| erosionRate (%) | 15.0 ± 32.0 | 82.8 ± 13.4 | ~2.75 (large) |

All effect sizes are large. The high within-condition variance on FULL_AWMU (erosion ±32.0) reflects runs where the ARC layer was overwhelmed on specific turns, pulling the mean down from what most runs achieved.

### adversarial-displacement

Displacement attack: adversary attempts to establish a new fact that displaces an existing one without direct contradiction.

| Metric | FULL_AWMU mean ± sd | NO_AWMU mean ± sd | Cohen's d |
|--------|--------------------|--------------------|-----------|
| factSurvivalRate (%) | 88.0 ± 9.8 | 34.0 ± 25.4 | ~2.84 (large) |
| contradictionCount | — | — | — |
| driftAbsorptionRate (%) | — | — | — |
| erosionRate (%) | — | — | — |

Note: contradictionCount, driftAbsorptionRate, and erosionRate breakdowns for adversarial-displacement are not available in the current export; the per-scenario CSV reports survival rates as the primary metric for this scenario type. Full per-metric breakdowns pending data re-export.

### adversarial-poisoned-player

Player-injection attack: adversarial player utterances attempt to introduce false facts as player-established canon.

| Metric | FULL_AWMU mean ± sd | NO_AWMU mean ± sd | Cohen's d |
|--------|--------------------|--------------------|-----------|
| factSurvivalRate (%) | 100.0 ± 0.0 | 82.0 ± 6.0 | ~3.27 (large) |
| contradictionCount | — | — | — |
| driftAbsorptionRate (%) | — | — | — |
| erosionRate (%) | — | — | — |

FULL_AWMU shows a ceiling effect (100% ± 0.0) — no discriminating variance within condition. The effect size is driven entirely by NO_AWMU variance. This scenario may be insufficiently hard to stress-test full ARC.

---

## Ablation Comparison

All ARC-enabled conditions vs NO_AWMU:

| Condition | Overall Resilience | vs NO_AWMU (76.7) |
|-----------|-------------------|-------------------|
| NO_LIFECYCLE | 96.0 | +19.3 |
| NO_TRUST | 95.7 | +19.0 |
| FLAT_AUTHORITY | 95.5 | +18.8 |
| FULL_AWMU | 95.2 | +18.5 |
| NO_RANK_DIFFERENTIATION | 94.4 | +17.7 |

Within-ARC pairwise comparisons (e.g., FULL_AWMU vs NO_LIFECYCLE, FULL_AWMU vs NO_TRUST) produce negligible to small effect sizes across all metrics. No within-ARC comparison reaches significance after BH correction.

Interpretation: the gap is between ARC-present and ARC-absent conditions, not between specific ARC configurations. Any form of governed working memory produces approximately equivalent benefit.

---

## Known Limitations

### Evaluator bias
Drift evaluation uses gpt-4.1-mini as judge. LLM-as-judge introduces its own variance and potential systematic bias (e.g., the judge may be more lenient toward certain phrasings). The judge's error rate is not separately characterized.

### Model susceptibility
gpt-4.1-nano is more susceptible to adversarial contradictions than larger models. The magnitude of the ARC-on vs ARC-off effect may shrink substantially with gpt-4.1 or gpt-5 as the DM. Results should not be generalized to claims about all LLMs without replication.

### Standard deviation reporting
`BenchmarkStatistics` computes population stddev (N denominator). CIs and Cohen's d in this document use sample stddev (N-1 denominator) converted as: s = σ × √(N/(N-1)). With N=10, the correction factor is √(10/9) ≈ 1.054 — modest but non-trivial.

### Ceiling effects
Many cells show 100% fact survival across all conditions. These cells contribute no between-condition discriminating power and inflate the within-condition means without adding information. The adversarial scenarios are the only cells where between-condition differences are reliably observable.

### Normal approximation for Mann-Whitney U
With n=10 per group, the normal approximation to the Mann-Whitney U distribution is less accurate than exact computation or permutation testing. The approximation is anti-conservative — it may overstate significance. Results reported as "significant" should be treated with caution.

### Multiple comparison correction conservatism
BH FDR at Q=0.05 over many simultaneous comparisons is conservative. Some effects that are real may not reach the corrected threshold, particularly for the within-ARC subsystem comparisons where true differences are small.

### Per-metric breakdown availability
Full per-metric (contradictionCount, driftAbsorptionRate, erosionRate) breakdowns are available for adversarial-contradictory from validation runs. adversarial-displacement and adversarial-poisoned-player report primary survival metrics only in the current CSV export. Full breakdowns require data re-export from the run logs.

---

## Natural Drift Follow-Up (2026-03-18)

Three rounds of drift experiments, no adversarial pressure:

| Experiment | Turns | Compaction | Runs | Result |
|-----------|-------|-----------|------|--------|
| 4 drift scenarios (tangent, dilution, priority, epistemic) | 25 | Yes (4K threshold) | 40 | 100% survival both conditions |
| Long horizon, no compaction | 50 | Disabled | 10 | 100% survival both conditions |

Zero contradictions across all 50 drift runs. No effect sizes — both conditions performed identically.

The first round tested whether compaction drops facts (it doesn't — summarization preserves them). The second round tested whether raw conversation length causes drift at 50 turns (it doesn't — 50 turns is ~3% of nano's 1M context window).

Natural drift from pure conversation length would require either hundreds of turns, a model with a smaller context window, or both. With current-generation 1M-context models, the adversarial case is where ARC's value is observable.

---

## Questions for Statistical Reviewer

1. **Mann-Whitney U vs t-test**: Given N=10 and roughly symmetric distributions (no strong prior that the data is non-normal), is Mann-Whitney U the right default? Should we use Welch's t-test instead, or bootstrapped permutation tests?

2. **Bootstrapped CIs**: Would bootstrapped confidence intervals be more appropriate than parametric z-based CIs given the small N and potential non-normality? The ceiling-effect cells (100% ± 0.0) break normality assumptions entirely.

3. **Effect size reporting convention**: We plan to lead with Cohen's d and report p-values as supplementary. Is this appropriate for an arXiv cs.AI submission? Are there preferred alternatives (Glass's delta, Hedges' g) given the unequal variances between conditions?

4. **Adversarial-only reporting**: Given the ceiling effects in non-adversarial scenarios, should the primary analysis report adversarial-scenario results separately from the full 10-scenario aggregate? Mixing ceiling-effect scenarios into the overall resilience score dilutes the between-condition signal.

5. **BH vs Bonferroni**: BH FDR is used throughout. Given the small number of primary hypotheses (H1, H2, H3, H5), would Bonferroni correction be more appropriate for the primary comparisons, reserving BH for the exploratory per-metric per-scenario tests?
