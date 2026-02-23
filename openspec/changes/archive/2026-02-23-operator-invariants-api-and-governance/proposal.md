## Why

The anchor system has well-defined technical invariants (A1–A5: budget enforcement, rank clamping, CANON immunity, pin protection, trust scoring) but no mechanism for operators to express business invariants — strategic constraints like "these facts MUST remain active", "no anchor in this context may be demoted below RELIABLE", or "these seed facts are non-negotiable ground truth". Currently, operators manage CANON status and pinning tactically through simulation YAML seeds and the canonization gate, but cannot define or enforce governance rules that survive across turns, contexts, or sessions.

The governance gap is visible in three areas: (1) CompliancePolicy maps authority to RFC 2119 strength but is read-only and compile-time — operators cannot override per-anchor or per-context; (2) CanonizationGate stores pending requests in-memory with no persistence or audit trail; (3) seed anchors are simulation-only — the chat flow has no equivalent seeding API. Adding an operator invariants framework provides the governance layer needed before benchmarking (F06) can measure policy compliance, and before upstream DICE integration (F08) can propose a governance contract.

## What Changes

- Add **operator invariant model** — a declarative rule type expressing constraints on anchor state (e.g., "anchor X must remain CANON", "no anchor in context Y may be evicted", "minimum 3 RELIABLE anchors in context Z"). Rules are evaluated on every lifecycle state change.
- Add **invariant enforcement hooks** in `AnchorEngine` — check applicable invariants before committing archive, eviction, demotion, and authority changes. Violations are blocked (for MUST rules) or logged as warnings (for SHOULD rules), following RFC 2119 semantics.
- Add **invariant configuration** via YAML — operators define invariants in simulation scenario YAML and/or `application.yml`. Invariants are scoped to contexts or globally.
- Add **invariant violation event** — publish an `InvariantViolation` lifecycle event with rule ID, violated constraint, and the attempted action that was blocked.
- Add **canonization gate persistence** — persist pending canonization requests to Neo4j instead of in-memory storage. Add audit trail for approved/rejected/stale requests.
- Add **chat seed anchors** — allow operators to seed CANON anchors in chat contexts via configuration, not just simulation scenarios.
- Add **invariant inspector UI** — add an "Invariants" tab to the Context Inspector panel showing active rules, their status, and any violations.
- Add **OTEL span attributes** for invariant checks — `invariant.checked_count`, `invariant.violated_count`, `invariant.blocked_action`.

## Capabilities

### New Capabilities
- `operator-invariants`: Declarative invariant rule model, enforcement hooks in AnchorEngine, YAML configuration, and violation events.
- `invariant-inspector`: UI panel for viewing active invariants, their status, and violation history.

### Modified Capabilities
- `canonization-gate`: Persist pending requests to Neo4j; add audit trail for approved/rejected/stale transitions.
- `anchor-lifecycle`: Add invariant enforcement hooks before archive, eviction, demotion, and authority changes.
- `anchor-lifecycle-events`: Add `InvariantViolation` event type with rule ID and violated constraint details.
- `observability`: Add invariant-related OTEL span attributes to lifecycle operation spans.

## Impact

- **Domain model**: New `OperatorInvariant` record/class, `InvariantRule` enum or DSL, `InvariantEvaluator` service.
- **Engine**: `AnchorEngine` gains invariant check hooks on state-changing operations.
- **Persistence**: `CanonizationGate` migrated from in-memory to Neo4j. New invariant storage (Neo4j or config-driven).
- **Events**: New `InvariantViolation` event in lifecycle event hierarchy.
- **Configuration**: New YAML config sections for invariant definitions and chat seed anchors.
- **UI**: New "Invariants" tab in Context Inspector panel.
- **Simulation**: Scenario YAML extended with invariant definitions.
- **Observability**: New OTEL span attributes for invariant enforcement.
- **Tests**: New test classes for invariant evaluation, enforcement, persistence, and UI.
