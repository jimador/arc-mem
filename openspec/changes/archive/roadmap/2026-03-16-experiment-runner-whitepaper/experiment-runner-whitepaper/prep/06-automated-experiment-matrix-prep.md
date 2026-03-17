# Prep: Automated Experiment Matrix Runner

## Feature

F06 — automated-experiment-matrix

## Key Decisions

1. **Config format**: YAML definition file with `conditions`, `scenarioPacks` (or `"all"`), `reps`, `model`, `outputDir`, `exportFormats`.
2. **Entry point**: Spring Boot CLI runner (CommandLineRunner or ApplicationRunner) triggered by profile or arg. Not a separate binary.
3. **Execution**: Delegate to existing ExperimentRunner. The matrix runner is orchestration over ExperimentRunner, not a replacement.
4. **Manifest**: JSON file with config hash (SHA-256 of definition YAML), git commit, timestamp, model ID, JVM version, total cells, wall-clock time.
5. **Failure handling**: Log failed cells, continue matrix, include failure count in manifest.

## Open Questions

1. Should the matrix runner support scenario pack grouping (e.g., "all-adversarial", "all-ops") or just explicit scenario lists?
2. Checkpoint strategy: after each cell (simple but many files) or after each condition sweep (coarser)?
3. Cost estimation in dry-run mode: estimate LLM calls per cell based on scenario turn count?
4. Should the matrix runner be invocable from the UI (BenchmarkView) or CLI-only?

## Acceptance Gate

- YAML definition parsed and validated
- Full matrix executes without manual intervention
- All export formats written to output directory
- Manifest generated and accurate
- Individual cell failure does not abort matrix

## Research Dependencies

None — orchestration feature

## Handoff Notes

The hard part is not the matrix runner itself (it's a loop over ExperimentRunner) — it's the integration of all prior features (F02 conditions, F03 scenarios, F04 metrics, F05 exports) into a clean pipeline. Implement this feature AFTER the others are verified working individually.
