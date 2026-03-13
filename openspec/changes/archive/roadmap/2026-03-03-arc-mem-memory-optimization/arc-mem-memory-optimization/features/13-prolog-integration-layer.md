# Feature: Prolog Integration Layer

## Feature ID

`F13`

## Summary

Establish DICE Prolog projection as a first-class integration in context units. Implement Prolog-backed alternatives for existing interfaces -- LOGICAL conflict detection (F05), Prolog audit pre-filter (F07), and Prolog invariant enforcer (F03) -- enabling A/B comparison of deterministic Prolog reasoning vs. LLM/heuristic approaches in the simulator. Zero new dependencies -- tuProlog (2p-kt) is already on classpath via DICE 0.1.0-SNAPSHOT. This feature consolidates all Prolog work from the roadmap's cross-cutting capability table into a single Wave 4 implementation effort.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. **Problem addressed**: The unit-memory-optimization roadmap identifies DICE Prolog (tuProlog/2p-kt) as a cross-cutting capability across 5 features (F03, F05, F07, F08, F10), but no dedicated feature establishes Prolog integration. Currently there is zero Prolog usage in context units. The `LOGICAL` conflict detection strategy throws `UnsupportedOperationException`. Prolog pre-filter hooks in F07 are documented but unimplemented. The `ComplianceEnforcer` interface accommodates a `PrologInvariantEnforcer` that does not exist. Each feature independently describes Prolog projection and rule design, creating fragmented, duplicated work.
2. **Value delivered**: A shared proposition-to-Prolog-fact projection foundation, plus three Prolog-backed implementations behind existing interfaces. Deterministic reasoning replaces expensive LLM calls for logically decidable questions (contradiction detection, invariant checking, audit pre-filtering). Sleeping-llm research validates deterministic pre-filters for reducing LLM call volume. The simulator can directly compare Prolog vs. non-Prolog approaches per scenario YAML.
3. **Why now**: Wave 4 -- all prerequisite interfaces exist from Waves 1-3 (F03 `ComplianceEnforcer`, F05 `ConflictDetectionStrategy`/`CompositeConflictDetector`, F07 `ProactiveMaintenanceStrategy`). This is pure implementation work against stable contracts.

## Scope

### In Scope

1. **Proposition-to-Prolog-fact projection** (`ContextUnitPrologProjector`): Shared foundation layer that projects active context units to Prolog facts. Each context unit becomes a fact encoding id, text tokens, authority, rank, pinned status, and relationships. Projection MUST use DICE 0.1.0-SNAPSHOT's `PrologEngine` API. This layer is consumed by all three Prolog implementations.
2. **Prolog rule definitions**: Reusable rule sets for contradiction detection (negation patterns, incompatible states), support/entailment inference, and invariant checking. Rules SHOULD be extensible per `DomainProfile`.
3. **LOGICAL conflict detection strategy** (F05 interface): Implement the reserved `LOGICAL` strategy in `ConflictDetectionStrategy`/`CompositeConflictDetector`. Project context units as Prolog facts, query for contradictions via backward chaining. Replaces the `throw new UnsupportedOperationException()` currently in `CompositeConflictDetector`. Shared interface: `ConflictDetectionStrategy` enum + `CompositeConflictDetector` switch branch.
4. **Prolog audit pre-filter** (F07 interface): Implement `PrologAuditPreFilter` that projects active context units to Prolog facts and queries for contradiction chains and unsupported claims. Context Units flagged by Prolog get score 0.0 in the audit step, reducing the LLM batch size. Toggleable in `ProactiveMaintenanceStrategy` via scenario YAML. Shared interface: toggleable pre-filter flag in the proactive maintenance sweep.
5. **Prolog invariant enforcer** (F03 interface): Implement `PrologInvariantEnforcer` as a `ComplianceEnforcer` that uses Prolog rules to check context unit invariants deterministically. Provides a mid-tier between lexical checks (fast/crude) and full LLM validation (slow/accurate). Shared interface: `ComplianceEnforcer`.
6. **A/B testability**: Every Prolog implementation MUST be selectable per-simulation via scenario YAML config. The simulator MUST support running the same scenario with Prolog vs. non-Prolog implementations for direct comparison of accuracy, latency, and LLM call reduction.
7. **Performance**: All Prolog query execution MUST be sub-second for context unit sets up to budget max (default 20).

