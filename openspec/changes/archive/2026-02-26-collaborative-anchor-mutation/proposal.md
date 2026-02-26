## Why

The `reviseFact` LLM tool allows adversarial user input to manipulate the LLM into defeating PROVISIONAL and UNRELIABLE anchors with no human oversight. This undermines the trust model that anchors are designed to protect. Anchor mutation MUST be strictly human-in-the-loop (HITL) — a user SHALL NOT be able to update an established fact via prompting. The first implementation provides manual revision exclusively through the UI, with an SPI so mutation behavior can be extended or replaced later.

## What Changes

- **BREAKING**: Remove `reviseFact` from LLM-callable tools — the LLM SHALL NOT mutate anchors
- Remove revision carveout from `dice-anchors.jinja` prompt template (no `[revisable]` annotations, no `reviseFact` tool instructions)
- Disable `RevisionAwareConflictResolver` — conflict detection no longer auto-resolves REVISION-type conflicts; all conflicts delegate to `AuthorityConflictResolver`
- Introduce `AnchorMutationStrategy` SPI — pluggable interface governing whether a mutation request is allowed, with a `HitlOnlyMutationStrategy` default implementation that requires UI-originated requests
- Retain the existing ChatView inline revision UI as the sole mutation path, gated through `AnchorMutationStrategy`
- Add revision UI to `AnchorManipulationPanel` (sim view) for HITL interventions during simulation pauses

## Capabilities

### New Capabilities
- `anchor-mutation-spi`: SPI interface (`AnchorMutationStrategy`) for gating and routing anchor text mutations, with `HitlOnlyMutationStrategy` default implementation

### Modified Capabilities
- `anchor-llm-tools`: Remove `reviseFact` tool from LLM-callable tools
- `authority-tiered-compliance`: Remove `[revisable]` annotations and revision carveout from prompt template
- `revision-intent-classification`: Disable `RevisionAwareConflictResolver` when mutation strategy is HITL-only (conflict type classification still runs for observability, but does not auto-resolve)

## Impact

- `AnchorTools.java`: `reviseFact` method removed or gated behind SPI
- `dice-anchors.jinja`: Revision carveout blocks removed
- `ChatView.java`: Revision UI calls route through `AnchorMutationStrategy`
- `AnchorManipulationPanel.java`: New revision text field + button for sim HITL
- `RevisionAwareConflictResolver`: Disabled/bypassed when HITL-only strategy active
- `DiceAnchorsProperties`: New `anchor.mutation.strategy` config property
- `ChatActions.java` / `ChatView.java`: Template variables `revision_enabled` and `reliable_revisable` no longer passed when HITL-only
- Existing adaptive simulation scenarios may need updated expectations (LLM can no longer revise anchors)
