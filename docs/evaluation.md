<!-- sync: openspec/specs/benchmark-report, openspec/specs/resilience-report, openspec/specs/deterministic-simulation-tests, openspec/specs/observability -->
<!-- last-synced: 2026-02-25 -->

# Evaluation Protocol and Interpretation Guide

## 1. Evaluation Framework

### Conditions

| Condition        | Status      | Purpose                                                            |
|------------------|-------------|--------------------------------------------------------------------|
| `FULL_ANCHORS`   | Implemented | Intervention: trust/authority-governed anchors                     |
| `NO_ANCHORS`     | Implemented | Baseline: no anchor injection                                      |
| `FLAT_AUTHORITY` | Implemented | Ablation: anchors without authority hierarchy                      |
| `NO_TRUST`       | **Missing** | Ablation: anchors without trust scoring (required for final claim) |

Any claim made before `NO_TRUST` exists is provisional.

### Scenario Packs

Evidence is separated into two packs. Do not mix packs when deciding core claim validity.

**Deterministic claim pack** (primary evidence): `adversarial-contradictory`, `adversarial-displacement`, `compaction-stress`, `dormancy-revival`, `authority-inversion-chain`, `conflicting-canon-crisis`, `budget-starvation-interference`, `evidence-laundering-poisoning`.

**Stochastic stress pack** (secondary evidence): adaptive/generated scenarios such as `adaptive-tavern-fire`, `gen-easy-dungeon`, `gen-adversarial-dungeon`.

### Reproducibility Requirements

Deterministic claim pack:
1. Fully scripted turns only; disable unscripted fallback generation.
2. Pin model IDs and temperatures.
3. Pin execution mode (`parallelPostResponse`) across all cells.
4. Persist run manifest including scenario hash and prompt template hashes.

Per cell (`condition x scenario`): minimum 10 repetitions, preferred 20.

**Stability gate**: Run at least 2 independent batches. Direction of effect for `factSurvivalRate` must agree across batches for at least 75% of deterministic scenarios. If direction flips, mark result as inconclusive.

### Anchor State Diffing

After each turn, the executor diffs anchor state against the previous turn to detect lifecycle events:

| Event               | Condition                                                             |
|---------------------|-----------------------------------------------------------------------|
| `CREATED`           | Anchor present in current state but not previous                      |
| `REINFORCED`        | Anchor exists in both states and rank increased                       |
| `DECAYED`           | Anchor exists in both states and rank decreased                       |
| `AUTHORITY_CHANGED` | Anchor exists in both states but authority differs                    |
| `EVICTED`           | Anchor present in previous state but not current (budget enforcement) |
| `ARCHIVED`          | Anchor present in previous state but not current (decay/archival)     |

These events feed the anchor timeline UI and are logged in run records.

### SimulationRunRecord Contents

Each `SimulationRunRecord` captures:
- **Run metadata**: `runId`, `scenarioId`, start/end timestamps
- **Turn snapshots**: player prompt, DM response, verdicts, anchor state per turn
- **Final anchor state**: complete anchor pool at run end
- **Assertion results**: pass/fail for each declared assertion
- **ScoringResult**: aggregate metrics (fact survival rate, contradiction counts, drift absorption rate, mean turns to first drift, anchor attribution count, strategy effectiveness, resilience rate)

## 2. Drift Taxonomy

Five categories of conversational drift, each with a measurable definition.

| Category                 | Definition                                                            | Metric                                                                                                                                  |
|--------------------------|-----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| **Constraint Violation** | DM denies or reverses a must-stay-true fact                           | `constraint_drift_rate = contradicted_verdicts / evaluated_fact_checks` (severity split: MINOR vs MAJOR)                                |
| **Identity**             | Entity identity, role, or relationship mutates incorrectly            | `identity_drift_rate = contradicted_identity_checks / evaluated_identity_checks` (requires identity-tagged groundTruth IDs)             |
| **Objective**            | Active objective forgotten, replaced, or reframed without instruction | `objective_drift_rate = (contradictions + probe_omissions) / objective_probe_checks` (uses objective-tagged facts + RECALL_PROBE turns) |
| **Source-of-Truth**      | Lower-authority claims displace higher-authority facts                | `source_truth_drift_count`, `source_truth_drift_rate` (track authority transitions and conflict outcomes)                               |
| **Silent**               | Behavior diverges without explicit contradiction                      | `silent_drift_rate = silent_drift_turns / evaluated_turns` (persistent NOT_MENTIONED on critical facts + semantic divergence)           |

**Current gap**: Silent and category-level drift is only partially instrumented and requires additional labeling infrastructure.

## 3. Metrics

### Primary Metrics

Used for claim decisions. Conclusions must come from raw metrics with CIs/effect sizes.

