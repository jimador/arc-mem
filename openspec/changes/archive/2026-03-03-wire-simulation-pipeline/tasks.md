# Wire Simulation Pipeline -- Tasks

## Task Group 1: Records and Facades (simple -- haiku-appropriate)

- [x] **T1.1**: Create `ComplianceSnapshot` record in `sim/engine/`
  - Fields: `violationCount`, `suggestedAction`, `wouldHaveRetried`, `validationMs`
  - Static factory: `none()` returning zeroed instance
  - Verification: compile, unit test for `none()` factory

- [x] **T1.2**: Create `SweepSnapshot` record in `sim/engine/`
  - Fields: `executed`, `summary`
  - Static factory: `none()` returning `new SweepSnapshot(false, "")`
  - Verification: compile, unit test for `none()` factory

- [x] **T1.3**: Extend `ContextTrace` record with new fields
  - Add `complianceSnapshot` (ComplianceSnapshot), `injectionPatternsDetected` (int), `sweepSnapshot` (SweepSnapshot) to canonical constructor
  - Update all 3 convenience constructors to default: `ComplianceSnapshot.none()`, `0`, `SweepSnapshot.none()`
  - Verification: all existing tests compile and pass without changes (convenience constructors provide defaults)

- [x] **T1.4**: Create `SimulationTurnServices` record in `sim/engine/`
  - Components: `extractionService`, `maintenanceStrategy`, `complianceEnforcer`, `pressureGauge`, `injectionEnforcer`
  - Verification: compile

## Task Group 2: LoggingPromptInjectionEnforcer (simple -- haiku-appropriate)

- [x] **T2.1**: Create `LoggingPromptInjectionEnforcer` in `sim/engine/`
  - `@Component` annotation
  - Pre-compiled `List<Pattern>` of 8 injection-signature regexes (see design doc)
  - `int scan(String playerMessage)` method returning match count
  - Log each detected pattern at INFO level
  - Thread-safe (stateless, final compiled patterns)
  - Verification: unit test with positive/negative cases

## Task Group 3: ProactiveMaintenanceStrategy Pressure Override (simple -- haiku-appropriate)

- [x] **T3.1**: Modify `ProactiveMaintenanceStrategy.shouldRunSweep()` to support pressure override
  - Check `context.metadata()` for key `"pressureOverride"` with value `Boolean.TRUE`
  - When present: bypass turn-interval guard, check only that pressure >= lightThreshold
  - Handle null metadata gracefully (treat as empty map)
  - Verification: unit test with pressure override metadata, null metadata, normal path

## Task Group 4: SimulationTurnExecutor Refactoring (complex -- sonnet-needed)

- [x] **T4.1**: Refactor SimulationTurnExecutor constructor to use `SimulationTurnServices`
  - Replace `SimulationExtractionService` parameter with `SimulationTurnServices`
  - Extract `extractionService` from facade; hold facade reference for other services
  - Constructor becomes: `ChatModelHolder, AnchorEngine, AnchorRepository, DiceAnchorsProperties, CompliancePolicy, TokenCounter, RelevanceScorer, @Nullable TieredAnchorRepository, SimulationTurnServices`
  - Verification: compile

- [x] **T4.2**: Wire injection scan into turn execution
  - In `executeTurn()` and `executeTurnFullParallel()`: call `LoggingPromptInjectionEnforcer.scan(playerMessage)` before LLM call
  - Capture count for ContextTrace
  - Wrap in try-catch; default to 0 on error
  - `LoggingPromptInjectionEnforcer` added as 5th component on `SimulationTurnServices`
  - Verification: unit test verifying scan is called and count appears on ContextTrace

- [x] **T4.3**: Wire compliance enforcement into turn execution
  - After `callLlm()`, before drift evaluation: call `ComplianceEnforcer.enforce(ComplianceContext)`
  - Build `ComplianceContext` with DM response, injected anchors, policy from config
  - Log violations at WARN level
  - Capture `ComplianceSnapshot` from result
  - Wrap in try-catch; default to `ComplianceSnapshot.none()` on error
  - Skip when `injectionEnabled = false`
  - Verification: unit test verifying enforcement is called and snapshot appears on ContextTrace

