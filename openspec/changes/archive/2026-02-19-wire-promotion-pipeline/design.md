## Context

`AnchorPromoter.evaluateAndPromote()` currently runs: confidence gate → conflict detection (skip if found) → trust evaluation → promote. Two components exist but are never called:

1. **DuplicateDetector** — `isDuplicate(contextId, candidateText)` exists as a Spring @Service but is not injected into `AnchorPromoter`
2. **ConflictResolver** — `resolve(Conflict)` returns KEEP/REPLACE/COEXIST decisions but `AnchorPromoter` only detects conflicts and skips on any match, never resolving

This means the promotion pipeline has two holes that undermine anchor quality.

## Goals / Non-Goals

**Goals:**
- Wire `DuplicateDetector` into promotion pipeline (inject via constructor, call before promotion)
- Wire `ConflictResolver` into promotion pipeline (resolve instead of blindly skipping)
- Add promotion funnel metrics logging (count at each gate)
- Preserve existing promotion semantics for non-duplicate, non-conflicting propositions

**Non-Goals:**
- Improve DuplicateDetector or ConflictDetector algorithms (separate changes #1 and #3)
- Add new config properties
- Change trust pipeline behavior
- Modify UI

## Decisions

### 1. Promotion Pipeline Order

The complete pipeline after wiring:

```
Proposition
  │
  ├─ 1. Confidence gate (< 0.65 → SKIP)
  │
  ├─ 2. Duplicate detection (NEW)
  │     isDuplicate(contextId, text) → true → SKIP
  │
  ├─ 3. Conflict detection + resolution (ENHANCED)
  │     detect(text, anchors) → conflicts
  │     if conflicts:
  │       for conflict in conflicts:
  │         resolution = resolver.resolve(conflict)
  │         KEEP → skip incoming (existing anchor wins)
  │         REPLACE → archive existing, promote incoming
  │         COEXIST → continue (both allowed)
  │       if any KEEP → SKIP incoming
  │
  ├─ 4. Trust evaluation (unchanged)
  │     ARCHIVE → SKIP, REVIEW → SKIP, AUTO_PROMOTE → proceed
  │
  └─ 5. Promote (unchanged)
        engine.promote(id, initialRank)
```

**Why**: Dedup before conflict detection avoids wasting LLM calls on duplicates. Conflict resolution acts on decisions instead of ignoring them.

**Alternative considered**: Dedup after conflict detection (wastes dedup call if conflict will skip anyway). Order chosen minimizes LLM calls.

### 2. Conflict Resolution Actions

Map `ConflictResolver.Resolution` to promotion decisions:

| Resolution | Action |
|-----------|--------|
| KEEP | Skip incoming proposition (existing anchor wins) |
| REPLACE | Archive existing anchor, promote incoming |
| COEXIST | Allow both — continue promotion |

For REPLACE:
- Call `repository.archiveAnchor(existingAnchorId)` (set rank=0, status=ARCHIVED)
- Then proceed with normal promotion of incoming

**Why**: KEEP and COEXIST are straightforward. REPLACE requires archiving the loser — this is the only new mutation.

### 3. Constructor Injection

Add `DuplicateDetector` to `AnchorPromoter` constructor:

```java
public AnchorPromoter(
    AnchorEngine engine,
    AnchorRepository repository,
    DuplicateDetector duplicateDetector,  // NEW
    TrustPipeline trustPipeline,
    DiceAnchorsProperties properties) { ... }
```

**Why**: Constructor injection per CLAUDE.md. No @Autowired fields.

### 4. Promotion Funnel Logging

Log counts at each gate:
```
logger.info("Promotion funnel: {} extracted, {} passed confidence, {} passed dedup, {} passed conflict, {} passed trust, {} promoted",
    total, postConfidence, postDedup, postConflict, postTrust, promoted);
```

**Why**: Visibility into which gate is most selective. Essential for debugging and demo.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Promotion rate drops significantly** | Expected and correct. Duplicates and conflicts should be filtered. Log funnel to quantify. |
| **REPLACE resolution archives anchor that was correct** | AuthorityConflictResolver uses authority ordering — higher authority wins. CANON is never auto-assigned, so REPLACE only affects lower-authority anchors. |
| **DuplicateDetector adds latency** | Currently LLM-based, but change #1 adds fast-path. Until then, latency is acceptable for async extraction. |

## Migration Plan

1. Add `DuplicateDetector` to `AnchorPromoter` constructor
2. Add dedup check between confidence gate and conflict detection
3. Replace "skip on any conflict" with resolution-based logic
4. Add `archiveAnchor()` method to `AnchorRepository` if not present
5. Add promotion funnel logging
6. Update existing tests to account for new gates
7. Run full test suite

No breaking changes to callers of `evaluateAndPromote()`.
