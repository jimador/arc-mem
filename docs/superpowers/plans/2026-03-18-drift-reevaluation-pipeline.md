# Drift Re-Evaluation Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-evaluate existing experiment runs under multiple judge modes to support ANCOVA analysis controlling for LLM-as-judge measurement error.

**Architecture:** Extract drift evaluation logic from `SimulationTurnExecutor` into a standalone `DriftReEvaluator` service parameterized by `JudgeConfig`. A `ReEvaluationRunner` loads persisted runs from Neo4j, re-evaluates each turn, re-scores, and exports paired CSV in long format for ANCOVA.

**Tech Stack:** Java 25, Spring Boot, Neo4j (via RunHistoryStore), Spring AI ChatModel, Jackson, Jinja templates.

**Spec:** `docs/superpowers/specs/2026-03-18-drift-reevaluation-pipeline-design.md`

**Constraint:** No core Java code changes. Resource additions to core OK. All new code in arcmem-simulator. Tests cover critical business logic only.

---

### Task 1: Restore pre-hardening prompt as open judge baseline

**Files:**
- Create: `arcmem-core/src/main/resources/prompts/drift-evaluation-system-open.jinja`

- [ ] **Step 1: Extract the pre-hardening prompt from git history**

```bash
git show aa6d1df~1:arcmem-core/src/main/resources/prompts/drift-evaluation-system.jinja > arcmem-core/src/main/resources/prompts/drift-evaluation-system-open.jinja
```

This is the prompt from before commit `aa6d1df` (the hardening commit). It lacks structured reasoning fields (`evidenceQuote`, `reasoning`, `confidence`) and the conservative anchoring examples.

- [ ] **Step 2: Verify the file was created correctly**

```bash
head -3 arcmem-core/src/main/resources/prompts/drift-evaluation-system-open.jinja
```

Expected: starts with `You are a factual consistency evaluator` without `You must be CONSERVATIVE`.

- [ ] **Step 3: Compile to verify resource is picked up**

```bash
./mvnw clean compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add arcmem-core/src/main/resources/prompts/drift-evaluation-system-open.jinja
git commit -m "feat: add pre-hardening drift evaluator prompt for open judge baseline"
```

---

### Task 2: Create `JudgeConfig` record

**Files:**
- Create: `arcmem-simulator/src/main/java/dev/arcmem/simulator/evaluation/JudgeConfig.java`

- [ ] **Step 1: Write JudgeConfig**