### Out of Scope

1. `PrologRelationshipScorer` (F08 Prolog implementation) -- lower priority; heuristic scoring works well for the demo repo.
2. Prolog transitive closure for interference density (F10) -- F10 itself is not yet implemented.
3. Prolog rule UI/editor -- rules are defined in code and configuration only.
4. Cross-context Prolog fact sharing -- each context maintains its own projection.
5. Prolog projection for non-context unit propositions (cold propositions that have not been promoted).

## Dependencies

1. Feature dependencies: F03 (compliance-enforcement-layer), F05 (precomputed-conflict-index), F07 (proactive-maintenance-cycle). All interfaces MUST exist before implementation begins -- all are done.
2. Technical prerequisites: `ComplianceEnforcer` interface (F03), `ConflictDetectionStrategy` + `CompositeConflictDetector` (F05), `ProactiveMaintenanceStrategy` (F07), DICE Prolog projection (tuProlog/2p-kt via DICE 0.1.0-SNAPSHOT -- already on classpath, zero new dependencies).
3. Priority: SHOULD (valuable for LLM call reduction and deterministic reasoning, but existing heuristic/LLM implementations work).
4. OpenSpec change slug: `prolog-integration-layer`.
5. Research rec: Cross-cutting (roadmap Prolog strategy table), sleeping-llm (deterministic pre-filter validation).

## Research Requirements

1. Open questions:
   - What Prolog fact schema best represents context units? (id, text tokens, authority, rank, relationships)
   - What contradiction detection rules are most effective? (negation patterns, incompatible states, temporal inconsistency)
   - How to handle Prolog projection for context units with complex text? (tokenization, entity extraction, simplification)
2. Required channels: `codebase`, DICE 0.1.0-SNAPSHOT `PrologEngine` API
3. Research completion gate: At least one Prolog-backed implementation SHOULD pass A/B comparison tests against its non-Prolog counterpart before all three implementations are completed.

## Impacted Areas

1. **`context unit/` package (primary)**: `CompositeConflictDetector` gains `LOGICAL` strategy implementation (replacing `UnsupportedOperationException`). New `PrologConflictDetector` implementing the logical detection path. New `ContextUnitPrologProjector` for proposition-to-fact projection.
2. **`assembly/` package**: New `PrologInvariantEnforcer` implementing `ComplianceEnforcer`. Integration with existing `PostGenerationValidator` and `PromptInjectionEnforcer` as alternative strategies.
3. **`context unit/` package (maintenance)**: `ProactiveMaintenanceStrategy` gains Prolog audit pre-filter hook. New `PrologAuditPreFilter` for deterministic contradiction chain and unsupported claim detection.
4. **`sim/engine/` package**: Scenario YAML gains Prolog strategy configuration. Strategy selection wiring for A/B comparison.
5. **`ArcMemProperties`**: New Prolog-related configuration properties (enable/disable per strategy, rule paths, performance thresholds).

## Visibility Requirements

### UI Visibility

1. User-facing surface: SimulationView SHOULD display which detection/enforcement strategy is active per run. RunInspectorView SHOULD show Prolog pre-filter hit counts alongside LLM audit results.
2. What is shown: Strategy selection (Prolog vs. non-Prolog), Prolog query hit/miss counts, pre-filter reduction metrics (context units flagged by Prolog vs. total audited).
3. Success signal: A simulation run with LOGICAL strategy produces conflict detection results comparable to SEMANTIC strategy with measurably lower latency.

### Observability Visibility

1. Logs/events/metrics: Prolog projection timing, query execution timing, hit/miss counts per query type (contradiction, unsupported claim, invariant). Logger MUST emit projection and query summaries at INFO level.
2. Trace/audit payload: Full Prolog fact set per projection, query results per context unit, A/B comparison metrics (Prolog vs. non-Prolog accuracy, latency, LLM call count).
3. How to verify: Log grep for `prolog.projection`, `prolog.query`, `prolog.conflict` events; compare strategy metrics in simulation reports.

