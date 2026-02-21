# Research: Operator-Defined Invariants API (Detailed Design)

## Objective

Provide a detailed, proposal-ready design for operator-defined invariants so critical facts/rules are explicitly authored and governed, rather than only inferred from conversation extraction.

This document addresses the selected research track for a detailed design.

## Why This Matters

For many production domains, the most important constraints should be authored up front:

1. legal/compliance constraints,
2. medical safety constraints,
3. system policy invariants,
4. canonical world rules.

Relying only on extraction for these constraints is unsafe.

## Parent Objective Dependencies

Implement after:

1. fail-open gate fixes,
2. authority ceiling enforcement,
3. strict token accounting.

## Design Goals

1. Explicit authoring and lifecycle governance.
2. Strong mutation safety.
3. Clear coexistence with extracted anchors.
4. Backward-compatible integration path for DICE proposal.

## Non-Goals

1. Replace DICE extraction pipeline.
2. Make all anchors operator-managed.
3. Introduce mandatory human review for every mutation type.

## Core Concepts

1. `Invariant`:
   operator-managed statement/rule.
2. `InvariantScope`:
   global, tenant, context, or campaign/session scope.
3. `InvariantMode`:
   hard constraint, soft constraint, advisory.
4. `InvariantStatus`:
   active, deprecated, superseded, archived.
5. `InvariantGovernance`:
   workflow requirements for create/update/deprecate.

## Proposed Data Model

Minimum fields:

1. `id`
2. `text`
3. `scope`
4. `mode` (`HARD`, `SOFT`, `ADVISORY`)
5. `priority`
6. `status`
7. `createdBy`
8. `approvedBy` (optional, policy-driven)
9. `createdAt`
10. `updatedAt`
11. `validFrom` / `validTo` (optional temporal scope)
12. `tags` (domain and policy categories)

Optional fields:

1. `justification`
2. `sourceReference`
3. `changeTicket`

## Governance Model

### Mutations

1. Create invariant.
2. Update wording/metadata.
3. Supersede invariant.
4. Deprecate/archive invariant.

### Policy

1. `HARD` invariants require approval workflow to mutate.
2. `SOFT` invariants allow controlled edits by authorized roles.
3. `ADVISORY` invariants can be edited with lighter governance.

### Audit

Every mutation should emit an immutable audit record:

1. actor,
2. action,
3. old value,
4. new value,
5. reason.

## Runtime Interaction with Anchors

### Precedence

1. Hard invariants override extracted anchor candidates.
2. Soft invariants influence trust/authority but can be superseded by governed evidence.
3. Advisory invariants are hints, not blockers.

### Budget policy

Recommended:

1. reserve separate token budget slice for invariants,
2. do not evict hard invariants under normal anchor budget pressure.

### Conflict handling

When incoming claim conflicts with an invariant:

1. `HARD` -> reject auto mutation; route to governed review.
2. `SOFT` -> require stronger evidence threshold and possibly review.
3. `ADVISORY` -> allow policy-based adjudication.

## API Surface (Proposal Seed)

### Management API

1. `POST /invariants`
2. `GET /invariants`
3. `GET /invariants/{id}`
4. `PATCH /invariants/{id}`
5. `POST /invariants/{id}/supersede`
6. `POST /invariants/{id}/archive`

### Resolution API

1. `POST /invariants/resolve`
   Input: candidate claim + context.
   Output: applicable invariants + precedence decision.

### Audit API

1. `GET /invariants/{id}/history`

## Integration Plan

### Phase 1: Data + read-only path

1. Add invariant persistence model.
2. Add read path and injection into prompt assembly.
3. Add trace visibility in context inspector.

### Phase 2: Governance and mutation workflows

1. Add role/policy checks.
2. Add mutation audit logging.
3. Add supersession flow.

### Phase 3: Conflict integration

1. Plug invariant resolver into conflict/promotion decisions.
2. Add precedence policy and budget partitioning.

## DICE Framework Fit

Upstream-friendly proposal shape:

1. Keep invariant policy app-owned by default.
2. Propose optional DICE extension interfaces for:
   - invariant resolution hook,
   - mutation audit hook,
   - scope-aware context projection.
3. Preserve backward compatibility with no-op defaults.

## Evaluation Plan

### Hypotheses

1. Hard invariants reduce catastrophic drift on protected facts.
2. Governance reduces unsafe automatic mutations.
3. Budget partitioning improves invariant retention without major narrative quality loss.

### Metrics

1. protected-fact violation rate,
2. false-block rate on valid updates,
3. review queue volume and resolution time,
4. latency overhead of invariant resolution.

## Risks

1. Over-constraining creative output.
2. Governance friction for operators.
3. Policy complexity and inconsistent usage across domains.

Mitigation:

1. clear mode semantics (`HARD/SOFT/ADVISORY`),
2. scope controls,
3. auditable overrides.

## Proposal-Ready Deliverables

1. Invariants API and governance RFC.
2. Policy matrix for precedence and mutation rights.
3. Data migration and compatibility plan.
4. Demo narrative showing invariant enforcement and governed change flow.

## Sources

1. Repo analysis and open questions: `docs/investigate.md`
2. Working memory framing: `docs/anchors-as-working-memory.md`
3. DICE-fit direction: `docs/research/dice-framework-fit-proposal-outline.md`
