> **Migrated:** This content has been consolidated into [`docs/dev/known-issues.md`](dev/known-issues.md). This file is retained as a transitional pointer and will be removed in a future cleanup.

---

# Known Limitations

This document is an explicit list of current gaps and deferred work. `dice-anchors` is an exploration-quality demo, not a production-hardened memory system.

## Trust Evaluation

### Profile Tuning

The three predefined domain profiles (`BALANCED`, `SECURE`, `NARRATIVE`) are still manually tuned by inspection.

Issue:

1. thresholds are not calibrated against a labeled ground-truth dataset,
2. profile behavior may be brittle across model versions and domains.

Improvement ideas:

1. build a labeled calibration set for promotion/conflict decisions,
2. run grid-search or Bayesian threshold tuning by profile,
3. publish confidence intervals for each profile's false-promote and false-reject rates.

## Compaction

### Summary Quality and Recovery

`CompactionValidator` detects loss of protected facts but does not automatically recover.

Issue:

1. detection without retry/fallback can still leave degraded context quality,
2. operator intervention is required for failures.

Improvement ideas:

1. add retry with stricter regeneration constraints,
2. add extractive fallback mode that preserves protected facts,
3. never replace existing context with a lower-validity compaction output.

### Token Estimation

Some paths still rely on coarse token heuristics.

Issue:

1. estimated token counts can diverge from model reality,
2. strict budget guarantees are weaker with heuristic counts.

Improvement ideas:

1. use model-specific tokenizer/provider count endpoints for strict paths,
2. record estimate-vs-actual deltas during runs.

## Simulation Harness

### Single-Threaded UI

The UI currently runs one scenario at a time.

Issue:

1. limited throughput for larger experiment sets.

Improvement ideas:

1. add headless batch runner for parallel scenario execution,
2. keep UI single-run but consume batch outputs for analysis views.

### Statistical Rigor

Single runs are non-deterministic and not statistically meaningful.

Issue:

1. comparisons can be overfit to one run,
2. claim confidence is weak without repeated trials.

Improvement ideas:

1. add repeated-run benchmark protocol with confidence intervals,
2. add baseline-vs-candidate effect-size reporting,
3. persist experiment manifests (model/version/prompt/scenario hashes).

### Auto-Generated Adversarial Messages

Fallback adversarial generation is still simplistic.

Issue:

1. random strategy/fact selection can produce weak or incoherent attacks.

Improvement ideas:

1. add curated attack templates by tactic class,
2. add red-team quality filters before accepting generated attack turns.

## Persistence

### Neo4j Dependency

A running Neo4j instance is required for normal operation.

Issue:

1. local quick-start friction for users without Docker.

Improvement ideas:

1. offer a lightweight local mode for exploration,
2. provide a fixture-backed testing profile for non-Neo4j environments.

### Cross-Session Persistence Scope

Anchors are scoped to per-session contexts in current chat flow.

Issue:

1. no user-facing long-term continuity across app restarts by default.

Improvement ideas:

1. add optional cross-session memory profile,
2. add retention policy and archive/restore controls.

## Deferred Research Tracks (Not Currently Prioritized for Implementation)

These are important but not selected as immediate research docs in the current cycle.

### Calibration and Policy Tuning

Issue:

1. budget, decay, authority thresholds, and profile cutoffs are still hand-tuned,
2. decision policy may not be stable across models and domains.

Improvement ideas:

1. design a calibration workflow with labeled data,
2. add profile-specific threshold recommendation reports,
3. add periodic re-calibration as model versions change.

### Adversarial Methodology and Red-Team Coverage

Issue:

1. current scenarios emphasize direct contradiction and reframing,
2. coordinated multi-turn and gradual drift attacks are underrepresented.

Improvement ideas:

1. build an explicit adversarial taxonomy (`setup`, `build`, `payoff`, `drift`),
2. add automated red-team generation with quality gates,
3. track attack efficacy distributions, not only per-run outcomes.

### Cross-Model Generalization

Issue:

1. most testing has centered on OpenAI model behavior,
2. anchor compliance may vary with instruction-following strength by model family.

Improvement ideas:

1. create a model matrix benchmark (at least 3 model families),
2. maintain per-model policy/prompt compatibility notes,
3. run compatibility checks before claiming generalized robustness.