## Acceptance Criteria

1. At least one Prolog-backed implementation MUST pass A/B comparison tests against its non-Prolog counterpart.
2. Proposition-to-Prolog-fact projection MUST work for active context unit sets up to budget max (default 20).
3. Prolog query latency MUST be < 100ms for 20-context unit sets.
4. `LOGICAL` conflict strategy MUST NOT throw `UnsupportedOperationException` -- it MUST produce conflict detection results.
5. Simulator MUST support selecting Prolog vs. non-Prolog per scenario YAML.
6. Every Prolog implementation MUST implement an existing interface (`ComplianceEnforcer`, `ConflictDetectionStrategy`, or pre-filter toggle) -- no new top-level abstractions.
7. Zero new dependencies -- all Prolog functionality MUST use tuProlog (2p-kt) already on classpath via DICE 0.1.0-SNAPSHOT.
8. Prolog rules SHOULD be extensible per `DomainProfile`.
9. `PrologInvariantEnforcer` MUST be selectable alongside `PostGenerationValidator` and `PromptInjectionEnforcer`.
10. `PrologAuditPreFilter` MUST be toggleable per-simulation and MUST reduce the LLM batch size in the audit step when enabled.

## Risks and Mitigations

1. Risk: Prolog fact schema too coarse -- complex context unit text loses semantic nuance in tokenization.
   Mitigation: Prolog is a pre-filter, not a replacement. Context Units not flagged by Prolog still get LLM evaluation. Start with entity-level projection (subject-verb-object triples) and refine.
2. Risk: Prolog query performance degrades with complex rule sets.
   Mitigation: Sub-second requirement enforced by tests. Budget max is 20 context units -- Prolog is efficient at this scale. Short-circuit evaluation on first match for contradiction queries.
3. Risk: DICE `PrologEngine` API changes in future DICE versions.
   Mitigation: `ContextUnitPrologProjector` encapsulates all DICE Prolog interaction. API changes are localized to one class.
4. Risk: Prolog rules produce false positives (flag non-contradicting context units as conflicting).
   Mitigation: A/B comparison tests validate Prolog results against LLM/heuristic baselines. Conservative rule design -- flag only clear logical contradictions.
5. Risk: A/B comparison shows Prolog provides minimal benefit over heuristics for small context unit sets.
   Mitigation: Feature is SHOULD priority, not MUST. Value increases with unit budget size and rule complexity. Even minimal benefit validates the integration path for future use.

## Proposal Seed

### Suggested OpenSpec Change Slug

`prolog-integration-layer`

### Proposal Starter Inputs

1. Problem statement: The roadmap documents DICE Prolog as a cross-cutting capability across 5 features, but no dedicated feature establishes the integration. Currently zero Prolog usage in context units. The `LOGICAL` conflict strategy throws `UnsupportedOperationException`. Three existing interfaces (`ComplianceEnforcer`, `ConflictDetectionStrategy`, `ProactiveMaintenanceStrategy` pre-filter) are designed to accept Prolog implementations that do not yet exist.
2. Why now: All prerequisite interfaces exist from Waves 1-3. This is pure implementation work against stable contracts. Consolidating Prolog work into a single feature avoids fragmented, duplicated projection and rule design across multiple changes.
3. Constraints: Zero new dependencies -- DICE 0.1.0-SNAPSHOT provides tuProlog (2p-kt). Every implementation MUST have a non-Prolog counterpart behind the same interface. Sub-second query latency for 20-context unit sets. A/B testable per-simulation via scenario YAML.
4. Visible outcomes: `LOGICAL` strategy works. Prolog pre-filter reduces LLM calls in audit sweep. Prolog invariant enforcer provides deterministic compliance checking. A/B comparison reports in simulation runs.

### Suggested Capability Areas

