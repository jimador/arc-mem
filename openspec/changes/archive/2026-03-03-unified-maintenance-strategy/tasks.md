# Implementation Tasks

## 1. Create MaintenanceMode enum + MaintenanceContext record + SweepResult record

- [x] 1.1 Create `MaintenanceMode` enum in `anchor/` with values REACTIVE, PROACTIVE, HYBRID
- [x] 1.2 Create `MaintenanceContext` record in `anchor/` with components: contextId (String), activeAnchors (List\<Anchor\>), turnNumber (int), metadata (@Nullable Map\<String, Object\>)
- [x] 1.3 Create `SweepResult` record in `anchor/` with components: anchorsAudited, anchorsRefreshed, anchorsPruned, anchorsValidated (int), duration (Duration), summary (String). Include `empty()` and `empty(String)` factory methods.

**Verification**: `./mvnw clean compile -DskipTests`

## 2. Create MaintenanceStrategy sealed interface

- [x] 2.1 Create `MaintenanceStrategy` sealed interface in `anchor/` permitting ReactiveMaintenanceStrategy, ProactiveMaintenanceStrategy, HybridMaintenanceStrategy
- [x] 2.2 Define three methods: `onTurnComplete(MaintenanceContext)`, `shouldRunSweep(MaintenanceContext) -> boolean`, `executeSweep(MaintenanceContext) -> SweepResult`
- [x] 2.3 Document sealed hierarchy, thread safety, error contract, and Sleeping LLM research attribution in Javadoc

**Verification**: `./mvnw clean compile -DskipTests`

## 3. Create ReactiveMaintenanceStrategy with unit test

- [x] 3.1 Create `ReactiveMaintenanceStrategy` as `final class` implementing `MaintenanceStrategy`. Constructor takes DecayPolicy + ReinforcementPolicy. `onTurnComplete` logs at DEBUG. `shouldRunSweep` returns false. `executeSweep` returns `SweepResult.empty()`.
- [x] 3.2 Add accessor methods `decayPolicy()` and `reinforcementPolicy()`
- [x] 3.3 Create unit test verifying: shouldRunSweep always false, executeSweep returns empty, policies accessible

**Verification**: `./mvnw test -Dtest=MaintenanceStrategyTest`

## 4. Create ProactiveMaintenanceStrategy stub + HybridMaintenanceStrategy

- [x] 4.1 Create `ProactiveMaintenanceStrategy` as `non-sealed class`. No constructor args. All methods no-op/stub with F07 deferred message.
- [x] 4.2 Create `HybridMaintenanceStrategy` as `non-sealed class`. Constructor takes ReactiveMaintenanceStrategy + ProactiveMaintenanceStrategy. Delegates onTurnComplete to reactive, shouldRunSweep/executeSweep to proactive.

**Verification**: `./mvnw clean compile -DskipTests`

## 5. Wire Spring config

- [x] 5.1 Add `MaintenanceConfig` record to `DiceAnchorsProperties` with `@DefaultValue("REACTIVE") MaintenanceMode mode`
- [x] 5.2 Add `@Bean @ConditionalOnMissingBean MaintenanceStrategy` to `AnchorConfiguration` with mode-based switch expression
- [x] 5.3 Add `dice-anchors.maintenance.mode: REACTIVE` to `application.yml`

**Verification**: `./mvnw clean compile -DskipTests`

## 6. Fix existing tests

- [x] 6.1 Update all test files that construct `DiceAnchorsProperties` directly to include the new `MaintenanceConfig` parameter (null)

**Verification**: `./mvnw test`

## Definition of Done

- [x] All compilation succeeds: `./mvnw clean compile -DskipTests`
- [x] All tests pass: `./mvnw test`
- [x] `MaintenanceStrategy` is a sealed interface with exactly three permitted implementations
- [x] `ReactiveMaintenanceStrategy` wraps `DecayPolicy` + `ReinforcementPolicy` with zero behavioral change
- [x] `ProactiveMaintenanceStrategy` is a no-op stub
- [x] `HybridMaintenanceStrategy` composes reactive + proactive via delegation
- [x] Default maintenance mode is REACTIVE (no behavioral change without opt-in)
- [x] `DecayPolicy` and `ReinforcementPolicy` remain as independent, unchanged Spring beans
- [x] No new dependencies added
