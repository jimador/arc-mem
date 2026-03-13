# Feature: Context Unit Transaction Framework

> **STATUS: DEFERRED — Future Enhancement**
>
> This feature is too heavy for the current PoC scope. It is preserved as a specification for future implementation. Active features (F02, F07) SHOULD design their APIs to accommodate transactional behavior later — e.g., sweep methods accepting optional snapshot/restore callbacks with no-op defaults.

## Feature ID

`F01`

## Summary

Introduce a declarative transaction/rollback framework for context unit operations, modeled after Spring's `@Transactional`. Snapshot context unit state before operations, execute, validate invariants, commit or rollback. Primary use case is bulk operations (maintenance sweeps, batch promotions, decay cycles) but the interface MUST support individual operations too.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: All context unit lifecycle operations are currently forward-only. Maintenance sweeps, batch promotions, and decay cycles can leave the system in a degraded state if interrupted or if results violate invariants. There is no way to atomically undo a set of mutations. When a decay cycle reduces ranks below thresholds triggering cascading authority demotions, the only recovery path is manual intervention.
2. **Value delivered**: Safe, reversible bulk operations. Proactive maintenance (F07) and compliance enforcement (F03) both require guaranteed rollback when their operations produce invariant violations. The framework also enables speculative execution -- try an operation, inspect the result, decide whether to commit.
3. **Why now**: Wave 1 foundation. F07 (proactive maintenance cycle) depends on transactional sweeps. F03 (compliance enforcement) needs transactional retry semantics. Building the framework first ensures these features have a solid foundation rather than ad-hoc rollback logic.

## Scope

### In Scope

