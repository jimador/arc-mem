# Feature: Ablation Condition Expansion

## Feature ID

`F02`

## Summary

Add the ablation conditions required by the whitepaper's evaluation matrix that don't yet exist in the codebase: NO_TRUST, NO_COMPLIANCE, and NO_LIFECYCLE. Align existing condition names with whitepaper terminology. Each condition MUST independently disable a single ARC subsystem while keeping all others active, enabling clean hypothesis testing.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: The whitepaper defines 7 conditions (4 core + 3 secondary). The codebase has 4, and two of those use different names than the paper. Missing NO_TRUST blocks hypothesis H2 ("Do trust and authority controls contribute independently?") and H4 ("Do compliance-gated high-authority units improve resistance?").
2. Value delivered: Complete ablation matrix enabling all 5 hypotheses to be tested.
3. Why now: Every experiment run without NO_TRUST produces incomplete evidence. This is the highest-impact infrastructure gap.

## Scope

### In Scope

1. New `AblationCondition` entries: NO_TRUST, NO_COMPLIANCE, NO_LIFECYCLE
2. Runtime flags in `SimulationRuntimeConfig` to disable trust pipeline, compliance enforcement, and lifecycle (decay/reinforcement/reactivation) independently
3. Plumbing through `SimulationTurnExecutor` to respect new flags
4. Condition name alignment: rename FULL_AWMU → FULL_ARC, NO_AWMU → NO_ACTIVE_MEMORY (or add aliases) per F01 terminology decision
5. UI update in `ExperimentConfigPanel` to show new conditions
6. Tests for each new condition's isolation properties

### Out of Scope

1. RETRIEVAL_ONLY condition (requires retrieval subsystem not yet built)
2. Custom user-defined conditions (future work)
3. Condition combinations (each condition disables exactly one subsystem)

## Dependencies

1. Feature dependencies: F01 (terminology alignment decision)
2. Technical prerequisites: Understanding of TrustPipeline, ComplianceEnforcer, DecayPolicy, ReinforcementPolicy call paths
3. Parent objectives: Whitepaper evaluation matrix completeness

## Research Requirements

1. Open questions: What exact code paths need bypassing for each condition? Can trust/compliance/lifecycle be disabled via config flags or do they need code-level switches?
2. Required channels: codebase
3. Research completion gate: Per-condition implementation sketch with affected classes documented

## Impacted Areas

1. Packages/components: `benchmark/AblationCondition`, `engine/SimulationRuntimeConfig`, `engine/SimulationTurnExecutor`, `ui/panels/ExperimentConfigPanel`
2. Data/persistence: New condition names in experiment reports stored in Neo4j
3. Domain-specific subsystem impacts: TrustPipeline, ComplianceEnforcer, DecayPolicy, ReinforcementPolicy — each gets a conditional bypass

## Visibility Requirements

### UI Visibility

1. User-facing surface: ExperimentConfigPanel shows all 7 conditions as selectable checkboxes
2. What is shown: Condition name + short description of what it disables
3. Success signal: User can select NO_TRUST and run an experiment; results show trust-related metrics differ from FULL_ARC

### Observability Visibility

1. Logs/events/metrics: Each run logs which condition is active and which subsystems are disabled
2. Trace/audit payload: `SimulationRunRecord` includes condition name and runtime config flags
3. How to verify: Compare FULL_ARC vs NO_TRUST runs — trust scores should remain at initial values in NO_TRUST

## Acceptance Criteria

1. `AblationCondition` enum MUST include NO_TRUST, NO_COMPLIANCE, NO_LIFECYCLE
2. NO_TRUST MUST disable `TrustPipeline` evaluation while keeping all other subsystems active
3. NO_COMPLIANCE MUST disable `ComplianceEnforcer` checks while keeping all other subsystems active
4. NO_LIFECYCLE MUST disable decay, reinforcement, and reactivation policies while keeping injection and authority active
5. Each condition MUST be selectable in `ExperimentConfigPanel`
6. Each condition MUST produce measurably different results from FULL_ARC on at least one metric
7. Condition names SHOULD align with whitepaper terminology per F01 decision

## Risks and Mitigations

1. Risk: Disabling trust pipeline may cascade into unexpected behavior in conflict resolution
2. Mitigation: NO_TRUST should set trust scores to a neutral pass-through value, not remove trust fields entirely
3. Risk: Condition naming rename breaks existing experiment reports in Neo4j
4. Mitigation: Add display name mapping; keep internal enum stable or migrate stored reports

## Proposal Seed

### Suggested OpenSpec Change Slug

`ablation-condition-expansion`

### Proposal Starter Inputs

1. Problem statement: The whitepaper's evaluation matrix requires 7 ablation conditions to test 5 hypotheses. Only 4 exist. NO_TRUST is critical for H2 and H4 — the paper cannot make claims about trust's independent contribution without it.
2. Why now: Every experiment run without these conditions produces data that cannot populate the ablation table (Table 3 in outline).
3. Constraints: Each condition MUST disable exactly one subsystem. Core logic MUST NOT reference simulation concepts (layer boundary). Conditions MUST be idempotent (AC2 invariant).
4. Outcomes: Complete condition set enabling full hypothesis testing matrix.

### Suggested Capability Areas

1. Trust pipeline bypass
2. Compliance enforcement bypass
3. Lifecycle policy bypass
4. Condition naming alignment

### Candidate Requirement Blocks

1. Requirement: NO_TRUST condition MUST produce identical results to FULL_ARC except for trust-gated behaviors
2. Scenario: Running FULL_ARC vs NO_TRUST on the same scenario produces different fact survival rates when trust would normally block a contradicting proposition

## Validation Plan

1. Unit tests: Each condition's `applySeedUnits()` tested for idempotency
2. Integration tests: Run each condition against a baseline scenario, verify subsystem is actually disabled (not just flagged)
3. Observability: Log output confirms subsystem bypass messages

## Known Limitations

1. RETRIEVAL_ONLY condition deferred — requires retrieval infrastructure
2. Condition combinations (e.g., NO_TRUST + NO_COMPLIANCE) not supported — each is independent

## Suggested Command

`/opsx:new ablation-condition-expansion`
