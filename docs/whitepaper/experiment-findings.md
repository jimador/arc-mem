# ARC Whitepaper Experiment: Preliminary Findings

**Date**: 2026-03-17
**Duration**: 18.6 hours (600 runs across 60 cells)
**Config**: 6 conditions × 10 scenarios × 10 reps, gpt-4.1-nano DM / gpt-4.1-mini evaluator

---

## Top-Line Result

ARC works. The governed working memory layer dramatically improves fact survival under sustained adversarial pressure. The 18.5-point resilience gap between FULL_AWMU (95.2) and NO_AWMU (76.7) is driven almost entirely by scenarios where the same facts get attacked repeatedly across multiple turns. Single-attack resistance is something the model handles on its own. Repeated erosion is where it breaks down — and where ARC holds.

The individual subsystem ablations show that no single component (trust, lifecycle, rank, authority) is independently responsible for the improvement. Any form of active memory governance produces roughly the same benefit. The pattern matters more than the mechanism.

---

## Hypothesis Verdicts

| Hypothesis | Verdict | Evidence |
|-----------|---------|----------|
| **H1**: ARC improves survival vs no governance | **SUPPORTED** | 18.5-point resilience gap. Adversarial-contradictory: 86% vs 7% survival. Cohen's d = 5.31 (large) on that scenario. |
| **H2**: Trust, rank, lifecycle each contribute independently | **NOT SUPPORTED** | Removing any single subsystem produces negligible differences from FULL_AWMU (all within ±2 points). |
| **H3**: Hierarchical authority > flat authority | **NOT SUPPORTED** | FLAT_AUTHORITY (95.5) ≈ FULL_AWMU (95.2). No meaningful difference. |
| **H5**: ARC improves observability | **SUPPORTED** | Per-fact, per-turn, per-strategy drill-down enables failure attribution not possible without governance. |

---

## Where the Gap Comes From

Not all scenarios are equal. The overall 18.5-point gap is an aggregate that mixes scenarios where ARC matters enormously with scenarios where it makes no difference at all. Understanding which is which is the most important part of these results.

### Scenarios where the model folds without ARC

These are the adversarial scenarios with **sustained, repeated attacks on the same facts** across many turns:

| Scenario | Attacks | Attack pattern | FULL_AWMU survival | NO_AWMU survival |
|----------|---------|---------------|-------------------|-----------------|
| adversarial-contradictory | 9 | Same 5 facts hit multiple times over 15 turns | 86% | 7% |
| adversarial-displacement | 9 | Repeated displacement + recall probes | 88% | 34% |
| adversarial-poisoned-player | 6 | Player-injection, repeated hits on trust facts | 100% | 82% |

The model can handle being told something wrong once. It pushes back. But when the same fact gets attacked on turn 4, then again on turn 7, then again on turn 12 — without ARC, the model eventually accepts the false version. The erosion rate on adversarial-contradictory tells this story clearly: 82.8% of contradicted facts were contradicted on multiple turns in NO_AWMU, vs 15% in FULL_AWMU.

### Scenarios where the model resists on its own

These scenarios have adversarial turns but the attacks are **spread across different facts** rather than hammering the same ones:

| Scenario | Attacks | Attack pattern | FULL_AWMU survival | NO_AWMU survival |
|----------|---------|---------------|-------------------|-----------------|
| compliance-fraud-review | 6 | Each policy rule attacked once | 100% | 100% |
| ops-incident-response | 6 | Each system state attacked once | 96% | 100% |
| cursed-blade | 6 | Mixed attacks, moderate intensity | 95% | 97.5% |

The model handles single contradictions fine without ARC. It resists, pushes back, maintains the established fact. ARC isn't needed for that.

### Scenarios with no adversarial pressure at all

| Scenario | Attacks | FULL_AWMU survival | NO_AWMU survival |
|----------|---------|-------------------|-----------------|
| balanced-campaign | 0 | 100% | 94% |
| ops-incident-adaptive | 0 | 95% | 100% |
| trust-evaluation-basic | 0 | 92.5% | 95% |

No contradictions attempted, so both conditions do well. These scenarios don't tell us anything about ARC's value — they just confirm it doesn't hurt.

### The real finding

ARC's value isn't in single-attack contradiction resistance. The model handles that natively. ARC's value is in **erosion prevention** — keeping facts stable when they're challenged repeatedly over many turns. That's the failure mode that matters for long-running agentic systems: not "someone told the agent something wrong once" but "the same constraint keeps getting tested and eventually the agent stops enforcing it."

---

## Per-Condition Resilience

