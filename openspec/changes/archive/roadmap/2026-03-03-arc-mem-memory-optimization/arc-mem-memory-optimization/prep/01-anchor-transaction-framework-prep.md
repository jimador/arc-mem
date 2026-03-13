# Prep: Context Unit Transaction Framework

**Feature**: F01 — `unit-transaction-framework`
**Wave**: 1
**Priority**: MUST
**Depends on**: none

## RFC 2119 Compliance

All normative statements use RFC 2119 keywords.

## Locked Decisions

1. **API shape**: `@UnitTransactional` annotation for declarative use + `UnitTransactionTemplate` for programmatic use. Both patterns are well-established in Spring. The programmatic template MUST be the primary API; the annotation is syntactic sugar via AOP.
2. **Snapshot/validate/rollback pattern**: Transaction lifecycle is snapshot-before -> execute -> validate-invariants -> commit-or-rollback. This matches sleeping-llm's validation-gated operations (PPL rollback threshold) adapted to context unit invariants.
3. **InvariantEvaluator integration**: Transaction validation MUST delegate to the existing `InvariantEvaluator`. No duplicate validation logic. Violations returned by `InvariantEvaluator` trigger rollback.
4. **Rollback = state restore**: Rollback restores context unit state (rank, authority, trust score, pinned) from `UnitSnapshot` via `ContextUnitRepository` bulk update. No compensating actions or saga patterns.
5. **Opt-in, non-breaking**: Non-transactional code paths have zero overhead. The framework does not wrap existing operations unless explicitly requested.
6. **Lifecycle events**: `TransactionBegun`, `TransactionCommitted`, `TransactionRolledBack` events extend the existing `ContextUnitLifecycleEvent` sealed hierarchy.

## Open Questions

| # | Question | Options | Leaning | Resolution Trigger |
|---|----------|---------|---------|-------------------|
| 1 | **Nested transaction semantics** | (a) Inner rollback always rolls back outer (flat). (b) Inner rollback is independent; outer can still commit (savepoint). (c) Configurable per-transaction. | (b) Savepoint semantics -- matches Spring `REQUIRES_NEW` and provides the most flexibility. | Design phase. Validate against F07 maintenance sweep use case (sweep contains sub-operations that may fail individually). |
| 2 | **Snapshot granularity** | (a) Full context snapshot (all context units in context). (b) Affected-only snapshot (context units touched by the operation). (c) Lazy snapshot (capture on first mutation). | (c) Lazy snapshot -- minimal overhead, captures only what is needed. Risk: first mutation in a batch may not know all affected context units upfront. | Design phase. Profile snapshot overhead in simulation. |
| 3 | **Neo4j transaction mapping** | (a) Each `ContextUnitTransaction` maps to a Neo4j transaction. (b) `ContextUnitTransaction` is purely domain-level; Neo4j operations use their own transactions per-write. (c) Hybrid: batch the rollback restore into a single Neo4j transaction. | (b) Domain-level. Neo4j transactions are managed by Drivine per-repository-call. Rollback issues individual restore calls. (c) is better for atomicity but requires Drivine transaction integration. | Design phase. Evaluate Drivine's programmatic transaction API. |
| 4 | **Thread-local vs. explicit context** | (a) Thread-local `UnitTransactionContext` (Spring-style). (b) Explicit context parameter passed through method signatures. | (a) Thread-local -- consistent with Spring `TransactionSynchronizationManager` pattern. Async operations document that context propagation is the caller's responsibility. | Spec phase. |

## Visibility Contract

| Surface | What | When | Format |
|---------|------|------|--------|
| RunInspectorView | Transaction outcome (committed/rolled-back) | Per-turn when transactions are used | Status badge + reason text in context trace |
| Structured logs | `transaction.begin`, `transaction.commit`, `transaction.rollback` | Each transaction lifecycle transition | INFO level with context ID, context unit count, duration, reason |
| OTEL spans | `context unit.transaction` span | Each transaction | Attributes: `id`, `scope`, `outcome`, `duration_ms`, `unit_count` |
| Lifecycle events | `TransactionBegun`, `TransactionCommitted`, `TransactionRolledBack` | Each transition | Standard `ContextUnitLifecycleEvent` payload |

## Acceptance Gates

| Gate | Verification | Command |
|------|-------------|---------|
| Transaction wraps batch promote without behavior change | Unit test: batch promote 5 context units inside transaction, no invariant violations, all promoted. Compare result to non-transactional batch promote. | `./mvnw test -pl . -Dtest=UnitTransactionManagerTest` |
| Rollback restores rank + authority + trust | Unit test: snapshot, mutate ranks/authorities/trust, trigger invariant violation, verify rollback restores all fields to snapshot values. | `./mvnw test -pl . -Dtest=UnitTransactionManagerTest` |
| Non-transactional paths unchanged | Regression test: existing `ArcMemEngineTest` suite passes without modification. | `./mvnw test` |
| Lifecycle events emitted | Unit test: verify `TransactionBegun`, `TransactionCommitted`, `TransactionRolledBack` events published. | `./mvnw test -pl . -Dtest=UnitTransactionManagerTest` |

## Small-Model Constraints

- **Max 5 files per task** (new types: `UnitSnapshot`, `UnitTransactionManager`, `UnitTransactionTemplate`, `@UnitTransactional`, transaction events)
- **Verification**: `./mvnw test` MUST pass after each task
- **No schema changes**: `PropositionNode` is not modified
- **Scope boundary**: `context unit/` and `persistence/` packages only; `sim/` consumption is a separate task

## Task Sketch

| # | Scope | Files | Gate |
|---|-------|-------|------|
| T1 | `UnitSnapshot` record + snapshot capture from `ContextUnitRepository` | `UnitSnapshot.java`, `ContextUnitRepository.java` | Snapshot captures all context unit fields for a context |
| T2 | `UnitTransactionManager` + `UnitTransactionTemplate` with commit/rollback | `UnitTransactionManager.java`, `UnitTransactionTemplate.java`, `UnitTransactionContext.java` | Transaction wraps operation, rollback restores state |
| T3 | `@UnitTransactional` annotation + AOP interceptor | `UnitTransactional.java`, `ContextUnitTransactionalAspect.java` | Annotation-based transaction demarcation works |
| T4 | Transaction lifecycle events | `TransactionBegun.java`, `TransactionCommitted.java`, `TransactionRolledBack.java` (within sealed hierarchy) | Events published and consumable |
| T5 | InvariantEvaluator integration + validation gates | `UnitTransactionManager.java` (update), `UnitTransactionManagerTest.java` | Invariant violations trigger rollback |

## Risks Requiring Design Attention

1. **Drivine bulk update API**: Rollback restore requires setting multiple context unit fields in a single repository call. Verify that `ContextUnitRepository` supports bulk update or if a new Cypher query is needed.
2. **Sealed hierarchy extension**: Adding transaction events to `ContextUnitLifecycleEvent` sealed interface requires modifying the sealed permits list. Verify no exhaustive switch expressions break.
