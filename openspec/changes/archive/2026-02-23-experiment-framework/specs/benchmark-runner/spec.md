## MODIFIED Requirements

### Requirement: runBenchmark method signature

`BenchmarkRunner` SHALL expose a method `runBenchmark(ScenarioDefinition scenario, int runCount, boolean injectionEnabled, int tokenBudget, Consumer<BenchmarkProgress> onProgress)` returning a `BenchmarkReport`. The `runCount` parameter MUST be >= 2. The method SHALL throw `IllegalArgumentException` if `runCount < 2`.

Additionally, `BenchmarkRunner` SHALL expose an overloaded method `runBenchmark(ScenarioDefinition scenario, int runCount, boolean injectionEnabled, int tokenBudget, Consumer<BenchmarkProgress> onProgress, AblationCondition condition)` that accepts an `AblationCondition` to be applied before each run begins. The original method signature without `condition` SHALL remain and SHALL behave identically to calling the overloaded method with `AblationCondition.FULL_ANCHORS` (the default/control condition). This ensures full backward compatibility.

#### Scenario: Valid invocation without condition (backward compatible)

- **GIVEN** a loaded `ScenarioDefinition` and `runCount = 5`
- **WHEN** `runBenchmark(scenario, 5, true, 4000, callback)` is called (without condition parameter)
- **THEN** the method SHALL return a `BenchmarkReport` with `runCount = 5`
- **AND** the behavior SHALL be identical to calling with `AblationCondition.FULL_ANCHORS`

#### Scenario: Valid invocation with explicit condition

- **GIVEN** a loaded `ScenarioDefinition`, `runCount = 5`, and `AblationCondition.NO_ANCHORS`
- **WHEN** `runBenchmark(scenario, 5, true, 4000, callback, AblationCondition.NO_ANCHORS)` is called
- **THEN** the method SHALL return a `BenchmarkReport` with `runCount = 5`
- **AND** the `NO_ANCHORS` condition SHALL be applied before each run

#### Scenario: Invalid run count rejected

- **GIVEN** `runCount = 1`
- **WHEN** `runBenchmark(scenario, 1, true, 4000, callback)` is called
- **THEN** the method SHALL throw `IllegalArgumentException` with a message indicating the minimum run count is 2

### Requirement: Condition application before each run

When an `AblationCondition` is provided, `BenchmarkRunner` SHALL apply the condition to the seed anchors before each run begins. The condition SHALL be applied according to the `ablation-conditions` spec: overriding authority, rank, and mutation/promotion flags on seed anchor definitions before they are inserted into the simulation context. The condition SHALL be applied identically for every run within the benchmark.

#### Scenario: NO_ANCHORS condition disables injection per run

- **GIVEN** `AblationCondition.NO_ANCHORS` and a scenario with seed anchors
- **WHEN** `runBenchmark` executes each run
- **THEN** each run SHALL proceed with `injectionEnabled = false`, regardless of the `injectionEnabled` parameter value

#### Scenario: FLAT_AUTHORITY condition applied to seed anchors each run

- **GIVEN** `AblationCondition.FLAT_AUTHORITY` and a scenario with seed anchors at authorities PROVISIONAL, UNRELIABLE, RELIABLE
- **WHEN** `runBenchmark` executes each run
- **THEN** each run SHALL start with all seed anchors having authority `RELIABLE` and authority promotion disabled

#### Scenario: Condition does not leak between runs

- **GIVEN** `AblationCondition.NO_RANK_DIFFERENTIATION` with `rankOverride = 500`
- **WHEN** run 1 completes and run 2 begins
- **THEN** run 2's seed anchors SHALL be freshly configured from the original scenario definition plus the condition override
- **AND** no state from run 1 SHALL affect run 2's seed anchor configuration

### Requirement: Condition interaction with injectionEnabled parameter

When an `AblationCondition` specifies `injectionEnabled = false` (e.g., `NO_ANCHORS`), it SHALL override the `injectionEnabled` parameter passed to `runBenchmark`. When the condition specifies `injectionEnabled = true`, the `injectionEnabled` parameter SHALL be respected as-is. This ensures the condition has authoritative control over injection state.

#### Scenario: Condition overrides injectionEnabled parameter

- **GIVEN** `AblationCondition.NO_ANCHORS` (injectionEnabled=false) and method parameter `injectionEnabled = true`
- **WHEN** `runBenchmark` executes
- **THEN** injection SHALL be disabled (condition takes precedence)

#### Scenario: FULL_ANCHORS respects method parameter

- **GIVEN** `AblationCondition.FULL_ANCHORS` (injectionEnabled=true) and method parameter `injectionEnabled = true`
- **WHEN** `runBenchmark` executes
- **THEN** injection SHALL be enabled

## Invariants

- **BR1**: Each benchmark run SHALL use a unique, isolated `contextId`. No two runs within the same benchmark SHALL share a context.
- **BR2**: The `BenchmarkRunner` SHALL NOT modify `SimulationService` state or require changes to the `SimulationService` interface beyond what is needed for condition application.
- **BR3**: Progress callbacks SHALL be invoked on the calling thread in run-completion order. No out-of-order delivery.
- **BR4**: The original `runBenchmark` method signature (without `AblationCondition`) SHALL continue to function identically to its pre-modification behavior.
