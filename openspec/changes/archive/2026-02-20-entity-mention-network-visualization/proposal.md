## Why

The Knowledge Browser currently ships a non-functional Graph tab with a placeholder message, which blocks one of the most valuable debugging and demo workflows: visually inspecting how entities co-occur across propositions and anchors. Implementing the entity mention network now closes that gap and makes context drift analysis substantially faster and more explainable.

## What Changes

- Replace the Graph tab placeholder in `KnowledgeBrowserPanel` with a fully functional, interactive entity mention network visualization scoped to the active `contextId`.
- Add context-scoped network data retrieval in the persistence layer (entity nodes + weighted co-mention edges derived from proposition mentions).
- Add graph controls (minimum edge weight, entity type filter, active/all proposition scope, reset layout) and node/edge detail panels.
- Integrate cross-panel navigation so anchor browsing and proposition browsing can focus/highlight the graph neighborhood for relevant entities.
- Add unit/integration coverage for graph data derivation and UI state behavior.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `knowledge-browser`: Upgrade the Graph tab requirement from placeholder behavior to production-ready entity mention network visualization with interactive exploration and filtering.

## Impact

- `src/main/java/dev/dunnam/diceanchors/sim/views/KnowledgeBrowserPanel.java` (Graph tab implementation and controls)
- `src/main/java/dev/dunnam/diceanchors/persistence/AnchorRepository.java` (network query/data derivation methods)
- `src/main/java/dev/dunnam/diceanchors/sim/views/SimulationView.java` and related panel wiring (cross-panel graph focus)
- New/updated tests under `src/test/java/dev/dunnam/diceanchors/sim/views/` and `src/test/java/dev/dunnam/diceanchors/persistence/`
- No external service/API breaking changes; optional frontend visualization utilities may be added if needed for rendering

## Constitutional Alignment

This change preserves the project constitution by improving observability and operator control without altering core anchor invariants (authority progression, rank clamping, budget semantics, and context isolation). Requirements remain testable and RFC 2119-compliant.

## Specification Overrides

None.
