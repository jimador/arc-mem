## ADDED Requirements

### Requirement: Message backup and rollback

`CompactedContextProvider.compact()` SHALL snapshot the full message history immediately before any compaction operation begins. The snapshot SHALL be stored in-memory for the duration of the compaction attempt. If the compaction fails — due to LLM error, validation failure, or atomicity guard rejection — the message history SHALL be fully restored from the snapshot. The restored state SHALL be byte-for-byte identical to the pre-compaction state.

#### Scenario: LLM failure triggers rollback

- **GIVEN** a `CompactedContextProvider` holding 24 messages
- **WHEN** `SimSummaryGenerator` throws an exception on all retry attempts
- **THEN** the message history SHALL be restored to the original 24 messages
- **AND** no messages SHALL be cleared from the context

#### Scenario: Validation failure triggers rollback

- **GIVEN** a compaction summary that fails the quality threshold check
- **WHEN** the atomicity guard rejects the summary
- **THEN** the message history SHALL be restored from the pre-compaction snapshot
- **AND** the context SHALL contain the same messages as before compaction was attempted

#### Scenario: Successful compaction discards snapshot

- **GIVEN** a compaction that produces a valid summary passing all validation checks
- **WHEN** message history is cleared and the summary is stored
- **THEN** the pre-compaction snapshot SHALL be discarded and not retained in memory

### Requirement: LLM retry with exponential backoff

`SimSummaryGenerator` SHALL retry failed LLM calls with a configurable retry count (default 2, meaning up to 3 total attempts) and exponential backoff between attempts. The initial backoff duration SHALL be 1 second, doubling on each subsequent retry (1s, 2s). The retry count and initial backoff duration SHALL be configurable via `CompactionConfig`. All retry attempts SHALL be logged at WARN level with the attempt number and exception message.

#### Scenario: First LLM call fails, second succeeds

- **GIVEN** `CompactionConfig.retryCount` is 2
- **AND** the first LLM call throws a runtime exception
- **WHEN** `SimSummaryGenerator` retries after the backoff interval
- **THEN** the second attempt succeeds and its result is returned
- **AND** a WARN log entry SHALL record the first failure with attempt number

#### Scenario: All retries exhausted

- **GIVEN** `CompactionConfig.retryCount` is 2
- **AND** all 3 LLM attempts (initial + 2 retries) throw exceptions
- **WHEN** the final retry is exhausted
- **THEN** `SimSummaryGenerator` SHALL fall through to the extractive fallback path
- **AND** no exception SHALL be propagated to the caller

#### Scenario: Custom retry count from config

- **GIVEN** `CompactionConfig.retryCount` is 0
- **WHEN** the first LLM call fails
- **THEN** no retries SHALL occur and the fallback path SHALL be entered immediately

### Requirement: Extractive fallback summary

When all LLM retry attempts fail, the system SHALL generate a deterministic extractive fallback summary. The fallback summary SHALL be constructed by concatenating the text of all protected content items, sorted by priority descending (highest priority first), separated by a space. The fallback summary SHALL be used in place of the LLM-generated summary for all downstream processing including validation and history replacement.

#### Scenario: Fallback constructs summary from protected content

- **GIVEN** all LLM retries have failed
- **AND** protected content contains two items: priority 100 with text "Baron Krell rules the Northern Province" and priority 50 with text "The sword is cursed"
- **WHEN** the extractive fallback is generated
- **THEN** the fallback summary SHALL be "Baron Krell rules the Northern Province The sword is cursed" (highest priority first)

#### Scenario: Empty protected content yields empty fallback

- **GIVEN** all LLM retries have failed
- **AND** no protected content items exist
- **WHEN** the extractive fallback is generated
- **THEN** the fallback summary SHALL be an empty string

#### Scenario: Fallback summary used for downstream validation

- **GIVEN** an extractive fallback summary is generated
- **WHEN** post-compaction validation runs against the fallback summary
- **THEN** validation SHALL use the fallback text as the summary input, applying the same word-match ratio check

### Requirement: Compaction atomicity guard

Message history SHALL NOT be cleared until the compaction summary passes validation. The summary SHALL be generated and validated before any mutation of message history occurs. If validation fails (word match ratio below `minMatchRatio`), the message history SHALL be preserved intact and compaction SHALL be treated as failed, triggering rollback. Only after successful validation SHALL message history be cleared and the summary stored.

#### Scenario: Validation passes before clear

- **GIVEN** a summary with word match ratio of 0.7 against all protected content
- **AND** `CompactionConfig.minMatchRatio` is 0.5
- **WHEN** the atomicity guard evaluates the summary
- **THEN** the message history SHALL be cleared
- **AND** the summary SHALL be stored as the compacted context

#### Scenario: Validation fails, messages preserved

- **GIVEN** a summary with word match ratio of 0.3 against protected content
- **AND** `CompactionConfig.minMatchRatio` is 0.5
- **WHEN** the atomicity guard rejects the summary
- **THEN** message history SHALL NOT be cleared
- **AND** the original messages SHALL remain in the context

#### Scenario: Clear only occurs after validation

- **GIVEN** compaction is in progress
- **WHEN** observed from outside `CompactedContextProvider.compact()`
- **THEN** message history count SHALL remain unchanged until validation confirms success — never transitionally empty

### Requirement: Configurable quality threshold

`CompactionConfig` SHALL include a `minMatchRatio` parameter of type `double` with default value `0.5` and valid range `[0.0, 1.0]`. The system SHALL reject `minMatchRatio` values outside this range at startup via `@Validated` constraint. `CompactionValidator` SHALL use this value in place of any previously hardcoded threshold. Setting `minMatchRatio` to `0.0` SHALL disable threshold-based rejection (all summaries pass). Setting `minMatchRatio` to `1.0` SHALL require every protected content word to appear in the summary.

#### Scenario: Default threshold rejects low-quality summary

- **GIVEN** `minMatchRatio` is not explicitly configured (defaults to 0.5)
- **AND** a summary covering only 3 of 10 protected content words (ratio 0.3)
- **WHEN** `CompactionValidator` evaluates the summary
- **THEN** validation SHALL reject the summary

#### Scenario: Custom threshold accepts previously-rejected summary

- **GIVEN** `minMatchRatio` is configured to 0.2
- **AND** a summary covering 3 of 10 protected content words (ratio 0.3)
- **WHEN** `CompactionValidator` evaluates the summary
- **THEN** validation SHALL accept the summary

#### Scenario: Zero threshold disables rejection

- **GIVEN** `minMatchRatio` is 0.0
- **AND** a summary containing none of the protected content words
- **WHEN** `CompactionValidator` evaluates the summary
- **THEN** validation SHALL accept the summary (threshold disabled)

#### Scenario: Invalid threshold rejected at startup

- **GIVEN** `dice-anchors.compaction.min-match-ratio` is set to 1.5 in `application.yml`
- **WHEN** the Spring context initializes
- **THEN** context startup SHALL fail with a constraint validation error identifying `minMatchRatio`
