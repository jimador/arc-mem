# Prep: Ablation Condition Expansion

## Feature

F02 — ablation-condition-expansion

## Key Decisions

1. **Bypass mechanism**: Use runtime config flags in `SimulationRuntimeConfig` (consistent with existing `rankMutationEnabled` / `authorityPromotionEnabled` pattern).
2. **NO_TRUST bypass value**: Trust pipeline returns neutral score (1.0 / pass-through), not null. This avoids NPE downstream.
3. **NO_LIFECYCLE scope**: Disables DecayPolicy, ReinforcementPolicy, AND reactivation. Different from NO_RANK_DIFFERENTIATION which sets all ranks to 500 but may still run policies.
4. **Naming alignment**: Defer rename of existing enum values to avoid breaking Neo4j stored reports. Add `displayName()` method instead.

## Open Questions

1. Does disabling trust change conflict resolution outcomes? Need to trace the dependency.
2. Is there a dormancy code path separate from decay that NO_LIFECYCLE should also disable?
3. Should NO_COMPLIANCE disable both PromptInjectionEnforcer and PostGenerationValidator, or just PostGenerationValidator?

## Acceptance Gate

- 3 new conditions in AblationCondition enum
- Each condition produces measurably different results from FULL_ARC (verified by pilot run)
- UI shows all conditions
- Tests verify isolation properties

## Research Dependencies

R02 (Ablation plumbing) — code path analysis should inform implementation

## Handoff Notes

Follow existing AblationCondition pattern. Each condition has `applySeedUnits()` for seed modification + runtime flags for execution-time bypass. Layer boundary: all bypass logic in simulator, not core.
