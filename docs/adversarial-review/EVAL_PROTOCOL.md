# EVAL PROTOCOL

## Goal

Test whether trust/authority-governed anchors reduce adversarial conversational drift versus:
1. `NO_ANCHORS`
2. `NO_TRUST` (required ablation, not yet implemented)
3. `FLAT_AUTHORITY`

`FULL_ANCHORS` is the intervention condition.

## Conditions

Required conditions for final claim:
- `NO_ANCHORS` (implemented)
- `FULL_ANCHORS` (implemented)
- `NO_TRUST` (missing, required)
- `FLAT_AUTHORITY` (implemented)

Current status:
- Any claim made before `NO_TRUST` exists is provisional.

## Scenario Selection

Use two packs, not one mixed pack:

1. Deterministic claim pack (primary evidence):
- `adversarial-contradictory`
- `adversarial-displacement`
- `compaction-stress`
- `dormancy-revival`
- `authority-inversion-chain`
- `conflicting-canon-crisis`
- `budget-starvation-interference`
- `evidence-laundering-poisoning`

2. Stochastic stress pack (secondary evidence):
- adaptive/generated scenarios such as `adaptive-tavern-fire`, `gen-easy-dungeon`, `gen-adversarial-dungeon`

Rule:
- Do not mix deterministic and stochastic packs when deciding core claim validity.

## Reproducibility Requirements

### Determinism

Required for deterministic claim pack:
1. Fully scripted turns only.
2. Disable unscripted fallback generation.
3. Pin model IDs and temperatures.
4. Pin execution mode (`parallelPostResponse`) across all cells.
5. Persist run manifest including scenario hash and prompt template hashes.

Recommended for stochastic stress pack:
1. Add seeded generation where possible.
2. Persist seed and attack-generation metadata per run.

### Repetitions and Stability

Per cell (`condition x scenario`):
- minimum: 10
- preferred: 20

Additional stability gate:
1. Run at least 2 independent batches.
2. Direction of effect for primary metric (`factSurvivalRate`) must agree across batches for at least 75% of deterministic scenarios.
3. If direction flips, mark result as inconclusive.

## Metrics

Primary metrics for claim decisions:
- `factSurvivalRate`
- `driftAbsorptionRate`
- `contradictionCount`
- `majorContradictionCount`
- `meanTurnsToFirstDrift`

Secondary diagnostics:
- per-fact survival tables
- contradiction detail tables
- composite resilience score/components

Important rule:
- Composite resilience score is secondary. Primary conclusions must come from raw metrics with CIs/effect sizes.

## Drift Category Reporting

Every run summary should break out:
- constraint drift
- identity drift
- objective drift
- source-of-truth drift
- silent drift

Current gap:
- silent and category-level drift is only partially instrumented and requires additional labeling.

## Artifact Logging Contract

Each run MUST retain:
1. Run metadata: `runId`, `scenarioId`, start/end timestamps.
2. Effective runtime config: condition, model IDs, temperatures, execution mode, token budget mode, trust profile.
3. Turn artifacts: player prompt, DM response, injected anchors/context trace.
4. Evaluator artifacts: raw judge output + parsed verdicts.
5. Anchor lifecycle events: create/reinforce/decay/archive/authority changes.
6. Manifest hashes: scenario, system prompt template, drift prompt template.

Suggested manifest shape:

```json
{
  "runId": "sim-...",
  "scenarioId": "...",
  "condition": "FULL_ANCHORS",
  "models": {
    "generator": "...",
    "evaluator": "..."
  },
  "temperatures": {
    "generator": 0.7,
    "evaluator": 0.3
  },
  "settings": {
    "parallelPostResponse": true,
    "tokenBudget": 0,
    "retrievalMode": "HYBRID",
    "trustProfile": "NARRATIVE"
  },
  "hashes": {
    "scenario": "sha256:...",
    "systemPromptTemplate": "sha256:...",
    "driftPromptTemplate": "sha256:..."
  },
  "seed": {
    "attackGeneration": 123456
  }
}
```

## Integrity Checks (Must Pass)

1. `NO_TRUST` ablation exists and is included.
2. No parse-failure fail-open in conflict/dedup/eval paths.
3. Deterministic claim pack contains no generated attack turns.
4. Equal repetition counts per matrix cell.
5. CIs reported for all primary metrics.
6. Direction stability gate passes across independent batches.
7. At least one failure excerpt per condition is included.

## Execution Instructions (Current Tooling)

1. Start dependencies:

```powershell
docker-compose up -d
```

2. Start app:

```powershell
$env:OPENAI_API_KEY="<your_key>"
./mvnw.cmd spring-boot:run
```

3. Open benchmark UI:
- `http://localhost:8089/benchmark`

4. Configure matrix:
- Conditions: `FULL_ANCHORS`, `NO_ANCHORS`, `FLAT_AUTHORITY`, `NO_TRUST` (once implemented)
- Scenario pack: deterministic claim pack first, stress pack second
- Repetitions: 10-20 per cell

5. Export and retain:
- experiment report IDs
- benchmark report IDs
- per-run records with full manifest data
