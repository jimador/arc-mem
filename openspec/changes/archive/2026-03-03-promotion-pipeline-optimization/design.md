## Context

The `AnchorPromoter` pipeline processes propositions through five gates: confidence, dedup, conflict, trust, promote. The dedup gate uses LLM calls (`DuplicateDetector`), and the trust gate uses LLM calls (`TrustPipeline`). Propositions that will be rejected at the conflict gate (gate 3) for contradicting established RELIABLE+ anchors waste LLM calls at gates 2 and 4.

F05 (`precomputed-conflict-index`) introduces `ConflictIndex` -- an in-memory index providing O(1) conflict lookup between anchor pairs. This change adds a pre-check gate that uses the index to filter doomed candidates before any LLM calls are incurred.

The current `AnchorPromoter` has two code paths:
1. **Sequential** (`evaluateAndPromoteWithOutcome`): processes each proposition through all gates individually.
2. **Batch** (`batchEvaluateAndPromoteWithOutcome`): processes candidates together through gates using batch LLM calls, then promotes sequentially.

Both paths MUST gain the pre-check gate.

## Goals / Non-Goals

**Goals:**
- Add a conflict pre-check gate using `ConflictIndex` O(1) lookup, filtering propositions that conflict with RELIABLE+ anchors before the dedup gate
- Integrate `ConflictIndex` into `AnchorPromoter` via `Optional<ConflictIndex>` for backward-compatible injection
- Provide graceful fallback when the index is empty or unavailable
- Add structured logging for pre-check rejections and funnel summary updates

**Non-Goals:**
- Fixed-size batch padding with `NoOpProposition` sentinel (deferred -- see Deferred Work)
- Gate reordering beyond adding the pre-check (existing gate order preserved)
- Changes to the conflict detection algorithms or trust evaluation
- Parallelization of gate execution
- Per-gate timing metrics (nice-to-have, not in initial scope)

## Decisions

### D1: Pre-Check Gate Placement

**Decision**: The pre-check gate is positioned after the confidence gate and before the dedup gate. The pipeline becomes: confidence -> **conflict pre-check** -> dedup -> conflict -> trust -> promote.

**Why**: The confidence gate is a fast, no-I/O filter. Placing the pre-check after confidence means only candidates that pass the threshold are checked against the index. Placing it before dedup means LLM calls for duplicate detection are avoided for doomed candidates.

**Alternative considered**: Pre-check before confidence. Rejected -- checking every proposition (including low-confidence ones that would be filtered anyway) wastes index lookups unnecessarily.

### D2: Optional<ConflictIndex> Injection

**Decision**: `AnchorPromoter` accepts `Optional<ConflictIndex>` as a constructor parameter.

```java
public AnchorPromoter(AnchorEngine engine, DiceAnchorsProperties properties,
                      TrustPipeline trustPipeline, AnchorRepository repository,
                      DuplicateDetector duplicateDetector,
                      Optional<ConflictIndex> conflictIndex) {
    // ...
    this.conflictIndex = conflictIndex.orElse(null);
}
```

**Why**: `Optional` injection is explicit about the dependency's optionality. When F05 is not configured (strategy is not INDEXED), no `ConflictIndex` bean exists and Spring injects `Optional.empty()`. The promoter stores the unwrapped value (nullable) internally to avoid repeated `Optional` boxing in the hot path.

**Alternative considered**: `@Nullable ConflictIndex`. Rejected -- the project prefers `Optional` for explicit optionality at the API boundary.

### D3: Authority Threshold Hardcoded to RELIABLE

**Decision**: The pre-check filters only against conflicts with RELIABLE or CANON anchors. The threshold is hardcoded, not configurable.

```java
private boolean isReliableOrHigher(Authority authority) {
    return authority.isAtLeast(Authority.RELIABLE);
}
```

**Why**: PROVISIONAL and UNRELIABLE anchors are not stable enough to gate new propositions. Filtering against low-authority anchors would be overly conservative and could starve the anchor pool. Making the threshold configurable adds a tuning knob with no demonstrated need in a demo repo.

### D4: Graceful Fallback on Empty or Absent Index

**Decision**: The pre-check gate is skipped entirely when the `ConflictIndex` is null or has `size() == 0`. No log noise, no dummy processing.

```java
private boolean shouldRunPrecheck() {
    return conflictIndex != null && conflictIndex.size() > 0;
}
```

**Why**: On cold start (before any conflicts are detected and indexed), the pre-check has no data and cannot make useful decisions. Skipping it entirely preserves the original pipeline behavior and avoids misleading "0 pre-check rejections" log entries.

### D5: Pre-Check Uses Proposition Text for Index Lookup

**Decision**: The pre-check iterates over the existing anchors' conflict entries in the index and matches by checking whether any `ConflictEntry` targets the incoming proposition's context. Since the `ConflictIndex` is keyed by anchor ID (not proposition text), the pre-check queries conflicts for each existing anchor and checks whether any entry matches.

