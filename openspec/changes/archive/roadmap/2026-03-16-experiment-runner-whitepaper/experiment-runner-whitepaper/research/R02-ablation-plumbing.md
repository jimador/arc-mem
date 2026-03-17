# Research Task: Ablation Condition Plumbing

## Task ID

`R02`

## Target Features

F02 (Ablation Condition Expansion)

## Research Question

What runtime flags and code paths need modification to independently disable trust, compliance, and lifecycle subsystems for new ablation conditions?

## Channels

- codebase

## Timebox

30 minutes

## Success Criteria

Per-condition implementation sketch with:
1. Affected classes and methods
2. Config flag name and location
3. Bypass mechanism (flag check, neutral value injection, or strategy swap)

## Preliminary Analysis

### NO_TRUST Condition

**Goal**: Disable TrustPipeline evaluation while keeping all other subsystems active.

**Affected code paths**:
- `TrustPipeline.evaluate()` — returns trust score that gates promotion
- `SimulationTurnExecutor` — calls trust pipeline during extraction phase
- `SemanticUnitPromoter` — may use trust scores for promotion decisions

**Bypass mechanism options**:
1. Config flag `trustEnabled: false` → TrustPipeline returns neutral pass-through score (e.g., 1.0)
2. AblationCondition sets `trustPipelineEnabled: false` in SimulationRuntimeConfig
3. SimulationTurnExecutor checks flag and skips trust evaluation

**Preferred**: Option 2 — consistent with existing pattern where AblationCondition sets runtime flags

### NO_COMPLIANCE Condition

**Goal**: Disable ComplianceEnforcer checks while keeping all other subsystems active.

**Affected code paths**:
- `ComplianceEnforcer.evaluate()` — called during prompt assembly or post-generation
- `PostGenerationValidator` — LLM-based compliance check
- `PromptInjectionEnforcer` — default (always ACCEPT)

**Bypass mechanism**: Config flag `complianceEnabled: false` → ComplianceEnforcer returns ACCEPT without evaluation

### NO_LIFECYCLE Condition

**Goal**: Disable decay, reinforcement, and reactivation policies while keeping injection and authority active.

**Affected code paths**:
- `DecayPolicy.apply()` — reduces activation scores over time
- `ReinforcementPolicy.apply()` — increases activation scores on mention
- Reactivation logic in maintenance strategy
- `ReactiveMaintenanceStrategy` and `ProactiveMaintenanceStrategy`

**Bypass mechanism**: Config flag `lifecycleEnabled: false` → maintenance strategy returns units unchanged

**Note**: This is DIFFERENT from existing `rankMutationEnabled: false` (NO_RANK_DIFFERENTIATION) — NO_LIFECYCLE disables the policies themselves, while NO_RANK_DIFFERENTIATION sets all ranks to 500 but may still run policies that have no effect.

## Open Questions

1. Does `rankMutationEnabled: false` already effectively disable decay/reinforcement? If so, NO_LIFECYCLE may need a different mechanism to be distinguishable.
2. How does trust pipeline interact with conflict resolution? Disabling trust might change conflict outcomes.
3. Is there a dormancy/reactivation code path separate from decay?

## Dependencies

- Codebase exploration of TrustPipeline, ComplianceEnforcer, DecayPolicy, ReinforcementPolicy
- Review of existing AblationCondition implementation pattern
