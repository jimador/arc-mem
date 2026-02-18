## 1. Graph Domain Model

- [x] 1.1 Add graph DTOs for entity nodes, edges, and graph payload in persistence layer
- [x] 1.2 Add deterministic fallback entity key utility for unresolved mentions (normalized span + type)
- [x] 1.3 Add mapper/helpers for converting query rows into DTOs

## 2. Persistence Query Implementation

- [x] 2.1 Implement `AnchorRepository` method to query context-scoped entity nodes from mention data
- [x] 2.2 Implement `AnchorRepository` method to query weighted co-mention edges from propositions
- [x] 2.3 Implement combined graph retrieval method with filters (min edge weight, entity type, active/all status)
- [x] 2.4 Add repository safeguards for empty contexts and malformed mention rows

## 3. Graph UI Component

- [x] 3.1 Create `EntityMentionNetworkView` component under `sim/views/` for network rendering
- [x] 3.2 Implement deterministic initial layout and edge rendering with weight labels/tooltips
- [x] 3.3 Implement node selection/focus behavior (highlight neighbors, dim unrelated nodes)
- [x] 3.4 Add empty-state and dense-graph warning states

## 4. Knowledge Browser Integration

- [x] 4.1 Replace Graph tab placeholder in `KnowledgeBrowserPanel` with `EntityMentionNetworkView`
- [x] 4.2 Add Graph controls (minimum edge weight, entity type, active/all scope)
- [x] 4.3 Wire Graph tab refresh lifecycle to `setContextId()` and `refresh()` flows
- [x] 4.4 Ensure Graph tab honors same context-scoping guarantees as propositions/anchors tabs

## 5. Cross-Panel Navigation & Focus

- [x] 5.1 Add API on `KnowledgeBrowserPanel` to focus/highlight an entity or anchor-related neighborhood
- [x] 5.2 Wire `SimulationView` browse interactions to switch to Graph tab when graph focus is requested
- [x] 5.3 Preserve existing search/browse behavior while adding graph focus path

## 6. Tests

- [x] 6.1 Add repository tests for node/edge derivation and weighted co-mention counts
- [x] 6.2 Add repository tests for context isolation and filter behavior
- [x] 6.3 Add UI/component tests for Graph tab empty state and control-driven filtering
- [x] 6.4 Add UI/component tests for node focus/highlight behavior

## 7. Verification

- [x] 7.1 Run full test suite: `./mvnw.cmd test`
- [x] 7.2 Build without tests: `./mvnw.cmd clean compile -DskipTests`
- [ ] 7.3 Manual smoke test: run simulation and verify Graph tab renders entity network for populated context
- [ ] 7.4 Manual filter test: verify minimum edge weight and type filters update graph output correctly
- [ ] 7.5 Manual context isolation test: verify graph data changes with context and does not leak across runs

## 8. Documentation & Cleanup

- [x] 8.1 Update Knowledge Browser documentation/comments to reflect implemented Graph tab capability
- [x] 8.2 Update CLAUDE.md key files if new graph view/component files are introduced
- [x] 8.3 Verify no placeholder copy remains for planned-future graph visualization
