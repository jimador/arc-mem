# Implementation Notes

<!-- sync: openspec/specs/anchor-conflict, openspec/specs/anchor-trust, openspec/specs/anchor-lifecycle, openspec/specs/observability -->
<!-- last-synced: 2026-02-25 -->

Caveats, architectural decisions, and instrumentation details for the anchor engine and simulation harness.

## 1. Engine Architecture Decisions

The anchor engine was built by surveying the Tor anchor-engine-core and selectively adopting patterns for demo scope.

### Adopted

| Pattern                                                        | Source                                 | dice-anchors Location                                                  |
|----------------------------------------------------------------|----------------------------------------|------------------------------------------------------------------------|
| RFC 2119 compliance preamble in prompt injection               | `DefaultPromptAssembler`               | `src/main/resources/prompts/dice-anchors.jinja`, `AnchorsLlmReference` |
| Injection toggle + context trace visibility                    | Tor `anchor-sim-injection-toggle` spec | `ContextTrace.injectionEnabled`, sim panels                            |
| Core drift metrics (fact survival, contradiction, attribution) | Tor `drift-evaluation` spec            | `ScoringService`                                                       |
| Trust scoring model (pipeline, evaluator, signals, profiles)   | Tor `trust-scoring` spec               | `TrustPipeline`, `TrustEvaluator`                                      |

### Remaining Candidates (not yet adopted)

| # | Pattern                                                                                 | Value for Demo                   | Complexity |
|---|-----------------------------------------------------------------------------------------|----------------------------------|------------|
| 1 | Authority-tiered compliance directives (separate instruction tiers per authority level) | Stronger prompt framing          | Low-medium |
| 2 | Token-budgeted prompt assembly (SPI-based `TokenCounter` + `PromptBudget`)              | Hard budget enforcement          | Medium     |
| 3 | Hybrid conflict detection (lexical first, then semantic on subject-filtered candidates) | Fewer LLM calls, better coverage | Medium     |
| 4 | Fast+fallback duplicate detection (normalized-string pre-check before LLM dedup)        | Cleaner anchor list, lower cost  | Low        |
| 5 | Lifecycle listener SPI for metrics/audit                                                | Telemetry without coupling       | Medium     |
| 6 | Per-context lock with idle cleanup                                                      | Correct concurrent mutation      | Medium     |
| 7 | Prompt ordering contract tests                                                          | Clearer causal framing evidence  | Low        |
| 8 | Deterministic simulation tests (canned responses)                                       | CI reliability                   | Medium     |
| 9 | Lightweight prompt variant A/B runs                                                     | Side-by-side framing evidence    | Low-medium |

## 2. Conflict Detection Pipeline

### Current Pipeline

1. **`NegationConflictDetector`** (`src/main/java/.../anchor/NegationConflictDetector.java`) -- lexical negation overlap with static threshold at line 35.
2. **`LlmConflictDetector`** (`src/main/java/.../anchor/LlmConflictDetector.java`) -- LLM-based batch conflict check. Parse failures in `parseBatchConflictResponse()` previously fell through to "no conflicts" (fail-open). This was fixed; parse failures now route to degraded/review handling.
3. **`AuthorityConflictResolver`** (`src/main/java/.../anchor/AuthorityConflictResolver.java`) -- resolves detected conflicts using authority tiers. Static thresholds at lines 33, 35, 42.

### Resolution Outcomes

`KEEP_EXISTING`, `REPLACE`, `DEMOTE_EXISTING`, `COEXIST`.

### ACON1 Behavior

When the conflict detector cannot parse LLM output, the system must not auto-accept the candidate. The corrected behavior marks the run as degraded and routes the candidate to review — not silent promotion.

### Subject Filtering

`SubjectFilter` (`src/main/java/.../anchor/SubjectFilter.java`) uses regex-based subject extraction (lines 25-27). On extraction failure, it returns all anchors as candidates (line 47) — deliberate fail-wide, trading precision for safety.

## 3. Trust Pipeline

### Flow

1. **Signal collection** -- `TrustSignal` implementations evaluate source authority (string-contains heuristic at lines 141-145), consistency, and corroboration.
2. **Batch evaluation** -- `TrustPipeline` (`src/main/java/.../anchor/TrustPipeline.java`) runs signals in batch. Results keyed by proposition text (line 52), which risks collision on identical text from different propositions. Migration to proposition-ID keys is tracked.
3. **Profile-gated decision** -- Three domain profiles (`BALANCED`, `SECURE`, `NARRATIVE`) apply thresholds. Manually tuned; no calibration dataset exists yet.
4. **Authority ceiling** -- Persisted on promotion (`PropositionNode.authorityCeiling`) and checked during reinforcement-driven upgrades. Effective re-evaluation ceiling is `min(trustCeiling, persistedCeiling)`.

