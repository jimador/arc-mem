## Why

`AnchorPromoter.evaluateAndPromote()` has two critical gaps: `DuplicateDetector` is never called (dead code), and `ConflictResolver.resolve()` is never invoked after conflicts are detected (detection-only, no resolution). This means near-duplicate propositions can be promoted as separate anchors, and contradictory propositions can coexist without resolution. Both undermine the core "anchors resist drift" narrative. This is a blocking fix — changes to duplicate detection (#1) and conflict detection (#3) are dead code without it.

## What Changes

- Wire `DuplicateDetector.isDuplicate()` into the promotion pipeline between conflict detection and trust evaluation
- Wire `ConflictResolver.resolve()` after conflict detection — act on KEEP/REPLACE/COEXIST decisions instead of just skipping
- Add promotion funnel logging (extracted → confidence-filtered → dedup-filtered → conflict-resolved → trust-filtered → promoted)
- No new classes; refactoring of `AnchorPromoter.evaluateAndPromote()` only

## Capabilities

### New Capabilities
- `promotion-pipeline-wiring`: Complete promotion pipeline with duplicate detection and conflict resolution gates

### Modified Capabilities
(None — this fixes existing wiring, not spec-level behavior)

## Impact

- **Files**: `extract/AnchorPromoter.java` (primary), constructor injection of `DuplicateDetector`
- **APIs**: `evaluateAndPromote()` now calls dedup check and conflict resolution; promotion rate may decrease (correct behavior)
- **Config**: No new config; uses existing `DuplicateDetector` and `ConflictResolver` beans
- **Affected**: Anchor promotion flow in chat — propositions that were previously promoted despite being duplicates or conflicting will now be correctly filtered
- **Risk**: Promotion count drops; verify with existing tests

## Constitutional Alignment

- RFC 2119 keywords: Duplicate propositions MUST NOT be promoted as separate anchors; conflicts MUST be resolved before promotion
- Single-module Maven project: Changes contained to `extract/AnchorPromoter.java`
- Authority-upgrade-only invariant preserved: conflict resolution respects authority ordering