- [x] **T4.4**: Wire maintenance strategy into `buildResult()`
  - After reinforcement loop: build `MaintenanceContext` and call `onTurnComplete()`
  - Compute pressure via `MemoryPressureGauge`; set `"pressureOverride"` in metadata when `pressure.total() >= config.softPrunePressureThreshold()`
  - Call `shouldRunSweep()`; if true, call `executeSweep()` and capture `SweepSnapshot`
  - Wrap all three calls in try-catch; default to `SweepSnapshot.none()` on error
  - Verification: unit test verifying maintenance strategy is invoked after reinforcement

- [x] **T4.5**: Update ContextTrace enrichment in `buildResult()`
  - Pass `ComplianceSnapshot`, `injectionPatternsDetected`, and `SweepSnapshot` through to the enriched ContextTrace
  - Ensure both parallel and sequential paths produce enriched traces
  - Verification: unit test verifying all three new fields appear on final ContextTrace

## Task Group 5: Bean Wiring (simple -- haiku-appropriate)

- [x] **T5.1**: Create `SimulationTurnServices` bean
  - Added `@Bean` method in `SimulationConfiguration`
  - Wires: `SimulationExtractionService`, `MaintenanceStrategy`, `ComplianceEnforcer`, `MemoryPressureGauge`, `LoggingPromptInjectionEnforcer`
  - Verification: application context starts without errors

- [x] **T5.2**: Register `LoggingPromptInjectionEnforcer` as component
  - `@Component` annotation present in `sim/engine/` package (scanned by Spring Boot)
  - Verification: inject in a test context

## Task Group 6: Test Updates (complex -- sonnet-needed)

- [x] **T6.1**: Update `SimulationTurnExecutorPipelineTest`
  - Change executor construction to pass `SimulationTurnServices` instead of `SimulationExtractionService`
  - Real `ReactiveMaintenanceStrategy` instance (sealed interface cannot be mocked); mock `ComplianceEnforcer`, `MemoryPressureGauge`
  - Existing assertions continue to pass
  - Verification: all existing tests in this file pass

- [x] **T6.2**: Update `SimulationTurnExecutorParallelTest`
  - Same constructor change as T6.1
  - Parallel execution path works with facade
  - Verification: all existing tests in this file pass

- [x] **T6.3**: Update `SimulationParallelismBenchmarkTest`
  - Same constructor change
  - Verification: all existing tests in this file pass

- [x] **T6.4**: Update `SimulationServiceCleanupTest`
  - Did not construct `SimulationTurnExecutor` directly; no changes needed
  - Verification: all existing tests in this file pass

- [x] **T6.5**: Add integration tests for new pipeline wiring
  - Test: compliance violations captured on ContextTrace when enforcer detects violation
  - Test: maintenance sweep fires when `AlwaysSweepStrategy` triggers (inner class extending `ProactiveMaintenanceStrategy`)
  - Test: injection scan count appears on ContextTrace
  - Test: compliance enforcer exception swallowed, defaults to `ComplianceSnapshot.none()`
  - Verification: new tests pass

## Task Group 7: Verification (simple -- haiku-appropriate)

- [x] **T7.1**: Run full test suite
  - `./mvnw test`
  - 1015 tests pass, 0 failures, 0 errors
  - Verification: BUILD SUCCESS

- [x] **T7.2**: Compile check
  - `./mvnw clean compile -DskipTests`
  - Zero compilation errors
  - Verification: BUILD SUCCESS

## Dependency Order

```
T1.1, T1.2 → T1.3 → T1.4 → T4.1
T2.1 (independent)
T3.1 (independent)
T4.1 → T4.2, T4.3, T4.4, T4.5
T4.1 → T5.1
T2.1 → T5.2
T4.1 → T6.1, T6.2, T6.3, T6.4
T4.5 + T6.1..T6.4 → T6.5
T6.5 → T7.1, T7.2
```

## Complexity Summary

| Complexity | Tasks |
|-----------|-------|
| Simple (haiku) | T1.1, T1.2, T1.3, T1.4, T2.1, T3.1, T5.1, T5.2, T7.1, T7.2 |
| Complex (sonnet) | T4.1, T4.2, T4.3, T4.4, T4.5, T6.1, T6.2, T6.3, T6.4, T6.5 |
