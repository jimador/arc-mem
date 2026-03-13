## Context

The repository already has the correct first-order module boundary: `arcmem-core` owns the ARC-Mem implementation layer and `arcmem-simulator` owns the executable D&D-driven harness. The remaining problem is internal topology. Core still uses the legacy `dev.dunnam.arcmem.arcmem` namespace and still exposes seam types from `dev.dunnam.arcmem.sim.engine`, while simulator packages still flatten unrelated concerns into broad roots such as `chat`, `domain`, `sim.engine`, and `sim.views`.

This change is a structural cleanup pass after the module split. It is intended to make package names communicate ownership and responsibility clearly enough that future refactors can happen inside well-defined areas instead of across accidental package boundaries.

Constraints:

- The module boundary established by `module-topology` remains unchanged.
- DICE remains an external dependency consumed by `arcmem-core`.
- Runtime behavior, scenario behavior, and benchmark semantics are not being redesigned in this change.
- Spring wiring, resource lookup, and test execution must remain functional after repackaging.

## Goals / Non-Goals

**Goals**

- Replace misleading or duplicated package names with canonical, ownership-aligned package roots.
- Separate major bounded areas inside each module so class placement matches responsibility.
- Remove simulator-named packages from core and move seam types into explicit core-owned boundary packages.
- Repackage UI, chat, D&D schema, simulator engine, and reporting code so the simulator structure is readable without module-level tribal knowledge.
- Preserve behavior while changing package declarations, imports, and file locations.

**Non-Goals**

- Changing the two-module Maven topology.
- Redesigning ARC-Mem algorithms, simulator flows, or D&D scenario content.
- Introducing a third module or a first-party `dice` module.
- Performing major API redesign beyond what is required to place types into coherent packages.

## Decisions

### Decision: Adopt canonical module-root package prefixes

`arcmem-core` SHALL use `dev.dunnam.arcmem.core.*` as its canonical package root. `arcmem-simulator` SHALL use `dev.dunnam.arcmem.simulator.*` as its canonical package root.

Rationale:

- The current package roots do not reflect the module split and still carry monolith-era naming.
- The `core` and `simulator` roots make ownership obvious in imports, stack traces, and documentation.

Alternatives considered:

- Keep `dev.dunnam.arcmem.*` in both modules and only refine lower-level packages: rejected because ownership remains ambiguous.
- Rename modules to match current package roots: rejected because the module names are already correct and clearer than the package layout.

### Decision: Organize core by ARC-Mem subdomain, not by technical stereotype

Core SHALL be grouped by ARC-Mem responsibilities rather than generic framework roles. The target topology is:

```text
dev.dunnam.arcmem.core
├── config
├── memory
│   ├── model
│   ├── engine
│   ├── trust
│   ├── conflict
│   ├── maintenance
│   ├── canon
│   ├── budget
│   ├── mutation
│   ├── attention
│   └── event
├── assembly
│   ├── retrieval
│   ├── compaction
│   ├── compliance
│   ├── protection
│   └── budget
├── extraction
├── persistence
├── prompt
└── spi
    └── llm
```

Rationale:

- ARC-Mem is the product domain inside core; package names should surface that domain structure directly.
- Generic package names like `service`, `util`, or one large `arcmem` bucket hide the actual architecture.

Alternatives considered:

- Split by Spring stereotypes (`service`, `config`, `repository`): rejected because it would dissolve domain boundaries and make large areas harder to reason about.

### Decision: Move core seam types into explicit SPI packages

Classes currently living in `dev.dunnam.arcmem.sim.engine` inside `arcmem-core` SHALL move into a core-owned boundary package such as `dev.dunnam.arcmem.core.spi.llm`.

Initial seam types include:

- `ChatModelHolder`
- `LlmCallService`
- `LlmCallTimeoutException`
- `RunHistoryStoreType`

Rationale:

- A core package named `sim.engine` is an architectural lie after the module split.
- These types are integration seams, not simulator implementation types.

