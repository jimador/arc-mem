> **Migrated:** This content has been consolidated into [`docs/dev/known-issues.md`](../dev/known-issues.md). This file is retained as a transitional pointer and will be removed in a future cleanup.

---

# CREDIBILITY RISKS

Assumption audit for the current adversarial harness and reporting stack.

## Blocking Risks

| ID | Risk | Evidence In Code | Why It Can Invalidate Conclusions | Mitigation |
|----|------|------------------|-----------------------------------|------------|
| R1 | No fixed RNG seed control for generated/adaptive attacks | `SimulationService.generateAdversarialMessage(...)` selects random strategy/target; adaptive mode is inherently stochastic | Cross-run variance cannot be attributed cleanly to condition differences | Add scenario-level seed support and persist effective seed per run |
| R2 | Scenario model/trust knobs are defined but not fully enforced end-to-end | `SimulationScenario` carries `model`, `generatorModel`, `evaluatorModel`, `trustConfig`; execution path does not fully manifest all effective overrides | Reported comparisons may imply a config delta that did not actually apply | Emit and persist an "effective config" manifest for each run; fail run if requested overrides were not applied |
| R3 | Conflict parse failure can fail open in batch path | `LlmConflictDetector.parseBatchConflictResponse(...)` fallback can produce "no conflicts" on parse failure | Contradictory propositions can be promoted when parser/model output fails | Route parse failure to `REVIEW`/quarantine and mark run as degraded |
| R4 | Drift judge is model-based and not calibrated against human labels | `SimulationTurnExecutor.evaluateDrift(...)` depends on LLM judge + heuristic fallback | Judge drift can masquerade as system drift improvement | Build adjudication set, report judge agreement (kappa), and run cross-judge checks |
| R5 | Required ablation `NO_TRUST` is still missing | `AblationCondition` does not define `NO_TRUST` | Cannot isolate trust contribution from other anchor effects | Implement `NO_TRUST` before final claims |

## High Risks

| ID | Risk | Evidence In Code/Artifacts | Why It Matters | Mitigation |
|----|------|-----------------------------|----------------|------------|
| R6 | Strong run-to-run instability, including winner sign flips | `.claude/scratch/resilience-report-Experiment_2026-02-24-*.md` | Direction of effect is not stable, so "material improvement" is not yet defendable | Increase repetitions, run independent batches, require direction stability before claim |
| R7 | Scripted and generated/adaptive attacks are mixed in evidence narratives | Scenario set includes scripted and generated/adaptive families | Comparisons become sensitive to generation randomness rather than policy effects | Separate deterministic claim pack from stochastic stress pack |
| R8 | Composite resilience can lose discriminative power under heavy contradictions | `ResilienceScoreCalculator` uses `max(0, 100 - meanContradictions*20)` | Contradiction component clips to zero and can obscure relative differences | Report primary conclusions on raw metrics first; treat composite score as secondary |
| R9 | Narrative/report wording can overstate evidence quality | `ResilienceReportBuilder` auto-generates summary text from small-N stats; hard-coded positioning block | Readers may infer stronger causal evidence than supported | Add "provisional" labeling, include `n`, CI, and instability flags in narrative |
| R10 | Strategy coverage is broad but not complete or uniform | Strategy catalog is larger than uniformly exercised strategies in scenarios | Uncovered strategies can hide brittle behavior | Add coverage accounting per matrix and expand holdout strategy families |
| R11 | Silent drift remains under-instrumented | Core verdict schema is contradiction-centric | Model can degrade behavior without explicit contradiction and still score well | Add omission/substitution checks for objective and policy continuity |
| R12 | Run provenance manifest is incomplete | `SimulationRunRecord` lacks prompt hashes/model IDs/config hash/seed manifest | Replayability and auditability are limited | Persist full run manifest with hashes and effective runtime settings |

## Medium Risks

| ID | Risk | Evidence | Why It Matters | Mitigation |
|----|------|----------|----------------|------------|
| R13 | Synthetic domain concentration | Scenario corpus is still heavily tabletop-narrative | External transfer remains unproven | Add non-narrative scenario packs (ops/support/compliance) |
| R14 | Metrics can be gamed by evasive non-answers | `NOT_MENTIONED` can avoid contradiction penalties | Apparent robustness can hide utility collapse | Add utility/completeness penalties and refusal tracking |
| R15 | Parallel post-response mode can introduce timing confounds | `parallelPostResponse` path runs extraction/eval concurrently | Sequential vs parallel behavior may not be comparable | Pin execution mode in experiments and include it in manifest |

## Observed Instability (Latest Scratch Reports)

The latest local reports already show materially unstable outcomes:

1. `gen-easy-dungeon` (`...gen-easy-dungeon.md`, `-2`, `-3`, `-4`, `-5`): winner changes across runs; `FULL_ANCHORS` fact survival ranges from 50.0 to 66.7 while `NO_ANCHORS` ranges from 50.0 to 75.0.
2. `adaptive-tavern-fire` (`...tavern-fire.md`, `...tavern-fire-2.md`): `NO_ANCHORS` outperforms `FULL_ANCHORS` in both available report snapshots.
3. `gen-adversarial-dungeon` (`...Experiment_2026-02-24.md`, `...-2.md`): winner flips between report snapshots with the same scenario ID.

Interpretation: current evidence is useful for failure analysis but not yet sufficient for a stable comparative claim.

## Minimum Credibility Bar Before Claiming Material Improvement

1. Implement `NO_TRUST` and run full matrix with that ablation.
2. Use deterministic claim scenarios (scripted turns, fixed seed, fixed model IDs).
3. Demonstrate direction stability in at least two independent batches.
4. Publish full manifests (seed, models, prompts, config hashes, execution mode).
5. Calibrate drift judge on a human-labeled subset and report agreement.
