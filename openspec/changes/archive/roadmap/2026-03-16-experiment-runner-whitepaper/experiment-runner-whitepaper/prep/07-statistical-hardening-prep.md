# Prep: Statistical Hardening & Reproducibility

## Feature

F07 — statistical-hardening

## Key Decisions

1. **Primary approach**: Hybrid — effect sizes (primary evidence) + non-parametric tests (supplementary) + multiple-comparison correction.
2. **Test choice**: Mann-Whitney U (non-parametric) as default. Small N + unknown distributions make parametric assumptions risky.
3. **Correction**: Benjamini-Hochberg FDR (less conservative than Bonferroni, more power at small N).
4. **Significance markers**: Standard notation in Markdown reports (* p<0.05, ** p<0.01, *** p<0.001) after correction.
5. **Deterministic tagging**: Scenarios marked `deterministic: true` in YAML reported separately from adaptive scenarios.

## Open Questions

1. R04 research outcome may change the test choice — defer final decision until R04 is complete.
2. Should we implement bootstrapped CIs (more robust) or stick with parametric 95% CI (already implemented)?
3. Is the project going to target arXiv only or also a venue like NeurIPS/EMNLP workshops? Venue affects methodology expectations.
4. Given arXiv submission target, should we include Bayesian analysis as well, or is frequentist + effect sizes sufficient?

## Acceptance Gate

- p-values computed for all condition-pair comparisons
- Multiple-comparison correction applied
- Markdown report includes significance annotations
- Deterministic scenarios reported separately
- Run manifest includes full reproducibility metadata

## Research Dependencies

R04 (Statistical methodology) — MUST be completed before implementation decisions are finalized

## Handoff Notes

This is the lowest-priority feature (Wave 4, SHOULD). The paper can proceed with directional findings (effect sizes + CI) if this doesn't make the cut. But for arXiv submission, having at least Mann-Whitney U + BH correction strengthens the evidence substantially.

Given the arXiv target, consider upgrading priority from SHOULD to MUST if time permits after Wave 3 completion.