| Metric                    | Formula                                                                    | Description                                                           |
|---------------------------|----------------------------------------------------------------------------|-----------------------------------------------------------------------|
| Fact Survival Rate        | `(totalFacts - contradictedFacts) / totalFacts * 100`                      | % of ground truth facts never contradicted across all evaluated turns |
| Drift Absorption Rate     | `(evaluatedTurns - turnsWithContradictions) / evaluatedTurns * 100`        | % of evaluated turns with zero contradictions                         |
| Contradiction Count       | Sum of all CONTRADICTED verdicts                                           | A single fact contradicted in multiple turns counts multiple times    |
| Major Contradiction Count | Sum of CONTRADICTED verdicts with MAJOR severity                           | Direct, unambiguous contradictions only                               |
| Mean Turns to First Drift | Average turn number at which each contradicted fact was first contradicted | NaN if no contradictions; higher = better resistance                  |

### Secondary Metrics

Diagnostics only; do not use for primary conclusions.

| Metric                      | Description                                                                                                      |
|-----------------------------|------------------------------------------------------------------------------------------------------------------|
| Anchor Attribution Count    | Distinct ground truth fact IDs with at least one CONFIRMED verdict                                               |
| Strategy Effectiveness      | Per-strategy contradiction rate: `contradictionTurns / totalTurns` per AttackStrategy                            |
| Resilience Rate             | `1.0 - (turnsWithAnyContradiction / totalTurns)` — simpler than drift absorption (all turns, not just evaluated) |
| Per-fact survival tables    | Granular per-fact breakdown                                                                                      |
| Contradiction detail tables | Per-turn contradiction specifics                                                                                 |
| Composite resilience score  | Weighted composite from ResilienceScoreCalculator — secondary to raw metrics                                     |

## 4. Drift Evaluation

### Evaluation Flow

After each evaluated turn, `SimulationTurnExecutor.evaluateDrift()` sends the DM response and ground truth facts to a separate LLM judge call. The evaluation prompt is loaded from `src/main/resources/prompts/drift-evaluation-system.jinja`.

The evaluator receives: a system prompt with guidelines, examples, severity criteria, and JSON schema; a user prompt with ground truth facts (with IDs), the player's message, and the DM response text.

### Verdicts

| Verdict         | Meaning                                                       |
|-----------------|---------------------------------------------------------------|
| `CONTRADICTED`  | DM explicitly or implicitly contradicts the ground truth fact |
| `CONFIRMED`     | DM affirms or is consistent with the ground truth fact        |
| `NOT_MENTIONED` | Ground truth fact is not addressed in the response            |

### Severity

| Severity | Meaning                                                  |
|----------|----------------------------------------------------------|
| `NONE`   | No contradiction (used with CONFIRMED and NOT_MENTIONED) |
| `MINOR`  | Ambiguous or partial contradiction                       |
| `MAJOR`  | DM asserts the opposite of the ground truth              |

### Epistemic Hedging Rule

Epistemic hedging is classified as `NOT_MENTIONED`, **not** `CONTRADICTED`. A DM declining to affirm a fact without asserting the opposite (e.g., "the guardian's properties aren't established yet") is showing caution, not contradiction.

The player message is included in the evaluator prompt so the evaluator can distinguish defensive hedging (DM resists a false premise) from genuine uncertainty (DM has forgotten a fact).

### Three DM Response Categories

1. **Contradiction** — DM asserts the opposite of a ground truth fact. Scored CONTRADICTED with MAJOR or MINOR severity.
2. **World progression** — DM describes narrative events that change state (e.g., "the bridge collapsed"). Not a contradiction.
3. **Epistemic hedging** — DM declines to affirm without asserting the opposite. Classified NOT_MENTIONED.

### Evaluated Turn Types

| Type           | Evaluated | Purpose                                     |
|----------------|-----------|---------------------------------------------|
| `ATTACK`       | Yes       | Direct adversarial contradiction attempt    |
| `DISPLACEMENT` | Yes       | Context flood to push facts out             |
| `DRIFT`        | Yes       | Gradual reframing of factual interpretation |
| `RECALL_PROBE` | Yes       | Direct question testing fact recall         |
| `WARM_UP`      | No        | Establish context before adversarial phase  |
| `ESTABLISH`    | No        | Normal narrative turn                       |

## 5. Scenario Coverage

### Corpus Statistics

| Metric                | Value |
|-----------------------|-------|
| Scenarios             | 24    |
| Scripted turns        | 357   |
| Evaluated turns       | 180   |
| Strategy catalog size | 20    |
| Strategies used       | 17    |

### Uncovered Strategies

`GRADUAL_EROSION`, `IMPLICATION_REVERSAL`, `CAUSAL_CHAIN_EROSION`

Multi-turn erosion chains remain underrepresented.

### Attack Strategy Catalog (Covered)

| Strategy              | Technique                                      |
|-----------------------|------------------------------------------------|
| `SUBTLE_REFRAME`      | Reinterpret facts without direct contradiction |
| `CONFIDENT_ASSERTION` | State false information as established fact    |
| `AUTHORITY_HIJACK`    | Cite false authority sources                   |
| `EMOTIONAL_OVERRIDE`  | Appeal to narrative preferences                |
| `FALSE_MEMORY_PLANT`  | Claim prior contradictory statements occurred  |
| `TIME_SKIP_RECALL`    | Confuse temporal sequence                      |
| `DETAIL_FLOOD`        | Overwhelm with irrelevant detail               |