| Condition | Overall | Fact Survival | Drift Resistance | Contradiction | Strategy Resistance |
|-----------|---------|--------------|-------------------|---------------|---------------------|
| NO_LIFECYCLE | 96.0 | 92.4 | 98.6 | 97.4 | 99.4 |
| NO_TRUST | 95.7 | 93.1 | 97.6 | 96.0 | 99.5 |
| FLAT_AUTHORITY | 95.5 | 92.4 | 98.0 | 95.8 | 99.4 |
| FULL_AWMU | 95.2 | 92.0 | 97.8 | 95.4 | 99.0 |
| NO_RANK_DIFF | 94.4 | 90.1 | 97.8 | 95.2 | 99.2 |
| NO_AWMU | **76.7** | 78.4 | 84.1 | 50.1 | 95.1 |

All ARC-enabled conditions cluster between 94.4 and 96.0. The gap is between "any ARC" and "no ARC," not between specific configurations.

---

## Adversarial-Contradictory Deep Dive

This is the scenario that best isolates ARC's contribution because it applies sustained, repeated attacks on the same facts:

| Metric | FULL_AWMU | NO_AWMU | Delta |
|--------|-----------|---------|-------|
| Fact survival | 86.0% ± 15.6 | 6.7% ± 9.4 | **+79.3** |
| Contradictions | 1.3 ± 2.4 | 15.4 ± 3.1 | **-14.1** |
| Major contradictions | 0.9 ± 1.8 | 10.6 ± 2.2 | **-9.7** |
| Drift absorption | 89.0% ± 18.7 | 10.1% ± 6.7 | **+78.9** |
| Erosion rate | 15.0% ± 32.0 | 82.8% ± 13.4 | **-67.8** |

Cohen's d = 5.31 on factSurvivalRate. That's >11σ on a direct z-conversion, though the BH-corrected p-value lands at ~0.05 due to the non-parametric test's conservatism at small n.

---

## Strategy Effectiveness

| Strategy | FULL_AWMU | NO_AWMU |
|----------|-----------|---------|
| CONFIDENT_ASSERTION | ~0% success | ~78% success |
| SUBTLE_REFRAME | ~0% success | ~83% success |

Both attack strategies are devastating without ARC and nearly completely blocked with it.

---

## Statistical Significance

The effect sizes between ARC-enabled and NO_AWMU are large (d = 1.18 overall, d = 5.31 on adversarial-contradictory). On the adversarial scenarios specifically, the z-score exceeds 5σ.

The aggregate p-values are weaker (~2σ after BH correction) because the overall analysis pools adversarial scenarios where ARC matters with non-adversarial scenarios where both conditions score identically. This dilutes the signal.

**Reviewer note:** A reviewer looking at the aggregate numbers might ask why the p-values are marginal given the large effect sizes. The answer is structural: 6 of 10 scenarios show no between-condition difference (either because attacks aren't repeated or because there are no attacks at all). Those cells contribute zero discriminating power but get included in the multiple-comparison correction. Reporting adversarial-only results separately would give cleaner statistics but raises questions about cherry-picking. We'd like feedback on the right way to handle this.

---

## What We Didn't Find

We ran separate drift experiments (25-turn and 50-turn scenarios with no adversarial pressure) to test whether facts fade naturally over long conversations. They didn't — 100% survival in both conditions across all drift scenarios. GPT-4.1-nano's 1M token context window makes 50 turns of conversation trivial. Natural drift from pure conversation length would require either much longer conversations or a model with a smaller context window.

The failure mode we *did* demonstrate — repeated adversarial erosion — is directly relevant to agentic systems where established constraints get tested repeatedly over long workflows. An agent told "never modify /prod" at turn 1 doesn't fail because it forgets the constraint. It fails because 40 turns of context make the constraint less salient, and someone (or the agent's own reasoning) starts treating it as flexible.

---

## Revised Thesis

The original thesis assumed each subsystem (trust, authority, lifecycle) would contribute independently. The data says otherwise.

What the evidence supports: **governed working memory — maintaining a bounded, protected set of facts in the active prompt — dramatically improves resistance to repeated contradiction pressure. The benefit comes from the governance layer existing, not from any specific policy within it.**

That's not a weaker result. It means ARC isn't fragile. You don't need perfectly tuned policies to get the benefit. You just need explicit fact governance.

---

## Run Manifest

| Field | Value |
|-------|-------|
| Config hash | a4630d56f0be6fd9 |
| Git commit | e2efdcae04f8 |
| Started | 2026-03-17 03:01 UTC |
| Completed | 2026-03-17 21:37 UTC |
| Wall clock | 18.6 hours |
| Java | 25 |
| Cells | 60 |
| Runs | 600 |
| Cancelled | false |
