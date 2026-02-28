## Why

The current `AnchorTools` class (`src/main/java/dev/dunnam/diceanchors/chat/AnchorTools.java`) mixes read operations (`queryFacts`, `listAnchors`) with state-mutating operations (`pinFact`, `unpinFact`, `demoteAnchor`) in a single `@MatryoshkaTools` class. A separate `AnchorRetrievalTools` class (`src/main/java/dev/dunnam/diceanchors/chat/AnchorRetrievalTools.java`) adds another read operation (`retrieveAnchors`) registered conditionally by retrieval mode.

This organization creates two problems:

1. **CQS violation**: The LLM cannot distinguish between safe read operations and state-changing mutations from the tool group structure alone. All six operations appear as a flat list under one or two opaque tool groups.

2. **No selective registration**: Read-only contexts (simulation audit, future replay mode) cannot register query tools without also exposing mutation tools. The current `ChatActions.respond()` (lines 128-138) builds a `toolObjects` list that always includes the full `AnchorTools` instance with all mutations. The only conditional logic is whether `AnchorRetrievalTools` is added for HYBRID/TOOL retrieval modes.

Splitting tools into query and mutation groups enables read-only contexts to register only `AnchorQueryTools`, while chat contexts register both. This also aligns with Embabel framework recommendations for tool organization and enables clearer OTEL observability by tool group.

## What Changes

**Code**:
- Create `AnchorQueryTools.java` (`@MatryoshkaTools`) with three `@LlmTool` methods: `queryFacts`, `listAnchors`, `retrieveAnchors`
- Create `AnchorMutationTools.java` (`@MatryoshkaTools`) with three `@LlmTool` methods: `pinFact`, `unpinFact`, `demoteAnchor`
- Add `@LlmTool.Param` annotations to all tool method parameters for richer LLM guidance
- Set `removeOnInvoke = false` on both `@MatryoshkaTools` classes so tools persist across conversation turns
- Consolidate `AnchorRetrievalTools.retrieveAnchors` into `AnchorQueryTools` (preserving HYBRID/TOOL conditional logic, `RelevanceScorer` blending, and OTEL span instrumentation)
- Update `ChatActions.respond()` (lines 128-148) to register both tool groups in chat mode, with `retrieveAnchors` conditional on retrieval mode via constructor parameter
- Delete `AnchorTools.java` and `AnchorRetrievalTools.java`
- Split `AnchorToolsTest` into `AnchorQueryToolsTest` and `AnchorMutationToolsTest`

**No version changes**: Embabel 0.3.5-SNAPSHOT remains as-is.

## Capabilities

### New Capabilities
- `anchor-query-tools`: Read-only tool group for querying established facts — `queryFacts`, `listAnchors`, `retrieveAnchors`
- `anchor-mutation-tools`: State-changing tool group for managing anchors — `pinFact`, `unpinFact`, `demoteAnchor`

### Modified Capabilities
- `chat-actions`: Updated registration logic to instantiate and register two separate tool groups instead of one monolithic class

### Removed Capabilities
- `anchor-tools`: Replaced by `anchor-query-tools` and `anchor-mutation-tools`
- `anchor-retrieval`: Consolidated into `anchor-query-tools`

## Impact

- **Domain model**: No changes to `Anchor`, `AnchorEngine`, `AnchorRepository`, or any core anchor logic
- **Tool classes**: Two new records replace two existing records; net zero file count change
- **Registration**: `ChatActions.respond()` updated — tool object construction changes from one/two objects to two/two objects depending on retrieval mode
- **Tests**: `AnchorToolsTest` split into two test classes; no test logic changes beyond class/constructor references
- **UI**: No Vaadin changes; tool availability in chat may shift LLM tool-use patterns (smoke test required)
- **Observability**: Tool calls logged by group; OTEL span attributes on `retrieveAnchors` preserved unchanged
