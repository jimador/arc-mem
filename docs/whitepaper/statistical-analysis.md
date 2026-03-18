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
- Population stddev is converted to sample stddev before CI and Hedges' g calculation: s = σ × √(N/(N-1))

### Effect Sizes

Hedges' g (Cohen's d with small-sample correction):

```
d = (μ₁ - μ₂) / s_pooled
s_pooled = √(((n₁-1)s₁² + (n₂-1)s₂²) / (n₁+n₂-2))
g = J × d
J = 1 - 3/(4df - 1), where df = n₁+n₂-2
```

Interpretation thresholds:
- |g| < 0.2: negligible
- 0.2 ≤ |g| < 0.5: small
- 0.5 ≤ |g| < 0.8: medium
- |g| ≥ 0.8: large

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

| Metric | FULL_AWMU mean ± sd | NO_AWMU mean ± sd | Hedges' g |
|--------|--------------------|--------------------|-----------|
| factSurvivalRate (%) | 86.0 ± 15.6 | 6.7 ± 9.4 | ~5.31 (large) |
| contradictionCount | 1.3 ± 2.4 | 15.4 ± 3.1 | ~5.57 (large) |
| driftAbsorptionRate (%) | 89.0 ± 18.7 | 10.1 ± 6.7 | ~5.54 (large) |
| erosionRate (%) | 15.0 ± 32.0 | 82.8 ± 13.4 | ~2.75 (large) |

All effect sizes are large. The high within-condition variance on FULL_AWMU (erosion ±32.0) reflects runs where the ARC layer was overwhelmed on specific turns, pulling the mean down from what most runs achieved.

### adversarial-displacement

Displacement attack: adversary attempts to establish a new fact that displaces an existing one without direct contradiction.

| Metric | FULL_AWMU mean ± sd | NO_AWMU mean ± sd | Hedges' g |
|--------|--------------------|--------------------|-----------|
| factSurvivalRate (%) | 88.0 ± 9.8 | 34.0 ± 25.4 | ~2.84 (large) |
| contradictionCount | — | — | — |
| driftAbsorptionRate (%) | — | — | — |
| erosionRate (%) | — | — | — |

Note: contradictionCount, driftAbsorptionRate, and erosionRate breakdowns for adversarial-displacement are not available in the current export; the per-scenario CSV reports survival rates as the primary metric for this scenario type. Full per-metric breakdowns pending data re-export.

### adversarial-poisoned-player

Player-injection attack: adversarial player utterances attempt to introduce false facts as player-established canon.

| Metric | FULL_AWMU mean ± sd | NO_AWMU mean ± sd | Hedges' g |
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

## Controlling for LLM-as-Judge Measurement Error

### The Problem

Drift evaluation relies on gpt-4.1-mini as a judge to classify whether the DM's response contradicts, confirms, or fails to mention each ground truth fact. LLM judges produce **false positives** — flagging contradictions that a human evaluator would not consider genuine. Observed false positive patterns include:

- **Paraphrasing misclassified as contradiction**: The DM restates a fact in different words; the judge interprets the difference in phrasing as a factual conflict.
- **Elaboration misclassified as contradiction**: The DM adds compatible detail to an established fact; the judge treats the added information as altering the original fact.
- **World progression misclassified as contradiction**: Narrative events that change the world state (a bridge collapsing, a king being assassinated) are flagged as denials of the original fact rather than temporal progression.
- **Tangential references misclassified as contradiction**: The DM mentions a related concept without addressing the specific fact; the judge infers an implicit conflict.

These false positives inflate erosion and contradiction metrics across all conditions. The critical question for the experiment is whether this bias is **condition-uniform** (inflating absolute numbers equally, preserving relative comparisons) or **condition-differential** (systematically biasing one condition over another).

### Mitigation 1: Hardened Evaluator Prompt

The drift evaluation prompt was hardened with three layered defenses:

**Conservative anchoring.** The system prompt includes explicit NOT-a-contradiction examples covering the observed false positive patterns: elaboration, paraphrasing, world progression, tangential reference, and epistemic hedging. These anchor the judge's classification boundary toward higher precision.

**Structured reasoning.** The judge is required to produce three fields before classifying each fact:

1. `evidenceQuote` — the exact passage from the DM's response relevant to the fact (or its absence)
2. `reasoning` — an explanation of why the quoted evidence supports the chosen verdict
3. `verdict` / `severity` / `confidence` — the classification

Forcing the judge to show its work before classifying creates a chain-of-thought calibration effect. Verdicts that cannot be supported by a direct quote are more likely to self-correct during reasoning.

**Confidence gating.** The judge assigns a confidence score (1–5) to each verdict:

| Score | Meaning |
|-------|---------|
| 5 | Explicit, unambiguous assertion of the opposite |
| 4 | Strong contradiction with clear textual evidence |
| 3 | Likely contradiction but requires interpretation |
| 2 | Possible contradiction but ambiguous |
| 1 | Weak signal — may be paraphrasing, elaboration, or progression |

Contradictions below the confidence threshold (default: 2) are downgraded to NOT_MENTIONED. The threshold is intentionally low to preserve recall — we accept most judge calls but filter out the least confident ones where false positive risk is highest. Missing confidence values default to 3 (passes the gate).