More specifically: for each incoming proposition, the pre-check checks `ConflictIndex.getConflicts()` for existing anchors in the context. If any returned `ConflictEntry` has `anchorText` matching the incoming proposition text and the entry's `authority` is RELIABLE+, the proposition is filtered.

**Alternative approach**: In the batch path, iterate over the `existingAnchors` list (already loaded), check the index for each anchor, and build a set of conflicting texts. Then filter the candidate list against that set. This is O(A) where A is the anchor count, but each lookup is O(1) in the index.

### D6: Invariant P3 Updated

**Decision**: The pipeline invariant P3 in `AnchorPromoter` Javadoc is updated to reflect the new gate:

```
P3: Gate sequence is preserved — confidence -> conflict pre-check -> dedup -> conflict -> trust -> promote.
    The pre-check gate is skipped when ConflictIndex is absent or empty.
```

## Data Flow

```
Proposition
    |
    v
Gate 1: Confidence (no I/O)
    |
    v
Gate 1.5: Conflict Pre-Check (O(1) index lookup, no LLM)    <-- NEW
    |   ConflictIndex.getConflicts(anchorId) for each existing anchor
    |   Filter if any ConflictEntry has authority >= RELIABLE
    |   Skip entirely if index is null or empty
    |
    v
Gate 2: Dedup (LLM call)
    |
    v
Gate 3: Full Conflict Detection (LLM call)
    |
    v
Gate 4: Trust Evaluation (LLM call)
    |
    v
Gate 5: Promote (AnchorEngine.promote)
```

## File Inventory

### Modified Files (2)

| File | Change |
|------|--------|
| `AnchorPromoter.java` | Add `Optional<ConflictIndex>` constructor parameter. Add pre-check gate logic to both `evaluateAndPromoteWithOutcome` and `batchEvaluateAndPromoteWithOutcome`. Update funnel logging to include `post-precheck` count. Update Javadoc invariant P3. |
| `AnchorPromoterTest.java` | Add tests for pre-check behavior: filtering with RELIABLE+ conflicts, pass-through for PROVISIONAL/UNRELIABLE conflicts, fallback on empty/absent index, batch path pre-check. |

### No New Files

The pre-check logic is small enough to live inside `AnchorPromoter` as a private method. No separate class is needed.

## Research Attribution

The conflict pre-check gate applies the "filter before injection" pattern from two sources:

1. **sleeping-llm's hallucination firewall**: The firewall's 3-pass verification (cleaning, grounding check, model verification) rejects low-quality facts before they reach MEMIT injection. The pre-check applies the same principle: reject propositions that conflict with established anchors before they reach LLM-intensive pipeline gates.

2. **Google AI STATIC's pre-injection constraint check**: STATIC precomputes constraint relationships as a sparse matrix for O(1) online enforcement. The `ConflictIndex` (F05) provides the sparse matrix; this change moves the enforcement point to before LLM-intensive gates.

## Deferred Work

| Item | Deferred To | Reason |
|------|-------------|--------|
| Fixed-size batch padding with `NoOpProposition` sentinel | Separate change | Adds implementation complexity (sentinel design, batch padding utility, sentinel handling at every gate) without proportional value in a demo repo. Batch sizes are small; padding overhead is not a demonstrated bottleneck. |
| Per-gate timing metrics (`promotion.gate.{name}.duration_ms`) | Future | Nice-to-have observability. Not critical for the pre-check gate. Can be added as a cross-cutting concern across all gates later. |
| Batch utilization ratio logging | With batch padding change | Only meaningful when fixed-size batches are implemented. |
| RunInspectorView pipeline summary display | Future | UI visibility for pre-check stats. Not critical for the demo. |

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| **Pre-check false positives from stale index**: Index entry says conflict exists, but the conflicting anchor has been demoted below RELIABLE since indexing. Candidate is incorrectly filtered. | The pre-check reads `authority` from the `ConflictEntry`, which captures authority at detection time. If the anchor has been demoted, the entry's authority is stale. Mitigation: the lifecycle event handler in F05 removes entries for archived/evicted anchors. Authority changes do not currently invalidate entries -- this is an accepted limitation. The full conflict gate (gate 3) still runs for candidates that pass the pre-check, providing a safety net. |
| **Pre-check cannot catch conflicts the index has not seen**: The index is lazily populated (F05 D3). New anchor pairs that have not been checked yet have no index entries. | Graceful fallback: when the index has no entry for a pair, the pre-check does not filter (fail-open). The full conflict gate catches these at gate 3. |
| **Promotion funnel attribution shift**: Propositions previously rejected at gate 3 are now rejected at the pre-check. Log analysis that tracks gate-specific rejection counts sees different distributions. | The funnel summary log explicitly includes both `post-precheck` and `post-conflict` counts. Consumers can add both for the total conflict-related rejection count. |

## Open Questions

None. All design decisions from the prep document have been resolved:
- Gate placement: After confidence, before dedup (D1).
- Authority threshold: RELIABLE+, hardcoded (D3).
- Fallback behavior: Skip on empty/absent index (D4).
- Fixed-size batching: Deferred (see Deferred Work).
