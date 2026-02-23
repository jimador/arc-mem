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
