## ADDED Requirements

### Requirement: reviseFact tool removal

The `AnchorTools` class SHALL NOT expose a `reviseFact` LLM-callable tool. Anchor text mutation SHALL NOT be available to the LLM. The `RevisionResult` record SHALL be removed.

#### Scenario: LLM cannot revise anchors

- **WHEN** the LLM attempts to call a revision tool
- **THEN** no such tool SHALL be available in the tool registry

#### Scenario: reviseFact method absent

- **WHEN** `AnchorTools` is instantiated
- **THEN** it SHALL NOT contain any method annotated with `@LlmTool` that performs anchor text mutation
