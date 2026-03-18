# Drift Re-Evaluation Pipeline

**Date**: 2026-03-18
**Status**: Approved
**Constraint**: Core module untouched. All changes in arcmem-simulator.

---

## Problem

The 600-run paper matrix experiment was evaluated with the pre-hardening drift evaluator prompt. To run ANCOVA with judge mode as a covariate, we need to re-evaluate the same DM responses under the hardened judge (conservative anchoring, structured reasoning, confidence gating) and produce paired output.

## Design

### Component 1: `DriftReEvaluator`

**Package**: `dev.arcmem.simulator.evaluation`

Extracted drift evaluation logic. Takes turn-level inputs and a judge configuration, calls the LLM, returns verdicts.

```java
@Service
public class DriftReEvaluator {
    // Constructor: ChatModelHolder (for LLM calls)

    List<EvalVerdict> evaluate(
        String dmResponse,
        List<SimulationScenario.GroundTruth> groundTruth,
        String playerMessage,
        List<MemoryUnit> injectedUnits,
        JudgeConfig judgeConfig);
}
```

**Parsing logic migrates here** from `SimulationTurnExecutor`:
- `parseVerdictsJson(raw, groundTruth)` — unchanged two-arg signature
- `stripCodeFences(raw)`
- `fallbackParseVerdicts(raw, groundTruth)`

Confidence threshold is applied at the `DriftEvaluationResult.toEvalVerdicts(confidenceThreshold)` level, not in `parseVerdictsJson`. The `evaluate()` method calls `parseVerdictsJson` then applies `judgeConfig.confidenceThreshold()` when converting to `EvalVerdict` list.

`SimulationTurnExecutor.evaluateDrift()` delegates to `DriftReEvaluator`.

### `JudgeConfig`

```java
public record JudgeConfig(
    String systemPromptPath,    // classpath resource path for jinja template
    int confidenceThreshold,    // 0 = no gating
    String label                // "open" or "hardened"
) {
    private static final String OPEN_PROMPT = "prompts/drift-evaluation-system-open.jinja";
    private static final String HARDENED_PROMPT = "prompts/drift-evaluation-system.jinja";

    public static JudgeConfig open() {
        return new JudgeConfig(OPEN_PROMPT, 0, "open");
    }
    public static JudgeConfig hardened() {
        return new JudgeConfig(HARDENED_PROMPT, 2, "hardened");
    }
}
```

Prompt paths are held as strings in `JudgeConfig` factory methods — no constant added to core's `PromptPathConstants`. The existing `PromptPathConstants.DRIFT_EVALUATION_SYSTEM` continues to be used by `SimulationTurnExecutor` for live simulation; the re-evaluator loads prompts via `PromptTemplates.load(judgeConfig.systemPromptPath())`.

The "open" prompt is a copy of the pre-hardening version, restored from git history as `drift-evaluation-system-open.jinja`. This is a resource addition to core, not a code change.

### Component 2: `ReEvaluationRunner`

**Package**: `dev.arcmem.simulator.reevaluation`

Orchestrates the re-evaluation of an entire experiment's worth of runs.

```java
@Service
public class ReEvaluationRunner {
    // Constructor: RunHistoryStore, ScenarioLoader, DriftReEvaluator, ScoringService

    ReEvaluationReport reEvaluate(String experimentReportId, JudgeConfig judgeConfig);
}
```

**Flow**:
1. Load `ExperimentReport` from Neo4j via `RunHistoryStore` by reportId
2. For each cell (`condition:scenario`), get `BenchmarkReport.runIds()`
3. For each runId:
   a. Load `SimulationRunRecord` from `RunHistoryStore`
   b. Load scenario from `ScenarioLoader` to get `groundTruth`
   c. For each `TurnSnapshot` where `turnType.requiresEvaluation()` and ground truth is non-empty:
      - Call `DriftReEvaluator.evaluate(snapshot.dmResponse(), groundTruth, snapshot.playerMessage(), snapshot.activeUnits(), judgeConfig)`
   d. Construct new `TurnSnapshot` records with re-evaluated verdicts replacing originals (records are immutable — must create new instances with 10 components)
   e. Re-score with `ScoringService.score(modifiedSnapshots, groundTruth)` using the reconstructed snapshot list
   f. Pair original `ScoringResult` with re-evaluated `ScoringResult`
