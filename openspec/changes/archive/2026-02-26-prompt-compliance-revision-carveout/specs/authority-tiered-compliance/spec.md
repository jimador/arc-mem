## MODIFIED Requirements

### Requirement: Authority-tiered compliance prompt with revision carveout

The `dice-anchors.jinja` template SHALL add revision carveout language when `revision_enabled` is true. The carveout SHALL be conditional and additive — existing anti-contradiction instructions SHALL be preserved for non-revisable facts.

When `revision_enabled` is true:
- PROVISIONAL and UNRELIABLE anchor entries SHALL be annotated with `[revisable]` suffix
- RELIABLE anchor entries SHALL be annotated with `[revisable]` only when `reliable_revisable` is also true
- CANON anchor entries SHALL NEVER be annotated with `[revisable]`
- The Critical Instructions section SHALL include a revision exception block explaining when the LLM MAY accept user revisions of `[revisable]` facts
- The Verification Protocol SHALL include an exception for accepted revisions of `[revisable]` facts

When `revision_enabled` is false, the template SHALL render identically to the current behavior — no `[revisable]` annotations, no carveout language.

#### Scenario: PROVISIONAL anchor annotated as revisable

- **GIVEN** `revision_enabled = true`
- **AND** a PROVISIONAL anchor "Anakin Skywalker is a wizard" at rank 500
- **WHEN** the tiered compliance template is rendered
- **THEN** the anchor entry SHALL read: `1. Anakin Skywalker is a wizard (rank: 500) [revisable]`

#### Scenario: CANON anchor never annotated as revisable

- **GIVEN** `revision_enabled = true`
- **AND** a CANON anchor "The campaign is set in Faerûn"
- **WHEN** the tiered compliance template is rendered
- **THEN** the anchor entry SHALL NOT include `[revisable]`

#### Scenario: RELIABLE anchor revisable when configured

- **GIVEN** `revision_enabled = true` and `reliable_revisable = true`
- **AND** a RELIABLE anchor at rank 700
- **WHEN** the tiered compliance template is rendered
- **THEN** the anchor entry SHALL include `[revisable]`

#### Scenario: RELIABLE anchor not revisable by default

- **GIVEN** `revision_enabled = true` and `reliable_revisable = false`
- **AND** a RELIABLE anchor
- **WHEN** the tiered compliance template is rendered
- **THEN** the anchor entry SHALL NOT include `[revisable]`

#### Scenario: Revision disabled renders identically to current behavior

- **GIVEN** `revision_enabled = false`
- **WHEN** the tiered compliance template is rendered
- **THEN** no `[revisable]` annotations SHALL appear
- **AND** no revision carveout language SHALL appear in Critical Instructions

#### Scenario: Critical Instructions include revision exception

- **GIVEN** `revision_enabled = true`
- **WHEN** the tiered compliance template is rendered
- **THEN** the Critical Instructions SHALL include guidance that facts marked `[revisable]` MAY be changed on explicit user request
- **AND** the instructions SHALL specify that non-revisable facts remain immutable

#### Scenario: Verification Protocol accounts for revisions

- **GIVEN** `revision_enabled = true`
- **WHEN** the tiered compliance template is rendered
- **THEN** the Verification Protocol SHALL include an exception stating that accepted revisions of `[revisable]` facts are valid

### Requirement: Revision template variables

`AnchorsLlmReference` SHALL pass `revision_enabled` and `reliable_revisable` boolean template variables to the `dice-anchors.jinja` template, sourced from `DiceAnchorsProperties.anchor().revision()`.

`ChatActions` SHALL include `revision_enabled` and `reliable_revisable` in the template variable map when rendering the system prompt.

#### Scenario: Template variables reflect configuration

- **GIVEN** `anchor.revision.enabled = true` and `anchor.revision.reliable-revisable = false`
- **WHEN** the system prompt is rendered
- **THEN** `revision_enabled` SHALL be `true` and `reliable_revisable` SHALL be `false`

#### Scenario: Template variables absent when revision disabled

- **GIVEN** `anchor.revision.enabled = false`
- **WHEN** the system prompt is rendered
- **THEN** `revision_enabled` SHALL be `false`

## Invariants

- **PRC1**: CANON anchors SHALL NEVER be annotated with `[revisable]` regardless of configuration.
- **PRC2**: When `revision_enabled = false`, the template output SHALL be byte-identical to the pre-carveout template for the same input.
- **PRC3**: The revision carveout SHALL NOT weaken compliance language for non-revisable facts.
