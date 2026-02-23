## 1. CompactionConfig Expansion (D4)

- [x] 1.1 Add `minMatchRatio` (double, default 0.5), `maxRetries` (int, default 2), and `retryBackoffMillis` (long, default 1000) fields to `CompactionConfig` record
- [x] 1.2 Add compact constructor validation: `minMatchRatio` in [0.0, 1.0], `maxRetries >= 0`, `retryBackoffMillis > 0`
- [x] 1.3 Update `CompactionConfig.disabled()` factory to include new fields with defaults
- [x] 1.4 Update all existing call sites that construct `CompactionConfig` (tests, YAML loader, scenario config)
- [x] 1.5 Add `events-enabled` config property (boolean, default true) for gating `CompactionCompleted` event publishing

## 2. SummaryResult Record and SimSummaryGenerator Retry (D3)

- [x] 2.1 Create `SummaryResult` record in `assembly/` package: `(String summary, int retryCount, boolean fallbackUsed)`
- [x] 2.2 Update `SimSummaryGenerator.generateSummary()` signature to accept `List<ProtectedContent> protectedContent`, `int maxRetries`, `Duration initialBackoff` and return `SummaryResult`
- [x] 2.3 Implement retry loop with exponential backoff: catch exceptions, WARN log each failure with attempt number, sleep with doubling backoff
- [x] 2.4 Implement extractive fallback: sort protected content by priority descending, concatenate texts with space separator, return `SummaryResult(fallback, retryCount, fallbackUsed=true)`
- [x] 2.5 Handle `maxRetries = 0` case: single attempt, immediate fallback on failure

## 3. CompactionValidator Threshold Update (D4)

- [x] 3.1 Update `CompactionValidator.validate()` signature to accept `double minMatchRatio` parameter
- [x] 3.2 Replace hardcoded `0.5` threshold with `minMatchRatio` parameter
- [x] 3.3 Update all call sites of `CompactionValidator.validate()` to pass `config.minMatchRatio()`

## 4. Compaction Atomicity and Message Backup/Rollback (D1, D2)

- [x] 4.1 Add `ApplicationEventPublisher` constructor parameter to `CompactedContextProvider`
- [x] 4.2 Refactor `compact()`: snapshot messages via `List.copyOf()` before any mutation
- [x] 4.3 Refactor `compact()`: call `SimSummaryGenerator` with new signature (passing protected content, retry config)
- [x] 4.4 Refactor `compact()`: validate summary BEFORE clearing messages (atomicity guard)
- [x] 4.5 Implement rollback path: on LLM failure or validation failure, restore messages from snapshot, return `CompactionResult(applied=false)`
- [x] 4.6 Implement success path: clear messages and store summary only after validation passes
- [x] 4.7 Add `compactionApplied` field to `CompactionResult` record

## 5. CompactionCompleted Event (D5)

- [x] 5.1 Create `CompactionCompleted` class extending `ApplicationEvent` in `assembly/` package with fields: contextId, triggerReason, tokensBefore, tokensAfter, lossCount, retryCount, fallbackUsed, compactionApplied, occurredAt
- [x] 5.2 Publish `CompactionCompleted` event at end of `compact()` when compaction was applied (success or fallback); do NOT publish on full rollback
- [x] 5.3 Gate event publishing on `events-enabled` config property

## 6. OTEL Span Attributes (D7)

- [x] 6.1 Add OTEL span attribute writes in `compact()` using `Span.current()`: trigger_reason, tokens_before, tokens_after, loss_count, retry_count, fallback_used, applied

## 7. Context Cleanup Guarantee (D6)

- [x] 7.1 Add `compactedContextProvider.clearContext(contextId)` to `SimulationService.runSimulation()` finally block
- [x] 7.2 Add `anchorRepository.clearByContext(contextId)` to the same finally block
- [x] 7.3 Wrap cleanup calls in try/catch so cleanup failure does not mask original exception

## 8. Unit Tests

- [x] 8.1 Test `CompactionConfig` validation: valid ranges, invalid `minMatchRatio` rejected, disabled() factory
- [x] 8.2 Test `SummaryResult` record construction and field access
- [x] 8.3 Test `SimSummaryGenerator` retry: first-fail-then-succeed, all-retries-exhausted triggers fallback, zero-retries immediate fallback
- [x] 8.4 Test extractive fallback: priority ordering, empty protected content yields empty string, fallbackUsed flag set
- [x] 8.5 Test `CompactionValidator.validate()` with configurable threshold: default rejects low ratio, custom threshold accepts, zero threshold disables rejection
- [x] 8.6 Test `CompactedContextProvider.compact()` atomicity: validate-before-clear ordering, message rollback on LLM failure, message rollback on validation failure, successful compaction clears messages
- [x] 8.7 Test `CompactionCompleted` event: published on success, published on fallback, NOT published on full rollback, events-enabled=false suppresses publishing
- [x] 8.8 Test OTEL span attributes: attributes set when compaction occurs, not set when no compaction
- [x] 8.9 Test `SimulationService` cleanup: clearContext called on normal completion, called on exception, called on cancellation, idempotent double-call

## 9. Existing Test Updates

- [x] 9.1 Update existing `CompactionValidator` tests to pass `minMatchRatio` parameter
- [x] 9.2 Update existing `CompactedContextProvider` tests for new constructor (ApplicationEventPublisher) and `compact()` flow changes
- [x] 9.3 Update existing `SimSummaryGenerator` tests for new signature and `SummaryResult` return type
- [x] 9.4 Verify all 81+ compaction tests pass after changes

## 10. Verification

- [x] 10.1 Run full test suite — all tests pass with zero failures
- [x] 10.2 Compile check — `./mvnw.cmd clean compile -DskipTests` succeeds

## 11. UI Regression Test (Playwright via Docker MCP Gateway)

- [x] 11.1 Navigate Playwright browser to SimulationView (`http://host.docker.internal:8089/`) — take snapshot, verify page renders with scenario selector and start button present
- [x] 11.2 Start a simulation via Playwright — click start, wait for turns to execute, verify Compaction tab in Context Inspector displays compaction results (trigger reason, token delta, loss count)
- [x] 11.3 Navigate Playwright browser to ChatView (`http://host.docker.internal:8089/chat`) — take snapshot, verify page renders with chat input and message area present
- [x] 11.4 Check Playwright browser console messages for errors — verify no JS errors or broken component rendering in either view after compaction pipeline changes
