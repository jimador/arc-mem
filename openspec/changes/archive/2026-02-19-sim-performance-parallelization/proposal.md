## Why

Simulation turns take ~60 seconds each, with ~95% of that time spent on sequential, blocking LLM calls. The turn pipeline executes DM response generation, drift evaluation, DICE extraction, per-proposition duplicate detection, conflict detection, and trust scoring all in serial — even though many of these operations are independent after the DM response arrives. The project targets Java 21 but runs on Java 25, meaning virtual threads and structured concurrency are production-ready and available without preview flags. Parallelizing independent LLM calls and batching per-item evaluations SHOULD cut turn times by 40-60%.

## What Changes

- Introduce a virtual-thread-based `LlmCallExecutor` service for concurrent, non-blocking LLM invocations across the simulation pipeline
- Parallelize independent post-response work: run drift evaluation and DICE extraction concurrently after the DM response arrives
- Batch per-proposition LLM gates in `AnchorPromoter`: duplicate detection, conflict detection, and trust scoring currently make one LLM call per proposition per gate — batch them into single prompts where possible
- Upgrade Java source/target from 21 to 25 to unlock structured concurrency (`StructuredTaskScope`) without preview flags
- Add concurrency-safe guards to `AnchorEngine` and `AnchorRepository` to handle parallel promotion safely

## Capabilities

### New Capabilities

- `parallel-llm-execution`: Virtual-thread-based executor for concurrent LLM calls with timeout, cancellation, and error propagation. Foundation layer that other capabilities build on.
- `batched-promotion-gates`: Batch duplicate detection, conflict detection, and trust scoring LLM calls so multiple propositions are evaluated in a single prompt per gate instead of one call per proposition.
- `parallel-turn-pipeline`: Restructure `SimulationTurnExecutor.executeTurnFull()` to run independent post-response stages concurrently using structured concurrency.

### Modified Capabilities

- `sim-extraction-lifecycle`: Extraction and promotion stages MUST be concurrency-safe; extraction results feed into parallelized promotion gates instead of a sequential loop.
- `conflict-detection`: Conflict detection MUST support batch evaluation of multiple candidate propositions against existing anchors in a single LLM call.
- `normalized-string-dedup`: Fast-path dedup MUST remain sequential (thread-safe in-memory check), but LLM fallback MUST support batched evaluation.
- `trust-scoring`: Trust evaluation MUST support batched scoring of multiple propositions in a single LLM call.

## Impact

- **SimulationTurnExecutor** — Pipeline restructured from linear to fork-join; `executeTurnFull()` uses structured concurrency to run drift eval + extraction in parallel
- **SimulationExtractionService** — Extraction result handoff to promotion becomes async-safe
- **AnchorPromoter** — Promotion funnel refactored from per-proposition sequential gates to batched parallel evaluation
- **DuplicateDetector** — New `batchIsDuplicate(List<String>)` method; LLM prompt restructured for multi-proposition input
- **NegationConflictDetector** — New batch evaluation method; prompt restructured for multi-proposition input
- **Trust pipeline** — Batch scoring API
- **AnchorEngine** — Thread-safety review for concurrent `promote()` / `detectConflicts()` calls
- **pom.xml** — Java version 21 -> 25, enable structured concurrency
- **Prompt templates** — Batch-aware versions of dedup, conflict, and trust prompts
- **No database schema changes** — Neo4j model is unaffected
- **No UI changes** — Performance improvement is transparent to Vaadin views
- **Risk**: Batch prompts may reduce per-item accuracy compared to individual calls; needs evaluation during implementation
