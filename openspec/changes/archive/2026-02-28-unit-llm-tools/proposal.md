## Why

The current `ContextTools` class (`src/main/java/dev/dunnam/arcmem/chat/ContextTools.java`) mixes read operations (`queryFacts`, `listUnits`) with state-mutating operations (`pinFact`, `unpinFact`, `demoteUnit`) in a single `@MatryoshkaTools` class. A separate `ContextUnitRetrievalTools` class (`src/main/java/dev/dunnam/arcmem/chat/ContextUnitRetrievalTools.java`) adds another read operation (`retrieveUnits`) registered conditionally by retrieval mode.

This organization creates two problems:

1. **CQS violation**: The LLM cannot distinguish between safe read operations and state-changing mutations from the tool group structure alone. All six operations appear as a flat list under one or two opaque tool groups.

2. **No selective registration**: Read-only contexts (simulation audit, future replay mode) cannot register query tools without also exposing mutation tools. The current `ChatActions.respond()` (lines 128-138) builds a `toolObjects` list that always includes the full `ContextTools` instance with all mutations. The only conditional logic is whether `ContextUnitRetrievalTools` is added for HYBRID/TOOL retrieval modes.

Splitting tools into query and mutation groups enables read-only contexts to register only `ContextUnitQueryTools`, while chat contexts register both. This also aligns with Embabel framework recommendations for tool organization and enables clearer OTEL observability by tool group.

## What Changes

**Code**:
- Create `ContextUnitQueryTools.java` (`@MatryoshkaTools`) with three `@LlmTool` methods: `queryFacts`, `listUnits`, `retrieveUnits`
- Create `ContextUnitMutationTools.java` (`@MatryoshkaTools`) with three `@LlmTool` methods: `pinFact`, `unpinFact`, `demoteUnit`
- Add `@LlmTool.Param` annotations to all tool method parameters for richer LLM guidance
- Set `removeOnInvoke = false` on both `@MatryoshkaTools` classes so tools persist across conversation turns
- Consolidate `ContextUnitRetrievalTools.retrieveUnits` into `ContextUnitQueryTools` (preserving HYBRID/TOOL conditional logic, `RelevanceScorer` blending, and OTEL span instrumentation)
- Update `ChatActions.respond()` (lines 128-148) to register both tool groups in chat mode, with `retrieveUnits` conditional on retrieval mode via constructor parameter
- Delete `ContextTools.java` and `ContextUnitRetrievalTools.java`
- Split `ContextToolsTest` into `ContextUnitQueryToolsTest` and `ContextUnitMutationToolsTest`

**No version changes**: Embabel 0.3.5-SNAPSHOT remains as-is.

## Capabilities

### New Capabilities
- `unit-query-tools`: Read-only tool group for querying established facts — `queryFacts`, `listUnits`, `retrieveUnits`
- `unit-mutation-tools`: State-changing tool group for managing context units — `pinFact`, `unpinFact`, `demoteUnit`

### Modified Capabilities
- `chat-actions`: Updated registration logic to instantiate and register two separate tool groups instead of one monolithic class

### Removed Capabilities
- `unit-tools`: Replaced by `unit-query-tools` and `unit-mutation-tools`
- `unit-retrieval`: Consolidated into `unit-query-tools`

## Impact

- **Domain model**: No changes to `Context Unit`, `ArcMemEngine`, `ContextUnitRepository`, or any core context unit logic
- **Tool classes**: Two new records replace two existing records; net zero file count change
- **Registration**: `ChatActions.respond()` updated — tool object construction changes from one/two objects to two/two objects depending on retrieval mode
- **Tests**: `ContextToolsTest` split into two test classes; no test logic changes beyond class/constructor references
- **UI**: No Vaadin changes; tool availability in chat may shift LLM tool-use patterns (smoke test required)
- **Observability**: Tool calls logged by group; OTEL span attributes on `retrieveUnits` preserved unchanged
