<!-- sync: openspec/specs/memory-unit-conflict, openspec/specs/memory-unit-trust, openspec/specs/memory-unit-lifecycle, openspec/specs/observability, openspec/specs/share-ready-implementation-gate -->
<!-- last-synced: 2026-02-25 -->

# Status and Caveats

What works, what is approximate, and what is still missing. This is the honest-status document — read it before forming expectations about evaluation maturity.

## What works

### Promotion pipeline

Current flow in `SemanticUnitPromoter`:

```text
confidence -> dedup -> conflict -> trust -> promote
```

Intentionally strict and ordered. Lifecycle order must remain stable:

```text
invariant checks -> trust gate -> mutation + lifecycle events
```

Hard invariants: rank clamp boundaries, budget cap enforcement, authority upgrade/demotion guards.

### Conflict pipeline

Stack: `NegationConflictDetector` (lexical) → `LlmConflictDetector` (semantic) → `AuthorityConflictResolver`.

Expected parse-failure behavior: mark degraded/review path, do not auto-accept candidate. Fail-open behavior previously existed and is now fixed.

### Trust pipeline

`TrustPipeline.evaluate(node, contextId)` combines signals and emits `TrustScore` with zone + audit.

### Harness and reporting

What works: turn-level artifacts (messages/verdicts/context traces), benchmark aggregates with confidence intervals, per-fact and per-strategy outputs.

### Observability

Memory unit operations emit OTel spans with useful fields (invariants, degraded conflict counts, trust outcomes). Langfuse integration through OTEL is useful for model-call debugging, but not tuned for production-scale telemetry.

## Approximations and limitations

| ID | Issue | Impact | Mitigation now | Next fix |
|---|---|---|---|---|
| L1 | Trust thresholds uncalibrated | profile behavior may drift across model/domain changes | conservative profiles | build labeled calibration set |
| L2 | Compaction is detect-only | protected fact loss can persist | validator warning events | add retry + extractive fallback |
| L3 | Token estimator is heuristic (`chars / 4`) | strict budget behavior is approximate | acceptable for demo | switch to model-aware token counting |
| L4 | `NO_TRUST` ablation missing | trust contribution not isolated | none | implement ablation condition |
| L5 | Drift judge uncalibrated vs humans | judge bias can masquerade as improvement | conservative interpretation | build adjudication set + agreement scoring |
| L6 | Generated attacks vary in quality | weak attacks can inflate robustness | deterministic pack exists | add quality gates/templates |
| L7 | Run manifest incomplete | reproducibility/debugging weaker than needed | partial metadata persisted | persist config/prompt hashes + seed |
| L8 | Revision classification unlabeled | false revision labels can leak bad edits | conservative conflict defaults | create labeled revision benchmark |
| L9 | Default mutation strategy is HITL-only | conflict resolver cannot auto-replace by default | intentional for demo safety | add additional strategies |
| L10 | Cross-session memory is limited | long-term continuity is weak | context isolation by design | optional long-term memory profile |

Additional caveats:

- **Proposition-text keying** in parts of trust flow is still collision-prone for identical text; should move toward proposition-ID keying.
- **Interference-density calibration**: the sleeping-LLM phase transition threshold (13-14 facts) was measured for weight-editing (8B model, MEMIT), not prompt-injection. Empirical calibration via simulation benchmarks is required before activating `InterferenceDensityBudgetStrategy`. Count-based strategy remains the default.
- **Adaptive prompt footprint**: the sleeping-LLM analogy (MEMIT scale-down as LoRA absorbs edits) does not directly translate to prompt-based systems — each API call is a fresh context window. Authority-graduated templates are an efficiency bet, not a correctness guarantee. `ComplianceEnforcer` is the safety net.

## Credibility blockers

Before making stronger claims:

1. `NO_TRUST` results
2. Calibrated drift judge
3. Full run manifest provenance
4. Deterministic vs stochastic evidence separation

## Research origins

This codebase adopts techniques from two papers:

- **Sleeping LLM** (Zenodo 18778768/18779159): Wake/sleep memory consolidation. Key finding: proactive sleep cycles recover degraded knowledge from 40% to 100% recall within 4 cycles. The "drowsiness signal" is the basis for `MemoryPressureGauge`. The 5-step proactive maintenance cycle maps to sleeping-LLM's sleep phases. Known risks: pruning death spiral, "Aria Effect" (some facts inherently harder to maintain), phase transition at 13-14 facts (recall drops from 0.92 to 0.57).

- **Google AI STATIC**: Sparse matrix constrained decoding. Key result: 100% constraint compliance at 0.25% inference time. Basis for `ComplianceEnforcer` abstraction, precomputed conflict index (offline-index-for-online-lookup), and fixed-size batch padding. Full constrained decoding requires local model infrastructure not available via OpenAI API.

## A/B testability constraint

Every new subsystem is A/B testable in the simulation harness. Every strategy, enforcer, or scorer has a counterpart behind the same interface. The simulator selects between them per-scenario YAML. This enables quantitative validation (e.g., SEMANTIC vs. LOGICAL conflict detection; REACTIVE vs. PROACTIVE maintenance) using existing adversarial scenarios.

## Prolog integration choices

- Drools/KIE (30 JARs, forward chaining) and Evrete (forward chaining only) were rejected. Prolog backward chaining is strictly more capable and already available via DICE 0.1.0-SNAPSHOT.
- Prolog is a pre-filter, not a replacement. Semantic contradictions still require LLM judgment.
- All DICE Prolog interaction encapsulated in `MemoryUnitPrologProjector` to isolate from `PrologEngine` API changes.

## Already fixed

- Conflict parser fail-open path is fixed.
- Parse failures in conflict detection no longer silently promote contradictory candidates.

## Open research uncertainty (not defects)

- Better semantics for revision vs contradiction vs world progression.
- Best cascade strategy for dependent memory units after supersession.
- Temporal validity vs authority interaction rules.
- Cross-model generalization behavior.
