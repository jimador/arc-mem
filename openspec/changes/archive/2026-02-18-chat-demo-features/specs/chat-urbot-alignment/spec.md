## MODIFIED Requirements

### Requirement: Flat template rendering (modified)

The `dice-anchors.jinja` template SHALL be a single flat file with all persona, guardrails, anchor-context, and user-context content inlined. The template SHALL NOT use `{% include %}` directives. The template SHALL use `{% if %}` / `{% else %}` for persona selection and `{% for %}` for anchor iteration, matching the existing template logic.

#### Scenario: Template renders without includes
- **WHEN** `ChatActions.respond()` renders the `dice-anchors` template
- **THEN** the template renders successfully without `InterpretException`
- **AND** the system prompt contains persona text, guardrails, anchor context, and user context
