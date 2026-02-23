## Why

The compaction pipeline currently operates without recovery semantics. When the LLM summary generation fails, the error message string (`[Summary generation failed: ...]`) is stored as the "summary" and future turns see it as narrative context. More critically, message history is cleared *before* post-compaction validation runs — if validation detects anchor content loss, the original messages are already gone with no rollback path. This means a single LLM failure or poor-quality summary can silently corrupt the simulation's context window, causing drift that's indistinguishable from adversarial attack drift.

The compaction system has comprehensive test coverage (81 tests) and a well-designed protected content SPI, but lacks the reliability guarantees needed before the benchmarking wave (F06) where compaction correctness becomes measurable. Adding recovery guardrails now hardens the pipeline against LLM failures, validation failures, and state corruption.

## What Changes

- Add **message backup and rollback** to `CompactedContextProvider.compact()` — snapshot message history before clearing, restore on validation failure or LLM error.
- Add **LLM retry with fallback** to `SimSummaryGenerator` — configurable retry count with exponential backoff, falling back to a deterministic extractive summary (concatenate protected content texts) when all retries fail.
- Add **compaction atomicity guard** — validate summary quality *before* clearing message history; only clear after validation passes.
- Add **compaction quality threshold** — configurable minimum match ratio (currently hardcoded 50%) below which compaction is rejected and messages are preserved.
- Add **compaction lifecycle event** — publish a `CompactionCompleted` event with trigger reason, token delta, loss count, and retry count for observability and UI display.
- Add **explicit context cleanup** on simulation cancellation/error — ensure `CompactedContextProvider.clearContext()` is called in `SimulationService` finally blocks.
- Add **OTEL span attributes** for compaction events — `compaction.trigger_reason`, `compaction.tokens_before`, `compaction.tokens_after`, `compaction.loss_count`, `compaction.retry_count`.

## Capabilities

### New Capabilities
- `compaction-recovery`: Recovery semantics for the compaction pipeline — message backup/rollback, LLM retry with extractive fallback, atomicity guard, and quality threshold enforcement.

### Modified Capabilities
- `compaction`: Modify the compact pipeline to enforce atomicity (validate-before-clear) and add configurable quality threshold. Add cleanup guarantees on context termination.
- `anchor-lifecycle-events`: Add `CompactionCompleted` event type to the sealed `AnchorLifecycleEvent` hierarchy (or a separate `CompactionEvent` hierarchy if the sealed class is inappropriate).
- `observability`: Add compaction-related OTEL span attributes to the simulation turn span.

## Impact

- **Assembly**: `CompactedContextProvider`, `SimSummaryGenerator`, `CompactionValidator`, `CompactionConfig` all modified.
- **Events**: New compaction lifecycle event type.
- **Simulation**: `SimulationTurnExecutor` and `SimulationService` updated for cleanup guarantees.
- **Configuration**: New config properties for retry count, backoff, quality threshold.
- **Observability**: New OTEL span attributes on compaction events.
- **Tests**: Existing 81 compaction tests updated; new tests for retry, rollback, atomicity, and cleanup paths.
