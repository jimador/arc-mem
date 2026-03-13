## ADDED Requirements

### Requirement: InvariantViolation lifecycle event

The system SHALL add an `InvariantViolation` event type to the `ContextUnitLifecycleEvent` sealed hierarchy. The event SHALL be published when an invariant is evaluated and found to be violated, regardless of whether the violation blocked the action or only produced a warning.

The `InvariantViolation` event SHALL include:

| Field | Type | Description |
|-------|------|-------------|
| `ruleId` | String | The ID of the invariant rule that was violated |
| `invariantType` | String | The type of invariant (e.g., `"UNIT_PROTECTED"`, `"AUTHORITY_FLOOR"`) |
| `constraintDescription` | String | Human-readable description of the violated constraint |
| `attemptedAction` | String | The lifecycle action that triggered the violation (e.g., `"EVICT"`, `"DEMOTE"`) |
| `targetUnitId` | String | The context unit ID that was the target of the attempted action |
| `blocked` | boolean | Whether the action was blocked (`true` for MUST-strength) or only warned (`false` for SHOULD-strength) |
| `strength` | String | The invariant strength (`"MUST"` or `"SHOULD"`) |
| `contextId` | String | Inherited from `ContextUnitLifecycleEvent` |
| `occurredAt` | Instant | Inherited from `ContextUnitLifecycleEvent` |

The `ContextUnitLifecycleEvent` sealed interface `permits` clause SHALL be updated to include `InvariantViolation`.

A static factory method `ContextUnitLifecycleEvent.invariantViolation(...)` SHALL be provided, consistent with the existing factory methods (`promoted(...)`, `reinforced(...)`, etc.).

#### Scenario: MUST-strength violation publishes blocked event

- **GIVEN** a MUST-strength `UNIT_PROTECTED` invariant with `ruleId = "protect-A1"` for context unit "A1"
- **WHEN** budget enforcement attempts to evict context unit "A1"
- **THEN** an `InvariantViolation` event SHALL be published with:
  - `ruleId = "protect-A1"`
  - `invariantType = "UNIT_PROTECTED"`
  - `constraintDescription = "Context Unit A1 is protected from eviction"`
  - `attemptedAction = "EVICT"`
  - `targetUnitId = "A1"`
  - `blocked = true`
  - `strength = "MUST"`

#### Scenario: SHOULD-strength violation publishes warned event

- **GIVEN** a SHOULD-strength `MINIMUM_COUNT` invariant with `ruleId = "min-reliable"` requiring 3 RELIABLE context units
- **WHEN** an eviction would drop the RELIABLE context unit count to 2
- **THEN** an `InvariantViolation` event SHALL be published with:
  - `ruleId = "min-reliable"`
  - `invariantType = "MINIMUM_COUNT"`
  - `attemptedAction = "EVICT"`
  - `blocked = false`
  - `strength = "SHOULD"`

#### Scenario: Multiple violations publish multiple events

- **GIVEN** two invariants are violated by a single demotion attempt (one MUST, one SHOULD)
- **WHEN** `ArcMemEngine.demote()` evaluates invariants
- **THEN** two `InvariantViolation` events SHALL be published -- one for each violated invariant

#### Scenario: InvariantViolation event gated by lifecycle events config

- **GIVEN** `context units.context unit.lifecycle-events-enabled` is `false`
- **WHEN** an invariant violation occurs
- **THEN** no `InvariantViolation` event SHALL be published

### Requirement: InvariantViolation event listener logging

The existing `ContextUnitLifecycleListener` SHALL handle `InvariantViolation` events with an `@EventListener` method. The listener SHALL:

1. Log at WARN level for MUST-strength violations: `"Invariant {} violated: {} — action {} BLOCKED for context unit {}"`
2. Log at WARN level for SHOULD-strength violations: `"Invariant {} violated: {} — action {} WARNED for context unit {}"`
3. Set OTEL span attributes when tracing is active (see observability spec)

#### Scenario: Blocked violation logged at WARN

- **GIVEN** a MUST-strength `InvariantViolation` event
- **WHEN** the `ContextUnitLifecycleListener` handles the event
- **THEN** a WARN-level log SHALL be emitted containing the rule ID, constraint description, attempted action, and "BLOCKED"

#### Scenario: Warned violation logged at WARN

- **GIVEN** a SHOULD-strength `InvariantViolation` event
- **WHEN** the `ContextUnitLifecycleListener` handles the event
- **THEN** a WARN-level log SHALL be emitted containing the rule ID, constraint description, attempted action, and "WARNED"