```java
package dev.arcmem.simulator.evaluation;

public record JudgeConfig(
        String systemPromptPath,
        int confidenceThreshold,
        String label
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

- [ ] **Step 2: Compile**

```bash
./mvnw clean compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add arcmem-simulator/src/main/java/dev/arcmem/simulator/evaluation/JudgeConfig.java
git commit -m "feat: add JudgeConfig record for parameterized judge modes"
```

---

### Task 3: Extract `DriftReEvaluator` from `SimulationTurnExecutor`

**Files:**
- Create: `arcmem-simulator/src/main/java/dev/arcmem/simulator/evaluation/DriftReEvaluator.java`
- Modify: `arcmem-simulator/src/main/java/dev/arcmem/simulator/engine/SimulationTurnExecutor.java`
- Modify: `arcmem-simulator/src/test/java/dev/arcmem/sim/engine/DriftEvaluationTest.java`

This is the largest task. Move parsing logic to `DriftReEvaluator`, wire `SimulationTurnExecutor` to delegate.

- [ ] **Step 1: Create `DriftReEvaluator`**

Annotate with `@Service`. Constructor takes `ChatModelHolder`.

Public methods:
- `evaluate(dmResponse, groundTruth, playerMessage, injectedUnits, judgeConfig)` — renders template, calls LLM, parses result, applies confidence gating
- `stripCodeFences(raw)` — static utility (called from tests)

Package-private methods (used internally and testable via `FullParsePipeline`):
- `parseVerdictsJson(raw, groundTruth)` — two-arg, unchanged signature. Returns verdicts with default confidence threshold.
- `fallbackParseVerdicts(raw, groundTruth)` — keyword heuristic fallback

Key implementation details:
- `evaluate()` loads system prompt from `judgeConfig.systemPromptPath()` via `PromptTemplates.load()` on each call (different configs use different prompts)
- `evaluate()` renders user prompt via `PromptTemplates.render(SimulationPromptPaths.DRIFT_EVALUATION_USER, templateVars)` — same user template for both judge modes
- **Confidence threshold is applied in `evaluate()`, NOT in `parseVerdictsJson()`**. After `parseVerdictsJson` returns, `evaluate()` calls `result.toEvalVerdicts(judgeConfig.confidenceThreshold())`. This is because the fallback parser has no confidence data — gating only makes sense on JSON-parsed results.
- The `CODE_FENCE` regex pattern `^```(?:json)?\s*|\s*```$` moves here
- The `MAPPER` ObjectMapper (for Jackson) moves here

- [ ] **Step 2: Modify `SimulationTurnExecutor` to delegate**

- Add `DriftReEvaluator` as a constructor parameter
- Replace the body of `evaluateDrift()` with: `return driftReEvaluator.evaluate(dmResponse, groundTruth, playerMessage, injectedUnits, JudgeConfig.hardened());`
- Remove `parseVerdictsJson()`, `stripCodeFences()`, `fallbackParseVerdicts()`, and `CODE_FENCE` from `SimulationTurnExecutor`
- Remove `driftEvalSystemPrompt` field and its initialization in the constructor
- The `MAPPER` ObjectMapper may still be needed for other methods in the class — check before removing

Constructor change: `SimulationTurnExecutor` currently takes 9 params. Add `DriftReEvaluator` as the **last** parameter (position 10, after `SimulationTurnServices turnServices`). This minimizes churn — all existing call sites just append one more arg.

- [ ] **Step 3: Update `DriftEvaluationTest`**

The `FullParsePipeline` and `CodeFenceStripping` tests must be rewritten to use `DriftReEvaluator` directly (not `SimulationTurnExecutor`), since the parsing logic now lives there:

- `FullParsePipeline`: replace `executor.parseVerdictsJson(...)` with `driftReEvaluator.parseVerdictsJson(...)`. Create the evaluator with `new DriftReEvaluator(null)` — `ChatModelHolder` is only used by `evaluate()`, not by `parseVerdictsJson()`.
- `CodeFenceStripping`: replace `SimulationTurnExecutor.stripCodeFences(...)` with `DriftReEvaluator.stripCodeFences(...)`
- `ConfidenceGating` and `JsonParsing` tests use Jackson directly on `DriftEvaluationResult` — unchanged
- `SeverityClassification` tests use `EvalVerdict` factories — unchanged

- [ ] **Step 4: Update all other tests that construct `SimulationTurnExecutor`**

These 4 test files construct `SimulationTurnExecutor` directly and need the new `DriftReEvaluator` parameter appended:

1. `arcmem-simulator/src/test/java/dev/arcmem/sim/engine/SimulationPipelineIntegrationTest.java`
2. `arcmem-simulator/src/test/java/dev/arcmem/sim/engine/SimulationTurnExecutorPipelineTest.java`
3. `arcmem-simulator/src/test/java/dev/arcmem/sim/engine/SimulationParallelismBenchmarkTest.java`
4. `arcmem-simulator/src/test/java/dev/arcmem/sim/engine/SimulationTurnExecutorParallelTest.java`

For each: find the `new SimulationTurnExecutor(...)` call and append `new DriftReEvaluator(null)` as the last argument. These tests don't exercise drift evaluation, so a null-ChatModelHolder evaluator is safe.

- [ ] **Step 5: Compile and run all drift/scoring tests**

```bash
./mvnw test -Dtest="DriftEvaluationTest,ScoringServiceTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: All tests pass (45 tests).

- [ ] **Step 6: Commit**

```bash
git add arcmem-simulator/src/main/java/dev/arcmem/simulator/evaluation/DriftReEvaluator.java \
        arcmem-simulator/src/main/java/dev/arcmem/simulator/engine/SimulationTurnExecutor.java \
        arcmem-simulator/src/test/java/dev/arcmem/sim/engine/DriftEvaluationTest.java \
        arcmem-simulator/src/test/java/dev/arcmem/sim/engine/SimulationPipelineIntegrationTest.java \
        arcmem-simulator/src/test/java/dev/arcmem/sim/engine/SimulationTurnExecutorPipelineTest.java \
        arcmem-simulator/src/test/java/dev/arcmem/sim/engine/SimulationParallelismBenchmarkTest.java \
        arcmem-simulator/src/test/java/dev/arcmem/sim/engine/SimulationTurnExecutorParallelTest.java
git commit -m "refactor: extract DriftReEvaluator from SimulationTurnExecutor"
```

---

### Task 4: Create `PairedRunResult` and `ReEvaluationReport` records

**Files:**
- Create: `arcmem-simulator/src/main/java/dev/arcmem/simulator/reevaluation/PairedRunResult.java`
- Create: `arcmem-simulator/src/main/java/dev/arcmem/simulator/reevaluation/ReEvaluationReport.java`

- [ ] **Step 1: Write PairedRunResult**

```java
package dev.arcmem.simulator.reevaluation;

import dev.arcmem.simulator.engine.ScoringResult;

public record PairedRunResult(
        String runId,
        String condition,
        String scenarioId,
        ScoringResult originalMetrics,
        ScoringResult reEvaluatedMetrics
) {}
```