1. Proposition-to-Prolog-fact projection foundation (ContextUnitPrologProjector).
2. Prolog rule definitions (contradiction, support, invariant checking).
3. LOGICAL conflict detection strategy implementation in CompositeConflictDetector.
4. Prolog audit pre-filter for ProactiveMaintenanceStrategy.
5. Prolog invariant enforcer as ComplianceEnforcer implementation.
6. A/B testability wiring (scenario YAML config, strategy selection).

### Candidate Requirement Blocks

1. Requirement: The proposition-to-Prolog-fact projection SHALL project all active context units in a context to Prolog facts encoding id, authority, rank, pinned status, and text-derived entities.
2. Scenario: In a 10-turn simulation with 15 active context units, the LOGICAL conflict detection strategy SHALL detect the same contradictions as SEMANTIC strategy for logically decidable conflicts (e.g., "X is alive" vs. "X is dead"), with query latency < 100ms.
3. Scenario: In a proactive maintenance sweep with 20 active context units, the Prolog audit pre-filter SHALL flag at least one logically inconsistent context unit (contradiction chain or unsupported claim), reducing the LLM batch size by at least one context unit.

## Research Findings

| Task ID | Key Finding | Evidence Source | Confidence | Impact on Scope |
|---------|-------------|-----------------|------------|-----------------|
| Cross-cutting | DICE Prolog (tuProlog/2p-kt) already on classpath via DICE 0.1.0-SNAPSHOT. Zero new dependencies. `PrologEngine.query()`, `queryAll()`, `findAll()` API available. | `openspec/roadmaps/unit-memory-optimization-roadmap.md` sec Cross-Cutting | High | Confirms zero-dependency implementation path. |
| Rec A | Sleeping-llm deterministic pre-filters reduce LLM call volume in audit cycles. | `openspec/research/llm-optimization-external-research.md` sec 3.1 | High | Validates Prolog as audit pre-filter strategy. |
| Rejected | Drools/KIE (30 JARs) and Evrete (forward chaining only) rejected in favor of DICE Prolog. | `openspec/research/dependency-research-memory-optimization.md` | High | Confirms Prolog is the right tool -- no alternatives needed. |
| Roadmap table | 5-feature Prolog strategy table maps implementations to shared interfaces. | `openspec/roadmaps/unit-memory-optimization-roadmap.md` sec Cross-Cutting | High | Defines the integration surface for all implementations. |

## Validation Plan

1. Unit tests: `ContextUnitPrologProjector` tested with mock context units; verify fact generation for all context unit properties. Each Prolog query type tested independently with known context unit sets.
2. Integration tests: LOGICAL conflict detection tested against same context unit pairs as SEMANTIC strategy; verify equivalent results for logically decidable conflicts.
3. A/B comparison tests: Same simulation scenario run with Prolog vs. non-Prolog strategies; compare accuracy, latency, and LLM call counts.
4. Performance tests: Prolog query latency benchmarked for 1, 10, and 20 context unit sets; all MUST be < 100ms.
5. Observability validation: Prolog projection and query events logged at INFO; metrics traceable in simulation reports.

## Known Limitations

1. Prolog fact projection loses semantic nuance -- complex context unit text is reduced to entity-level facts. Contradictions requiring deep semantic understanding (irony, implication, context-dependent meaning) are not detectable by Prolog. LLM evaluation remains necessary for these cases.
2. Prolog rules are domain-independent by default. Domain-specific contradiction patterns (e.g., medical incompatibilities, temporal physics constraints) require custom rule sets per `DomainProfile`. Out-of-the-box coverage is limited to negation patterns and incompatible states.
3. The `PrologRelationshipScorer` (F08) and Prolog transitive closure (F10) are not included in this feature. These represent additional Prolog integration work that depends on their respective features being implemented.
4. DICE `PrologEngine` API is experimental in 0.1.0-SNAPSHOT. API stability is not guaranteed across DICE versions. The `ContextUnitPrologProjector` encapsulation mitigates this but does not eliminate the risk.
5. A/B comparison validity depends on test scenario design. Scenarios must include both logically decidable and semantically complex conflicts to properly assess Prolog vs. LLM coverage boundaries.

## Suggested Command

`/opsx:new prolog-integration-layer`
