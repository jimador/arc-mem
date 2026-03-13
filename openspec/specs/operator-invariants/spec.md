## ADDED Requirements

### Requirement: Operator invariant rule model

The system SHALL define an `OperatorInvariant` record representing a declarative governance rule that constrains memory unit lifecycle operations. Each invariant SHALL contain the following fields:

| Field | Type | Description |
|-------|------|-------------|
| `ruleId` | String | Unique identifier for the invariant rule (e.g., `"protect-cursed-blade"`) |
| `type` | InvariantType | The category of constraint (see InvariantType enum below) |
| `scope` | InvariantScope | Whether the rule applies globally or to a specific context |
| `contextId` | String (nullable) | The context ID this rule applies to; null for global rules |
| `targetUnitId` | String (nullable) | Specific memory unit ID the rule targets; null for type-wide rules |
| `strength` | InvariantStrength | RFC 2119 enforcement level: `MUST` (blocks action) or `SHOULD` (logs warning) |
| `description` | String | Human-readable description of the invariant |
| `enabled` | boolean | Whether the invariant is currently active |

The `OperatorInvariant` record SHALL be immutable and use constructor validation to ensure `ruleId` is non-null and non-blank.

#### Scenario: Create a MUST-strength memory unit protection invariant

- **GIVEN** an operator wants to protect memory unit "A1" from eviction
- **WHEN** an `OperatorInvariant` is created with `type = UNIT_PROTECTED`, `targetUnitId = "A1"`, `strength = MUST`
- **THEN** the invariant is valid and can be registered with the `InvariantEvaluator`

#### Scenario: Create a global SHOULD-strength minimum count invariant

- **GIVEN** an operator wants at least 3 RELIABLE memory units in all contexts
- **WHEN** an `OperatorInvariant` is created with `type = MINIMUM_COUNT`, `scope = GLOBAL`, `strength = SHOULD`
- **THEN** the invariant is valid and applies to all contexts

### Requirement: InvariantType enum

The system SHALL define an `InvariantType` enum with the following values:

| Value | Description |
|-------|-------------|
| `UNIT_PROTECTED` | The specified memory unit MUST NOT be archived, evicted, or demoted. Requires `targetUnitId`. |
| `AUTHORITY_FLOOR` | The specified memory unit's authority MUST NOT drop below a configured level. Requires `targetUnitId` and a minimum authority parameter. |
| `MINIMUM_COUNT` | The context MUST maintain at least N active memory units at or above a specified authority level. |
| `CONTEXT_FROZEN` | No memory units in the specified context MAY be archived, evicted, or demoted. The context is read-only for destructive operations. |

Each `InvariantType` SHALL declare which lifecycle actions it constrains (archive, evict, demote, authority-change) via a `constrainedActions()` method returning `Set<LifecycleAction>`.

#### Scenario: UNIT_PROTECTED constrains archive, evict, and demote

- **GIVEN** an invariant of type `UNIT_PROTECTED`
- **WHEN** `constrainedActions()` is called
- **THEN** the result SHALL contain `ARCHIVE`, `EVICT`, and `DEMOTE`

#### Scenario: AUTHORITY_FLOOR constrains demote and authority-change

- **GIVEN** an invariant of type `AUTHORITY_FLOOR`
- **WHEN** `constrainedActions()` is called
- **THEN** the result SHALL contain `DEMOTE` and `AUTHORITY_CHANGE`

#### Scenario: CONTEXT_FROZEN constrains all destructive actions

- **GIVEN** an invariant of type `CONTEXT_FROZEN`
- **WHEN** `constrainedActions()` is called
- **THEN** the result SHALL contain `ARCHIVE`, `EVICT`, `DEMOTE`, and `AUTHORITY_CHANGE`

### Requirement: InvariantScope enum

The system SHALL define an `InvariantScope` enum with values:

- `GLOBAL` -- the invariant applies to all contexts
- `CONTEXT` -- the invariant applies only to the context specified in `contextId`

When evaluating invariants, global invariants SHALL be evaluated for every context. Context-scoped invariants SHALL only be evaluated when the lifecycle operation targets a matching `contextId`.

#### Scenario: Global invariant evaluated for any context

