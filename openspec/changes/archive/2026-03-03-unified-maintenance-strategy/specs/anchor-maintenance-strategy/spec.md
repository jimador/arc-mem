# Anchor Maintenance Strategy Specification

## ADDED Requirements

### REQ-INTERFACE: MaintenanceStrategy sealed interface

`MaintenanceStrategy` MUST be a sealed interface in the `anchor/` package. The interface MUST permit exactly three implementations: `ReactiveMaintenanceStrategy`, `ProactiveMaintenanceStrategy`, and `HybridMaintenanceStrategy`.

#### Scenario: Sealed interface restricts implementations
- **WHEN** a class attempts to implement `MaintenanceStrategy` outside the permitted set
- **THEN** the compiler MUST reject the implementation

#### Scenario: All three permitted implementations exist
- **WHEN** the `anchor/` package is inspected
- **THEN** `ReactiveMaintenanceStrategy`, `ProactiveMaintenanceStrategy`, and `HybridMaintenanceStrategy` MUST each implement `MaintenanceStrategy`

### REQ-METHODS: Interface method contract

The `MaintenanceStrategy` interface MUST define three methods:
1. `void onTurnComplete(MaintenanceContext context)` -- reactive hook fired after each conversation or simulation turn.
2. `boolean shouldRunSweep(MaintenanceContext context)` -- returns `true` when a proactive sweep should be triggered.
3. `SweepResult executeSweep(MaintenanceContext context)` -- executes a proactive maintenance sweep and returns structured results.

All methods MUST accept a non-null `MaintenanceContext` parameter.

#### Scenario: onTurnComplete receives valid context
- **WHEN** `onTurnComplete` is called with a `MaintenanceContext` containing a contextId, activeAnchors list, and turnNumber
- **THEN** the method completes without throwing

#### Scenario: shouldRunSweep returns boolean
- **WHEN** `shouldRunSweep` is called with a valid `MaintenanceContext`
- **THEN** the method MUST return a `boolean` value

#### Scenario: executeSweep returns non-null SweepResult
- **WHEN** `executeSweep` is called with a valid `MaintenanceContext`
- **THEN** the method MUST return a non-null `SweepResult`

### REQ-REACTIVE: ReactiveMaintenanceStrategy wraps existing policies

`ReactiveMaintenanceStrategy` MUST accept `DecayPolicy` and `ReinforcementPolicy` via constructor injection. It MUST hold references to both policies for future scheduling coordination.

The `onTurnComplete` method MUST perform lightweight per-turn bookkeeping (logging). Actual decay and reinforcement remain invoked by `AnchorEngine` directly -- this strategy is a coordination layer, not a replacement for the engine's existing policy invocation.

#### Scenario: Constructor requires both policies
- **WHEN** `ReactiveMaintenanceStrategy` is constructed
- **THEN** it MUST accept a `DecayPolicy` and a `ReinforcementPolicy` as constructor parameters

#### Scenario: Policies are accessible
- **WHEN** a `ReactiveMaintenanceStrategy` instance exists
- **THEN** its `decayPolicy()` and `reinforcementPolicy()` accessor methods MUST return the injected policy instances

### REQ-REACTIVE-SWEEP: ReactiveMaintenanceStrategy never triggers sweeps

`ReactiveMaintenanceStrategy.shouldRunSweep()` MUST return `false` unconditionally. `ReactiveMaintenanceStrategy.executeSweep()` MUST return `SweepResult.empty()`.

#### Scenario: shouldRunSweep returns false
- **WHEN** `shouldRunSweep` is called on a `ReactiveMaintenanceStrategy` with any `MaintenanceContext`
- **THEN** the return value MUST be `false`

#### Scenario: executeSweep returns empty result
- **WHEN** `executeSweep` is called on a `ReactiveMaintenanceStrategy`
- **THEN** the return value MUST be `SweepResult.empty()` with all counters at zero

### REQ-PROACTIVE-STUB: ProactiveMaintenanceStrategy is a non-sealed stub

`ProactiveMaintenanceStrategy` MUST be a `non-sealed` class to permit future subclassing in F07. All methods MUST be no-ops in this initial implementation:
- `onTurnComplete` MUST be a no-op (proactive mode does not participate in per-turn hooks).
- `shouldRunSweep` MUST return `false` (sweeps are not yet implemented).
- `executeSweep` MUST return `SweepResult.empty()` with a summary indicating deferred implementation.

#### Scenario: Stub can be instantiated
- **WHEN** `ProactiveMaintenanceStrategy` is constructed with no arguments
- **THEN** instantiation MUST succeed without error

#### Scenario: All methods are safe no-ops
- **WHEN** any method on the stub is called
- **THEN** the method MUST NOT throw and MUST return a safe default value

### REQ-HYBRID: HybridMaintenanceStrategy composes reactive and proactive

`HybridMaintenanceStrategy` MUST accept a `ReactiveMaintenanceStrategy` and a `ProactiveMaintenanceStrategy` via constructor injection. Delegation semantics:
- `onTurnComplete` MUST delegate to the reactive strategy only.
- `shouldRunSweep` MUST delegate to the proactive strategy only.
- `executeSweep` MUST delegate to the proactive strategy only.

#### Scenario: onTurnComplete delegates to reactive
- **WHEN** `onTurnComplete` is called on a `HybridMaintenanceStrategy`
- **THEN** the reactive delegate's `onTurnComplete` MUST be invoked

#### Scenario: shouldRunSweep delegates to proactive
- **WHEN** `shouldRunSweep` is called on a `HybridMaintenanceStrategy`
- **THEN** the proactive delegate's `shouldRunSweep` MUST be invoked