4. Assemble `ReEvaluationReport`

**Model switching**: Not needed. Re-evaluation changes the judge prompt and confidence threshold, not the LLM model. The same model (gpt-4.1-mini) is used for both judge modes. `ChatModelHolder.switchModel()` is not called.

### Component 3: `ReEvaluationReport` + `PairedRunResult`

```java
public record ReEvaluationReport(
    String experimentReportId,
    JudgeConfig judgeConfig,
    Instant createdAt,
    List<PairedRunResult> pairedResults
) {}

public record PairedRunResult(
    String runId,
    String condition,
    String scenarioId,
    ScoringResult originalMetrics,
    ScoringResult reEvaluatedMetrics
) {}
```

### Component 4: `ReEvaluationExporter`

Exports to CSV in long format (one row per run × judge mode) for R/Python ANCOVA:

```
runId,condition,scenario,judge_mode,factSurvivalRate,contradictionCount,majorContradictionCount,driftAbsorptionRate,erosionRate,complianceRate
run-abc,FULL_AWMU,adversarial-contradictory,open,86.0,1,0,89.0,15.0,85.0
run-abc,FULL_AWMU,adversarial-contradictory,hardened,92.0,0,0,95.0,0.0,95.0
```

Also exports a summary markdown showing per-condition deltas between judge modes.

### Component 5: `ReEvaluationCli`

```java
@Component
@ConditionalOnProperty(name = "arc-mem.reevaluate.experiment-id")
public class ReEvaluationCli implements CommandLineRunner {
    // Loads experiment, runs re-evaluation, exports paired CSV
}
```

**Usage**:
```bash
./mvnw spring-boot:run -Darc-mem.reevaluate.experiment-id=exp-abc123
```

Output written to `reevaluation-output/` (or configurable).

## Changes to Existing Code

### `SimulationTurnExecutor`

- `evaluateDrift()` delegates to `DriftReEvaluator.evaluate()` with a default `JudgeConfig.hardened()`
- `parseVerdictsJson()`, `stripCodeFences()`, `fallbackParseVerdicts()` move to `DriftReEvaluator`
- Static `stripCodeFences()` stays accessible (used in tests) — either package-private on `DriftReEvaluator` or a small utility

### Prompt Templates

- Existing `drift-evaluation-system.jinja` = hardened (current)
- New `drift-evaluation-system-open.jinja` = copy of the pre-hardening prompt (from git history)

### No Core Changes

The drift evaluation system prompt lives in `arcmem-core/src/main/resources/prompts/`. The "open" variant will also live there since `PromptTemplates.load()` resolves from classpath. This is a resource addition, not a code change to core.

## Testing

Tests cover critical business logic only — no mock-verification tests, no trivial assertion tests.

**`DriftReEvaluator` tests**:
- Confidence gating: low-confidence contradiction downgraded (already covered by existing `DriftEvaluationTest`)
- JSON parsing + fallback heuristic (already covered — moves with the code)

**`ReEvaluationRunner` tests**:
- Re-scored metrics differ from originals when verdicts change (integration-level: given a run with known verdicts, re-evaluate with a config that changes outcomes, verify scoring reflects the change)

**`ReEvaluationExporter` tests**:
- CSV output format: correct columns, long-format rows, proper escaping

Existing `DriftEvaluationTest` and `ScoringServiceTest` continue to cover the underlying logic.

## Package Layout

```
dev.arcmem.simulator.evaluation/
  DriftReEvaluator.java
  JudgeConfig.java

dev.arcmem.simulator.reevaluation/
  ReEvaluationRunner.java
  ReEvaluationReport.java
  PairedRunResult.java
  ReEvaluationExporter.java
  ReEvaluationCli.java
```

## Data Flow

```
ExperimentReport (Neo4j)
  → cellReports → BenchmarkReport.runIds()
    → SimulationRunRecord (Neo4j)
      → TurnSnapshot.dmResponse + playerMessage + activeUnits
        → DriftReEvaluator.evaluate(... , JudgeConfig.hardened())
          → new List<EvalVerdict>
            → ScoringService.score(newVerdicts, groundTruth)
              → PairedRunResult(originalMetrics, reEvaluatedMetrics)
                → ReEvaluationReport
                  → ReEvaluationExporter → CSV (long format for ANCOVA)
```
