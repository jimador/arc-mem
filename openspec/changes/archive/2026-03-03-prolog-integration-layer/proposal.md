## Why

The anchor-memory-optimization roadmap identifies DICE Prolog (tuProlog/2p-kt) as a cross-cutting capability across five features (F03, F05, F07, F08, F10), but zero Prolog usage exists in dice-anchors today. The `LOGICAL` conflict detection strategy in `CompositeConflictDetector` throws `UnsupportedOperationException`. Prolog audit pre-filter hooks in `ProactiveMaintenanceStrategy` are documented but unimplemented. The `ComplianceEnforcer` interface accommodates a Prolog invariant enforcer that does not exist. Each feature independently describes Prolog projection and rule design, creating fragmented, duplicated work.

The sleeping-llm research validates deterministic pre-filters for reducing LLM call volume: logically decidable questions (negation, incompatible states, authority invariants) do not require LLM evaluation. Prolog backward chaining provides this deterministic reasoning at sub-millisecond latency for the anchor budget scale (20 anchors).

All prerequisite interfaces exist from Waves 1-3: `ComplianceEnforcer` (F03), `ConflictDetectionStrategy.LOGICAL` + `CompositeConflictDetector` (F05), `ProactiveMaintenanceStrategy` (F07). This is pure implementation work against stable contracts.

## What Changes

Establish DICE Prolog projection as a first-class integration in dice-anchors via four new components and integration wiring.

### Core projection foundation (anchor/ package)

- **`AnchorPrologProjector`**: Shared foundation that projects active anchors to Prolog facts. Uses DICE 0.1.0-SNAPSHOT's `PrologEngine` API (Kotlin, `com.embabel.dice.projection.prolog`). Hybrid fact schema: `anchor/5` for metadata (id, authority ordinal, rank, pinned, reinforcement count) + `claim/4` for entity triples (anchor id, subject, predicate, object) extracted via heuristic SVO decomposition. All three Prolog implementations consume this projector.

### Prolog rule definitions (resources)

- **Contradiction rules**: Layered with short-circuit -- negation first (cheapest), then incompatible states. Rules defined as Prolog clauses, not hardcoded Java. Default rules cover common patterns (alive/dead, present/absent, true/false, open/closed). Extensible per `DomainProfile` via additional rule sets.
- **Invariant rules**: Authority floor and eviction immunity checks expressed as Prolog rules, mirroring `InvariantEvaluator` logic deterministically.

### LOGICAL conflict detection (anchor/ package)

- **`PrologConflictDetector`**: Implements `ConflictDetector`. Projects anchors via `AnchorPrologProjector`, queries for contradictions via `PrologEngine.queryAll()`. Replaces the `UnsupportedOperationException` in `CompositeConflictDetector`'s `LOGICAL` branch.

### Prolog audit pre-filter (anchor/ package)

- **`PrologAuditPreFilter`**: Projects active anchors to Prolog facts, queries for contradiction chains. Anchors flagged by Prolog get score 0.0 in the audit step, reducing LLM batch size. Integrates with `ProactiveMaintenanceStrategy` audit step. Toggleable via configuration.

### Prolog invariant enforcer (assembly/ package)

- **`PrologInvariantEnforcer`**: Implements `ComplianceEnforcer`. Projects active anchors, loads invariant rules, queries for violations. Returns `ComplianceResult` with violation details. Selectable alongside `PostGenerationValidator` and `PromptInjectionEnforcer`.

### A/B testability (sim/ and config)

- Scenario YAML gains conflict detection strategy selection. `ConflictStrategy` enum gains `LOGICAL` value. Simulation runs can select Prolog vs. non-Prolog strategies for direct comparison.

## Capabilities

### New Capabilities

- `prolog-projection`: Shared anchor-to-Prolog-fact projection foundation using DICE `PrologEngine`
- `prolog-conflict-detection`: Deterministic contradiction detection via Prolog backward chaining
- `prolog-audit-prefilter`: Prolog-based pre-filter for proactive maintenance audit
- `prolog-invariant-enforcement`: Prolog-based compliance enforcement

### Modified Capabilities

- `conflict-detection`: `ConflictDetectionStrategy.LOGICAL` becomes functional (replaces `UnsupportedOperationException`)
- `conflict-index`: `ConflictStrategy` enum gains `LOGICAL` value for configuration
- `compliance-enforcement`: `PrologInvariantEnforcer` joins the enforcer family
- `proactive-maintenance-cycle`: Audit step gains optional Prolog pre-filter

## Impact

### New files

- `src/main/java/dev/dunnam/diceanchors/anchor/AnchorPrologProjector.java` -- projection foundation
- `src/main/java/dev/dunnam/diceanchors/anchor/PrologConflictDetector.java` -- LOGICAL strategy implementation
- `src/main/java/dev/dunnam/diceanchors/anchor/PrologAuditPreFilter.java` -- audit pre-filter
- `src/main/java/dev/dunnam/diceanchors/assembly/PrologInvariantEnforcer.java` -- compliance enforcer
- `src/main/resources/prolog/anchor-rules.pl` -- contradiction and invariant rules

### Modified files

- `CompositeConflictDetector.java` -- LOGICAL branch delegates to `PrologConflictDetector`
- `ConflictStrategy.java` -- add `LOGICAL` value
- `AnchorConfiguration.java` -- wire Prolog beans, LOGICAL strategy case
- `DiceAnchorsProperties.java` -- Prolog configuration properties
- `ProactiveMaintenanceStrategy.java` -- Prolog pre-filter integration in audit step

### Constitutional alignment

- **Article I (RFC 2119)**: All normative statements use RFC 2119 keywords.
- **Article II (Neo4j only)**: No new persistence. Prolog operates on in-memory projections of existing Neo4j-persisted anchors.
- **Article III (Constructor injection)**: All new beans use constructor injection.
- **Article IV (Records)**: Projection result types use records where appropriate.
- **Article V (Anchor invariants)**: Invariants A1-A4 preserved. Prolog reads anchor state but does not modify it.
- **Article VI (Sim isolation)**: Prolog projections are per-invocation -- no cross-context state.
- **Article VII (Test-first)**: Unit tests for critical business logic.

## Specification Overrides

None required.
