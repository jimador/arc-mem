<!-- sync: openspec/specs/anchor-conflict, openspec/specs/anchor-trust, openspec/specs/anchor-lifecycle, openspec/specs/observability -->
<!-- last-synced: 2026-02-25 -->

# Implementation Notes

Implementation details and caveats used during iteration.

## 1. Promotion pipeline is stable

Current flow in `AnchorPromoter`:

```text
confidence -> dedup -> conflict -> trust -> promote
```

It is intentionally strict and ordered.

## 2. Conflict pipeline behavior

Stack:
- `NegationConflictDetector` (lexical)
- `LlmConflictDetector` (semantic)
- `AuthorityConflictResolver`

Expected parse-failure behavior (important):
- mark degraded/review path
- do not auto-accept candidate

Fail-open behavior previously existed here and is now fixed.

## 3. Trust pipeline details

`TrustPipeline.evaluate(node, contextId)` combines signals and emits `TrustScore` with zone + audit.

Current caveat:
- thresholds are manually tuned, not calibration-driven

Technical note:
- proposition-text-keying in parts of trust flow is still collision-prone for identical text and should move toward proposition-ID keying.

## 4. Lifecycle order (must remain stable)

```text
invariant checks -> trust gate -> mutation + lifecycle events
```

Invariants to keep hard:
- rank clamp boundaries
- budget cap enforcement
- authority upgrade/demotion guards

## 5. Context compaction caveats

- `CompactionValidator` is detect-only.
- `CharHeuristicTokenCounter` is approximate.
- strict budget scenarios need model-aware tokenization if this ever graduates beyond demo scope.

Approximation currently used:

```text
estimatedTokens ~= characters / 4
```

## 6. Harness/reporting status

What works:
- turn-level artifacts (messages/verdicts/context traces)
- benchmark aggregates with confidence intervals
- per-fact and per-strategy outputs

What still needs work:
- missing `NO_TRUST`
- incomplete run manifest provenance
- partial instrumentation for non-constraint drift categories

## 7. Observability notes

Anchor operations emit OTel spans with useful fields (invariants, degraded conflict counts, trust outcomes).

Langfuse integration through OTEL is useful for model-call debugging, but this setup is not tuned for production-scale telemetry.
