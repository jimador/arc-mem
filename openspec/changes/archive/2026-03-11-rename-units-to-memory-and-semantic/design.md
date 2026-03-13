## Context

The repo already has a clean module split and a mostly coherent package structure. The remaining inconsistency is language: `ContextUnit` and generic `Unit*` names still leak older terminology that does not cleanly distinguish extracted semantic content from promoted ARC-Mem state.

The intended conceptual model is:

`Conversation -> SemanticUnit extraction -> promotion -> MemoryUnit lifecycle`

This change turns that conceptual model into the canonical naming model across code, tests, docs, prompts, and UI labels.

Constraints:

- Behavior is not being redesigned.
- The module boundary remains `arcmem-core` -> `arcmem-simulator`.
- Neo4j schema names MAY remain stable if renaming them would imply data migration.
- Existing type relationships and Spring wiring must remain functional after the rename.

## Goals / Non-Goals

**Goals**

- Rename promoted ARC-Mem state from `ContextUnit` to `MemoryUnit`.
- Rename public API types tied to promoted ARC-Mem state to `MemoryUnit*`.
- Rename generic semantic-content helper types to `SemanticUnit*` where they are not specific to promoted memory state.
- Update docs, UI text, and prompts so humans encounter one consistent conceptual model.

**Non-Goals**

- Changing runtime algorithms or persistence behavior.
- Changing Neo4j property names if that requires migration.
- Redesigning package layout again.

## Decisions

### Decision: Use `MemoryUnit` for promoted ARC-Mem objects

The promoted working-memory object currently named `ContextUnit` SHALL be renamed to `MemoryUnit`.

Rationale:

- It describes the role of the object inside ARC-Mem, not just that it is “in context”.
- It pairs cleanly with `SemanticUnit` for upstream extraction concepts.
- It is easier to explain to new contributors than `ContextUnit`.

### Decision: Use `SemanticUnit` for extracted or generic semantic-content concepts

Classes and user-facing text that refer to extracted semantic content or generic semantic constraints SHALL use `SemanticUnit` rather than the overloaded `Unit`.

Initial candidates include:

- `UnitPromoter` -> `SemanticUnitPromoter`
- `UnitConstraint` -> `SemanticUnitConstraint`
- `UnitConstraintIndex` -> `SemanticUnitConstraintIndex`

### Decision: Rename the `ContextUnit` support family consistently

Public types specifically centered on promoted ARC-Mem state SHALL be renamed consistently, including:

- `ContextUnitRepository` -> `MemoryUnitRepository`
- `ContextUnitCache` -> `MemoryUnitCache`
- `TieredContextUnitRepository` -> `TieredMemoryUnitRepository`
- `ContextUnitLifecycleEvent` -> `MemoryUnitLifecycleEvent`
- `ContextUnitLifecycleListener` -> `MemoryUnitLifecycleListener`
- `ContextUnitEventConfiguration` -> `MemoryUnitEventConfiguration`
- `ContextUnitPrologProjector` -> `MemoryUnitPrologProjector`
- `ContextUnitMutationTools` -> `MemoryUnitMutationTools`
- `ContextUnitQueryTools` -> `MemoryUnitQueryTools`
- `UnitSummary` -> `MemoryUnitSummary`
- `UnitCountAssertion` -> `MemoryUnitCountAssertion`

### Decision: Keep database schema compatibility unless migration is trivial

Internal storage labels, property names, and wire formats MAY retain legacy names if renaming them would require migration or break backward compatibility. Code comments or mapping boundaries SHOULD make that explicit where needed.

## Implementation approach

1. Rename `ContextUnit` to `MemoryUnit` and rewrite all Java references.
2. Rename supporting `ContextUnit*` types to `MemoryUnit*`.
3. Rename generic `Unit*` helper types to `SemanticUnit*` or `MemoryUnit*` based on role.
4. Update tests, prompt templates, docs, and UI labels.
5. Run full verification and fix any broken wiring.

## Type mapping

### Promoted ARC-Mem state

| Current name | New name |
| --- | --- |
| `ContextUnit` | `MemoryUnit` |
| `ContextUnitRepository` | `MemoryUnitRepository` |
| `ContextUnitCache` | `MemoryUnitCache` |
| `TieredContextUnitRepository` | `TieredMemoryUnitRepository` |
| `ContextUnitLifecycleEvent` | `MemoryUnitLifecycleEvent` |
| `ContextUnitLifecycleListener` | `MemoryUnitLifecycleListener` |
| `ContextUnitEventConfiguration` | `MemoryUnitEventConfiguration` |
| `ContextUnitPrologProjector` | `MemoryUnitPrologProjector` |
| `ContextUnitMutationTools` | `MemoryUnitMutationTools` |
| `ContextUnitQueryTools` | `MemoryUnitQueryTools` |
| `UnitSummary` | `MemoryUnitSummary` |
| `UnitCountAssertion` | `MemoryUnitCountAssertion` |

### Semantic-content helpers

| Current name | New name |
| --- | --- |
| `UnitPromoter` | `SemanticUnitPromoter` |
| `UnitConstraint` | `SemanticUnitConstraint` |
| `UnitConstraintIndex` | `SemanticUnitConstraintIndex` |

## Risks / Trade-offs

- Import churn is large because `ContextUnit` was deeply referenced.
- Some tests and docs currently embed exact type names and UI text.
- Over-renaming generic `Unit` symbols could reduce clarity if a type is actually memory-specific rather than semantic.

Mitigation:

- Apply the rename mechanically from the type map above.
- Compile early after each rename cluster.
- Prefer the role-based rule: if the type is about promoted ARC-Mem state, use `MemoryUnit`; if it constrains or promotes semantic content in general, use `SemanticUnit`.