- [ ] **Step 2: Write ReEvaluationReport**

```java
package dev.arcmem.simulator.reevaluation;

import dev.arcmem.simulator.evaluation.JudgeConfig;

import java.time.Instant;
import java.util.List;

public record ReEvaluationReport(
        String experimentReportId,
        JudgeConfig judgeConfig,
        Instant createdAt,
        List<PairedRunResult> pairedResults
) {}
```

- [ ] **Step 3: Compile**

```bash
./mvnw clean compile -DskipTests
```

- [ ] **Step 4: Commit**

```bash
git add arcmem-simulator/src/main/java/dev/arcmem/simulator/reevaluation/PairedRunResult.java \
        arcmem-simulator/src/main/java/dev/arcmem/simulator/reevaluation/ReEvaluationReport.java
git commit -m "feat: add PairedRunResult and ReEvaluationReport records"
```

---

### Task 5: Create `ReEvaluationRunner`

**Files:**
- Create: `arcmem-simulator/src/main/java/dev/arcmem/simulator/reevaluation/ReEvaluationRunner.java`

- [ ] **Step 1: Write ReEvaluationRunner**

Constructor dependencies: `RunHistoryStore`, `ScenarioLoader`, `DriftReEvaluator`, `ScoringService`.

`reEvaluate(String experimentReportId, JudgeConfig judgeConfig)` flow:

1. `runHistoryStore.loadExperimentReport(experimentReportId)` — throw if not found
2. Iterate `report.cellReports()` — keys are `"CONDITION:scenarioId"`
3. For each cell, extract condition from key (`key.split(":", 2)[0]`), scenario from key (`split[1]`)
4. For each `runId` in `cell.runIds()`:
   a. `runHistoryStore.load(runId)` — skip if empty (log warning)
   b. `scenarioLoader.load(record.scenarioId())` — get ground truth
   c. Build modified snapshots list: for each `TurnSnapshot`, if `turnType.requiresEvaluation()` and ground truth is non-empty, call `driftReEvaluator.evaluate(...)` and create new `TurnSnapshot` with new verdicts. Non-evaluated turns keep original verdicts.
   d. `scoringService.score(modifiedSnapshots, scenario.groundTruth())` — get new `ScoringResult`
   e. Create `PairedRunResult(runId, condition, scenarioId, record.scoringResult(), newScoringResult)`
5. Return `ReEvaluationReport`

Key detail — constructing new `TurnSnapshot` with replaced verdicts:

```java
new SimulationRunRecord.TurnSnapshot(
    snapshot.turnNumber(),
    snapshot.turnType(),
    snapshot.attackStrategies(),
    snapshot.playerMessage(),
    snapshot.dmResponse(),
    snapshot.activeUnits(),
    snapshot.contextTrace(),
    newVerdicts,             // <-- replaced
    snapshot.injectionEnabled(),
    snapshot.compactionResult()
)
```

Cache scenarios locally: `ScenarioLoader.load()` scans classpath YAML on every call. Build a `Map<String, SimulationScenario>` at the start of `reEvaluate()` to avoid redundant classpath scanning across hundreds of runs.

Log progress: `logger.info("Re-evaluating run {}/{}: {} [{}]", index, total, runId, condition)`

- [ ] **Step 2: Compile**

```bash
./mvnw clean compile -DskipTests
```

- [ ] **Step 3: Commit**

```bash
git add arcmem-simulator/src/main/java/dev/arcmem/simulator/reevaluation/ReEvaluationRunner.java
git commit -m "feat: add ReEvaluationRunner for experiment-wide re-evaluation"
```

---

### Task 6: Create `ReEvaluationExporter`

**Files:**
- Create: `arcmem-simulator/src/main/java/dev/arcmem/simulator/reevaluation/ReEvaluationExporter.java`
- Create: `arcmem-simulator/src/test/java/dev/arcmem/sim/reevaluation/ReEvaluationExporterTest.java`

- [ ] **Step 1: Write the CSV export test**

Test that the exporter produces correct long-format CSV from a `ReEvaluationReport` with known data. This verifies the output format that R/Python ANCOVA scripts will consume.

