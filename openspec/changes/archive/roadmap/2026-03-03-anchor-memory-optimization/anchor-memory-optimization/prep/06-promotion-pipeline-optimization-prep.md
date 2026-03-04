# Prep: Promotion Pipeline Optimization

**Feature**: F06 — `promotion-pipeline-optimization`
**Wave**: 2
**Priority**: SHOULD
**Depends on**: F05 (precomputed conflict index)

## RFC 2119 Compliance

All normative statements use RFC 2119 keywords.

## Locked Decisions

1. **Pre-extraction conflict check**: New gate inserted between confidence and dedup in the `AnchorPromoter` pipeline. Uses `ConflictIndex.getConflicts()` (O(1)) to filter propositions conflicting with RELIABLE+ anchors before spending LLM calls on dedup and trust evaluation.
2. **Fixed-size batches**: All batch operations (conflict detection, trust evaluation, dedup) process exactly N candidates per batch. Batch size is configurable (default 10). Batches with fewer than N real candidates are padded with no-op entries.
3. **ConflictIndex integration**: The pre-check gate consumes `ConflictIndex` from F05. If the index is empty (cold start) or unavailable, the pre-check gate is skipped and the pipeline operates in its original gate order.
4. **Full LLM verification retained**: The existing full LLM conflict detection is not removed. It moves to a later position (after trust evaluation) as a verification step for candidates that passed the pre-check. This ensures index misses do not cause false negatives.
5. **No outcome changes**: The set of propositions that ultimately get promoted MUST be identical to the unoptimized pipeline for the same inputs. The optimization changes ordering and timing, not decisions.

## Open Questions

| # | Question | Options | Leaning | Resolution Trigger |
|---|----------|---------|---------|-------------------|
| 1 | **Gate reordering impact on edge cases** | (a) Pre-check rejects a candidate that the full LLM check would have accepted (false positive from stale index). The candidate is lost. (b) Pre-check accepts a candidate that later fails full verification. Wasted dedup/trust LLM calls. (c) Both are acceptable given the optimization benefit. | (c) Both are acceptable. (a) is a false positive from index staleness -- bounded by F05's synchronous update guarantee. (b) is the current behavior (candidates can fail at any gate). Log pre-check rejections for analysis. | Design phase. Measure pre-check false positive rate in simulation. |
| 2 | **Padding implementation** | (a) Null entries in the batch list, with null-check guards in processing. (b) Sentinel `NoOpProposition` that passes through all operations unchanged. (c) Optional wrapper: `BatchEntry<T>` with `isReal()` flag. | (b) Sentinel `NoOpProposition`. Clean design -- no null checks scattered through batch processing. The sentinel is filtered out of results at batch completion. | Design phase. |
| 3 | **Batch size tuning** | (a) Fixed default of 10 for all batch operations. (b) Per-operation batch sizes (conflict batch size, trust batch size, dedup batch size). (c) Single configurable batch size applied uniformly. | (c) Single configurable batch size. Simplest configuration. Per-operation tuning is premature optimization. | Calibration via simulation if needed. |
| 4 | **Pre-check authority threshold** | (a) Filter against RELIABLE+ anchors only (RELIABLE and CANON). (b) Filter against all active anchors. (c) Configurable threshold. | (a) RELIABLE+ only. PROVISIONAL and UNRELIABLE anchors are not stable enough to gate new propositions. Filtering against low-authority anchors would be overly conservative. | Design phase. |

## Visibility Contract

| Surface | What | When | Format |
|---------|------|------|--------|
| Structured logs | Per-gate timing | Each promotion cycle | DEBUG: `promotion.gate.confidence.duration_ms=12`, `promotion.gate.precheck.duration_ms=1`, etc. |
| Structured logs | Early-rejection count | Each promotion cycle | INFO: `promotion.precheck.rejected=3 reason=conflict_with_reliable` |
| Structured logs | Batch utilization | Each batch operation | DEBUG: `promotion.batch.utilization=7/10` |
| RunInspectorView | Pipeline summary | Per-turn | Candidates in, pre-check rejected, promoted out, per-gate timing |

## Acceptance Gates

| Gate | Verification | Command |
|------|-------------|---------|
| Pipeline throughput improved | Integration test: same scenario before/after optimization. Measure LLM call count and total promotion duration. After MUST have fewer LLM calls. | `./mvnw test -pl . -Dtest=AnchorPromoterOptimizationTest` |
| No change to promotion outcomes | Unit test: same input propositions + same anchor state -> same promoted set with optimized and unoptimized pipelines. | `./mvnw test -pl . -Dtest=AnchorPromoterTest` |
| Fixed-size batches work with padding | Unit test: 3 real candidates + 7 padding in a batch of 10 -> result contains exactly 3 real outcomes. | `./mvnw test -pl . -Dtest=AnchorPromoterTest` |
| Graceful fallback on empty index | Unit test: ConflictIndex is empty -> pre-check gate skipped, pipeline operates in original order. | `./mvnw test -pl . -Dtest=AnchorPromoterTest` |

## Small-Model Constraints

- **Max 3 files per task** (promoter refactor + batch utility + tests)
- **Verification**: `./mvnw test` MUST pass after each task
- **No new LLM calls**: Pre-check is index-only; existing LLM calls move but are not added
- **Scope boundary**: `extract/` package primarily; `anchor/` for ConflictIndex consumption

## Task Sketch

| # | Scope | Files | Gate |
|---|-------|-------|------|
| T1 | Pre-check gate in `AnchorPromoter` using `ConflictIndex` + gate reordering | `AnchorPromoter.java` (refactor), `AnchorPromoterTest.java` (update) | Pre-check filters conflicting propositions; pipeline outcome unchanged for non-conflicting |
| T2 | Fixed-size batch padding with `NoOpProposition` sentinel | `BatchPaddingUtil.java`, `AnchorPromoter.java` (update), `BatchPaddingUtilTest.java` | Batches are fixed-size; padding filtered from results |
| T3 | Observability: per-gate timing + early-rejection logging + batch utilization | `AnchorPromoter.java` (update), observability test | Structured logs emitted with timing and rejection metrics |

## Risks Requiring Design Attention

1. **AnchorPromoter refactor scope**: Reordering gates in `AnchorPromoter` touches the core promotion logic. The refactor MUST be tested against all existing promotion test cases to ensure no behavioral change.
2. **NoOpProposition sentinel design**: The sentinel MUST be distinguishable from real propositions at every gate. Verify that confidence scoring, dedup, trust evaluation, and conflict detection all handle the sentinel correctly (pass through without side effects).
3. **ConflictIndex dependency injection**: `AnchorPromoter` gains a new dependency on `ConflictIndex`. If the index bean is not available (e.g., INDEXED strategy not configured), the pre-check gate MUST be skipped gracefully. Use `Optional<ConflictIndex>` injection.
