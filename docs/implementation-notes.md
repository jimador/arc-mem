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

## 8. Research origins

This codebase adopts techniques from two papers:

- **Sleeping LLM** (Zenodo 18778768/18779159): Wake/sleep memory consolidation. Key finding: proactive sleep cycles recover degraded knowledge from 40% to 100% recall within 4 cycles. The "drowsiness signal" is the basis for `MemoryPressureGauge`. The 5-step proactive maintenance cycle maps to sleeping-llm's sleep phases. Known risks: pruning death spiral, "Aria Effect" (some facts inherently harder to maintain), phase transition at 13-14 facts (recall drops from 0.92 to 0.57).

- **Google AI STATIC**: Sparse matrix constrained decoding. Key result: 100% constraint compliance at 0.25% inference time. Basis for `ComplianceEnforcer` abstraction, precomputed conflict index (offline-index-for-online-lookup), and fixed-size batch padding. Full constrained decoding requires local model infrastructure not available via OpenAI API.

## 9. A/B testability constraint

Every new subsystem is A/B testable in the simulation harness. Every strategy, enforcer, or scorer has a counterpart behind the same interface. The simulator selects between them per-scenario YAML. This enables quantitative validation (e.g., SEMANTIC vs. LOGICAL conflict detection; REACTIVE vs. PROACTIVE maintenance) using existing adversarial scenarios.

## 10. Prolog integration choices

- Drools/KIE (30 JARs, forward chaining) and Evrete (forward chaining only) were rejected. Prolog backward chaining is strictly more capable and already available via DICE 0.1.0-SNAPSHOT.
- Prolog is a pre-filter, not a replacement. Semantic contradictions still require LLM judgment.
- All DICE Prolog interaction encapsulated in `AnchorPrologProjector` to isolate from `PrologEngine` API changes.

## 11. Interference-density calibration caveat

The sleeping-llm phase transition threshold (13-14 facts) was measured for weight-editing (8B model, MEMIT), not prompt-injection. Empirical calibration via simulation benchmarks is required before activating `InterferenceDensityBudgetStrategy`. Count-based strategy remains the default until calibrated.

## 12. Adaptive prompt footprint limitation

The sleeping-llm analogy (MEMIT scale-down as LoRA absorbs edits) does not directly translate to prompt-based systems — each API call is a fresh context window. Authority-graduated templates are an efficiency bet, not a correctness guarantee. `ComplianceEnforcer` is the safety net for compliance degradation when templates are condensed.