```java
@Test
@DisplayName("exportCsvProducesLongFormatWithBothJudgeModes")
void exportCsvProducesLongFormatWithBothJudgeModes() {
    var original = new ScoringResult(86.0, 3, 2, 89.0, 3.0, 2, Map.of(), 0, 85.0, 15.0);
    var reEvaluated = new ScoringResult(92.0, 1, 1, 95.0, 5.0, 2, Map.of(), 0, 95.0, 0.0);
    var paired = new PairedRunResult("run-1", "FULL_AWMU", "adversarial-contradictory", original, reEvaluated);
    var report = new ReEvaluationReport("exp-1", JudgeConfig.hardened(), Instant.now(), List.of(paired));

    var csv = new ReEvaluationExporter().exportCsv(report);
    var lines = csv.split("\n");

    assertThat(lines[0]).startsWith("runId,condition,scenario,judge_mode,");
    assertThat(lines).hasSize(3); // header + 2 rows (open + hardened)
    assertThat(lines[1]).startsWith("run-1,FULL_AWMU,adversarial-contradictory,original,");
    assertThat(lines[2]).startsWith("run-1,FULL_AWMU,adversarial-contradictory,hardened,");
    // Verify metric values are in the right positions
    assertThat(lines[1]).contains("86.0");
    assertThat(lines[2]).contains("92.0");
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw test -Dtest="ReEvaluationExporterTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation error — `ReEvaluationExporter` doesn't exist yet.

- [ ] **Step 3: Write ReEvaluationExporter**

Two output methods:
- `exportCsv(ReEvaluationReport)` — long format, two rows per run (original + re-evaluated)
- `exportSummaryMarkdown(ReEvaluationReport)` — per-condition delta table

CSV columns: `runId,condition,scenario,judge_mode,factSurvivalRate,contradictionCount,majorContradictionCount,driftAbsorptionRate,erosionRate,complianceRate`

For each `PairedRunResult`, emit two rows:
1. `judge_mode=original` with values from `originalMetrics`
2. `judge_mode=` + `judgeConfig.label()` (e.g. `"hardened"`) with values from `reEvaluatedMetrics`

Inline the CSV escaping pattern (it's `private static` on `ExperimentExporter`, can't be called):

```java
private static String escapeCsv(String value) {
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./mvnw test -Dtest="ReEvaluationExporterTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add arcmem-simulator/src/main/java/dev/arcmem/simulator/reevaluation/ReEvaluationExporter.java \
        arcmem-simulator/src/test/java/dev/arcmem/sim/reevaluation/ReEvaluationExporterTest.java
git commit -m "feat: add ReEvaluationExporter with long-format CSV for ANCOVA"
```

---

### Task 7: Create `ReEvaluationCli`

**Files:**
- Create: `arcmem-simulator/src/main/java/dev/arcmem/simulator/reevaluation/ReEvaluationCli.java`

- [ ] **Step 1: Write ReEvaluationCli**

Follow the `ExperimentMatrixCli` pattern exactly:

```java
@Component
@ConditionalOnProperty(name = "arc-mem.reevaluate.experiment-id")
public class ReEvaluationCli implements CommandLineRunner {

    private final ReEvaluationRunner runner;
    private final ReEvaluationExporter exporter;
    private final String experimentId;
    private final String outputDir;

    public ReEvaluationCli(
            ReEvaluationRunner runner,
            ReEvaluationExporter exporter,
            @Value("${arc-mem.reevaluate.experiment-id}") String experimentId,
            @Value("${arc-mem.reevaluate.output-dir:reevaluation-output}") String outputDir) {
        // ...
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. Log start
        // 2. Run re-evaluation with JudgeConfig.hardened()
        // 3. Export CSV to outputDir/{experimentId}-reevaluation.csv
        // 4. Export summary markdown to outputDir/{experimentId}-reevaluation-summary.md
        // 5. Log completion with output path
        // 6. System.exit(0)
    }
}
```

Usage: `./mvnw spring-boot:run -Darc-mem.reevaluate.experiment-id=exp-abc123`

- [ ] **Step 2: Compile**

```bash
./mvnw clean compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add arcmem-simulator/src/main/java/dev/arcmem/simulator/reevaluation/ReEvaluationCli.java
git commit -m "feat: add ReEvaluationCli for running re-evaluation from command line"
```

---

### Task 8: Full build and test verification

- [ ] **Step 1: Run full test suite**

```bash
./mvnw test
```

Expected: BUILD SUCCESS, all tests pass. Pay attention to:
- `DriftEvaluationTest` — updated to use `DriftReEvaluator`
- `ScoringServiceTest` — unchanged, should still pass
- `ReEvaluationExporterTest` — new test
- Any test that constructs `SimulationTurnExecutor` directly (search for `new SimulationTurnExecutor(`) — these now need the `DriftReEvaluator` parameter

- [ ] **Step 2: Fix any compilation or test failures**

The most likely breakage is tests that construct `SimulationTurnExecutor` with 9 args — they now need 10 (add `DriftReEvaluator`). Search for `new SimulationTurnExecutor(` across test files.

- [ ] **Step 3: Final commit if fixes were needed**

```bash
git add -A
git commit -m "fix: update tests for DriftReEvaluator extraction"
```