Alternatives considered:

- Leave the package in place for compatibility: rejected because it codifies a misleading dependency story.

### Decision: Organize simulator by harness capability

Simulator SHALL use bounded package areas that match the harness responsibilities:

```text
dev.dunnam.arcmem.simulator
├── bootstrap
├── config
├── chat
├── domain
│   └── dnd
├── engine
├── scenario
├── adversary
├── assertions
├── benchmark
├── history
├── report
└── ui
```

UI subpackages MAY be introduced where density justifies it, including `ui.views`, `ui.panels`, `ui.dialogs`, and `ui.controllers`.

Rationale:

- The simulator is a harness with several distinct surfaces. Flattening them under `sim.*` and `views` obscures ownership and leaves too much implicit.
- D&D should remain visible, but explicitly as scenario-domain content under `domain.dnd`, not as a generic `domain` package that looks like product core.

Alternatives considered:

- Keep `sim.*` and only rename `views`: rejected because the rest of the simulator would still be overly coarse.

### Decision: Repackage in phases to limit blast radius

Implementation SHALL be phased:

1. Rename obvious root packages and seam packages.
2. Rehome major bounded areas in core and simulator.
3. Update tests, docs, and resource/config references.
4. Verify build and test behavior before any optional fine-grained sub-splitting.

Rationale:

- This is a large import-graph change. Phasing makes regressions diagnosable.
- The highest-value cleanup comes from the top-level package truthfulness, not from immediately perfect lower-level granularity.

Alternatives considered:

- Perform a fully granular repackaging in a single sweep: rejected as unnecessarily risky.

## Target Package Map

### Core

| Current area | Target package |
| --- | --- |
| `dev.dunnam.arcmem.ArcMemProperties` | `dev.dunnam.arcmem.core.config` |
| `dev.dunnam.arcmem.arcmem` | `dev.dunnam.arcmem.core.memory.*` split by subdomain |
| `dev.dunnam.arcmem.arcmem.attention` | `dev.dunnam.arcmem.core.memory.attention` |
| `dev.dunnam.arcmem.arcmem.event` | `dev.dunnam.arcmem.core.memory.event` |
| `dev.dunnam.arcmem.assembly` | `dev.dunnam.arcmem.core.assembly.*` split by bounded area |
| `dev.dunnam.arcmem.extract` | `dev.dunnam.arcmem.core.extraction.*` |
| `dev.dunnam.arcmem.persistence` | `dev.dunnam.arcmem.core.persistence.*` |
| `dev.dunnam.arcmem.prompt` | `dev.dunnam.arcmem.core.prompt` |
| `dev.dunnam.arcmem.sim.engine` seam classes in core | `dev.dunnam.arcmem.core.spi.llm` or another explicit core boundary package |

### Decision: Use the scratch inventory as the authoritative move source for core

The current implementation proved that a root rename alone is too easy to mistake for a completed repackage. The package inventory in [`docs/package-structure-scratch.md`](../../../docs/package-structure-scratch.md) SHALL be treated as the authoritative source for the remaining core move plan.

Rationale:

- The scratch inventory captures the actual density of the legacy buckets: `arcmem` with 86 classes and `assembly` with 44 classes.
- Those counts show that the real maintainability problem is package overload, not only legacy naming.
- A class-by-class move matrix makes review and verification mechanical instead of interpretive.

### Core move matrix from the scratch inventory

The legacy `dev.dunnam.arcmem.arcmem` bucket SHALL be decomposed as follows:

