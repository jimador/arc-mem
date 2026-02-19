## Context

The simulation UI already includes a `KnowledgeBrowserPanel` with three tabs (Propositions, Anchors, Graph), but the Graph tab currently renders a static placeholder string. This creates a mismatch with the `knowledge-browser` spec and removes a key investigation tool for drift-debugging and scenario walkthroughs.

Current relevant code paths:
- `src/main/java/dev/dunnam/diceanchors/sim/views/KnowledgeBrowserPanel.java` (tab shell + placeholder)
- `src/main/java/dev/dunnam/diceanchors/persistence/AnchorRepository.java` (context-scoped proposition/mention access)
- `src/main/java/dev/dunnam/diceanchors/persistence/PropositionView.java` + `Mention.java` (mention graph model)
- `src/main/java/dev/dunnam/diceanchors/sim/views/SimulationView.java` (cross-panel navigation wiring)

Constraints:
- Data MUST remain context-scoped (`contextId`) and never leak across runs.
- Neo4j/Drivine is the only persistence substrate.
- Vaadin UI should remain responsive in dense scenarios.

## Goals / Non-Goals

**Goals:**
- Deliver an interactive entity mention network visualization in the Knowledge Browser Graph tab.
- Render entity nodes and co-mention edges computed from proposition mentions for the active context.
- Provide filtering and focus controls that support simulation diagnostics.
- Keep the implementation additive and compatible with existing browsing/search flows.

**Non-Goals:**
- No global cross-context graph analytics.
- No long-term graph history/timeline replay in this change.
- No replacement of existing Propositions/Anchors tabs.
- No dependency on external graph databases/services beyond existing Neo4j.

## Decisions

### Decision 1: Derive network edges from co-mentions within the same proposition

We will model an undirected edge between two entities when they are mentioned in the same proposition. Edge weight equals the number of distinct propositions where that pair co-occurs.

Why:
- Matches existing DICE proposition+mention structure.
- Deterministic and explainable for audits.
- Efficient to query in Neo4j.

Alternatives considered:
- Subject/object-only edges: rejected (drops meaningful co-occurrence signal from OTHER mentions).
- LLM-inferred semantic edges: rejected (non-deterministic, slower, hard to verify).

### Decision 2: Add repository-level graph DTO query in `AnchorRepository`

Introduce context-scoped repository methods returning graph DTOs:
- `EntityNode` (entityId, label/span, type, mentionCount, propositionCount)
- `EntityEdge` (sourceEntityId, targetEntityId, weight, propositionIds)
- `EntityMentionGraph` (nodes, edges)

Why:
- Keeps graph derivation close to persistence and context filtering.
- Reusable by UI and tests.

Alternatives considered:
- Build graph entirely in UI from full proposition payloads: rejected (higher payload, duplicates aggregation logic).

### Decision 3: Implement a dedicated Vaadin graph component with deterministic layout + interactive highlighting

Create a focused component (e.g., `EntityMentionNetworkView`) rendered in the Graph tab. Layout is deterministic (seeded force/circular fallback), supports node click, edge hover details, and selection highlighting.

Why:
- Avoids Graph tab becoming monolithic.
- Easier unit testing of view-model transformations.

Alternatives considered:
- Keep rendering inline in `KnowledgeBrowserPanel`: rejected (maintainability).

### Decision 4: Add guardrails for dense graphs

Apply limits and UX fallbacks:
- Default minimum edge weight = 2 for rendering clarity.
- Cap visible nodes/edges (configurable constants) with warning banner.
- Provide empty-state and "too dense" guidance.

Why:
- Prevent UI lockups in large contexts.

Alternatives considered:
- Render full graph always: rejected (poor UX and performance risk).

```mermaid
flowchart LR
    A[KnowledgeBrowserPanel Graph Tab] --> B[EntityMentionNetworkView]
    B --> C[AnchorRepository.queryEntityMentionGraph(contextId, filters)]
    C --> D[(Neo4j: Proposition- HAS_MENTION -> Mention)]
    D --> C
    C --> E[EntityMentionGraph DTO]
    E --> B
    B --> F[Node/Edge Detail Pane + Filters]
```

## Risks / Trade-offs

- [Dense contexts can overwhelm visualization] -> Mitigation: default weight threshold, node/edge caps, user warnings, filter-first UX.
- [Entity identity quality depends on mention resolution] -> Mitigation: use `resolvedId` when present, fallback to normalized span/type key, and expose this in details.
- [Cypher aggregation complexity] -> Mitigation: add repository-level tests with deterministic fixtures and explicit explain-plan-friendly query structure.
- [UI complexity increase] -> Mitigation: isolate graph component and keep `KnowledgeBrowserPanel` as orchestrator.

## Migration Plan

1. Add repository graph query DTOs and tests.
2. Implement graph view component and Graph tab integration behind existing panel.
3. Wire filters/focus interactions from existing browsing controls.
4. Validate context isolation and dense graph fallback behavior.
5. Rollback strategy: Graph tab can fall back to placeholder/empty-state component without affecting propositions/anchors features.

## Open Questions

- Should minimum edge weight default vary by scenario size (dynamic) or remain fixed?
- Should node color coding use mention type, authority influence, or both?
- Do we need keyboard navigation/accessibility semantics in this first release or follow-up?
