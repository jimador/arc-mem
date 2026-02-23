## ADDED Requirements

### Requirement: CompactionCompleted event

The system SHALL publish a `CompactionCompleted` event record after each completed compaction operation (whether successful or fallen-back). `CompactionCompleted` SHALL be a standalone record and SHALL NOT be added to the sealed `AnchorLifecycleEvent` hierarchy, as compaction is a context-assembly concern rather than an anchor state-machine concern.

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

Event publishing SHALL be gated by `dice-anchors.compaction.events-enabled` (default `true`). When disabled, no `CompactionCompleted` events SHALL be published.

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

- **GIVEN** `dice-anchors.compaction.events-enabled` is `false`
- **WHEN** a compaction completes successfully
- **THEN** no `CompactionCompleted` event SHALL be published via `ApplicationEventPublisher`
