# Research: Compaction Reliability and Recovery

## Objective

Define a proposal-ready approach for reliable compaction that preserves protected facts and provides deterministic recovery when compaction quality is insufficient.

This document addresses the selected compaction research track.

## Why This Matters

Current limitation:

1. compaction loss is detected,
2. but there is no automatic recovery path.

That creates a fragile operator-only safety model and can silently degrade grounding quality over long runs.

## Parent Objective Dependencies

Start implementation after:

1. fail-open gate fixes,
2. authority ceiling enforcement,
3. strict token counting for policy-critical paths.

## Current State in This Repo

Known behavior from docs:

1. `CompactionValidator` catches protected-fact loss,
2. no retry/regeneration or automatic fallback,
3. token estimation still heuristic in some paths.

## External Findings

1. Recursive summarization can compound error and lose critical details over repeated compression.
   Source: https://arxiv.org/abs/2308.15022
2. Task-aware compression approaches show quality can be maintained with targeted strategy rather than naive summarization.
   Source: https://openreview.net/pdf?id=7JbSwX6bNL

## Design Options

### Option A: Detect-only (current)

Pros:

1. Simple.

Cons:

1. Manual recovery burden.
2. Weak resilience under long conversations.

### Option B: Retry with guarded regeneration (recommended)

Pros:

1. Strong reliability gain.
2. Minimal architecture disruption.

Cons:

1. Additional latency for failed compactions.

### Option C: Structured compaction from graph/proposition subsets

Pros:

1. Highest reliability potential.

Cons:

1. Bigger implementation effort and schema coupling.

## Recommendation

Adopt Option B first:

1. detect loss,
2. regenerate with stricter constraints,
3. fallback to safe mode if validation still fails.

Then evaluate Option C for high-assurance paths.

## Proposed Recovery Policy

### Validation gates

1. Protected-fact coverage.
2. Contradiction check against protected set.
3. Token budget compliance.

### Recovery sequence

1. Attempt 1: standard compaction.
2. If validation fails, Attempt 2: stricter prompt with explicit missing-fact list.
3. If still failing, Attempt 3: extractive fallback (copy protected facts + minimal narrative summary).
4. If still failing, keep prior context block and mark compaction as failed.

No attempt should silently replace good context with degraded context.

## Data and Observability Additions

1. `compactionAttemptCount`
2. `compactionFailureReason`
3. `missingProtectedFacts`
4. `recoveredByFallback` flag

These fields should be visible in run inspection.

## Integration Plan

### Phase 1: Retry framework

1. Add retry orchestration around compaction call.
2. Add explicit failure reason taxonomy.

### Phase 2: Safe fallback mode

1. Add extractive fallback generator.
2. Add no-regression guard (never replace with lower validity output).

### Phase 3: Policy tuning

1. Tune retry limits and prompt strictness.
2. Profile latency impact on turn flow.

## Evaluation Plan

### Hypotheses

1. Recovery pipeline reduces protected-fact loss rate materially.
2. Fallback mode reduces catastrophic context corruption events.

### Metrics

1. Protected-fact retention rate.
2. Compaction failure rate by scenario.
3. Mean retries per successful compaction.
4. Latency overhead.

## Proposal-Ready Deliverables

1. Compaction Recovery RFC.
2. Failure taxonomy and handling policy.
3. Benchmark results comparing detect-only vs retry+fallback.

## Sources

1. Recursive Summarization: https://arxiv.org/abs/2308.15022
2. ACON Task-Aware Compression: https://openreview.net/pdf?id=7JbSwX6bNL
3. Repo limitations: `docs/known-limitations.md`
