## ADDED Requirements

### Requirement: Authority-graduated prompt template files

The system SHALL provide four authority-graduated prompt template files, one per authority tier. Each template SHALL define how a memory unit at that authority level is rendered into the prompt context block.

- **PROVISIONAL template**: SHALL include the full memory unit text, rank, and a contextual note indicating low confidence. This is the most verbose template.
- **UNRELIABLE template**: SHALL include the full memory unit text and rank. Less verbose than PROVISIONAL (no contextual note).
- **RELIABLE template**: SHALL include a condensed form of the memory unit text and rank. Omits boilerplate present in lower tiers.
- **CANON template**: SHALL include a minimal reference -- the memory unit text only, with no rank or contextual metadata. This is the most compact template.

Template files SHALL be Jinja2 templates stored in `src/main/resources/prompts/` and referenced via `PromptPathConstants`.

#### Scenario: PROVISIONAL template is most verbose

- **GIVEN** a PROVISIONAL memory unit with text "The castle has a moat" and rank 300
- **WHEN** the PROVISIONAL template renders this memory unit
- **THEN** the output includes the full text, rank, and a low-confidence contextual note
- **AND** the output is longer than the RELIABLE or CANON template output for the same memory unit

#### Scenario: CANON template is most compact

- **GIVEN** a CANON memory unit with text "The king is named Aldric" and rank 800
- **WHEN** the CANON template renders this memory unit
- **THEN** the output includes only the memory unit text
- **AND** the output is shorter than any other authority tier template for the same memory unit

#### Scenario: All four templates exist and load

- **GIVEN** the application classpath includes the four authority-graduated template files
- **WHEN** `PromptTemplates.load()` is called with each template path constant
- **THEN** each template loads successfully and is non-empty

### Requirement: Template selection by authority in prompt assembly

When adaptive footprint is enabled, `ArcMemLlmReference.getContent()` SHALL select the prompt template for each memory unit based on its authority level. Each authority tier SHALL use its corresponding graduated template file.

When adaptive footprint is disabled (default), `ArcMemLlmReference.getContent()` SHALL use the existing uniform `ANCHORS_REFERENCE` template for all memory units, preserving current behavior identically.

Template selection SHALL occur per-unit within the authority-grouped rendering loop. The authority tier ordering in the rendered output (CANON first, PROVISIONAL last) SHALL remain unchanged.

#### Scenario: Adaptive footprint enabled uses graduated templates

- **GIVEN** adaptive footprint is enabled
- **AND** 4 memory units exist: 1 CANON, 1 RELIABLE, 1 UNRELIABLE, 1 PROVISIONAL
- **WHEN** `ArcMemLlmReference.getContent()` assembles the prompt context
- **THEN** the CANON memory unit is rendered with the CANON template (minimal)
- **AND** the RELIABLE memory unit is rendered with the RELIABLE template (condensed)
- **AND** the UNRELIABLE memory unit is rendered with the UNRELIABLE template (moderate)
- **AND** the PROVISIONAL memory unit is rendered with the PROVISIONAL template (full detail)

#### Scenario: Adaptive footprint disabled preserves current behavior

- **GIVEN** adaptive footprint is disabled (default)
- **AND** 4 memory units exist at various authority levels
- **WHEN** `ArcMemLlmReference.getContent()` assembles the prompt context
- **THEN** all memory units are rendered using the existing uniform `ANCHORS_REFERENCE` template
- **AND** the output is identical to the pre-change behavior

#### Scenario: Empty memory unit set produces empty content

- **GIVEN** adaptive footprint is enabled
- **AND** no active memory units exist
- **WHEN** `ArcMemLlmReference.getContent()` is called
- **THEN** an empty string is returned

### Requirement: Configuration toggle for adaptive footprint

The system SHALL provide a `arc-mem.assembly.adaptive-footprint-enabled` configuration property (boolean, default `false`). When `false`, prompt assembly SHALL use uniform templates (current behavior). When `true`, prompt assembly SHALL use authority-graduated templates.

The property SHALL be bindable via Spring Boot `@ConfigurationProperties` through the existing `AssemblyConfig` record.

#### Scenario: Default configuration is disabled

- **GIVEN** `arc-mem.assembly.adaptive-footprint-enabled` is not set
- **WHEN** the application starts
- **THEN** adaptive footprint is disabled
- **AND** prompt assembly uses uniform templates

#### Scenario: Enabled via configuration

- **GIVEN** `arc-mem.assembly.adaptive-footprint-enabled` is set to `true`
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
