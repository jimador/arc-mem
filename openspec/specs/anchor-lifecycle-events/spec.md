## ADDED Requirements

### Requirement: CompactionCompleted event

The system SHALL publish a `CompactionCompleted` event record after each completed compaction operation (whether successful or fallen-back). `CompactionCompleted` SHALL be a standalone record and SHALL NOT be added to the sealed `UnitLifecycleEvent` hierarchy, as compaction is a context-assembly concern rather than an memory unit state-machine concern.

`CompactionCompleted` SHALL contain the following fields:

| Field | Type | Description |
|-------|------|-------------|
| `contextId` | String | The simulation context for which compaction ran |
| `triggerReason` | String | The reason compaction was triggered (e.g., `"TOKEN_LIMIT_EXCEEDED"`, `"MANUAL"`) |
| `tokensBefore` | int | Estimated token count in the message history before compaction |
| `tokensAfter` | int | Estimated token count after the summary replaces history |
| `lossCount` | int | Number of protected content items that failed validation (CompactionLossEvents count) |
| `retryCount` | int | Number of LLM retry attempts made (0 means first attempt succeeded) |
| `fallbackUsed` | boolean | Whether the extractive fallback summary was used instead of an LLM-generated summary |
| `occurredAt` | Instant | The instant at which compaction completed |

`CompactionCompleted` SHALL be published via Spring's `ApplicationEventPublisher` after the compaction outcome is fully determined (summary stored or rollback completed). The event SHALL be published regardless of whether the compaction succeeded or fell back, but SHALL NOT be published when compaction is rolled back without producing any summary (i.e., full failure with rollback and no stored result).

Event publishing SHALL be gated by `arc-mem.compaction.events-enabled` (default `true`). When disabled, no `CompactionCompleted` events SHALL be published.

#### Scenario: Successful compaction publishes event

- **GIVEN** a compaction that succeeds with an LLM-generated summary on the first attempt
- **WHEN** the summary is stored and message history is cleared
- **THEN** a `CompactionCompleted` event SHALL be published with `retryCount = 0`, `fallbackUsed = false`, `lossCount` equal to the number of failed protected content items, and `tokensBefore > tokensAfter`

#### Scenario: Fallback compaction publishes event with fallbackUsed = true

- **GIVEN** all LLM retries are exhausted and the extractive fallback summary is used
- **WHEN** the fallback summary passes validation and is stored
- **THEN** a `CompactionCompleted` event SHALL be published with `fallbackUsed = true` and `retryCount` equal to `CompactionConfig.retryCount`

#### Scenario: Rolled-back compaction does NOT publish event

- **GIVEN** a compaction where even the fallback summary fails validation and rollback occurs
- **WHEN** message history is restored from the pre-compaction snapshot
- **THEN** no `CompactionCompleted` event SHALL be published

#### Scenario: Event includes accurate retry count

- **GIVEN** `CompactionConfig.retryCount` is 2
- **AND** the first LLM call fails, the second fails, and the third (second retry) succeeds
- **WHEN** the `CompactionCompleted` event is published
- **THEN** `retryCount` SHALL be 2

#### Scenario: Events disabled suppresses publishing

- **GIVEN** `arc-mem.compaction.events-enabled` is `false`
- **WHEN** a compaction completes successfully
- **THEN** no `CompactionCompleted` event SHALL be published via `ApplicationEventPublisher`

## Operator Invariants (F07)

### Requirement: InvariantViolation lifecycle event

The system SHALL add an `InvariantViolation` event type to the `UnitLifecycleEvent` sealed hierarchy. The event SHALL be published when an invariant is evaluated and found to be violated, regardless of whether the violation blocked the action or only produced a warning.

The `InvariantViolation` event SHALL include:

| Field | Type | Description |
|-------|------|-------------|
| `ruleId` | String | The ID of the invariant rule that was violated |
| `invariantType` | String | The type of invariant (e.g., `"UNIT_PROTECTED"`, `"AUTHORITY_FLOOR"`) |
| `constraintDescription` | String | Human-readable description of the violated constraint |
| `attemptedAction` | String | The lifecycle action that triggered the violation (e.g., `"EVICT"`, `"DEMOTE"`) |
| `targetUnitId` | String | The memory unit ID that was the target of the attempted action |
| `blocked` | boolean | Whether the action was blocked (`true` for MUST-strength) or only warned (`false` for SHOULD-strength) |
| `strength` | String | The invariant strength (`"MUST"` or `"SHOULD"`) |
| `contextId` | String | Inherited from `UnitLifecycleEvent` |
| `occurredAt` | Instant | Inherited from `UnitLifecycleEvent` |

The `UnitLifecycleEvent` sealed interface `permits` clause SHALL be updated to include `InvariantViolation`.

A static factory method `UnitLifecycleEvent.invariantViolation(...)` SHALL be provided, consistent with the existing factory methods (`promoted(...)`, `reinforced(...)`, etc.).

#### Scenario: MUST-strength violation publishes blocked event

- **GIVEN** a MUST-strength `UNIT_PROTECTED` invariant with `ruleId = "protect-A1"` for memory unit "A1"
- **WHEN** budget enforcement attempts to evict memory unit "A1"
- **THEN** an `InvariantViolation` event SHALL be published with:
  - `ruleId = "protect-A1"`
  - `invariantType = "UNIT_PROTECTED"`
  - `constraintDescription = "Memory unit A1 is protected from eviction"`
  - `attemptedAction = "EVICT"`
  - `targetUnitId = "A1"`
  - `blocked = true`
  - `strength = "MUST"`

#### Scenario: SHOULD-strength violation publishes warned event

- **GIVEN** a SHOULD-strength `MINIMUM_COUNT` invariant with `ruleId = "min-reliable"` requiring 3 RELIABLE memory units
- **WHEN** an eviction would drop the RELIABLE memory unit count to 2
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

- **GIVEN** `arc-mem.unit.lifecycle-events-enabled` is `false`
- **WHEN** an invariant violation occurs
- **THEN** no `InvariantViolation` event SHALL be published

### Requirement: InvariantViolation event listener logging

The existing `UnitLifecycleListener` SHALL handle `InvariantViolation` events with an `@EventListener` method. The listener SHALL:

1. Log at WARN level for MUST-strength violations: `"Invariant {} violated: {} — action {} BLOCKED for memory unit {}"`
2. Log at WARN level for SHOULD-strength violations: `"Invariant {} violated: {} — action {} WARNED for memory unit {}"`
3. Set OTEL span attributes when tracing is active (see observability spec)

#### Scenario: Blocked violation logged at WARN

- **GIVEN** a MUST-strength `InvariantViolation` event
- **WHEN** the `UnitLifecycleListener` handles the event
- **THEN** a WARN-level log SHALL be emitted containing the rule ID, constraint description, attempted action, and "BLOCKED"

#### Scenario: Warned violation logged at WARN

- **GIVEN** a SHOULD-strength `InvariantViolation` event
- **WHEN** the `UnitLifecycleListener` handles the event
- **THEN** a WARN-level log SHALL be emitted containing the rule ID, constraint description, attempted action, and "WARNED"
