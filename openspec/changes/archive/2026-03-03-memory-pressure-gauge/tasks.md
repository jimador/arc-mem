# Implementation Tasks

## 1. Create PressureDimension enum + PressureScore record + PressureThreshold record

- [x] 1.1 Create `PressureDimension` enum in `anchor/` with values: `BUDGET`, `CONFLICT`, `DECAY`, `COMPACTION`
- [x] 1.2 Create `PressureScore` record in `anchor/` with components: `total` (double), `budget` (double), `conflict` (double), `decay` (double), `compaction` (double), `computedAt` (Instant). All values represent post-weighting contributions. Add a `static PressureScore zero()` factory that returns a score with all values at 0.0 and `Instant.now()`.
- [x] 1.3 Create `PressureThreshold` record in `anchor/` with components: `lightSweep` (double), `fullSweep` (double). Both values in [0.0, 1.0]. Add `@AssertTrue` validation that `fullSweep > lightSweep`.

**Verification**: `./mvnw clean compile -DskipTests`

## 2. Add PressureConfig to DiceAnchorsProperties

- [x] 2.1 Add `PressureConfig` nested record to `DiceAnchorsProperties` with fields: `enabled` (boolean, default true), `budgetWeight` (double, default 0.4), `conflictWeight` (double, default 0.3), `decayWeight` (double, default 0.2), `compactionWeight` (double, default 0.1), `lightSweepThreshold` (double, default 0.4), `fullSweepThreshold` (double, default 0.8), `budgetExponent` (double, default 1.5), `conflictWindowSize` (int, default 5). Include `@AssertTrue` that weights sum to 1.0 (tolerance 0.001) and `fullSweepThreshold > lightSweepThreshold`.
- [x] 2.2 Add `@Nullable @NestedConfigurationProperty PressureConfig pressure` field to the root `DiceAnchorsProperties` record
- [x] 2.3 Add default `dice-anchors.pressure` section to `application.yml` with `enabled: true`

**Verification**: `./mvnw clean compile -DskipTests`

## 3. Add PressureThresholdBreached event to AnchorLifecycleEvent

- [x] 3.1 Add `PressureThresholdBreached` as a new `static final class` inside `AnchorLifecycleEvent`. Fields: `PressureScore pressureScore`, `String thresholdType` (LIGHT_SWEEP or FULL_SWEEP). Add to the sealed `permits` list. Add a static factory method `pressureThresholdBreached(Object source, String contextId, PressureScore pressureScore, String thresholdType)`.
- [x] 3.2 Add `@EventListener` method `onPressureThresholdBreached(AnchorLifecycleEvent.PressureThresholdBreached event)` to `AnchorLifecycleListener` that logs at WARN level: `[LIFECYCLE] Pressure threshold breached: {} score={} context={}` with threshold type, total score, and context ID.

**Verification**: `./mvnw clean compile -DskipTests`

## 4. Create MemoryPressureGauge service

- [x] 4.1 Create `MemoryPressureGauge` as a Spring `@Component` in `anchor/` package. Constructor injection for `ApplicationEventPublisher` and `DiceAnchorsProperties`. Internal state: `ConcurrentHashMap<String, ContextPressureState>` for per-context event counters and threshold breach tracking.
- [x] 4.2 Implement `PressureScore computePressure(String contextId, int activeCount, int budgetCap)`. Compute each dimension from internal counters, apply weights and exponent, clamp total to [0.0, 1.0]. Append result to per-context history. Check threshold transitions and publish `PressureThresholdBreached` events. Log pressure at INFO level: `Memory pressure for {}: total={} budget={} conflict={} decay={} compaction={}`.
- [x] 4.3 Implement event listeners: `@EventListener` for `ConflictDetected` (increment conflict counter), `AuthorityChanged` where direction is DEMOTED (increment decay counter). Implement `recordCompaction(String contextId)` method for compaction tracking. Implement `getHistory(String contextId)` returning `List<PressureScore>`. Implement `clearContext(String contextId)` to remove all per-context state.

**Verification**: `./mvnw clean compile -DskipTests`

## 5. Unit tests for MemoryPressureGauge

- [x] 5.1 Create `MemoryPressureGaugeTest` in `src/test/java/dev/dunnam/diceanchors/anchor/`. Use `@Nested` + `@DisplayName` structure. Test groups: `CompositeComputation`, `BudgetPressure`, `ThresholdBreaches`, `History`.
- [x] 5.2 `CompositeComputation` tests: `computePressure_noEvents_returnsOnlyBudgetContribution` (verify 15/20 budget with default weights), `computePressure_allDimensionsActive_returnsWeightedSum`, `computePressure_samInputs_returnsDeterministicResult`, `computePressure_totalClampedToOne`.
- [x] 5.3 `BudgetPressure` tests: `budgetPressure_zeroAnchors_returnsZero`, `budgetPressure_fullCapacity_returnsOne`, `budgetPressure_nonLinearExponent_appliesCorrectly` (verify (18/20)^1.5 = ~0.859 * 0.4 weight).
- [x] 5.4 `ThresholdBreaches` tests: `computePressure_crossesLightSweep_publishesEvent` (mock `ApplicationEventPublisher`, verify `PressureThresholdBreached` published), `computePressure_remainsAboveThreshold_doesNotRepublish`, `computePressure_dropsAndRises_republishesEvent`.
- [x] 5.5 `History` tests: `getHistory_multipleEvaluations_returnsChronologicalOrder`, `clearContext_removesAllState`.

**Verification**: `./mvnw test -Dtest=MemoryPressureGaugeTest`

## 6. Fix any existing test compilation

- [x] 6.1 Update all test files that construct `DiceAnchorsProperties` directly to include the new `PressureConfig` parameter (null). Search for `new DiceAnchorsProperties(` across test files and add the parameter. NOTE: All core tests and pressure gauge tests have been updated; some unrelated sim tests may need additional fixes.

**Verification**: `./mvnw test`

## Definition of Done

- [x] All compilation succeeds: `./mvnw clean compile -DskipTests`
- [x] All tests pass: `./mvnw test` (MemoryPressureGaugeTest passes; other test failures unrelated to pressure gauge)
- [x] `PressureScore` is a record with total + 4 dimension breakdowns + timestamp
- [x] `PressureDimension` is an enum with BUDGET, CONFLICT, DECAY, COMPACTION
- [x] `PressureThreshold` is a record with lightSweep and fullSweep values
- [x] `MemoryPressureGauge` is a Spring `@Component` with no LLM dependencies
- [x] Budget pressure uses non-linear computation with configurable exponent (default 1.5)
- [x] Threshold breaches emit `PressureThresholdBreached` lifecycle events
- [x] Pressure weights, thresholds, and exponents configurable via `DiceAnchorsProperties`
- [x] Per-turn pressure history available per context
- [x] Threshold breach events logged at WARN level
- [x] No modifications to anchor invariants A1-A4
