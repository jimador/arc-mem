# Prep: <Feature Title>

## Feature ID

`FNN`

## Change Slug

`<change-slug>`

## Intent

State what this feature MUST accomplish before implementation planning begins.

## Locked Decisions

List decisions that are already made and should not be re-litigated in proposal drafting.

1. ...
2. ...

## Open Questions

List only unresolved items that proposal/spec/design work must settle.

1. ...
2. ...

## Visibility Contract

At least one visibility channel is REQUIRED.

### UI Visibility

1. User-facing surface:
2. Expected signal:

### Observability Visibility

1. Logs/metrics/traces:
2. Verification approach:

## Acceptance Gates (Pre-Implementation)

The future proposal/spec/design SHOULD satisfy:

1. ...
2. ...

Normative gates SHOULD use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Small-Model Constraints

These constraints help later task decomposition and smaller-model execution.

1. Max files touched per atomic task:
2. Max runtime/complexity per task:
3. Mandatory verification command(s):

## Handoff to OpenSpec

Use these commands:

1. `openspec new change <change-slug>` (if not already scaffolded)
2. `openspec status --change <change-slug>`
3. Continue normal OpenSpec artifact flow for proposal/spec/design/tasks