1. `UnitTransactionTemplate` programmatic API (analogous to Spring's `TransactionTemplate`).
2. `@UnitTransactional` annotation for declarative transaction demarcation on service methods.
3. `UnitSnapshot` value type capturing context unit state (IDs, ranks, authorities, trust scores, pinned status) at a point in time.
4. `UnitTransactionManager` managing snapshot lifecycle, invariant validation, commit/rollback orchestration.
5. Validation gates: configurable invariant checks that determine commit/rollback (reusing `InvariantEvaluator`).
6. Rollback implementation: restore snapshot state via `ContextUnitRepository` bulk update.
7. Transaction lifecycle events (`TransactionBegun`, `TransactionCommitted`, `TransactionRolledBack`).
8. Integration with `ArcMemEngine` bulk operations (batch promote, decay cycle, maintenance sweep).

### Out of Scope

1. Distributed transactions across multiple Neo4j databases.
2. Saga-style compensation patterns (rollback is snapshot-restore, not compensating actions).
3. Neo4j-level transaction management (this operates at the context unit domain level, above the persistence layer).
4. Automatic retry on rollback (callers decide whether to retry).
5. UI for transaction management (visibility is via lifecycle events and RunInspectorView traces).

## Dependencies

1. Feature dependencies: none.
2. Priority: MUST.
3. OpenSpec change slug: `unit-transaction-framework`.
4. Research rec: user requirement, inspired by sleeping-llm validation-gated operations + Spring `@Transactional`.

## Research Requirements

None. The pattern is well-established (Spring `TransactionTemplate`, sleeping-llm PPL rollback threshold). No open research questions.

## Impacted Areas

1. **`context unit/` package (primary)**: New types -- `UnitSnapshot` (record), `UnitTransactionManager` (service), `UnitTransactionTemplate` (programmatic API), `@UnitTransactional` (annotation), `UnitTransactionContext` (thread-local state).
2. **`context unit/event/` package**: New lifecycle events -- `TransactionBegun`, `TransactionCommitted`, `TransactionRolledBack` extending `ContextUnitLifecycleEvent`.
3. **`persistence/` package**: New repository methods for bulk snapshot capture and bulk state restore. No schema changes to `PropositionNode`.
4. **`sim/engine/` package (consumer)**: `SimulationTurnExecutor` MAY wrap decay cycles and maintenance operations in transactions. `SimulationService` MAY use transactions for scene-setting extraction.

## Visibility Requirements

### UI Visibility

1. Transaction outcome (committed/rolled-back) MUST be visible in RunInspectorView context traces when transactions are used during simulation turns.
2. Rollback events SHOULD display the reason (which invariant was violated) and the count of context units restored.

### Observability Visibility

1. Transaction lifecycle MUST emit structured log events at INFO level: `transaction.begin` (context ID, scope), `transaction.commit` (context unit count, duration), `transaction.rollback` (reason, context unit count restored).
2. Transaction events SHOULD include OpenTelemetry span attributes: `context unit.transaction.id`, `context unit.transaction.scope`, `context unit.transaction.outcome`.
3. Rollback events MUST log the specific invariant violations that triggered the rollback.

## Acceptance Criteria

1. Bulk context unit operations (batch promote, decay cycle, maintenance sweep) MUST be wrappable in a transaction via `UnitTransactionTemplate`.
2. Failed invariant validation MUST trigger automatic rollback to snapshot state.
3. Transaction outcome (committed/rolled-back/reason) MUST be observable in lifecycle events.
4. Rollback MUST restore all context unit states (rank, authority, trust score, pinned status) to pre-transaction values.
5. Individual operations SHOULD be wrappable (opt-in, not mandatory for single-context unit mutations).
6. Existing non-transactional code MUST continue working unchanged -- the framework is opt-in.
7. `@UnitTransactional` annotation MUST support declarative usage on Spring-managed service methods.
8. Snapshot capture MUST NOT require schema changes to `PropositionNode`.
9. Nested transactions SHOULD be supported: inner failure does not necessarily rollback outer transaction (savepoint semantics).
10. Transaction validation MUST integrate with existing `InvariantEvaluator` -- no duplicate validation logic.

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Snapshot overhead for large contexts** | Medium | Medium | Snapshot only affected context units (scoped by context ID), not all context units globally. Lazy snapshot: capture on first mutation, not on transaction begin. |
| **Race conditions with concurrent transactions** | Low | High | Context Unit operations are already context-isolated (`contextId`). Transactions inherit this isolation. Document that concurrent transactions on the same context ID are undefined behavior. |
| **Rollback inconsistency with Neo4j state** | Medium | High | Rollback uses `ContextUnitRepository` bulk update (same persistence layer as forward operations). Test rollback round-trip: snapshot -> mutate -> rollback -> verify state matches snapshot. |
| **Performance regression for non-transactional operations** | Low | Medium | Transaction is opt-in. Non-transactional paths have zero overhead (no snapshot, no validation). Benchmark transaction overhead in simulation runs. |

## Proposal Seed

### Change Slug

`unit-transaction-framework`

### Proposal Starter Inputs

1. **Problem statement**: All context unit lifecycle operations are forward-only. Maintenance sweeps, batch promotions, and decay cycles can leave the system in a degraded state if interrupted or if results violate invariants. There is no way to atomically undo a set of mutations.
2. **Why now**: Proactive maintenance (F07) and compliance enforcement (F03) both require safe rollback. Building the framework first ensures these features have a solid foundation.
3. **Constraints/non-goals**: MUST NOT require schema changes to `PropositionNode`. MUST use existing Neo4j persistence. SHOULD minimize performance overhead for non-transactional operations. No distributed transactions. No saga compensation.
4. **Visible outcomes**: Operators MUST see transaction outcomes (committed/rolled-back) in simulation run traces. Rollback reasons MUST be logged with violated invariant details.

### Suggested Capability Areas

1. **Snapshot management**: Capture and restore context unit state (rank, authority, trust score, pinned).
2. **Transaction lifecycle**: Begin, validate, commit/rollback orchestration with invariant gates.
3. **Annotation support**: `@UnitTransactional` for declarative transaction demarcation.
4. **Event integration**: Lifecycle events for transaction begin/commit/rollback.

### Candidate Requirement Blocks

1. **REQ-SNAPSHOT**: The system SHALL capture context unit state (rank, authority, trust score, pinned status) as a snapshot before transactional operations begin.
2. **REQ-VALIDATE**: The system SHALL validate invariants (via `InvariantEvaluator`) before committing transactional changes.
3. **REQ-ROLLBACK**: The system SHALL restore all context units to their snapshot state when validation fails.
4. **REQ-EVENT**: The system SHALL emit lifecycle events for transaction begin, commit, and rollback.
5. **REQ-COMPAT**: The system SHALL NOT require changes to existing non-transactional code paths.

## Validation Plan

1. **Unit tests** MUST verify snapshot capture preserves all context unit fields (rank, authority, trust score, pinned).
2. **Unit tests** MUST verify rollback restores exact pre-transaction state after mutations.
3. **Unit tests** MUST verify that invariant violations trigger automatic rollback.
4. **Unit tests** MUST verify that successful invariant validation commits changes.
5. **Unit tests** SHOULD verify nested transaction semantics (inner rollback, outer commit).
6. **Integration test** SHOULD verify transaction wrapping a batch promote operation in the simulation harness.
7. **Observability validation** MUST confirm lifecycle events are emitted with correct attributes.
8. **Regression**: Non-transactional code paths MUST produce identical results to current behavior.

## Known Limitations

1. **Snapshot granularity**: Snapshots capture unit-level state only. Relationship changes (edges in Neo4j) between context units during a transaction are not captured or rolled back. If transaction operations modify inter-context unit relationships, rollback may be incomplete.
2. **No automatic retry**: On rollback, the caller decides whether to retry. Automatic retry-with-backoff is a candidate extension but out of scope.
3. **Thread-local scope**: Transaction context is thread-local. Async operations within a transaction boundary require explicit context propagation.
4. **Performance overhead**: Snapshot + validate + potential rollback adds latency to bulk operations. Initial measurements SHOULD establish the overhead baseline.

## Suggested Command

```
/opsx:new unit-transaction-framework
```
