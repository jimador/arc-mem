## ADDED Requirements

### Requirement: Authority-graduated prompt template files

The system SHALL provide four authority-graduated prompt template files, one per authority tier. Each template SHALL define how an anchor at that authority level is rendered into the prompt context block.

- **PROVISIONAL template**: SHALL include the full anchor text, rank, and a contextual note indicating low confidence. This is the most verbose template.
- **UNRELIABLE template**: SHALL include the full anchor text and rank. Less verbose than PROVISIONAL (no contextual note).
- **RELIABLE template**: SHALL include a condensed form of the anchor text and rank. Omits boilerplate present in lower tiers.
- **CANON template**: SHALL include a minimal reference -- the anchor text only, with no rank or contextual metadata. This is the most compact template.

Template files SHALL be Jinja2 templates stored in `src/main/resources/prompts/` and referenced via `PromptPathConstants`.

#### Scenario: PROVISIONAL template is most verbose

- **GIVEN** a PROVISIONAL anchor with text "The castle has a moat" and rank 300
- **WHEN** the PROVISIONAL template renders this anchor
- **THEN** the output includes the full text, rank, and a low-confidence contextual note
- **AND** the output is longer than the RELIABLE or CANON template output for the same anchor

#### Scenario: CANON template is most compact

- **GIVEN** a CANON anchor with text "The king is named Aldric" and rank 800
- **WHEN** the CANON template renders this anchor
- **THEN** the output includes only the anchor text
- **AND** the output is shorter than any other authority tier template for the same anchor

#### Scenario: All four templates exist and load

- **GIVEN** the application classpath includes the four authority-graduated template files
- **WHEN** `PromptTemplates.load()` is called with each template path constant
- **THEN** each template loads successfully and is non-empty

### Requirement: Template selection by authority in prompt assembly

When adaptive footprint is enabled, `AnchorsLlmReference.getContent()` SHALL select the prompt template for each anchor based on its authority level. Each authority tier SHALL use its corresponding graduated template file.

When adaptive footprint is disabled (default), `AnchorsLlmReference.getContent()` SHALL use the existing uniform `ANCHORS_REFERENCE` template for all anchors, preserving current behavior identically.

Template selection SHALL occur per-anchor within the authority-grouped rendering loop. The authority tier ordering in the rendered output (CANON first, PROVISIONAL last) SHALL remain unchanged.

#### Scenario: Adaptive footprint enabled uses graduated templates

- **GIVEN** adaptive footprint is enabled
- **AND** 4 anchors exist: 1 CANON, 1 RELIABLE, 1 UNRELIABLE, 1 PROVISIONAL
- **WHEN** `AnchorsLlmReference.getContent()` assembles the prompt context
- **THEN** the CANON anchor is rendered with the CANON template (minimal)
- **AND** the RELIABLE anchor is rendered with the RELIABLE template (condensed)
- **AND** the UNRELIABLE anchor is rendered with the UNRELIABLE template (moderate)
- **AND** the PROVISIONAL anchor is rendered with the PROVISIONAL template (full detail)

#### Scenario: Adaptive footprint disabled preserves current behavior

- **GIVEN** adaptive footprint is disabled (default)
- **AND** 4 anchors exist at various authority levels
- **WHEN** `AnchorsLlmReference.getContent()` assembles the prompt context
- **THEN** all anchors are rendered using the existing uniform `ANCHORS_REFERENCE` template
- **AND** the output is identical to the pre-change behavior

#### Scenario: Empty anchor set produces empty content

- **GIVEN** adaptive footprint is enabled
- **AND** no active anchors exist
- **WHEN** `AnchorsLlmReference.getContent()` is called
- **THEN** an empty string is returned

### Requirement: Configuration toggle for adaptive footprint

The system SHALL provide a `dice-anchors.assembly.adaptive-footprint-enabled` configuration property (boolean, default `false`). When `false`, prompt assembly SHALL use uniform templates (current behavior). When `true`, prompt assembly SHALL use authority-graduated templates.

The property SHALL be bindable via Spring Boot `@ConfigurationProperties` through the existing `AssemblyConfig` record.

#### Scenario: Default configuration is disabled

- **GIVEN** `dice-anchors.assembly.adaptive-footprint-enabled` is not set
- **WHEN** the application starts
- **THEN** adaptive footprint is disabled
- **AND** prompt assembly uses uniform templates

#### Scenario: Enabled via configuration

- **GIVEN** `dice-anchors.assembly.adaptive-footprint-enabled` is set to `true`
- **WHEN** the application starts
- **THEN** adaptive footprint is enabled
- **AND** prompt assembly uses authority-graduated templates

### Requirement: PromptPathConstants for graduated templates

`PromptPathConstants` SHALL define constants for all four authority-graduated template file paths:
- `ANCHOR_TEMPLATE_PROVISIONAL`
- `ANCHOR_TEMPLATE_UNRELIABLE`
- `ANCHOR_TEMPLATE_RELIABLE`
- `ANCHOR_TEMPLATE_CANON`

Each constant SHALL reference a Jinja2 template file under `src/main/resources/prompts/`.

#### Scenario: Constants resolve to loadable templates

- **GIVEN** the four template path constants are defined in `PromptPathConstants`
- **WHEN** each constant is passed to `PromptTemplates.load()`
- **THEN** a non-empty template string is returned for each

## Invariants

- **I1**: When adaptive footprint is disabled, prompt assembly output MUST be identical to pre-change behavior (zero behavioral change for default configuration).
- **I2**: Authority tier ordering in rendered output MUST be CANON, RELIABLE, UNRELIABLE, PROVISIONAL (same as current).
- **I3**: Template verbosity MUST decrease monotonically with increasing authority: PROVISIONAL > UNRELIABLE > RELIABLE > CANON.
- **I4**: All four authority levels MUST have a corresponding template. Adding a new authority level without a template MUST cause a compile-time or startup error.
