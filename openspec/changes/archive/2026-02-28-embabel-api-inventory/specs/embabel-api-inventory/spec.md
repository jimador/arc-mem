# Embabel API Inventory Specification

## ADDED Requirements

### Requirement: Inventory document exists

An Embabel API inventory document SHALL exist at `docs/dev/embabel-api-inventory.md`. The document MUST be a Markdown file and MUST be reachable from `DEVELOPING.md`.

#### Scenario: Document is present and linked
- **GIVEN** the dice-anchors repository
- **WHEN** a contributor opens `DEVELOPING.md`
- **THEN** a reference to `docs/dev/embabel-api-inventory.md` is present
- **AND** the linked file exists and is non-empty

### Requirement: Current usage catalog

The inventory MUST contain a "Current Usage" section documenting every Embabel annotation actively used in the codebase. Each entry MUST include the annotation name, parameters used, and at least one file:line reference.

#### Scenario: All four annotations documented
- **GIVEN** the Current Usage section
- **WHEN** a reader inspects the annotation catalog
- **THEN** the following annotations are documented with file:line references:
  - `@EmbabelComponent` (used in `ChatActions.java`)
  - `@Action` with `trigger` and `canRerun` parameters (used in `ChatActions.java`)
  - `@MatryoshkaTools` with `name` and `description` parameters (used in `AnchorTools.java`, `AnchorRetrievalTools.java`)
  - `@LlmTool` with `description` parameter (used in `AnchorTools.java`, `AnchorRetrievalTools.java`)

#### Scenario: File:line references are verifiable
- **GIVEN** any file:line reference in the Current Usage section
- **WHEN** a contributor navigates to that location
- **THEN** the referenced annotation is present at or near the stated line

### Requirement: Available-but-unused capabilities

The inventory MUST contain an "Available But Unused" section documenting Embabel capabilities that exist in the 0.3.5-SNAPSHOT API but are not currently used by dice-anchors. Each entry SHOULD include the capability name, what it enables, and when it would be valuable.

#### Scenario: Unused annotation parameters documented
- **GIVEN** the Available But Unused section
- **WHEN** a reader inspects unused parameters
- **THEN** at least the following 8 parameters are documented:
  - `@Action(cost)` -- planning cost for path selection
  - `@Action(toolGroups)` -- declarative tool group binding
  - `@MatryoshkaTools(removeOnInvoke)` -- progressive disclosure
  - `@MatryoshkaTools(categoryParameter)` -- category-based child tool selection
  - `@MatryoshkaTools(childToolUsageNotes)` -- LLM guidance text
  - `@LlmTool(returnDirect)` -- bypass additional LLM processing
  - `@LlmTool(category)` -- tool grouping within @MatryoshkaTools
  - `@LlmTool.Param(description, required)` -- rich parameter descriptions

#### Scenario: Unused annotations documented
- **GIVEN** the Available But Unused section
- **WHEN** a reader inspects unused annotations
- **THEN** `@AchievesGoal`, `@Agent`, and `@Export` are documented with purpose and applicability context

### Requirement: Tool restructuring rationale

The inventory MUST contain a section documenting the CQS (Command-Query Separation) principle as it applies to `@MatryoshkaTools` organization. The section MUST explain why separating read-only tools from mutation tools enables selective registration in different execution contexts.

#### Scenario: CQS rationale is present
- **GIVEN** the Tool Organization section
- **WHEN** a reader inspects the restructuring rationale
- **THEN** the CQS principle is explained
- **AND** the distinction between query tools and command tools is documented
- **AND** the benefit for selective registration (read-only simulation vs. full-access chat) is stated

### Requirement: Recommended patterns

The inventory SHOULD contain a "Recommended Patterns" section capturing Embabel framework best practices relevant to dice-anchors. This section SHOULD cover tool description quality, error handling in tools, and the message-vs-trigger pattern.

#### Scenario: Patterns section provides actionable guidance
- **GIVEN** the Recommended Patterns section
- **WHEN** a contributor reads the guidance
- **THEN** each pattern includes a brief explanation of when and why to apply it

## Invariants

- **I1**: The inventory document MUST NOT contain code changes or executable artifacts
- **I2**: File:line references MUST be validated against the codebase at the time of feature completion
- **I3**: The inventory MUST cover both used and unused capabilities -- omitting either category is incomplete