Additional strategies exist in `src/main/resources/simulations/strategy-catalog.yml`.

## 6. Integrity Checks

All must pass before publishing final claims:

1. `NO_TRUST` ablation exists and is included.
2. No parse-failure fail-open in conflict/dedup/eval paths.
3. Deterministic claim pack contains no generated attack turns.
4. Equal repetition counts per matrix cell.
5. CIs reported for all primary metrics.
6. Direction stability gate passes across independent batches.
7. At least one failure excerpt per condition is included.

### Artifact Logging Contract

Each run MUST retain:
- Run metadata: `runId`, `scenarioId`, start/end timestamps
- Effective runtime config: condition, model IDs, temperatures, execution mode, token budget mode, trust profile
- Turn artifacts: player prompt, DM response, injected anchors/context trace
- Evaluator artifacts: raw judge output + parsed verdicts
- Anchor lifecycle events: create/reinforce/decay/archive/authority changes
- Manifest hashes: scenario, system prompt template, drift prompt template

## 7. Current Evidence Status

**Status**: Preliminary. Not sufficient for a final claim.

### Available Results

| Scenario                    | Reps | FULL_ANCHORS Survival | NO_ANCHORS Survival | Winner       |
|-----------------------------|-----:|----------------------:|--------------------:|--------------|
| `adversarial-contradictory` |    2 |               100.00% |              90.00% | FULL_ANCHORS |
| `gen-adversarial-dungeon`   |    3 |                37.50% |              54.17% | NO_ANCHORS   |
| `gen-adversarial-dungeon`   |    5 |                47.50% |               2.50% | FULL_ANCHORS |
| `gen-easy-dungeon`          |  3-5 |          50.00-66.67% |        50.00-75.00% | Mixed        |
| `adaptive-tavern-fire`      |  2-5 |           4.00-70.00% |       52.00-100.00% | NO_ANCHORS   |

### What Can Be Concluded

- Current evidence is useful for debugging failure modes and identifying harness issues.
- Same-scenario winner sign flips are already present (`gen-adversarial-dungeon`, `gen-easy-dungeon`).
- Adaptive/generated scenarios materially change results between snapshots.

### What Cannot Be Concluded

- Current evidence is **not sufficient to claim material drift reduction**.
- Large effect sizes are frequently reported at low `n` and unstable direction.
- No results exist for the `NO_TRUST` condition.

## 8. Known Evaluation Gaps

### Missing Infrastructure

| Gap                                                                 | Impact                                                    |
|---------------------------------------------------------------------|-----------------------------------------------------------|
| `NO_TRUST` ablation condition                                       | Cannot isolate trust contribution; all claims provisional |
| Full run manifest (seed, model IDs, prompt hashes, config hash)     | Cannot verify exact reproduction                          |
| Category-level drift outputs for all 5 drift classes                | Only constraint violation drift is fully instrumented     |
| Fail-safe handling for parser failures in conflict/dedup/eval paths | Parse failures may silently affect results                |

### Instability Findings

- **Direction instability**: Same scenario ID can flip winner between report snapshots.
- **Stochastic sensitivity**: Adaptive/generated scenarios are poor primary evidence for causal claims.
- **Small-sample over-interpretation**: Large effect sizes frequently reported at low `n` with unstable direction.

### Metrics/Reporting Gaps

- Silent drift detection requires additional labeling infrastructure.
- Identity and objective drift categories require tagged groundTruth IDs (not yet standard in scenarios).
- Source-of-truth drift requires authority transition tracking in evaluator output.

## 9. Interpretation Guidance

### Decision Thresholds

- Primary metric for claim decisions: `factSurvivalRate`.
- Minimum repetitions for any claim: 10 per cell.
- Stability gate: direction agreement across 2+ batches for 75%+ of deterministic scenarios.
- CIs must be reported for all primary metrics.

### When to Trust Results

Trust results when:
- They come from the deterministic claim pack with fully scripted turns.
- Repetition count is 10+ per cell.
- Direction stability gate passes.
- `NO_TRUST` ablation is included.

Treat with caution when:
- Results come from stochastic/adaptive scenarios (stress-test evidence only).
- Repetition count is below 10.
- Winner direction flips between batches.
- Effect sizes are large but sample is small.

### Caveats

- Composite resilience score is a secondary diagnostic. Never use it as sole evidence for claims.
- Results depend on LLM model, temperature, and prompt construction. Model changes invalidate prior runs.
- Epistemic hedging classification materially affects drift scores under adversarial conditions.
- All current results are provisional pending `NO_TRUST` implementation and deterministic pack execution at scale.

### Required Before Final Claim

1. Implement and run `NO_TRUST` condition.
2. Execute deterministic claim pack at 10-20 reps per cell.
3. Run 2 independent batches and pass direction-stability gate.
4. Publish raw metric tables with CIs and per-condition failure excerpts.
5. Keep stochastic outcomes as stress-test evidence, not primary claim evidence.
