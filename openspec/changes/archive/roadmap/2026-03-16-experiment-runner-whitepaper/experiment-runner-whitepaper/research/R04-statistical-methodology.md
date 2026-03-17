# Research Task: Statistical Methodology for LLM Ablation Studies

## Task ID

`R04`

## Target Features

F07 (Statistical Hardening)

## Research Question

What statistical methodology is standard for LLM ablation studies published on arXiv? Frequentist (p-values + Bonferroni) vs Bayesian vs effect-size-only?

## Channels

- web (arXiv, NeurIPS, EMNLP, ACL proceedings)

## Timebox

30 minutes

## Success Criteria

Methodology recommendation with:
1. 2+ precedent papers using the recommended approach
2. Justification for the recommendation
3. Specific tests/corrections to implement

## Context

The project targets arXiv submission. The whitepaper outline explicitly says "keep claim language conservative unless deterministic runs, confidence intervals, and stability checks are complete." The current experiment runner computes:
- Descriptive statistics: mean, stddev, min, max, median, p95
- Effect sizes: Cohen's d with low-confidence flags
- Confidence intervals: 95% CI per metric per cell

Missing:
- Inferential statistics (p-values, Bayesian credible intervals)
- Multiple-comparison correction
- Normality tests

## Candidate Approaches

### 1. Frequentist (traditional)
- **Tests**: Independent-samples t-test (parametric) or Mann-Whitney U (non-parametric)
- **Correction**: Bonferroni or Benjamini-Hochberg FDR for multiple comparisons
- **Pros**: Widely understood, expected by reviewers
- **Cons**: p-values are misunderstood; small N makes normality assumptions fragile

### 2. Bayesian
- **Tests**: Bayesian t-test (BEST), Bayes factors
- **Correction**: Built-in through posterior updating
- **Pros**: Better for small samples, directly interpretable, no multiple-comparison problem
- **Cons**: Less common in LLM evaluation papers, requires prior specification

### 3. Effect-size-only with CI
- **Tests**: Cohen's d + bootstrapped 95% CI (already partially implemented)
- **Correction**: Not applicable (no null hypothesis testing)
- **Pros**: Avoids p-value pitfalls, focuses on magnitude of effect
- **Cons**: Some reviewers expect significance testing; insufficient for strong causal claims

### 4. Hybrid (recommended starting point for investigation)
- **Tests**: Effect sizes (primary) + non-parametric tests (supplementary) + correction
- **Rationale**: Effect sizes communicate practical significance; p-values satisfy reviewer expectations; non-parametric tests handle small/non-normal samples
- **Specific implementation**: Cohen's d (primary) + Mann-Whitney U (supplementary) + Benjamini-Hochberg FDR

## Papers to Survey

1. Check how MemGPT evaluation reports statistical significance
2. Check how LoCoMo benchmark reports results
3. Check NeurIPS 2024-2025 LLM memory papers for methodology patterns
4. Check arXiv:2508.13171 (Cognitive Workspace) for their evaluation methodology

## Open Questions

1. What sample sizes are typical in LLM ablation studies? (Informs test choice)
2. Do arXiv LLM papers commonly use Bayesian methods, or is frequentist still dominant?
3. Is bootstrapped CI sufficient without formal hypothesis testing for arXiv submission?
4. Given arXiv submission goal, is the hybrid approach sufficient or should we go full Bayesian?
