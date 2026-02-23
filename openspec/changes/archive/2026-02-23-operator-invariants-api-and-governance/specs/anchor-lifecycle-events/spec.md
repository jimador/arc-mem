## ADDED Requirements

### Requirement: InvariantViolation lifecycle event

The system SHALL add an `InvariantViolation` event type to the `AnchorLifecycleEvent` sealed hierarchy. The event SHALL be published when an invariant is evaluated and found to be violated, regardless of whether the violation blocked the action or only produced a warning.

The `InvariantViolation` event SHALL include:

| Field | Type | Description |
|-------|------|-------------|
| `ruleId` | String | The ID of the invariant rule that was violated |
| `invariantType` | String | The type of invariant (e.g., `"ANCHOR_PROTECTED"`, `"AUTHORITY_FLOOR"`) |
| `constraintDescription` | String | Human-readable description of the violated constraint |
| `attemptedAction` | String | The lifecycle action that triggered the violation (e.g., `"EVICT"`, `"DEMOTE"`) |
| `targetAnchorId` | String | The anchor ID that was the target of the attempted action |
| `blocked` | boolean | Whether the action was blocked (`true` for MUST-strength) or only warned (`false` for SHOULD-strength) |
| `strength` | String | The invariant strength (`"MUST"` or `"SHOULD"`) |
| `contextId` | String | Inherited from `AnchorLifecycleEvent` |
| `occurredAt` | Instant | Inherited from `AnchorLifecycleEvent` |

The `AnchorLifecycleEvent` sealed interface `permits` clause SHALL be updated to include `InvariantViolation`.

A static factory method `AnchorLifecycleEvent.invariantViolation(...)` SHALL be provided, consistent with the existing factory methods (`promoted(...)`, `reinforced(...)`, etc.).

#### Scenario: MUST-strength violation publishes blocked event

- **GIVEN** a MUST-strength `ANCHOR_PROTECTED` invariant with `ruleId = "protect-A1"` for anchor "A1"
- **WHEN** budget enforcement attempts to evict anchor "A1"
- **THEN** an `InvariantViolation` event SHALL be published with:
  - `ruleId = "protect-A1"`
  - `invariantType = "ANCHOR_PROTECTED"`
  - `constraintDescription = "Anchor A1 is protected from eviction"`
  - `attemptedAction = "EVICT"`
  - `targetAnchorId = "A1"`
  - `blocked = true`
  - `strength = "MUST"`

#### Scenario: SHOULD-strength violation publishes warned event

- **GIVEN** a SHOULD-strength `MINIMUM_COUNT` invariant with `ruleId = "min-reliable"` requiring 3 RELIABLE anchors
- **WHEN** an eviction would drop the RELIABLE anchor count to 2
- **THEN** an `InvariantViolation` event SHALL be published with:
  - `ruleId = "min-reliable"`
  - `invariantType = "MINIMUM_COUNT"`
  - `attemptedAction = "EVICT"`
  - `blocked = false`
  - `strength = "SHOULD"`

#### Scenario: Multiple violations publish multiple events

- **GIVEN** two invariants are violated by a single demotion attempt (one MUST, one SHOULD)
- **WHEN** `AnchorEngine.demote()` evaluates invariants
- **THEN** two `InvariantViolation` events SHALL be published -- one for each violated invariant

#### Scenario: InvariantViolation event gated by lifecycle events config

- **GIVEN** `dice-anchors.anchor.lifecycle-events-enabled` is `false`
- **WHEN** an invariant violation occurs
- **THEN** no `InvariantViolation` event SHALL be published

### Requirement: InvariantViolation event listener logging

The existing `AnchorLifecycleListener` SHALL handle `InvariantViolation` events with an `@EventListener` method. The listener SHALL:

1. Log at WARN level for MUST-strength violations: `"Invariant {} violated: {} — action {} BLOCKED for anchor {}"`
2. Log at WARN level for SHOULD-strength violations: `"Invariant {} violated: {} — action {} WARNED for anchor {}"`
3. Set OTEL span attributes when tracing is active (see observability spec)

#### Scenario: Blocked violation logged at WARN

- **GIVEN** a MUST-strength `InvariantViolation` event
- **WHEN** the `AnchorLifecycleListener` handles the event
- **THEN** a WARN-level log SHALL be emitted containing the rule ID, constraint description, attempted action, and "BLOCKED"

#### Scenario: Warned violation logged at WARN

- **GIVEN** a SHOULD-strength `InvariantViolation` event
- **WHEN** the `AnchorLifecycleListener` handles the event
- **THEN** a WARN-level log SHALL be emitted containing the rule ID, constraint description, attempted action, and "WARNED"
