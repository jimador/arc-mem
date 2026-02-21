# <Initiative Title> Roadmap

## Intent

State the high-level idea and intended end-state in 3-6 lines.

## RFC 2119 Compliance

Normative requirements in this roadmap SHOULD use RFC 2119 keywords only (`MUST`, `SHOULD`, `MAY`, and their negations).

## Research Configuration

Set to `off`, `scoped`, or `full`.

- `Research mode`:
- `Research criteria summary`:
- `Evidence freshness expectation`:
- `Minimum sources per research task`:

## Scope

### In Scope

1. ...

### Out of Scope

1. ...

## Constraints

1. Project and architecture constraints from `openspec/project.md` (if present).
2. Constitutional/governance constraints from `openspec/constitution.md` (if present).
3. Existing roadmap conventions from repository docs (if present).
4. User-provided constraints.

## Proposal Waves

| Wave | Feature ID | Feature Slug | Priority | Depends On | Visibility | OpenSpec Change Slug |
|------|------------|--------------|----------|------------|------------|----------------------|
| 1 | F01 | <feature-slug> | MUST | none | UI + Observability | <change-slug> |

Guidance:

1. `Priority` uses `MUST`, `SHOULD`, `MAY`.
2. `Visibility` is one of `UI`, `Observability`, or `UI + Observability`.
3. Keep features independently proposable.

## Research Backlog (Optional)

Create this table when research mode is `scoped` or `full`.

| Task ID | Question | Target Feature(s) | Channels | Timebox | Success Criteria | Output Doc |
|---------|----------|-------------------|----------|---------|------------------|------------|
| R01 | <question> | F01, F02 | codebase + web | 30m | <clear pass/fail evidence goal> | `docs/roadmaps/<roadmap-slug>/research/R01-<research-slug>.md` |

Channel values:

1. `codebase`
2. `repo-docs`
3. `web`
4. `similar-repos`

If no research is required, state why.

## Sequencing Rationale

Explain why this order is chosen.

## Feature Documents

List generated feature docs with paths:

1. `docs/roadmaps/<roadmap-slug>/features/01-<feature-slug>.md`

If a different docs convention is used in this repository, replace these paths consistently.

## Prep Documents (Optional)

List generated prep docs with paths:

1. `docs/roadmaps/<roadmap-slug>/prep/01-<feature-slug>-prep.md`

## Change Scaffolds (Optional)

Track whether OpenSpec change directories were created.

| Feature ID | Change Slug | Scaffold Status | Path |
|------------|-------------|-----------------|------|
| F01 | <change-slug> | pending/created/existing | `openspec/changes/<change-slug>/` |

## Research Documents (Optional)

List generated research docs with paths:

1. `docs/roadmaps/<roadmap-slug>/research/R01-<research-slug>.md`

## Global Risks

1. ...

## Exit Criteria

Roadmap is complete when:

1. every feature has a doc,
2. dependencies are explicit,
3. each feature has acceptance criteria,
4. each feature has visibility requirements,
5. each feature has a proposal seed and `/opsx:new` command,
6. each feature is ready for `openspec new change <change-slug>`.

## Suggested Proposal Commands

1. `/opsx:new <change-slug-1>`
2. `/opsx:new <change-slug-2>`

## Known Limitations

Capture deferred items:

1. limitation description,
2. expected impact,
3. improvement ideas.