| Legacy classes | Target package |
| --- | --- |
| `ContextUnit`, `Authority`, `MemoryTier`, `PromotionZone`, `DomainProfile`, `UnitCluster`, `EvictedUnitInfo` | `dev.dunnam.arcmem.core.memory.model` |
| `ArcMemEngine`, `ArcMemConfiguration`, `ContextUnitPrologProjector` | `dev.dunnam.arcmem.core.memory.engine` |
| `TrustScore`, `TrustSignal`, `TrustEvaluator`, `TrustPipeline`, `TrustContext`, `TrustAuditRecord`, `AuditScore`, `TrustConfiguration`, `ImportanceSignal`, `NoveltySignal`, `GraphConsistencySignal` | `dev.dunnam.arcmem.core.memory.trust` |
| `ConflictDetector`, `ConflictResolver`, `ConflictDetectionStrategy`, `ConflictStrategy`, `ConflictType`, `ConflictEntry`, `ConflictIndex`, `BatchConflictResult`, `CompositeConflictDetector`, `LlmConflictDetector`, `NegationConflictDetector`, `PrologConflictDetector`, `AuthorityConflictResolver`, `RevisionAwareConflictResolver`, `InMemoryConflictIndex`, `ConnectedComponentsCalculator`, `SubjectFilter`, `PrologAuditPreFilter` | `dev.dunnam.arcmem.core.memory.conflict` |
| `MaintenanceStrategy`, `MaintenanceContext`, `MaintenanceMode`, `HybridMaintenanceStrategy`, `ProactiveMaintenanceStrategy`, `ReactiveMaintenanceStrategy`, `MemoryPressureGauge`, `PressureDimension`, `PressureScore`, `PressureThreshold`, `CycleMetrics`, `SweepResult`, `SweepType`, `DecayPolicy`, `DecayType` | `dev.dunnam.arcmem.core.memory.maintenance` |
| `CanonizationGate`, `CanonizationRequest`, `CanonizationStatus`, `InvariantEvaluator`, `InvariantEvaluation`, `InvariantRule`, `InvariantRuleProvider`, `InvariantRuleType`, `InvariantStrength`, `InvariantViolationData`, `CompliancePolicy`, `CompliancePolicyMode`, `ComplianceStrength`, `ComplianceConfiguration`, `DemotionReason`, `ProposedAction` | `dev.dunnam.arcmem.core.memory.canon` |
| `BudgetStrategy`, `BudgetStrategyFactory`, `BudgetStrategyConfiguration`, `BudgetStrategyType`, `CountBasedBudgetStrategy`, `InterferenceDensityBudgetStrategy`, `InterferenceDensityCalculator` | `dev.dunnam.arcmem.core.memory.budget` |
| `MutationDecision`, `MutationRequest`, `MutationSource`, `UnitMutationStrategy`, `HitlOnlyMutationStrategy`, `ReinforcementPolicy` | `dev.dunnam.arcmem.core.memory.mutation` |

The legacy `dev.dunnam.arcmem.assembly` bucket SHALL be decomposed as follows:

| Legacy classes | Target package |
| --- | --- |
| `ArcMemLlmReference`, `PropositionsLlmReference`, `RelevanceScorer`, `ScoredUnit`, `RetrievalMode` | `dev.dunnam.arcmem.core.assembly.retrieval` |
| `CompactedContextProvider`, `CompactionConfig`, `CompactionCompleted`, `CompactionDriftEvaluator`, `CompactionLossEvent`, `CompactionResult`, `CompactionValidator`, `SimSummaryGenerator`, `SummaryResult`, `ArcMemContextLock` | `dev.dunnam.arcmem.core.assembly.compaction` |
| `ComplianceAction`, `ComplianceContext`, `ComplianceEnforcer`, `ComplianceEnforcerFactory`, `ComplianceResult`, `ComplianceViolation`, `HybridComplianceEnforcer`, `PostGenerationValidator`, `PromptInjectionEnforcer`, `PrologInvariantEnforcer`, `EnforcementStrategy` | `dev.dunnam.arcmem.core.assembly.compliance` |
| `ProtectedContent`, `ProtectedContentProvider`, `PropositionContentProtector`, `UnitContentProtector`, `UnitConstraint`, `UnitConstraintIndex`, `ConstraintMask` | `dev.dunnam.arcmem.core.assembly.protection` |
| `PromptBudgetEnforcer`, `BudgetResult`, `TokenCounter`, `CharHeuristicTokenCounter`, `LogitBiasEnforcer`, `LogitBiasMap`, `ConstrainedDecodingEnforcer`, `NoOpConstrainedDecodingEnforcer`, `ModelCapabilityDetector` | `dev.dunnam.arcmem.core.assembly.budget` |

