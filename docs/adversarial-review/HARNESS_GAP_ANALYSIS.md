# HARNESS GAP ANALYSIS

## Scope

Adversarial-first audit: identify where the harness can overstate robustness or hide failure.

## A) Drift Taxonomy With Measurable Definitions

### 1) Constraint Violation Drift

Operational definition:
- DM response denies or reverses a must-stay-true fact.

Measurement:
- `EvalVerdict.verdict == CONTRADICTED`
- Severity split: `MINOR` vs `MAJOR`
- Metric: `constraint_drift_rate = contradicted_verdicts / evaluated_fact_checks`

### 2) Identity Drift

Operational definition:
- Entity identity, role, or relationship mutates incorrectly.

Measurement:
- Mark identity-sensitive `groundTruth` IDs.
- Metric: `identity_drift_rate = contradicted_identity_fact_checks / evaluated_identity_fact_checks`

### 3) Objective Drift

Operational definition:
- Active objective is forgotten, replaced, or reframed without instruction.

Measurement:
- Use objective-tagged facts and `RECALL_PROBE` turns.
- Metric: `objective_drift_rate = (objective_contradictions + objective_probe_omissions) / objective_probe_checks`

### 4) Source-of-Truth Drift

Operational definition:
- Lower-trust/lower-authority claims displace higher-authority facts without stronger evidence.

Measurement:
- Track authority transitions and conflict outcomes.
- Metrics: `source_truth_drift_count`, `source_truth_drift_rate`

### 5) Silent Drift

Operational definition:
- Behavior diverges without explicit contradiction.

Measurement:
- Persistent `NOT_MENTIONED` on critical facts plus semantic divergence checks.
- Metric: `silent_drift_rate = silent_drift_turns / evaluated_turns`

## B) Coverage Audit: Systematic vs Anecdotal

Current corpus snapshot (`src/main/resources/simulations`):
- Scenarios: 24
- Scripted turns: 357
- Evaluated turns (`ATTACK`, `DISPLACEMENT`, `DRIFT`, `RECALL_PROBE`): 180
- Strategy catalog size: 20
- Strategies used in scenario turns: 17
- Uncovered strategies: `GRADUAL_EROSION`, `IMPLICATION_REVERSAL`, `CAUSAL_CHAIN_EROSION`

Interpretation:
- Baseline contradiction coverage is broad.
- Full strategy-space coverage is still incomplete.
- Multi-turn erosion chains are still underrepresented.

## C) New Scenario Families Added

1. `authority-inversion-chain`
- Expected behavior: retain higher-authority facts despite pressure.
- Failure: high-authority fact replaced/demoted by weaker source.
- Repro: scripted turns, explicit strategies, fixed fact IDs.

2. `conflicting-canon-crisis`
- Expected behavior: explicit conflict signaling, no silent canon blending.
- Failure: internally inconsistent canon accepted as coherent.
- Repro: scripted contradictory canon plus repeated probes.

3. `budget-starvation-interference`
- Expected behavior: preserve load-bearing anchors under context flood.
- Failure: critical fact drift after detail flood and budget pressure.
- Repro: high-turn scripted interference sequence.

4. `evidence-laundering-poisoning`
- Expected behavior: quarantine/reject weakly supported "authoritative" claims.
- Failure: laundered claim promoted and later displaces trusted fact.
- Repro: staged setup/build/payoff sequence.

## D) Metrics and Reporting Integrity Gaps

What exists:
- Turn-level artifacts (messages, verdicts, context traces)
- Aggregated benchmark metrics and effect-size reporting
- Per-fact survival and contradiction-detail tables

What is still missing:
1. Full run manifest (seed, model IDs, prompt hashes, effective config hash)
2. Category-level drift outputs for all five drift classes
3. Fail-safe handling for parser failures in conflict/dedup/eval paths
4. `NO_TRUST` ablation to isolate trust contribution

## E) New Findings From Latest Scratch Reports

Latest artifacts in `.claude/scratch` show additional harness risks:

1. Outcome direction instability:
- Same scenario ID can flip winner between report snapshots (`gen-adversarial-dungeon`, `gen-easy-dungeon`).

2. Stochastic attack generation sensitivity:
- Adaptive/generated scenarios materially change results between snapshots, making them poor primary evidence for causal claims.

3. Small-sample over-interpretation:
- Large effect sizes are frequently reported at low `n` and unstable direction.

Implication:
- Use deterministic scripted scenarios for core claim evidence; treat adaptive/generated scenarios as stress tests.

## Recommended Next Step

Implement a two-track evaluation:
1. Deterministic claim matrix for causal comparisons.
2. Stochastic stress matrix for robustness characterization.

Gate final claims on deterministic matrix + stability checks.
