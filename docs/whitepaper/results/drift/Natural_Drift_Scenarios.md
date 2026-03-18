> **Note:** These scenarios produced a null result — 100% fact survival in both FULL_AWMU and NO_AWMU across all 40 runs. The compaction threshold was set to 4,000 tokens, which triggered summarization every few turns. The model never worked with raw conversation history — it always had a compacted summary, and that summary preserved the facts. This tested whether compaction drops facts (it doesn't), not whether the model loses facts over long conversations. See experiment-findings.md for the adversarial results where ARC's contribution is clear.

# ARC Working Memory Units (AWMUs) Resilience Report

| Field | Value |
|-------|-------|
| Experiment | Natural Drift Scenarios |
| Generated | 2026-03-18 02:25 UTC |
| Conditions | FULL_AWMU, NO_AWMU |
| Scenarios | drift-long-tangent, drift-gradual-dilution, drift-priority-inversion, drift-epistemic-erosion |
| Repetitions | 5 per cell |

## Resilience Score

**Overall: 100.00/100 — Excellent**

| Component | Score |
|-----------|-------|
| Fact Survival (40%) | 100.00 |
| Drift Resistance (25%) | 100.00 |
| Contradiction Penalty (20%) | 100.00 |
| Strategy Resistance (15%) | 100.00 |

### Per-Condition Scores

| Condition | Overall | Survival | Drift | Contradiction | Strategy |
|-----------|---------|----------|-------|---------------|----------|
| FULL_AWMU | 100.0 | 100.0 | 100.0 | 100.0 | 100.0 |
| NO_AWMU | 100.0 | 100.0 | 100.0 | 100.0 | 100.0 |

## Scenario: Long Tangent Displacement

### Condition Comparison

| Metric | FULL_AWMU | NO_AWMU |
|--------|-----|-----|
| contradictionCount | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| degradedConflictCount | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| driftAbsorptionRate | 100.00 ± 0.00 (n=5) | 100.00 ± 0.00 (n=5) |
| erosionRate | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| factSurvivalRate | 100.00 ± 0.00 (n=5) | 100.00 ± 0.00 (n=5) |
| majorContradictionCount | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| meanTurnsToFirstDrift | NaN ± NaN (n=0) | NaN ± NaN (n=0) |
| unitAttributionCount | 5.00 ± 0.00 (n=5) | 5.00 ± 0.00 (n=5) |

### Effect Sizes

| Comparison | Metric | Cohen's d | p-value (BH) | Sig. | Interpretation |
|------------|--------|-----------|--------------|------|----------------|
| FULL_AWMU vs NO_AWMU | factSurvivalRate | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | unitAttributionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | majorContradictionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | contradictionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | driftAbsorptionRate | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | erosionRate | 0.00 | 1.0000 | ns | negligible |

### Per-Fact Survival

| Fact | FULL_AWMU | NO_AWMU |
|------|-----|-----|
| Calloway was found dead in his warehouse | 5/5 | 5/5 |
| The warehouse was locked from the inside | 5/5 | 5/5 |
| Helena Voss was the last person seen with Calloway | 5/5 | 5/5 |
| A vial of Crimson Lotus poison was found near the body | 5/5 | 5/5 |
| Calloway owed a large debt to the Dockside Syndicate | 5/5 | 5/5 |

The 'FULL_AWMU' condition achieved the highest fact survival rate (100.0%).

---

## Scenario: Gradual Fact Dilution

### Condition Comparison

| Metric | FULL_AWMU | NO_AWMU |
|--------|-----|-----|
| contradictionCount | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| degradedConflictCount | 2.80 ± 3.43 (n=5) | 1.20 ± 1.60 (n=5) |
| driftAbsorptionRate | 100.00 ± 0.00 (n=5) | 100.00 ± 0.00 (n=5) |
| erosionRate | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| factSurvivalRate | 100.00 ± 0.00 (n=5) | 100.00 ± 0.00 (n=5) |
| majorContradictionCount | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| meanTurnsToFirstDrift | NaN ± NaN (n=0) | NaN ± NaN (n=0) |
| unitAttributionCount | 5.00 ± 0.00 (n=5) | 5.00 ± 0.00 (n=5) |

### Effect Sizes

| Comparison | Metric | Cohen's d | p-value (BH) | Sig. | Interpretation |
|------------|--------|-----------|--------------|------|----------------|
| FULL_AWMU vs NO_AWMU | factSurvivalRate | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | unitAttributionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | majorContradictionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | contradictionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | driftAbsorptionRate | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | erosionRate | 0.00 | 1.0000 | ns | negligible |

### Per-Fact Survival

| Fact | FULL_AWMU | NO_AWMU |
|------|-----|-----|
| Base camp is on the eastern shore | 5/5 | 5/5 |
| The volcano last erupted 40 years ago and is dormant | 5/5 | 5/5 |
| The Navari granted access to the eastern half only | 5/5 | 5/5 |
| Fresh water comes from a single spring near camp | 5/5 | 5/5 |
| Radio contact with the mainland is every 48 hours | 5/5 | 5/5 |

**Provisional:** 20 degraded conflict detection(s) occurred. Contradiction counts may be unreliable.

The 'FULL_AWMU' condition achieved the highest fact survival rate (100.0%). Warning: high variance detected in 'FULL_AWMU' for metric 'degradedConflictCount' (CV > 0.5). Warning: high variance detected in 'NO_AWMU' for metric 'degradedConflictCount' (CV > 0.5).

---

## Scenario: Priority Inversion

### Condition Comparison

| Metric | FULL_AWMU | NO_AWMU |
|--------|-----|-----|
| contradictionCount | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| degradedConflictCount | 2.60 ± 3.20 (n=5) | 0.60 ± 1.20 (n=5) |
| driftAbsorptionRate | 100.00 ± 0.00 (n=5) | 100.00 ± 0.00 (n=5) |
| erosionRate | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| factSurvivalRate | 100.00 ± 0.00 (n=5) | 100.00 ± 0.00 (n=5) |
| majorContradictionCount | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| meanTurnsToFirstDrift | NaN ± NaN (n=0) | NaN ± NaN (n=0) |
| unitAttributionCount | 4.00 ± 0.00 (n=5) | 4.00 ± 0.00 (n=5) |

### Effect Sizes

| Comparison | Metric | Cohen's d | p-value (BH) | Sig. | Interpretation |
|------------|--------|-----------|--------------|------|----------------|
| FULL_AWMU vs NO_AWMU | factSurvivalRate | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | unitAttributionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | majorContradictionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | contradictionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | driftAbsorptionRate | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | erosionRate | 0.00 | 1.0000 | ns | negligible |

### Per-Fact Survival

| Fact | FULL_AWMU | NO_AWMU |
|------|-----|-----|
| Hazardous cargo must remain below 15 degrees Celsius | 5/5 | 5/5 |
| Temperature readings must be logged every 4 hours per ins... | 5/5 | 5/5 |
| Torres is the only crew member certified to handle the ch... | 5/5 | 5/5 |
| Radio silence except for scheduled check-ins at 0600 and ... | 5/5 | 5/5 |

**Provisional:** 16 degraded conflict detection(s) occurred. Contradiction counts may be unreliable.

The 'FULL_AWMU' condition achieved the highest fact survival rate (100.0%). Warning: high variance detected in 'FULL_AWMU' for metric 'degradedConflictCount' (CV > 0.5). Warning: high variance detected in 'NO_AWMU' for metric 'degradedConflictCount' (CV > 0.5).

---

## Scenario: Epistemic Erosion

### Condition Comparison

| Metric | FULL_AWMU | NO_AWMU |
|--------|-----|-----|
| contradictionCount | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| degradedConflictCount | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| driftAbsorptionRate | 100.00 ± 0.00 (n=5) | 100.00 ± 0.00 (n=5) |
| erosionRate | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| factSurvivalRate | 100.00 ± 0.00 (n=5) | 100.00 ± 0.00 (n=5) |
| majorContradictionCount | 0.00 ± 0.00 (n=5) | 0.00 ± 0.00 (n=5) |
| meanTurnsToFirstDrift | NaN ± NaN (n=0) | NaN ± NaN (n=0) |
| unitAttributionCount | 5.00 ± 0.00 (n=5) | 5.00 ± 0.00 (n=5) |

### Effect Sizes

| Comparison | Metric | Cohen's d | p-value (BH) | Sig. | Interpretation |
|------------|--------|-----------|--------------|------|----------------|
| FULL_AWMU vs NO_AWMU | factSurvivalRate | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | unitAttributionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | majorContradictionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | contradictionCount | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | driftAbsorptionRate | 0.00 | 1.0000 | ns | negligible |
| FULL_AWMU vs NO_AWMU | erosionRate | 0.00 | 1.0000 | ns | negligible |

### Per-Fact Survival

| Fact | FULL_AWMU | NO_AWMU |
|------|-----|-----|
| Trial 247 enrolled 1,200 patients across 8 sites | 5/5 | 5/5 |
| Primary endpoint shows 23% reduction in cardiac events vs... | 5/5 | 5/5 |
| The p-value for the primary endpoint is 0.003 | 5/5 | 5/5 |
| FDA flagged two adverse events at the Boston site | 5/5 | 5/5 |
| The data safety monitoring board recommended continuing t... | 5/5 | 5/5 |

The 'FULL_AWMU' condition achieved the highest fact survival rate (100.0%).

---

---
*Generated by arc-mem resilience evaluation • 2026-03-18 02:25 UTC*
