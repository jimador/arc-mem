# ARC Preliminary Results — Reviewer Guide

## What we're asking you to review

These are preliminary results from a 600-run ablation experiment testing whether "governed working memory" — a bounded, protected set of facts maintained in the LLM's active prompt — improves long-horizon conversation consistency.

We're not asking you to review prose or a finished paper. We're asking for feedback on:

1. **Are the claims supported by the data?** (see experiment-findings.md)
2. **Is the statistical methodology sound?** (see statistical-analysis.md)
3. **Are the sources properly attributed?** (see sources.md)
4. **What's missing before this is arXiv-ready?**

## The core claim

Long-running LLM conversations degrade because established facts lose force over time — not because the model forgets them, but because nothing keeps them active and weighted in the prompt. ARC (Activation-Ranked Context) maintains a bounded working set of facts that remain visible in the system prompt across turns. This dramatically improves contradiction resistance under adversarial pressure.

## What the data shows

- **ARC on vs off**: 18.5-point resilience gap (95.2 vs 76.7 on 100-point scale)
- **Adversarial scenarios**: Fact survival jumps from 7-34% (no ARC) to 86-100% (with ARC)
- **Non-adversarial scenarios**: Model naturally resists contradictions even without ARC
- **Subsystem ablations**: No individual component (trust, authority, lifecycle, rank) is independently critical — the pattern itself is what matters

## Key files

| File | What it contains |
|------|-----------------|
| `experiment-findings.md` | Narrative summary of results with tables and interpretation |
| `statistical-analysis.md` | Methodology, per-scenario breakdowns, limitations, questions for statisticians |
| `sources.md` | Full attribution for all research, repos, and frameworks |
| `results/ARC_Whitepaper_Full_Matrix.csv` | Raw data (60 rows, one per condition×scenario cell) |
| `results/ARC_Whitepaper_Full_Matrix.json` | Full structured experiment report |
| `results/manifest.json` | Reproducibility metadata (git commit, config hash, timing) |

## What we know is weak

1. **Cross-domain scenarios are too easy** — most show 100% survival regardless of condition
2. **gpt-4.1-nano is very susceptible** — a stronger model might resist contradictions without ARC
3. **Statistical significance is marginal** — large effect sizes but BH-corrected p-values mostly non-significant
4. **Compliance (H4) was never tested** — the enforcer was a no-op (PROMPT_ONLY mode); H4 is excluded from the findings
5. **Ceiling effects** — many cells at 100% provide no discriminating power

## Target venue

arXiv (cs.AI / cs.CL). Not a top-tier venue submission yet — this is a working paper establishing the pattern and evaluation methodology.