### Audit Records

Trust decisions are captured in anchor lifecycle events (`src/main/java/.../anchor/event/`). Each mutation records trigger reason and signal values — enough for post-hoc analysis.

## 4. Lifecycle Hook Order

Anchor mutations follow a fixed order:

1. **Invariant checks** -- budget cap (max 20), rank clamping [100-900], authority upgrade-only constraint.
2. **Trust gate** -- trust pipeline evaluation determines promotion eligibility and authority ceiling.
3. **Mutation** -- create/promote/reinforce/archive/evict with lifecycle event emission.

### Key Invariants

- **ALC1**: Authority only goes up. `PROVISIONAL -> UNRELIABLE -> RELIABLE -> CANON`. CANON is never auto-assigned.
- **ALC2**: Budget enforcement evicts lowest-ranked non-pinned anchors when active count exceeds 20.

## 5. Context Compaction

### Detect-Only Validation

`CompactionValidator` detects loss of protected facts in compacted output but does not retry or recover. Operator intervention required on failure.

### Token Estimation Caveats

`CharHeuristicTokenCounter` (`src/main/java/.../assembly/CharHeuristicTokenCounter.java`, lines 21, 28) uses a fixed chars-per-token ratio. Good enough for rough simulation, but not for hard budget enforcement. Model-specific tokenization needed for strict paths.

### PromptBudgetEnforcer

`PromptBudgetEnforcer` (`src/main/java/.../assembly/PromptBudgetEnforcer.java`) manages anchor injection token allocation. Preamble is always mandatory; lower-priority content gets truncated when budget is tight.

## 6. Harness Instrumentation

### Current Coverage

| Metric                                                                 | Status       |
|------------------------------------------------------------------------|--------------|
| Turn-level artifacts (messages, verdicts, context traces)              | Instrumented |
| Aggregated benchmark metrics with effect-size reporting                | Instrumented |
| Per-fact survival and contradiction-detail tables                      | Instrumented |
| Scenario corpus: 24 scenarios, 357 scripted turns, 180 evaluated turns | Active       |
| Strategy catalog: 20 strategies, 17 exercised in scenarios             | Partial      |

### Drift Taxonomy

Five drift categories. Instrumentation depth varies:

| Category              | Measurement                                      | Instrumentation Status       |
|-----------------------|--------------------------------------------------|------------------------------|
| Constraint violation  | `CONTRADICTED` verdicts, severity split          | Full                         |
| Identity drift        | Identity-tagged fact checks                      | Partial (tagging incomplete) |
| Objective drift       | Objective-tagged facts + `RECALL_PROBE`          | Partial                      |
| Source-of-truth drift | Authority transition tracking                    | Partial                      |
| Silent drift          | Persistent `NOT_MENTIONED` + semantic divergence | Under-instrumented           |

### Gaps

1. **Full run manifest** -- seed, model IDs, prompt hashes, effective config hash not yet persisted per run.
2. **Category-level drift outputs** -- only contradiction-centric metrics are fully reported; the other four drift classes lack dedicated aggregate outputs.
3. **`NO_TRUST` ablation** -- not yet implemented; cannot isolate trust contribution from other anchor effects.
4. **Uncovered strategies** -- `GRADUAL_EROSION`, `IMPLICATION_REVERSAL`, `CAUSAL_CHAIN_EROSION` have no scenario coverage.
5. **Stochastic vs deterministic separation** -- adaptive/generated scenarios and scripted scenarios are mixed in evidence narratives. Deterministic claim matrix is better for causal comparisons; stochastic scenarios for robustness stress testing.

### New Adversarial Scenario Families

| Family                           | Tests For                                                       |
|----------------------------------|-----------------------------------------------------------------|
| `authority-inversion-chain`      | Higher-authority facts retained despite pressure                |
| `conflicting-canon-crisis`       | Explicit conflict signaling, no silent canon blending           |
| `budget-starvation-interference` | Load-bearing anchor preservation under context flood            |
| `evidence-laundering-poisoning`  | Quarantine/rejection of weakly supported "authoritative" claims |

## 7. Observability

### OTEL Span Attributes

Anchor engine operations emit OpenTelemetry spans with these attributes:

- **Invariant summary** -- budget utilization, rank distribution, authority tier counts.
- **Degraded conflict counts** -- number of conflict detections that fell back to degraded mode (parse failures, timeouts).
- **Trust decision traces** -- signal values, profile applied, promotion outcome per anchor mutation.

### Langfuse Integration

Optional observability via Langfuse (separate Docker Compose stack). OTEL endpoint at `http://localhost:3000/api/public/otel`. Captures LLM call traces for conflict detection, trust evaluation, and drift judgment.
