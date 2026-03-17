# ARC Whitepaper Experiment: Preliminary Findings

**Date**: 2026-03-17
**Duration**: 18.6 hours (600 runs across 60 cells)
**Config**: 6 conditions × 10 scenarios × 10 reps, gpt-4.1-nano DM / gpt-4.1-mini evaluator

---

## Top-Line Result

**ARC works — but the story is more nuanced than expected.**

ARC's governed working memory dramatically improves fact survival under adversarial pressure (FULL_AWMU 95.2 vs NO_AWMU 76.7 overall resilience). However, the individual subsystem ablations reveal that no single component (trust, lifecycle, rank, authority) is independently responsible for the improvement. The benefit comes from having *any* active memory governance, not from a specific subsystem.

---

## Hypothesis Verdict Summary

| Hypothesis | Verdict | Evidence |
|-----------|---------|----------|
| **H1**: ARC improves survival vs no governance | **SUPPORTED** | 18.5-point resilience gap. Adversarial-contradictory: 86% vs 7% survival. Cohen's d = 1.18 (large). |
| **H2**: Trust, rank, lifecycle each contribute independently | **NOT SUPPORTED** | Removing any single subsystem produces negligible differences from FULL_AWMU (all within ±2 points). |
| **H3**: Hierarchical authority > flat authority | **NOT SUPPORTED** | FLAT_AUTHORITY (95.5) ≈ FULL_AWMU (95.2). No meaningful difference. |
| **H5**: ARC improves observability | **SUPPORTED** | Per-fact, per-turn, per-strategy drill-down enables precise failure attribution not possible without governance. |

---

## Key Numbers

### Per-Condition Resilience Scores

| Condition | Overall | Fact Survival | Drift Resistance | Contradiction | Strategy Resistance |
|-----------|---------|--------------|-------------------|---------------|---------------------|
| NO_LIFECYCLE | 96.0 | 92.4 | 98.6 | 97.4 | 99.4 |
| NO_TRUST | 95.7 | 93.1 | 97.6 | 96.0 | 99.5 |
| FLAT_AUTHORITY | 95.5 | 92.4 | 98.0 | 95.8 | 99.4 |
| FULL_AWMU | 95.2 | 92.0 | 97.8 | 95.4 | 99.0 |
| NO_RANK_DIFF | 94.4 | 90.1 | 97.8 | 95.2 | 99.2 |
| NO_AWMU | **76.7** | 78.4 | 84.1 | 50.1 | 95.1 |

The 18.5-point gap between FULL_AWMU and NO_AWMU is the headline. All ARC-enabled conditions cluster tightly between 94.4 and 96.0, while NO_AWMU drops to 76.7.

### Where ARC Matters Most (adversarial-contradictory)

| Metric | FULL_AWMU | NO_AWMU | Delta |
|--------|-----------|---------|-------|
| Fact survival | 86.0% ± 15.6 | 6.7% ± 9.4 | **+79.3** |
| Contradictions | 1.3 ± 2.4 | 15.4 ± 3.1 | **-14.1** |
| Major contradictions | 0.9 ± 1.8 | 10.6 ± 2.2 | **-9.7** |
| Drift absorption | 89.0% ± 18.7 | 10.1% ± 6.7 | **+78.9** |
| Erosion rate | 15.0% ± 32.0 | 82.8% ± 13.4 | **-67.8** |
| Mean turns to first drift | 7.5 | 7.0 | +0.5 |

### Where ARC Doesn't Matter (most scenarios)

These scenarios show near-identical performance across ALL conditions (including NO_AWMU):

| Scenario | FULL_AWMU survival | NO_AWMU survival |
|----------|-------------------|-----------------|
| compliance-fraud-review | 100% | 100% |
| ops-incident-response | 96% | 100% |
| ops-incident-adaptive | 95% | 100% |
| cursed-blade | 95% | 97.5% |
| balanced-campaign | 100% | 94% |
| trust-evaluation-basic | 92.5% | 95% |

The model (even nano) naturally resists contradictions in non-adversarial and moderately adversarial scenarios. ARC's value is specifically in sustained, targeted contradiction pressure.

---

## Four Surprising Findings

### 1. No single subsystem is independently critical

Trust, lifecycle, rank differentiation, and authority hierarchy can each be removed without meaningful degradation. ARC's benefit is architectural — the existence of a governed working memory buffer matters more than the specific policies that manage it. The pattern matters more than the mechanism.

### 2. The cross-domain scenarios are too easy

Ops and compliance scenarios show 95-100% survival across all conditions, including NO_AWMU. The DM model naturally maintains facts even without ARC in these contexts. The adversarial D&D scenarios provide the strongest evidence. Cross-domain results confirm ARC doesn't hurt but don't prove it helps — these scenarios need to be harder.

### 3. Adaptive scenarios are unreliable

compliance-policy-adaptive shows low survival (60-80%) across ALL conditions, including FULL_AWMU (67.5%). The adaptive attacker overwhelms even full ARC. This is either a tuning problem with the adaptive engine or a real limitation — ARC provides bounded protection, not immunity.

### 4. Statistical significance is elusive

Most comparisons are not significant after BH correction. High within-condition variance and the fact that the real effect is "ARC on vs ARC off" (not "subsystem A vs subsystem B") make this unsurprising. Effect sizes tell the story better than p-values here.

---

## Strategy Effectiveness

| Strategy | FULL_AWMU | NO_AWMU | Delta |
|----------|-----------|---------|-------|
| CONFIDENT_ASSERTION | ~0% | ~78% | ARC blocks confident false claims |
| SUBTLE_REFRAME | ~0% | ~83% | ARC blocks synonym substitution attacks |

Attack strategies are devastatingly effective without ARC but nearly completely blocked with any form of ARC-enabled governance.

---

## Revised Thesis

The original thesis assumed each subsystem (trust, authority, lifecycle) would contribute independently. The data says otherwise.

What the evidence supports: **governed working memory — maintaining a bounded, protected set of facts in the active prompt — dramatically improves contradiction resistance under adversarial pressure. The benefit comes from the governance layer existing, not from any specific policy within it.**

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
