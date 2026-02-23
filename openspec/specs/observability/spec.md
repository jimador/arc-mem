## ADDED Requirements

### Requirement: OTEL span attributes for compaction events

Each `simulation.turn` span SHALL include the following compaction-related OTEL span attributes when a compaction operation occurs during that turn. These attributes SHALL be set as low-cardinality key-value pairs via the Micrometer Observation API on the `simulation.turn` span. When no compaction occurs during a turn, these attributes SHALL NOT be set.

| Attribute | Type | Description |
|-----------|------|-------------|
| `compaction.trigger_reason` | String | The reason compaction was triggered (e.g., `"TOKEN_LIMIT_EXCEEDED"`, `"MANUAL"`) |
| `compaction.tokens_before` | int | Estimated token count in message history before compaction |
| `compaction.tokens_after` | int | Estimated token count after the summary replaces history |
| `compaction.loss_count` | int | Number of protected content items that failed post-compaction validation |
| `compaction.retry_count` | int | Number of LLM retry attempts beyond the initial call (0 means first attempt succeeded) |
| `compaction.fallback_used` | boolean | Whether the extractive fallback summary was used instead of an LLM-generated summary |

Attribute values SHALL be sourced from the `CompactionCompleted` event or an equivalent in-process result record produced by `CompactedContextProvider.compact()`. The attributes SHALL reflect the final outcome of the compaction attempt, including fallback and retry resolution.

#### Scenario: Turn span includes compaction attributes when compaction occurs

- **GIVEN** a simulation turn where token count exceeds the compaction threshold
- **AND** compaction runs with `triggerReason = "TOKEN_LIMIT_EXCEEDED"`, `tokensBefore = 3200`, `tokensAfter = 800`, `lossCount = 1`, `retryCount = 0`, `fallbackUsed = false`
- **WHEN** the `simulation.turn` span is recorded
- **THEN** the span SHALL include:
  - `compaction.trigger_reason = "TOKEN_LIMIT_EXCEEDED"`
  - `compaction.tokens_before = 3200`
  - `compaction.tokens_after = 800`
  - `compaction.loss_count = 1`
  - `compaction.retry_count = 0`
  - `compaction.fallback_used = false`

#### Scenario: Turn span omits compaction attributes when no compaction occurs

- **GIVEN** a simulation turn where the token count remains below the compaction threshold
- **WHEN** the `simulation.turn` span is recorded
- **THEN** the span SHALL NOT include any `compaction.*` attributes

#### Scenario: Fallback usage recorded in span

- **GIVEN** a compaction turn where all LLM retries fail and the extractive fallback is used
- **AND** `retryCount = 2`, `fallbackUsed = true`
- **WHEN** the `simulation.turn` span is recorded
- **THEN** the span SHALL include `compaction.fallback_used = true` and `compaction.retry_count = 2`

#### Scenario: Perfect compaction with no loss

- **GIVEN** a compaction that produces a valid LLM summary covering all protected content items on the first attempt
- **AND** `lossCount = 0`, `retryCount = 0`, `fallbackUsed = false`
- **WHEN** the `simulation.turn` span is recorded
- **THEN** the span SHALL include `compaction.loss_count = 0`, `compaction.retry_count = 0`, `compaction.fallback_used = false`

## Operator Invariants (F07)

### Requirement: Invariant evaluation OTEL span attributes

Each lifecycle operation span that evaluates invariants SHALL include the following OTEL span attributes, set as low-cardinality key-value pairs via the Micrometer Observation API or direct OpenTelemetry Span API:

| Attribute | Type | Description |
|-----------|------|-------------|
| `invariant.checked_count` | int | Total number of invariants evaluated during the lifecycle operation |
| `invariant.violated_count` | int | Number of invariants that were violated (both MUST and SHOULD) |
| `invariant.blocked_action` | boolean | Whether any MUST-strength invariant blocked the lifecycle action |

These attributes SHALL be set on the active span after `InvariantEvaluator.evaluate()` returns, regardless of the evaluation outcome. When no invariants are registered, `invariant.checked_count` SHALL be `0`, `invariant.violated_count` SHALL be `0`, and `invariant.blocked_action` SHALL be `false`.

#### Scenario: Lifecycle operation with invariants evaluated

