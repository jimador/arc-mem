## ADDED Requirements

### Requirement: reviseFact tool removal

The `ContextTools` class SHALL NOT expose a `reviseFact` LLM-callable tool. Context Unit text mutation SHALL NOT be available to the LLM. The `RevisionResult` record SHALL be removed.

#### Scenario: LLM cannot revise context units

- **WHEN** the LLM attempts to call a revision tool
- **THEN** no such tool SHALL be available in the tool registry

#### Scenario: reviseFact method absent

- **WHEN** `ContextTools` is instantiated
- **THEN** it SHALL NOT contain any method annotated with `@LlmTool` that performs context unit text mutation