#### Scenario: executeSweep delegates to proactive
- **WHEN** `executeSweep` is called on a `HybridMaintenanceStrategy`
- **THEN** the proactive delegate's `executeSweep` MUST be invoked
- **AND** the result MUST be the proactive delegate's return value

### REQ-THREAD-SAFETY: All implementations MUST be thread-safe

All `MaintenanceStrategy` implementations MUST be safe for concurrent access from multiple threads. Multiple simulation runs MAY share a single strategy instance.

`ReactiveMaintenanceStrategy` achieves thread safety through final fields and stateless methods. `ProactiveMaintenanceStrategy` achieves thread safety through absence of mutable state in the stub. `HybridMaintenanceStrategy` achieves thread safety through final delegate fields and delegating thread-safety responsibility to its components.

#### Scenario: Concurrent invocation from multiple threads
- **WHEN** multiple threads invoke methods on the same strategy instance concurrently
- **THEN** no data corruption, race conditions, or unexpected exceptions MUST occur

### REQ-ERROR: Implementations MUST NOT throw

Implementations MUST NOT throw exceptions from any method. On failure, implementations MUST:
- Log the error at an appropriate level.
- Return a safe default: no-op for `onTurnComplete`, `false` for `shouldRunSweep`, `SweepResult.empty()` for `executeSweep`.

#### Scenario: onTurnComplete failure returns safely
- **WHEN** an internal error occurs during `onTurnComplete`
- **THEN** the method MUST NOT propagate the exception and MUST log the failure

#### Scenario: executeSweep failure returns empty result
- **WHEN** an internal error occurs during `executeSweep`
- **THEN** the method MUST return `SweepResult.empty()` and MUST log the failure

### REQ-CONFIG: DiceAnchorsProperties includes MaintenanceConfig

`DiceAnchorsProperties` MUST include a `MaintenanceConfig` nested record with a `MaintenanceMode mode` field. The default mode MUST be `REACTIVE`.

#### Scenario: Default mode is REACTIVE
- **WHEN** the application starts without specifying `dice-anchors.maintenance.mode`
- **THEN** the effective maintenance mode MUST be `REACTIVE`

#### Scenario: Mode is configurable via properties
- **WHEN** `dice-anchors.maintenance.mode` is set to `HYBRID`
- **THEN** the effective maintenance mode MUST be `HYBRID`

### REQ-BEAN: AnchorConfiguration produces MaintenanceStrategy bean

`AnchorConfiguration` MUST define a `@Bean` method that produces a `MaintenanceStrategy` instance based on the configured `MaintenanceMode`:
- `REACTIVE` MUST produce a `ReactiveMaintenanceStrategy` wrapping the existing `DecayPolicy` and `ReinforcementPolicy` beans.
- `PROACTIVE` MUST produce a `ProactiveMaintenanceStrategy`.
- `HYBRID` MUST produce a `HybridMaintenanceStrategy` composing a `ReactiveMaintenanceStrategy` and a `ProactiveMaintenanceStrategy`.

The bean MUST be annotated with `@ConditionalOnMissingBean` to allow test overrides.

#### Scenario: REACTIVE mode produces ReactiveMaintenanceStrategy
- **WHEN** the configured mode is `REACTIVE`
- **THEN** the `MaintenanceStrategy` bean MUST be an instance of `ReactiveMaintenanceStrategy`

#### Scenario: PROACTIVE mode produces ProactiveMaintenanceStrategy
- **WHEN** the configured mode is `PROACTIVE`
- **THEN** the `MaintenanceStrategy` bean MUST be an instance of `ProactiveMaintenanceStrategy`

#### Scenario: HYBRID mode produces HybridMaintenanceStrategy
- **WHEN** the configured mode is `HYBRID`
- **THEN** the `MaintenanceStrategy` bean MUST be an instance of `HybridMaintenanceStrategy`

### REQ-COMPAT: DecayPolicy and ReinforcementPolicy remain backward compatible

The `DecayPolicy` and `ReinforcementPolicy` interfaces MUST NOT be modified by this change. Both MUST remain as independent Spring beans. Code that injects `DecayPolicy` or `ReinforcementPolicy` directly MUST continue to work without changes.

#### Scenario: Existing DecayPolicy injection works
- **WHEN** a component injects `DecayPolicy` via constructor
- **THEN** the injection MUST succeed and the policy MUST behave identically to pre-change behavior

#### Scenario: Existing ReinforcementPolicy injection works
- **WHEN** a component injects `ReinforcementPolicy` via constructor
- **THEN** the injection MUST succeed and the policy MUST behave identically to pre-change behavior

## Invariants

- **I1**: `MaintenanceStrategy` MUST be sealed and permit exactly `ReactiveMaintenanceStrategy`, `ProactiveMaintenanceStrategy`, and `HybridMaintenanceStrategy`.
- **I2**: The default maintenance mode MUST be `REACTIVE`, ensuring zero behavioral change for existing users and configurations.
- **I3**: `ReactiveMaintenanceStrategy.shouldRunSweep()` MUST always return `false`.
- **I4**: No implementation of `MaintenanceStrategy` SHALL throw from any method.
- **I5**: `DecayPolicy` and `ReinforcementPolicy` interfaces MUST remain unchanged and independently injectable.
- **I6**: All anchor invariants (A1-A4 per Article V of the constitution) MUST be preserved. The maintenance strategy coordinates scheduling; it MUST NOT modify rank clamping, budget enforcement, promotion rules, or authority transitions.
