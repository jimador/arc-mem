# OpenSpec Proposal Roadmap from Research Tracks

## Purpose

This roadmap converts the current research set into a sequenced series of OpenSpec proposals.

It is designed to let us move from research -> proposal -> implementation without losing clarity for Anchors + DICE integration.

## Inputs

Research docs in `docs/research/`:

1. `memory-tiering-dice-integration-research.md`
2. `retrieval-quality-control-toolishrag-research.md`
3. `conflict-detection-calibration-research.md`
4. `temporal-validity-bi-temporal-research.md`
5. `compaction-reliability-recovery-research.md`
6. `benchmarking-statistical-rigor-research.md`
7. `operator-defined-invariants-api-research.md`
8. `dice-framework-fit-proposal-outline.md`

## Assumptions

This roadmap assumes parent objectives are complete before executing these proposals:

1. fail-open gate fixes,
2. authority ceiling persistence + enforcement,
3. strict token counting on policy-critical paths.

## Visibility Contract (Applies to Every Proposal)

For each OpenSpec proposal, requirements MUST include visibility.

Minimum:

1. either a UI-visible artifact OR an observability artifact.

Recommended (demo-grade):

1. one UI-visible artifact, and
2. one observability artifact.

Examples:

1. UI: new panel, badge, inspector row, timeline event, run-comparison chart.
2. Observability: structured logs, lifecycle events, counters/histograms, trace spans, audit records.

## Suggested Proposal Series

### Wave 1: Core Memory and Safety

#### Proposal 1: `working-memory-tiering-core`

Source:

1. `memory-tiering-dice-integration-research.md`

Goal:

1. introduce tier metadata and transitions (`T0/T1/T2`) in `dice-anchors`.

Dependencies:

1. parent objectives only.

Visibility:

1. UI: show tier on anchors/propositions in inspector/timeline.
2. Observability: emit tier transition events with reason codes.

Exit criteria:

1. extraction -> episodic tier,
2. promotion -> working tier,
3. transition traces visible.

#### Proposal 2: `conflict-detection-calibration-core`

Source:

1. `conflict-detection-calibration-research.md`

Goal:

1. replace static-threshold conflict decisions with calibrated assessments and explicit `REVIEW/ABSTAIN`.

Dependencies:

1. Proposal 1 recommended, not mandatory.

Visibility:

1. UI: conflict decision breakdown in turn inspector.
2. Observability: structured conflict assessment + decision audit.

Exit criteria:

1. calibrated contradiction pipeline in shadow mode then blocking mode,
2. policy matrix enforced.

#### Proposal 3: `retrieval-quality-gate-toolishrag`

Source:

1. `retrieval-quality-control-toolishrag-research.md`

Goal:

1. introduce retrieval quality adjudication before promotion/replacement mutations.

Dependencies:

1. Proposal 2 (preferred), because conflict policy and retrieval quality are coupled.

Visibility:

1. UI: evidence quality verdict badge on candidate mutations.
2. Observability: `EvidenceBundle` + `QualityVerdict` trace events.

Exit criteria:

1. `ACCEPT/REJECT/RETRIEVE_MORE/REVIEW` quality gate operational,
2. no silent permissive fallback on weak evidence.

### Wave 2: Temporal and Reliability Hardening

#### Proposal 4: `bi-temporal-validity-and-supersession`

Source:

1. `temporal-validity-bi-temporal-research.md`

Goal:

1. add temporal validity semantics (`validFrom/validTo/recordedAt`) and temporal-aware conflict handling.

Dependencies:

1. Proposal 1 (required),
2. Proposal 2 (strongly recommended).

Visibility:

1. UI: timeline-aware fact state and supersession markers.
2. Observability: temporal conflict relation metrics (`TEMPORAL_SUCCESSION`, etc.).

Exit criteria:

1. temporal progression no longer misclassified as contradiction in benchmark scenarios.

#### Proposal 5: `compaction-recovery-guardrails`

Source:

1. `compaction-reliability-recovery-research.md`

Goal:

1. add retry/fallback compaction recovery with no-regression replacement policy.

Dependencies:

1. none beyond parent objectives.

Visibility:

1. UI: compaction status and fallback indicator in run inspector.
2. Observability: compaction attempt counters, failure reason metrics.

Exit criteria:

1. protected-fact retention improves vs detect-only baseline,
2. failed compaction paths are auditable.

### Wave 3: Demo Evidence and Product-Facing Governance

#### Proposal 6 (Nice-to-Have, Demo-High): `benchmarking-and-statistical-rigor`

Source:

1. `benchmarking-statistical-rigor-research.md`

Goal:

1. add repeatable benchmark protocol with confidence intervals and baseline comparisons.

Dependencies:

1. Waves 1-2 for meaningful comparative evidence.

Visibility:

1. UI: benchmark dashboard/scoreboard with CI ranges.
2. Observability: experiment manifests + aggregate metric artifacts.

Exit criteria:

1. demo claims backed by repeated-run statistics.

#### Proposal 7 (Nice-to-Have, Detailed Design First): `operator-invariants-api-and-governance`

Source:

1. `operator-defined-invariants-api-research.md`

Goal:

1. define and implement operator-authored invariants with governance and precedence over extracted anchors.

Dependencies:

1. Proposal 1 recommended,
2. Proposal 2 for conflict policy integration.

Visibility:

1. UI: invariant management and status panel.
2. Observability: immutable mutation audit records.

Exit criteria:

1. `HARD/SOFT/ADVISORY` invariant modes enforced,
2. governed mutation flow operational.

### Wave 4: Upstream Alignment Package

#### Proposal 8: `dice-framework-fit-upstream-proposal`

Source:

1. `dice-framework-fit-proposal-outline.md`

Goal:

1. package validated extension points and compatibility story for upstream review.

Dependencies:

1. at least Proposals 1-3 completed,
2. Proposal 6 strongly recommended for evidence quality.

Visibility:

1. UI: optional "DICE fit" demo walkthrough mode (or scripted run profile).
2. Observability: exportable evidence bundle for proposal appendix.

Exit criteria:

1. proposal-ready artifact set for upstream review.

## OpenSpec Execution Plan

Use one change per proposal slug:

1. `/opsx:new working-memory-tiering-core`
2. `/opsx:new conflict-detection-calibration-core`
3. `/opsx:new retrieval-quality-gate-toolishrag`
4. `/opsx:new bi-temporal-validity-and-supersession`
5. `/opsx:new compaction-recovery-guardrails`
6. `/opsx:new benchmarking-and-statistical-rigor`
7. `/opsx:new operator-invariants-api-and-governance`
8. `/opsx:new dice-framework-fit-upstream-proposal`

For each change:

1. include a `## Visibility Requirements` section in proposal/spec/design,
2. include at least one UI or observability acceptance scenario,
3. include explicit dependency references to prior changes.

## Suggested Immediate Next Step

Start Proposal 1 (`working-memory-tiering-core`) and Proposal 2 (`conflict-detection-calibration-core`) in parallel design work, then implement Proposal 1 first to establish stable memory structure before calibrated conflict rollout.