- **GIVEN** a global `MINIMUM_COUNT` invariant requiring at least 2 RELIABLE memory units
- **WHEN** an eviction is attempted in context "ctx-1"
- **THEN** the invariant SHALL be evaluated against context "ctx-1"

#### Scenario: Context-scoped invariant skipped for non-matching context

- **GIVEN** a context-scoped invariant for context "ctx-1"
- **WHEN** an eviction is attempted in context "ctx-2"
- **THEN** the invariant SHALL NOT be evaluated

### Requirement: InvariantStrength and RFC 2119 mapping

The system SHALL define an `InvariantStrength` enum with values:

- `MUST` -- violation blocks the lifecycle action and publishes an `InvariantViolation` event with `blocked = true`
- `SHOULD` -- violation logs a WARN-level message and publishes an `InvariantViolation` event with `blocked = false`, but the action proceeds

This mapping SHALL follow RFC 2119 semantics: `MUST` expresses an absolute requirement (the action SHALL NOT proceed), and `SHOULD` expresses a strong recommendation (the action SHOULD NOT proceed but MAY if the system determines it is appropriate).

#### Scenario: MUST-strength violation blocks eviction

- **GIVEN** an `UNIT_PROTECTED` invariant with `strength = MUST` targeting memory unit "A1"
- **WHEN** budget enforcement attempts to evict memory unit "A1"
- **THEN** the eviction SHALL be blocked and an `InvariantViolation` event SHALL be published with `blocked = true`

#### Scenario: SHOULD-strength violation allows eviction with warning

- **GIVEN** an `UNIT_PROTECTED` invariant with `strength = SHOULD` targeting memory unit "A1"
- **WHEN** budget enforcement attempts to evict memory unit "A1"
- **THEN** the eviction SHALL proceed, a WARN-level log SHALL be emitted, and an `InvariantViolation` event SHALL be published with `blocked = false`

### Requirement: InvariantEvaluator service

The system SHALL provide an `InvariantEvaluator` service (`@Service`) responsible for evaluating invariants against proposed lifecycle actions. The service SHALL expose the following methods:

- `evaluate(LifecycleAction action, String unitId, String contextId)` -- evaluates all applicable invariants for the given action and returns an `InvariantEvaluation` containing the list of violations (if any) and whether the action is blocked.
- `register(OperatorInvariant invariant)` -- registers a new invariant rule at runtime.
- `deregister(String ruleId)` -- removes an invariant rule by its ID.
- `activeInvariants()` -- returns all currently enabled invariants.
- `activeInvariants(String contextId)` -- returns invariants applicable to a specific context.
- `violationHistory()` -- returns all recorded violations since last clear.

The `InvariantEvaluation` record SHALL contain:
- `blocked` (boolean) -- true if any MUST-strength invariant was violated
- `violations` (List<InvariantViolation>) -- all violations found during evaluation
- `checkedCount` (int) -- total number of invariants evaluated

#### Scenario: No applicable invariants returns clean evaluation

- **GIVEN** no invariants are registered
- **WHEN** `evaluate(ARCHIVE, "A1", "ctx-1")` is called
- **THEN** the result SHALL have `blocked = false`, `violations` empty, and `checkedCount = 0`

#### Scenario: Multiple invariants evaluated with mixed results

- **GIVEN** a MUST-strength `UNIT_PROTECTED` invariant for memory unit "A1" and a SHOULD-strength `MINIMUM_COUNT` invariant
- **WHEN** `evaluate(EVICT, "A1", "ctx-1")` is called and both invariants are violated
- **THEN** the result SHALL have `blocked = true` (due to MUST violation), `violations` containing 2 entries, and `checkedCount = 2`

#### Scenario: Disabled invariant skipped

- **GIVEN** an `UNIT_PROTECTED` invariant with `enabled = false`
- **WHEN** `evaluate(ARCHIVE, "A1", "ctx-1")` is called
- **THEN** the disabled invariant SHALL NOT be evaluated and `checkedCount` SHALL not include it

### Requirement: LifecycleAction enum

The system SHALL define a `LifecycleAction` enum representing the lifecycle operations that invariants can constrain:

- `ARCHIVE` -- memory unit archival
- `EVICT` -- budget enforcement eviction
- `DEMOTE` -- authority demotion
- `AUTHORITY_CHANGE` -- any authority transition (promotion or demotion)

