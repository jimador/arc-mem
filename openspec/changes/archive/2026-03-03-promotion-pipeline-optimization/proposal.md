## Why

The promotion pipeline (`AnchorPromoter`) processes propositions through five sequential gates: confidence, dedup, conflict, trust, promote. Conflict detection at gate 3 is the most LLM-call-intensive operation in the pipeline. Gates 1-2 (confidence filtering and duplicate detection) spend LLM calls on propositions that will be rejected at gate 3 when they conflict with established anchors. For propositions that conflict with RELIABLE+ anchors, the dedup LLM call is wasted work -- the candidate is doomed before it enters the dedup gate.

With F05's `ConflictIndex` providing O(1) conflict lookup, a pre-check gate can filter doomed candidates before any LLM calls are made. This is the same principle as sleeping-llm's hallucination firewall: filter before injection. The firewall's 3-pass verification (cleaning, grounding check, model verification) rejects low-quality facts before they reach MEMIT injection. Similarly, the conflict pre-check rejects propositions that contradict established anchors before they reach the dedup and trust LLM gates.

Google AI's STATIC paper reinforces this: precomputed constraint relationships enable O(1) online enforcement. The conflict index converts an O(N) per-candidate LLM problem into an O(1) lookup, and the pre-check gate moves that lookup to the earliest possible position in the pipeline.

## What Changes

Add a **conflict pre-check gate** to the `AnchorPromoter` pipeline, positioned between the confidence gate and the dedup gate. The pre-check uses `ConflictIndex` (from F05) for O(1) lookup against existing RELIABLE+ anchors. Propositions that conflict with RELIABLE or CANON anchors are filtered before any LLM calls (dedup, trust) are incurred.

### Pipeline change

**Before**: confidence -> dedup -> conflict -> trust -> promote
**After**: confidence -> **conflict pre-check** -> dedup -> conflict -> trust -> promote

The existing full LLM conflict detection at gate 3 is retained. The pre-check is a fast filter, not a replacement. Candidates that pass the pre-check still undergo full conflict detection.

### Integration

- **`AnchorPromoter`**: Gains an `Optional<ConflictIndex>` dependency. When present and non-empty, the pre-check gate filters propositions that have indexed conflicts with RELIABLE+ anchors. When absent or empty (cold start), the gate is skipped and the pipeline operates in its original order.
- **Structured logging**: Pre-check rejections are logged at INFO with the conflicting anchor ID and authority for auditability.

### Scoping decision

**Fixed-size batch padding is deferred.** The feature doc describes two optimizations: (1) pre-check gate and (2) fixed-size batching with no-op padding. The batch padding concept adds implementation complexity (sentinel `NoOpProposition`, batch padding utility, sentinel handling at every gate) without proportional value in a demo repo where batch sizes are small. This change implements the pre-check gate only. Batch padding MAY be revisited as a separate change if profiling demonstrates a need.

## Capabilities

### New Capabilities

- `conflict-precheck`: O(1) conflict pre-check gate in the promotion pipeline using `ConflictIndex`. Filters propositions conflicting with RELIABLE+ anchors before LLM-intensive gates (dedup, trust). Graceful fallback when the index is empty or unavailable.

### Modified Capabilities

- `promotion-pipeline`: `AnchorPromoter` gains a new first gate (after confidence) for conflict pre-checking. Pipeline invariant P3 is updated to include the pre-check gate.

## Impact

### Modified files

- `src/main/java/dev/dunnam/diceanchors/extract/AnchorPromoter.java` -- add `Optional<ConflictIndex>` dependency, pre-check gate in both sequential and batch paths, structured logging for rejections
- `src/test/java/dev/dunnam/diceanchors/extract/AnchorPromoterTest.java` -- tests for pre-check behavior (filtering, fallback, transparency)

### Constitutional alignment

- **Article I (RFC 2119)**: All normative statements in the spec use RFC 2119 keywords.
- **Article II (Neo4j only)**: No persistence changes. The pre-check reads from the in-memory `ConflictIndex`.
- **Article III (Constructor injection)**: `ConflictIndex` injected via constructor as `Optional<ConflictIndex>`.
- **Article IV (Records)**: No new records. Existing `PromotionOutcome` and `ConflictResolutionResult` unchanged.
- **Article V (Anchor invariants)**: A1-A4 preserved. The pre-check filters candidates but does not modify anchor state.
- **Article VI (Sim isolation)**: Pre-check operates within the existing context isolation model.
- **Article VII (Test-first)**: Unit tests accompany the implementation.