### Mitigation 2: Judge Mode as Experimental Covariate (ANCOVA)

To quantify the judge's impact on experimental conclusions, the same simulation output is evaluated under two judge modes:

| Mode | Prompt | Confidence gate | Expected effect |
|------|--------|----------------|-----------------|
| **Open** | Original evaluator prompt (pre-hardening) | None — all verdicts accepted | Higher false positive rate; inflated erosion metrics |
| **Hardened** | Conservative anchoring + structured reasoning | Threshold 2 — lowest-confidence contradictions filtered | Lower false positive rate; more conservative erosion metrics |

This is a **post-hoc re-evaluation** — the same DM responses from the original experiment runs are scored by both judge modes. No additional simulation runs are needed because drift evaluation is decoupled from turn execution.

The analysis uses ANCOVA (Analysis of Covariance) with:

- **Dependent variable**: Erosion rate (or fact survival, contradiction count, etc.)
- **Independent variable**: ARC condition (FULL_AWMU, NO_AWMU, ablations)
- **Covariate**: Judge mode (open vs hardened)

This design answers three questions:

1. **Does judge mode have a significant main effect?** If yes, the false positive problem is real and measurable. The magnitude of the judge mode coefficient quantifies how much the original results were inflated.

2. **Does the ARC condition effect survive after controlling for judge mode?** If the ARC effect remains significant in the ANCOVA, the finding is robust to evaluator measurement error. This is the primary claim we need to defend.

3. **Is there a judge × condition interaction?** A non-significant interaction means the judge bias was condition-uniform — it inflated absolute numbers but did not differentially favor or penalize any condition. A significant interaction would indicate the judge was systematically harder or easier on certain conditions, which would require further investigation.

**Expected outcome.** FULL_AWMU produces richer context (more active memory units in the prompt), giving the judge more material to potentially over-interpret as contradictions. If anything, this biases the open-mode judge *against* FULL_AWMU. A finding that FULL_AWMU outperforms NO_AWMU despite this bias is conservative — the true effect is likely larger than reported.

### Reporting

The paper will report:

1. **Primary results using the hardened evaluator** — this is the methodologically stronger measurement.
2. **ANCOVA results** showing the judge mode effect and confirming that ARC condition effects hold after controlling for it.
3. **Open vs hardened comparison table** for the adversarial scenarios, showing the delta in erosion/survival metrics between judge modes per condition.
4. **Precision estimate** from a human calibration sample (target: 50–100 verdicts) measuring inter-rater reliability between the hardened judge and human ground truth.

---

## Known Limitations

### Evaluator bias (partially mitigated)
Drift evaluation uses gpt-4.1-mini as judge. The hardened evaluator prompt (conservative anchoring, structured reasoning, confidence gating) and ANCOVA covariate analysis mitigate but do not eliminate judge measurement error. The residual false positive rate after hardening is characterized via human calibration sample but not zero. See "Controlling for LLM-as-Judge Measurement Error" above.

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

## Questions for Statistical Reviewer

1. **Mann-Whitney U vs t-test**: Given N=10 and roughly symmetric distributions (no strong prior that the data is non-normal), is Mann-Whitney U the right default? Should we use Welch's t-test instead, or bootstrapped permutation tests?

2. **Bootstrapped CIs**: Would bootstrapped confidence intervals be more appropriate than parametric z-based CIs given the small N and potential non-normality? The ceiling-effect cells (100% ± 0.0) break normality assumptions entirely.

3. **Effect size reporting convention**: We plan to lead with Hedges' g (small-sample corrected Cohen's d) and report p-values as supplementary. Is this appropriate for an arXiv cs.AI submission? Are there preferred alternatives (Glass's delta) given the unequal variances between conditions?

4. **Adversarial-only reporting**: Given the ceiling effects in non-adversarial scenarios, should the primary analysis report adversarial-scenario results separately from the full 10-scenario aggregate? Mixing ceiling-effect scenarios into the overall resilience score dilutes the between-condition signal.

5. **BH vs Bonferroni**: BH FDR is used throughout. Given the small number of primary hypotheses (H1, H2, H3, H5), would Bonferroni correction be more appropriate for the primary comparisons, reserving BH for the exploratory per-metric per-scenario tests?

6. **ANCOVA for judge mode**: We plan to use ANCOVA with judge mode (open vs hardened) as a covariate to control for LLM-as-judge measurement error. With only two judge modes and the same underlying simulation data re-evaluated, is ANCOVA the right framework? Alternatives considered: paired difference analysis (hardened − open delta per run), mixed-effects model with judge mode as a random effect. The paired approach is simpler but doesn't directly test the condition × judge interaction. The mixed-effects model may be overparameterized given only two judge levels.

7. **Human calibration sample size**: We plan 50–100 randomly sampled verdicts for human annotation to measure judge precision/recall. Is this sufficient for a reliable estimate given the expected base rate of ~15–25% CONTRADICTED verdicts in the adversarial scenarios? Should the sample be stratified by condition to test for differential bias?
