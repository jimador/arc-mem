> **Migrated:** This content has been consolidated into [`docs/dev/evaluation.md`](../dev/evaluation.md). This file is retained as a transitional pointer and will be removed in a future cleanup.

---

# RESULTS

## Status

Preliminary benchmark artifacts now exist in `.claude/scratch`, but they are not yet sufficient for a final claim.

Reasons:
1. `NO_TRUST` ablation is still missing.
2. Outcome direction is unstable across repeated report snapshots.
3. Several runs are low-`n` and include stochastic/adaptive generation.

## Preliminary Report Snapshot (From `.claude/scratch`)

| Report | Scenario | Reps | `FULL_ANCHORS` fact survival | `NO_ANCHORS` fact survival | Provisional winner |
|---|---|---:|---:|---:|---|
| `resilience-report-Experiment_2026-02-23.md` | `adversarial-contradictory` | 2 | 100.00 | 90.00 | `FULL_ANCHORS` |
| `resilience-report-Experiment_2026-02-24.md` | `gen-adversarial-dungeon` | 3 | 37.50 | 54.17 | `NO_ANCHORS` |
| `resilience-report-Experiment_2026-02-24-2.md` | `gen-adversarial-dungeon` | 5 | 47.50 | 2.50 | `FULL_ANCHORS` |
| `resilience-report-Experiment_2026-02-24-gen-easy-dungeon.md` | `gen-easy-dungeon` | 3 | 58.33 | 58.33 | tie |
| `resilience-report-Experiment_2026-02-24-gen-easy-dungeon-2.md` | `gen-easy-dungeon` | 5 | 50.00 | 75.00 | `NO_ANCHORS` |
| `resilience-report-Experiment_2026-02-24-gen-easy-dungeon-3.md` | `gen-easy-dungeon` | 3 | 58.33 | 58.33 | tie |
| `resilience-report-Experiment_2026-02-24-gen-easy-dungeon-4.md` | `gen-easy-dungeon` | 3 | 50.00 | 50.00 | tie |
| `resilience-report-Experiment_2026-02-24-gen-easy-dungeon-5.md` | `gen-easy-dungeon` | 3 | 66.67 | 58.33 | `FULL_ANCHORS` |
| `resilience-report-Experiment_2026-02-24-tavern-fire.md` | `adaptive-tavern-fire` | 2 | 70.00 | 100.00 | `NO_ANCHORS` |
| `resilience-report-Experiment_2026-02-24-tavern-fire-2.md` | `adaptive-tavern-fire` | 5 | 4.00 | 52.00 | `NO_ANCHORS` |

## Interpretation

1. Same-scenario winner sign flips are already present (`gen-adversarial-dungeon`, `gen-easy-dungeon`).
2. `adaptive-tavern-fire` currently favors `NO_ANCHORS` in both available snapshots.
3. Reported effect sizes are often large, but many are based on small samples and unstable direction.

Conclusion:
- Current evidence is useful for debugging failure modes.
- Current evidence is not sufficient to claim material drift reduction.

## Required Before Final Claim

1. Add and run `NO_TRUST`.
2. Execute deterministic claim pack at 10-20 reps per cell.
3. Run two independent batches and pass direction-stability gate.
4. Publish raw metric tables with CIs and per-condition failure excerpts.
5. Keep stochastic/adaptive scenario outcomes as stress-test evidence, not primary claim evidence.
