## Why

This repository is close to share-ready, but reviewers still face two problems: technical docs are fragmented, and DICE integration intent is not explicit enough for the primary audience. We need a focused cleanup that strengthens Anchors implementation confidence and presents a clear, concise DICE-integration narrative.

## What Changes

- Harden Anchors implementation paths that MUST be trustworthy before external review (conflict handling, trust behavior, lifecycle invariants, and failure-mode visibility).
- Introduce a pragmatic share-readiness validation contract focused on required implementation/documentation checks (not a formal release program).
- Consolidate technical documentation into two canonical surfaces only: `docs/dev` and `openspec`.
- Execute a one-time migration of current `docs/` content, keeping only needed canonical docs and archiving/deprecating redundant material.
- Add DICE-focused developer docs that clearly explain integration boundaries, data flow, extension points, and adoption path.
- Explicitly document that Anchors augments DICE Agent Memory rather than replacing it:
  - DICE memory retrieval (`searchByTopic`, `searchRecent`, `searchByType`) remains the base retrieval surface.
  - Anchors adds a governed working set for prompt assembly with budget/rank/authority/trust controls.
  - Lower-trust knowledge can remain available with constrained authority instead of being discarded.
- Keep documentation concise and audience-focused; remove extraneous detail that does not help reviewers evaluate Anchors behavior or DICE fit.
- No public API break is planned; any discovered **BREAKING** requirement SHALL be documented before implementation.

## Capabilities

### New Capabilities
- `share-ready-implementation-gate`: Define MUST-pass implementation and documentation checks required before community sharing.
- `developer-documentation-suite`: Define canonical docs structure, migration, and sync contract between `docs/dev` and `openspec`.
- `dice-integration-review-docs`: Define required DICE-integration architecture/design docs for reviewer understanding.

### Modified Capabilities
- `anchor-conflict`: Tighten conflict parse-failure semantics and degraded-outcome visibility.
- `anchor-trust`: Tighten trust re-evaluation auditability and promotion-decision traceability.
- `anchor-lifecycle`: Clarify deterministic lifecycle hook ordering and mutation-block behavior.
- `benchmark-report`: Add required provenance/completeness metadata needed for reviewer interpretation.
- `resilience-report`: Tighten narrative constraints to avoid overstated claims and surface caveats cleanly.
- `run-history-persistence`: Extend replay-grade manifest retention required by report/docs traceability.
- `observability`: Add required diagnostics for degraded decisions and invariant outcomes.

## Impact

- Affected specs (domain paths):
  - `openspec/specs/anchor-conflict/spec.md`
  - `openspec/specs/anchor-trust/spec.md`
  - `openspec/specs/anchor-lifecycle/spec.md`
  - `openspec/specs/benchmark-report/spec.md`
  - `openspec/specs/resilience-report/spec.md`
  - `openspec/specs/run-history-persistence/spec.md`
  - `openspec/specs/observability/spec.md`
  - new: `openspec/specs/share-ready-implementation-gate/spec.md`
  - new: `openspec/specs/developer-documentation-suite/spec.md`
  - new: `openspec/specs/dice-integration-review-docs/spec.md`
- Expected code surfaces:
  - `src/main/java/dev/dunnam/diceanchors/anchor/*`
  - `src/main/java/dev/dunnam/diceanchors/extract/*`
  - `src/main/java/dev/dunnam/diceanchors/sim/engine/*`
  - `src/main/java/dev/dunnam/diceanchors/sim/report/*`
  - `src/main/java/dev/dunnam/diceanchors/sim/views/*`
- Expected documentation surfaces:
  - `docs/dev/*` (canonical developer docs)
  - `openspec/*` (canonical normative specs/design/tasks)
  - migration/cleanup of overlapping material currently in `docs/*`
- External interfaces:
  - Existing runtime API is expected to remain backward compatible for initial review.
  - Any discovered **BREAKING** requirement MUST be explicitly flagged in spec deltas before merge.
- Dependencies/systems:
  - OpenSpec artifact workflow
  - Neo4j run-history schema
  - benchmark/report generation pipeline

## Constitutional Alignment

- This change SHALL keep OpenSpec as the normative source of requirements, design, and tasks.
- All resulting spec requirements MUST use RFC 2119 keywords and GIVEN/WHEN/THEN scenarios.
- Shared technical narratives SHALL remain evidence-based and scoped to reviewer needs.

## Specification Overrides

- None currently requested.
- If any override becomes necessary, it MUST include rationale, affected spec references, and reviewer impact.
