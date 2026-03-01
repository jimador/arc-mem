# Prep: Anchor LLM Tool Restructuring

## Visibility Contract

- **UI Visibility**: Chat tool availability changes; may affect LLM tool-use patterns (smoke test required)
- **Observability Visibility**: Tool calls logged by group (query vs. mutation); OTEL spans tagged accordingly
- **Acceptance**: All tests pass; chat integration test confirms query + mutation tools work; tool descriptions verified

## Key Decisions

1. **Separation principle**: Query tools always available; mutation tools context-conditional
2. **Consolidation**: `retrieveAnchors` moved from `AnchorRetrievalTools` to `AnchorQueryTools` (preserves conditional logic)
3. **Old classes deleted**: `AnchorTools.java` and `AnchorRetrievalTools.java` removed entirely (no migration shims)
4. **Test update**: `AnchorToolsTest` split into `AnchorQueryToolsTest` and `AnchorMutationToolsTest`
5. **Registration strategy**: Explicit conditional check in `ChatActions` (not `@Condition` guard)

## Open Questions (Answered by Feature Specification)

1. Should `retrieveAnchors` remain conditional (HYBRID/TOOL modes), or be always available?
   - **Decision**: Keep conditional; retrieval is optimization when enabled
2. Should read-only contexts use `@Condition` guard or explicit registration check?
   - **Decision**: Explicit registration check (clearer for Embabel config)

## Acceptance Gates

- [ ] `AnchorQueryTools` class created with three `@LlmTool` methods
- [ ] `AnchorMutationTools` class created with three `@LlmTool` methods
- [ ] `retrieveAnchors` consolidated (with mode conditional logic preserved)
- [ ] `ChatActions` updated for dual-group registration
- [ ] Test suite: `./mvnw test` passes
- [ ] Chat integration test verifies query + mutation tools callable
- [ ] Read-only mode test verifies only query tools available
- [ ] Tool descriptions verified (clear and specific)
- [ ] OTEL instrumentation updated (tool group tracking)

## Small-Model Constraints

- **Files touched**: 6-8 (two new tool classes, ChatActions, 3+ test files)
- **Estimated runtime**: 2-3 hours
- **Verification commands**:
  ```bash
  ./mvnw test
  # Chat integration test output shows both tool groups registered
  # Read-only mode test shows only query tools registered
  ```

## Implementation Sequence

1. Create `AnchorQueryTools.java` with query methods
2. Move `retrieveAnchors` from `AnchorRetrievalTools` into `AnchorQueryTools`
3. Create `AnchorMutationTools.java` with mutation methods
4. Update `ChatActions.respond()` registration logic
5. Update test files (split `AnchorToolsTest`)
6. Delete `AnchorTools.java` and `AnchorRetrievalTools.java`
7. Run full test suite
8. Verify chat integration test (manual or CI)

## Implementation Notes

- Preserve all tool descriptions from original classes
- Keep OTEL span instrumentation for `retrieveAnchors` unchanged
- Ensure HYBRID/TOOL mode conditional logic still works
- Update CLAUDE.md reference if tool class locations are documented there
- Consider: Should tool class names be singular (`AnchorQueryTool`) or plural (`AnchorQueryTools`)? Match existing pattern.
