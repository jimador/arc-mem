# Feature: Promotion Pipeline Optimization

## Feature ID

`F06`

## Summary

Optimize the `UnitPromoter` pipeline with two changes: (1) move conflict detection earlier to extraction time via a pre-injection check using the `ConflictIndex` (F05), and (2) adopt fixed-size batch operations for predictable throughput. These changes reduce LLM calls for doomed candidates and eliminate variable-length batch timing.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: The promotion pipeline processes propositions through 5 gates sequentially (confidence, dedup, conflict, trust, promote), with conflict detection at gate 3. Gates 1-2 (confidence, dedup) execute LLM calls on propositions that will be rejected for conflicts at gate 3. Additionally, variable-size batches cause unpredictable LLM call timing -- a batch of 2 candidates and a batch of 10 candidates have wildly different execution durations.
2. **Value delivered**: Moving conflict pre-check to before dedup (using O(1) index lookup) avoids wasting LLM calls on doomed candidates. Fixed-size batches provide predictable per-batch timing for throughput planning and monitoring.
3. **Why now**: Wave 2. Depends on F05 (conflict index) for the O(1) pre-check. Together with F05, these optimizations significantly reduce LLM calls and improve throughput predictability.

## Scope

### In Scope

1. **Pre-extraction conflict check**: New gate between confidence and dedup that uses `ConflictIndex` (F05) for O(1) conflict lookup against existing RELIABLE+ context units.
2. **Gate reordering**: Pipeline becomes: confidence -> **conflict pre-check** -> dedup -> trust -> **full conflict verification** -> promote. The existing full LLM conflict detection moves to a later verification step for index misses.
3. **Fixed-size batch operations**: Process exactly N candidates per batch, padding with no-ops if fewer than N candidates are available.
4. **Configurable batch size**: Via `ArcMemProperties` (default: 10, reusing existing `sim.batchMaxSize`).
5. **Padding implementation**: No-op entries that pass through batch operations without affecting results.

### Out of Scope

1. Conflict index implementation (F05 -- this feature consumes the index).
2. Changes to promotion outcomes for non-conflicting propositions.
3. Parallelization of gate execution (sequential gate processing is preserved).
4. Changes to the trust evaluation or confidence scoring algorithms.

## Dependencies

1. Feature dependencies: F05 (precomputed conflict index -- provides `ConflictIndex` for O(1) pre-check).
2. Priority: SHOULD.
3. OpenSpec change slug: `promotion-pipeline-optimization`.
4. Research rec: E (pre-injection conflict check at extraction time), H (fixed-size batch padding for predictable throughput).

## Research Requirements

None. The pre-check pattern is established by sleeping-llm's hallucination firewall (filter before injection). Fixed-size batching is established by STATIC's VNTK (fixed-size speculative slices).

## Impacted Areas

1. **`extract/` package (primary)**: `UnitPromoter` gate reordering, new `ConflictPreCheck` gate, batch sizing logic.
2. **`context unit/` package (integration)**: `ConflictIndex` consumed for pre-check lookups.
3. **`ArcMemProperties`**: Batch size configuration (MAY reuse existing `sim.batchMaxSize` or add dedicated `promotion.batchSize`).

## Visibility Requirements

### UI Visibility

1. Per-gate timing MAY be displayed in RunInspectorView for promotion pipeline analysis.

### Observability Visibility

1. Per-gate timing MUST be logged: `promotion.gate.{name}.duration_ms` for each gate in the pipeline.
2. Early-rejection counts MUST be logged: `promotion.precheck.rejected` with count and reason (conflict with existing context unit).
3. Batch utilization ratio MUST be logged: `promotion.batch.utilization={actual}/{padded}` (e.g., `7/10` means 7 real candidates + 3 padding).
4. Overall pipeline metrics MUST be trackable: propositions processed per second, LLM calls per promotion cycle.

## Acceptance Criteria

