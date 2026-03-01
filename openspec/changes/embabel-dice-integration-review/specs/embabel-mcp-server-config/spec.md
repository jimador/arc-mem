## ADDED Requirements

### Requirement: Embabel API inventory document

The project SHALL maintain a comprehensive inventory document (`docs/dev/embabel-api-inventory.md`) that catalogs Embabel Agent framework usage in dice-anchors.

#### Scenario: Inventory captures current usage
- **WHEN** the inventory document is reviewed
- **THEN** it SHALL list all Embabel annotations used (`@EmbabelComponent`, `@Action`, `@LlmTool`, `@MatryoshkaTools`)
- **AND** SHALL document each with location in codebase (file:line), purpose, and usage pattern

#### Scenario: Inventory identifies available but unused capabilities
- **WHEN** the inventory covers available capabilities
- **THEN** it SHALL document unused annotations (`@Goal`, `@Condition`, `@State`, `@AchievesGoal`)
- **AND** SHALL describe what each provides and when it might be valuable

#### Scenario: Inventory surfaces recommended patterns
- **WHEN** best practices section is reviewed
- **THEN** it SHALL list Embabel patterns recommended by framework documentation
- **AND** SHALL indicate which are adopted and which are deferred with rationale

### Requirement: Tool restructuring rationale documented

The inventory document SHALL explain the rationale for splitting `AnchorTools` into `AnchorQueryTools` and `AnchorMutationTools`.

#### Scenario: CQS principle is justified
- **WHEN** the tool restructuring rationale is reviewed
- **THEN** it SHALL explain Command Query Segregation principle
- **AND** SHALL document how this enables read-only mode (simulation) vs. full access (chat)
