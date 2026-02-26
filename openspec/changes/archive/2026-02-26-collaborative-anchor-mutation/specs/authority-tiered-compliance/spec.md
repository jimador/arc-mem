## REMOVED Requirements

### Requirement: Authority-tiered compliance prompt with revision carveout

**Reason**: Revision carveout removed — anchor mutation is now HITL-only via UI. The prompt template SHALL NOT contain `[revisable]` annotations, `reviseFact` tool instructions, or revision exception blocks.
**Migration**: Remove all revision-related conditional blocks from `dice-anchors.jinja`. Stop passing `revision_enabled` and `reliable_revisable` template variables.

#### Scenario: No revisable annotations in prompt

- **GIVEN** any configuration
- **WHEN** the prompt template is rendered
- **THEN** the output SHALL NOT contain `[revisable]` annotations on any anchor entry

#### Scenario: No revision carveout in Critical Instructions

- **WHEN** the prompt template is rendered
- **THEN** the Critical Instructions section SHALL NOT contain `reviseFact` tool references

#### Scenario: No revision exception in Verification Protocol

- **WHEN** the prompt template is rendered
- **THEN** the Verification Protocol SHALL NOT contain a revision exception clause

### Requirement: Revision template variables

**Reason**: Template variables `revision_enabled` and `reliable_revisable` are no longer needed since all revision UI is HITL-only.
**Migration**: Remove `revision_enabled` and `reliable_revisable` from `ChatActions.respond()` and `ChatView.renderChatPrompt()`.