1. Pre-extraction conflict check MUST use `ConflictIndex` for O(1) lookup, not LLM calls.
2. Propositions conflicting with existing RELIABLE+ context units MUST be filtered before the dedup gate.
3. Full LLM conflict detection MUST remain as a verification step for candidates that pass the pre-check (handling index misses).
4. Batch operations MUST use fixed-size batches with configurable size.
5. Pipeline throughput (propositions processed per second) SHOULD be measurably improved compared to baseline (same scenario, before and after optimization).
6. No change to promotion outcomes for non-conflicting propositions -- the optimization MUST be transparent to callers.
7. Padding entries MUST NOT affect batch operation results (no-op semantics).
8. Gate reordering MUST NOT change the set of propositions that ultimately get promoted (same inputs, same promoted outputs).
9. Pre-check rejection MUST be logged with the conflicting context unit ID and text for auditability.

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Gate reordering edge cases** | Medium | Medium | The pre-check uses index lookups (conservative -- may have false positives). Full LLM detection verifies later. Edge case: index says conflict, LLM says no conflict. Resolution: pre-check is a fast filter, not a final decision. Candidates filtered by pre-check SHOULD be logged for analysis. |
| **Padding overhead** | Low | Low | Padding entries are trivial no-ops. LLM batch APIs typically ignore empty/null entries. Overhead is negligible. |
| **Batch size misconfiguration** | Low | Medium | Batch size too large wastes padding; too small increases batch count. Default of 10 matches existing `sim.batchMaxSize`. Configurable for tuning. |
| **Conflict index unavailability** | Low | Medium | If `ConflictIndex` is empty (cold start) or unavailable, pre-check SHOULD be skipped gracefully, falling back to the existing pipeline order. |

## Proposal Seed

### Change Slug

`promotion-pipeline-optimization`

### Proposal Starter Inputs

1. **Problem statement**: The promotion pipeline processes propositions through 5 gates sequentially, with conflict detection at gate 3. Gates 1-2 run on propositions that will be rejected at gate 3. Variable-size batches cause unpredictable LLM timing.
2. **Why now**: Depends on F05 (conflict index) for the O(1) pre-check. Together, these features significantly reduce LLM calls and improve throughput predictability.
3. **Constraints/non-goals**: Gate reordering MUST NOT change promotion outcomes. Padding MUST NOT affect batch results. No parallelization of gates. No algorithm changes.
4. **Visible outcomes**: Per-gate timing in logs. Early-rejection counts quantifying wasted work avoided. Batch utilization ratio showing padding overhead.

### Suggested Capability Areas

1. **Conflict pre-check gate**: O(1) index lookup before dedup, filtering doomed candidates early.
2. **Gate reordering**: Restructured pipeline with pre-check before dedup and full verification after trust.
3. **Fixed-size batching**: Padded batches for predictable per-batch timing.
4. **Observability**: Per-gate timing, rejection counts, batch utilization metrics.

### Candidate Requirement Blocks

1. **REQ-PRECHECK**: The system SHALL check incoming propositions against the `ConflictIndex` before the dedup gate, filtering candidates that conflict with RELIABLE+ context units.
2. **REQ-VERIFY**: The system SHALL retain full LLM conflict detection as a verification step for candidates that pass the pre-check.
3. **REQ-BATCH**: Batch operations SHALL use fixed-size batches with configurable size and no-op padding.
4. **REQ-TRANSPARENT**: Gate reordering SHALL NOT change the set of propositions that ultimately get promoted.
5. **REQ-METRIC**: Per-gate timing and early-rejection counts SHALL be logged for performance analysis.

## Validation Plan

1. **Unit tests** MUST verify that pre-check filters propositions conflicting with RELIABLE+ context units.
2. **Unit tests** MUST verify that non-conflicting propositions pass through the pipeline identically to the unoptimized pipeline.
3. **Unit tests** MUST verify fixed-size batching with padding (batch of 3 real + 7 padding = 10 total, result contains only 3 real outcomes).
4. **Unit tests** MUST verify graceful fallback when `ConflictIndex` is empty (pre-check skipped, pipeline operates as before).
5. **Integration test** SHOULD compare promotion outcomes between optimized and unoptimized pipelines for the same scenario (same promoted set).
6. **Performance test** SHOULD measure LLM call count and per-turn duration before and after optimization in a simulation run.
7. **Observability validation** MUST confirm per-gate timing and rejection counts appear in structured logs.

## Known Limitations

1. **Pre-check is conservative.** The `ConflictIndex` may contain false positives (pairs flagged as conflicting that are not truly conflicting). These candidates are filtered early, potentially reducing promotion throughput. Full LLM verification is not applied to pre-check rejections.
2. **Fixed-size batching adds padding overhead.** When candidate count is consistently low, most batch slots are padding no-ops. The overhead is negligible but visible in utilization metrics.
3. **Gate reordering changes rejection attribution.** Propositions that were previously rejected at gate 3 (conflict) after passing gates 1-2 (confidence, dedup) are now rejected at the pre-check gate. Log analysis that depends on gate-specific rejection counts will see different distributions.

## Suggested Command

```
/opsx:new promotion-pipeline-optimization
```