#### Scenario: LifecycleAction values cover all constrained operations

- **GIVEN** the `LifecycleAction` enum
- **WHEN** its values are enumerated
- **THEN** it SHALL contain exactly `ARCHIVE`, `EVICT`, `DEMOTE`, and `AUTHORITY_CHANGE`

### Requirement: YAML configuration for operator invariants

The system SHALL support defining operator invariants via YAML configuration under the `arc-mem.invariants` key. Each invariant definition SHALL map to an `OperatorInvariant` record.

The configuration schema SHALL be:

```yaml
dice-anchors:
  invariants:
    rules:
      - rule-id: protect-cursed-blade
        type: UNIT_PROTECTED
        scope: GLOBAL
        target-unit-id: "unit-uuid-123"
        strength: MUST
        description: "The cursed blade memory unit must never be removed"
        enabled: true
      - rule-id: min-reliable-count
        type: MINIMUM_COUNT
        scope: CONTEXT
        context-id: "sim-adventure-1"
        strength: SHOULD
        description: "Maintain at least 3 RELIABLE memory units"
        enabled: true
        parameters:
          min-count: 3
          min-authority: RELIABLE
```

The configuration SHALL be bound via `@ConfigurationProperties` on `ArcMemProperties` and validated at startup. Invalid invariant definitions (e.g., `UNIT_PROTECTED` without `targetUnitId`) SHALL cause a startup failure with a descriptive error.

#### Scenario: Valid YAML invariants loaded at startup

- **GIVEN** `application.yml` contains two valid invariant definitions
- **WHEN** the application starts
- **THEN** two `OperatorInvariant` instances SHALL be registered with the `InvariantEvaluator`

#### Scenario: Invalid invariant definition fails startup

- **GIVEN** an `UNIT_PROTECTED` invariant in YAML with no `target-unit-id`
- **WHEN** the application starts
- **THEN** startup SHALL fail with `IllegalStateException` containing "UNIT_PROTECTED requires targetUnitId"

#### Scenario: Simulation scenario YAML extends invariant definitions

- **GIVEN** a simulation scenario YAML file containing an `invariants` section
- **WHEN** the simulation starts
- **THEN** the scenario-defined invariants SHALL be registered with the `InvariantEvaluator` for the simulation context and deregistered when the simulation completes

### Requirement: Invariant evaluation lifecycle

The `InvariantEvaluator` SHALL evaluate invariants at the following lifecycle points:

1. **Before archive** -- evaluated when `ArcMemEngine.archive()` is called
2. **Before eviction** -- evaluated during budget enforcement in `ArcMemEngine.promote()` before each candidate memory unit is evicted
3. **Before demotion** -- evaluated when `ArcMemEngine.demote()` is called
4. **Before authority change** -- evaluated when any authority transition is about to occur (both promotion and demotion paths)

Evaluation SHALL occur before the state change is committed. If a MUST-strength invariant is violated, the state change SHALL NOT be committed and the lifecycle operation SHALL skip the violating action (e.g., skip evicting a protected memory unit and try the next candidate).

#### Scenario: Protected memory unit skipped during eviction

- **GIVEN** a MUST-strength `UNIT_PROTECTED` invariant for memory unit "A1" and the budget is full
- **WHEN** "A1" is the lowest-ranked non-pinned memory unit and a new memory unit is being promoted
- **THEN** "A1" SHALL be skipped and the next-lowest-ranked non-pinned, non-protected memory unit SHALL be evicted instead

#### Scenario: All eviction candidates protected

- **GIVEN** MUST-strength `UNIT_PROTECTED` invariants for all non-pinned memory units and the budget is full
- **WHEN** a new memory unit is promoted
- **THEN** the budget SHALL temporarily exceed the limit, a WARN-level log SHALL be emitted indicating invariant protection prevented eviction, and the memory unit SHALL still be promoted

#### Scenario: Invariant blocks demotion

- **GIVEN** an `AUTHORITY_FLOOR` invariant with `strength = MUST` requiring memory unit "A1" to remain at RELIABLE or above
- **WHEN** `demote("A1", DemotionReason.RANK_DECAY)` is called
- **THEN** the demotion SHALL be blocked, the memory unit SHALL remain at RELIABLE, and an `InvariantViolation` event SHALL be published