- **GIVEN** an active OTEL span during `AnchorEngine.demote()`
- **AND** 3 invariants are evaluated, 1 MUST-strength is violated, and 1 SHOULD-strength is violated
- **WHEN** the span attributes are recorded
- **THEN** the span SHALL include `invariant.checked_count = 3`, `invariant.violated_count = 2`, `invariant.blocked_action = true`

#### Scenario: Lifecycle operation with no violations

- **GIVEN** an active OTEL span during `AnchorEngine.archive()`
- **AND** 2 invariants are evaluated and none are violated
- **WHEN** the span attributes are recorded
- **THEN** the span SHALL include `invariant.checked_count = 2`, `invariant.violated_count = 0`, `invariant.blocked_action = false`

#### Scenario: No invariants registered

- **GIVEN** an active OTEL span during `AnchorEngine.promote()`
- **AND** no invariants are registered
- **WHEN** the span attributes are recorded
- **THEN** the span SHALL include `invariant.checked_count = 0`, `invariant.violated_count = 0`, `invariant.blocked_action = false`

#### Scenario: No active span

- **GIVEN** no active OTEL span (e.g., background decay job without tracing)
- **WHEN** invariant evaluation completes
- **THEN** the invariant evaluation result SHALL still be returned correctly but no span attributes SHALL be set

### Requirement: InvariantViolation event span attributes

When the `AnchorLifecycleListener` handles an `InvariantViolation` event, it SHALL set the following OTEL span attributes on the active span (if present):

| Attribute | Type | Description |
|-----------|------|-------------|
| `invariant.violation.rule_id` | String | The rule ID of the violated invariant |
| `invariant.violation.type` | String | The invariant type (e.g., `"ANCHOR_PROTECTED"`) |
| `invariant.violation.strength` | String | `"MUST"` or `"SHOULD"` |
| `invariant.violation.action` | String | The attempted lifecycle action (e.g., `"EVICT"`) |
| `invariant.violation.blocked` | boolean | Whether the action was blocked |

These attributes SHALL be set as low-cardinality key-value pairs. When multiple violations occur in a single span, the attributes SHALL reflect the last violation processed (last-write-wins semantics). The aggregate counts (`invariant.checked_count`, `invariant.violated_count`, `invariant.blocked_action`) from the evaluation-level attributes provide the complete picture.

#### Scenario: Blocked violation sets span attributes

- **GIVEN** an active OTEL span during eviction
- **WHEN** an `InvariantViolation` event is handled with `ruleId = "protect-A1"`, `strength = "MUST"`, `blocked = true`
- **THEN** the span SHALL include `invariant.violation.rule_id = "protect-A1"`, `invariant.violation.type = "ANCHOR_PROTECTED"`, `invariant.violation.strength = "MUST"`, `invariant.violation.action = "EVICT"`, `invariant.violation.blocked = true`

#### Scenario: No span during background operation

- **GIVEN** no active OTEL span
- **WHEN** an `InvariantViolation` event is handled
- **THEN** the event SHALL be logged at WARN level but no span attributes SHALL be set

### Requirement: Simulation turn span includes invariant summary

Each `simulation.turn` span SHALL include aggregated invariant attributes for all invariant evaluations that occurred during the turn:

| Attribute | Type | Description |
|-----------|------|-------------|
| `invariant.turn.total_checked` | int | Total invariants evaluated across all lifecycle operations in the turn |
| `invariant.turn.total_violated` | int | Total invariant violations across all lifecycle operations in the turn |
| `invariant.turn.actions_blocked` | int | Number of lifecycle actions that were blocked by MUST-strength invariants during the turn |

These attributes SHALL be set on the `simulation.turn` observation span after all turn-level lifecycle operations complete.

#### Scenario: Turn with invariant activity

- **GIVEN** a simulation turn that evaluates invariants during 2 lifecycle operations
- **AND** the first operation checks 3 invariants (1 violated, not blocked) and the second checks 2 invariants (1 violated, blocked)
- **WHEN** the `simulation.turn` span is recorded
- **THEN** the span SHALL include `invariant.turn.total_checked = 5`, `invariant.turn.total_violated = 2`, `invariant.turn.actions_blocked = 1`

#### Scenario: Turn with no invariant activity

- **GIVEN** a simulation turn where no lifecycle operations trigger invariant evaluation
- **WHEN** the `simulation.turn` span is recorded
- **THEN** the span SHALL include `invariant.turn.total_checked = 0`, `invariant.turn.total_violated = 0`, `invariant.turn.actions_blocked = 0`
