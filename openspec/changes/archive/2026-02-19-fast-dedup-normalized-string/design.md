## Context

Current `DuplicateDetector` invokes the LLM for every candidate proposition, even obvious dupes like exact matches or minor formatting variations. This adds cost and latency to the extraction pipeline. The detector uses `engine.inject(contextId)` to fetch active anchors, then sends them all to the model via a prompt.

No existing fast-path filtering exists. The opportunity is to add a simple normalized-string pass before LLM invocation.

## Goals / Non-Goals

**Goals:**
- Reduce LLM calls for dedup by ~80% (catch obvious dupes early)
- Preserve `DuplicateDetector.isDuplicate()` API contract (same return type, same behavior for novel candidates)
- Make strategy configurable via property (FAST_ONLY, LLM_ONLY, FAST_THEN_LLM)
- Demonstrate cost/perf improvement in demo without introducing instability

**Non-Goals:**
- Build a full dedup SPI framework (simple composition only)
- Persist normalized forms (compute on-the-fly)
- Semantic dedup (that's separate work; fast-path handles lexical matches only)
- Change data models or Neo4j schema

## Decisions

### 1. Composite Pattern with Dual Detection

Use composition, not inheritance: `DuplicateDetector` owns both `NormalizedStringDuplicateDetector` (fast) and delegates to the existing LLM logic (fallback).

```
isDuplicate(candidate)
  ├─ NormalizedStringDuplicateDetector.isDuplicate()
  │   └─ true? → return true (STOP)
  └─ false? → ChatModel.call(LLM prompt) → return result
```

**Why**: Keeps the LLM detector isolated and testable. No breaking changes. Enables toggling via config.

**Alternative considered**: Full strategy SPI (too much complexity for initial work). Hardcoded fallback pattern is simpler and works.

### 2. Normalization Algorithm

Normalize both candidate and each anchor via:
```
1. toLowerCase()
2. replaceAll("\\s+", " ").trim()  // collapse whitespace
3. replaceAll("[^a-z0-9 ]", "")     // strip punctuation and special chars
4. Exact string equality check
```

Cache normalized forms in memory during detection (short-lived).

**Why**: Catches 80%+ of dupes (exact matches, case differences, punctuation, spacing). Simple and fast. No ML required.

**Alternative considered**: Levenshtein distance or fuzzy matching (more complex, less reliable for demo).

### 3. Strategy Configuration

Add property: `anchor.dedup-strategy` with enum values:
- `FAST_ONLY`: NormalizedStringDuplicateDetector only (never calls LLM)
- `LLM_ONLY`: Skip fast-path, go straight to LLM (current behavior)
- `FAST_THEN_LLM`: Fast-path first, LLM fallback for novel (recommended, default)

Inject via `DiceAnchorsProperties` (extend existing config record).

**Why**: Allows tuning behavior for demo (show fast-path benefit) and toggling if issues arise. Property-driven per CLAUDE.md.

### 4. Data Flow

```
Candidate Proposition
  │
  ├─ DuplicateDetector.isDuplicate()
  │   │
  │   ├─ fetch active anchors via engine.inject(contextId)
  │   │
  │   ├─ FOR each anchor:
  │   │   ├─ normalize(anchor.text())
  │   │   ├─ normalize(candidate)
  │   │   └─ exact match? → DUPLICATE (return true)
  │   │
  │   ├─ No match found in fast-path?
  │   │   └─ (if strategy != FAST_ONLY)
  │   │       └─ ChatModel.call(LLM dedup prompt)
  │   │           └─ return LLM result
  │   │
  │   └─ return false (novel, per LLM or fast-path)
  │
  └─ Anchor promotion (if not duplicate)
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Normalization removes nuance** (e.g., "Dr. Smith" → "dr smith") | Accept for MVP. Semantic dedup (separate work) handles edge cases. Test data will be representative. |
| **False negatives** (semantic dupes missed) | LLM fallback catches them. Fast-path is purely lexical. |
| **Performance regression if strategy is FAST_ONLY on tricky data** | Make FAST_THEN_LLM the default. Tests will validate behavior. |
| **Config typos or invalid values** | Validate enum at startup; fail fast if invalid property value. |

## Migration Plan

1. Add `NormalizedStringDuplicateDetector` class (isolated, no changes to existing code yet)
2. Extend `DiceAnchorsProperties` with `dedup-strategy` field
3. Refactor `DuplicateDetector` to use composition and strategy
4. Update `application.yml` with default strategy (FAST_THEN_LLM)
5. Tests: unit test normalization, integration test end-to-end dedup with both strategies
6. Demo: show LLM call reduction in traces/metrics

No breaking changes; rollback is property change to LLM_ONLY.

## Open Questions

- Should normalized forms be cached across multiple `isDuplicate()` calls in a turn? (Low priority; profile if slow)
- Should admin be able to tune normalization rules (e.g., strip digits too)? (Out of scope; hardcoded for now)