Notes:

- `attention` and `event` are already coherent and stay under `dev.dunnam.arcmem.core.memory`.
- `extraction`, `persistence`, `prompt`, and `spi.llm` are currently acceptable top-level bounded areas and do not need further mandatory splitting in this change.

### Simulator

| Current area | Target package |
| --- | --- |
| simulator root bootstrap classes | `dev.dunnam.arcmem.simulator.bootstrap` |
| simulator root config classes | `dev.dunnam.arcmem.simulator.config` |
| `dev.dunnam.arcmem.chat` | `dev.dunnam.arcmem.simulator.chat.*` |
| `dev.dunnam.arcmem.domain` | `dev.dunnam.arcmem.simulator.domain.dnd` |
| `dev.dunnam.arcmem.sim.engine` | `dev.dunnam.arcmem.simulator.engine` plus `scenario` and `history` splits where appropriate |
| `dev.dunnam.arcmem.sim.engine.adversary` | `dev.dunnam.arcmem.simulator.adversary` |
| `dev.dunnam.arcmem.sim.assertions` | `dev.dunnam.arcmem.simulator.assertions` |
| `dev.dunnam.arcmem.sim.benchmark` | `dev.dunnam.arcmem.simulator.benchmark` |
| `dev.dunnam.arcmem.sim.report` | `dev.dunnam.arcmem.simulator.report` |
| `dev.dunnam.arcmem.sim.views` | `dev.dunnam.arcmem.simulator.ui.*` |

## Risks / Trade-offs

- [Import churn] -> Mitigation: repackage by bounded area and keep changes mechanically consistent.
- [Spring scan regressions] -> Mitigation: move bootstrap/config packages first and verify application startup wiring by module tests.
- [Resource path regressions] -> Mitigation: avoid changing resource locations unless the owning package change requires it; verify prompt loading explicitly.
- [Excessive subpackage fragmentation] -> Mitigation: start with top-level ownership-correct packages and only split further where the package size justifies it.
- [Documentation drift] -> Mitigation: update architecture docs in the same change so canonical package names match the code.

## Migration Plan

1. Define the canonical `package-topology` spec and update affected topology/documentation specs.
2. Repackage root bootstrap/config classes in simulator and root config classes in core.
3. Move core seam types out of `sim.engine` and remove the `dev.dunnam.arcmem.arcmem` duplication.
4. Split the legacy core `arcmem` bucket into `model`, `engine`, `trust`, `conflict`, `maintenance`, `canon`, `budget`, and `mutation`.
5. Split the legacy `assembly` bucket into retrieval, compaction, compliance, protection, and budget subpackages.
6. Repackage simulator areas: chat, D&D domain, engine, adversary, assertions, benchmark, history, report, UI.
7. Update tests and imports in lockstep with package moves.
8. Run full build and targeted simulator/core verification.
9. Update architecture documentation and package references.

Rollback strategy:

- Because this change is structural, rollback is by reverting the package moves and import rewrites as a single unit.
- No runtime data migration is involved.

## Open Questions

- Should `RunHistoryStoreType` stay alongside LLM seam types in a shared SPI package, or move into a narrower simulator-history boundary if core does not truly need it?
- Which simulator engine classes should be split immediately into `scenario` and `history`, versus left in `engine` for a later cleanup pass?
- Is there enough density in `chat` to warrant immediate subpackages for `application`, `model`, `persistence`, `tools`, and `ui`, or should that remain a second-phase refinement?
