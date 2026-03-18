# ARC Whitepaper

*Activation-Ranked Context: Governed Working Memory for Long-Horizon LLM Conversations*

**Target**: arXiv (cs.AI / cs.CL)
**Status**: Preliminary results complete. Under associate review.

## Review Package

| Document | What's in it |
|----------|-------------|
| [experiment-findings.md](experiment-findings.md) | Results, analysis, attack pattern breakdown, revised thesis |
| [statistical-analysis.md](statistical-analysis.md) | Methods, per-scenario tables, limitations, reviewer questions |
| [sources.md](sources.md) | Full attribution — research, repos, frameworks |
| [reviewer-guide.md](reviewer-guide.md) | Cover letter for reviewers |

## Raw Data

| File | Format |
|------|--------|
| [results/ARC_Whitepaper_Full_Matrix.csv](results/ARC_Whitepaper_Full_Matrix.csv) | Flattened CSV (60 cells, one row per condition × scenario) |
| [results/ARC_Whitepaper_Full_Matrix.json](results/ARC_Whitepaper_Full_Matrix.json) | Full structured experiment report |
| [results/manifest.json](results/manifest.json) | Reproducibility metadata (git commit, config hash, timing) |

## Experiment Summary

600 runs. 6 conditions × 10 scenarios × 10 reps. gpt-4.1-nano DM, gpt-4.1-mini evaluator. ~$30 total cost. 18.6 hours wall clock.

**Core result**: ARC-enabled conditions score 94.4–96.0 overall resilience. NO_AWMU scores 76.7. The gap comes from scenarios with sustained, repeated attacks on the same facts — not single contradictions.

## Hypotheses

| # | Claim | Verdict |
|---|-------|---------|
| H1 | ARC improves fact survival vs no governance | Supported (d = 5.31 on adversarial-contradictory) |
| H2 | Trust, rank, lifecycle each contribute independently | Not supported (all within ±2 points of FULL_AWMU) |
| H3 | Hierarchical authority > flat authority | Not supported (FLAT_AUTHORITY ≈ FULL_AWMU) |
| H5 | ARC improves observability | Supported (per-fact, per-turn, per-strategy drill-down) |

## Conditions Tested

| Condition | What it disables |
|-----------|-----------------|
| FULL_AWMU | Nothing (control) |
| NO_AWMU | All injection + mutation |
| FLAT_AUTHORITY | Authority hierarchy |
| NO_RANK_DIFFERENTIATION | Rank dynamics |
| NO_TRUST | Trust pipeline |
| NO_LIFECYCLE | Decay, reinforcement, reactivation |

## Background Documents

| Document | Path |
|----------|------|
| Whitepaper outline | [`docs/drafts/whitepaper-outline.md`](../drafts/whitepaper-outline.md) |
| Related work | [`docs/related-work-and-research.md`](../related-work-and-research.md) |
| Evaluation methodology | [`docs/evaluation.md`](../evaluation.md) |
| Published blog post | [Medium](https://medium.com/@jamesdunnam/long-running-llm-conversations-need-working-memory-not-just-more-context-b929600a4e05) |
| Architecture | [`docs/architecture.md`](../architecture.md) |
| Experiment config | [`experiments/paper-matrix.yml`](../../arcmem-simulator/src/main/resources/experiments/paper-matrix.yml) |

## Running Experiments

```bash
./run-experiment.sh arcmem-simulator/src/main/resources/experiments/paper-matrix.yml
```

Results go to `experiment-output/` — JSON, CSV, Markdown report, and manifest.
