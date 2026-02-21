---
name: openspec-roadmap
description: Create or update an OpenSpec roadmap package and optionally automate prep docs and change scaffolding so each feature is proposal-ready and can start cleanly with OpenSpec new change.
---

Build a roadmap package from a high-level idea and, when requested, automate high-level planning outputs that feed OpenSpec cleanly.

**IMPORTANT: This skill is for planning artifacts only.** Do not implement application code while using this skill.

## Self-Check Required (Hard Gate)

Before performing any work, the agent MUST explicitly confirm it is operating under project guidelines.

1. Read project guidance documents (`AGENTS.md`, `CLAUDE.md`, and `openspec/constitution.md` when present).
2. Report exactly one of:
   - `SELF-CHECK: PASS`
   - `SELF-CHECK: FAIL`

If the agent cannot confirm, it MUST respond with `SELF-CHECK: FAIL` and MUST NOT perform further actions.

## RFC 2119 Requirement

All normative planning statements MUST use RFC 2119 keywords:

1. `MUST`, `MUST NOT`, `SHALL`, `SHALL NOT`
2. `SHOULD`, `SHOULD NOT`
3. `MAY`

Avoid ambiguous normative phrasing such as "should" or "needs to" outside RFC 2119 usage.

## Outcome

Produce:

1. One roadmap doc with sequencing, dependencies, and change slugs.
2. One feature doc per planned OpenSpec change.
3. Optional research docs (criteria-driven).
4. Optional prep docs (thin pre-OpenSpec handoff).
5. Optional change scaffolding for each feature slug.

Default output paths:

1. `docs/roadmaps/<roadmap-slug>-roadmap.md`
2. `docs/roadmaps/<roadmap-slug>/features/<NN>-<feature-slug>.md`
3. `docs/roadmaps/<roadmap-slug>/research/R<NN>-<research-slug>.md` (optional)
4. `docs/roadmaps/<roadmap-slug>/prep/<NN>-<feature-slug>-prep.md` (optional)
If repository conventions differ, adapt consistently.

## Optional Inputs

Capture these when provided:

1. `automation_mode`:
   - `roadmap-only` (default)
   - `roadmap-plus-prep`
   - `roadmap-plus-prep-and-change-scaffolds`
2. `research_mode`: `off` (default), `scoped`, `full`
3. `research_criteria`:
   - source priorities,
   - freshness window,
   - minimum evidence count,
   - confidence target,
   - timebox per task,
   - allowed channels (`codebase`, `repo-docs`, `web`, `similar-repos`)
4. `small_model_limits`:
   - max files touched per card,
   - max estimated runtime per card,
   - mandatory verification command(s)

## Workflow

1. **Run hard-gate self-check first**
   - Emit `SELF-CHECK` result.
   - Continue only if `SELF-CHECK: PASS`.

2. **Clarify initiative and automation level**
   - Confirm desired end-state, constraints, priorities, and visibility expectations.
   - If user asks to automate everything, use `automation_mode=roadmap-plus-prep-and-change-scaffolds`.

3. **Load OpenSpec and repo context**
   Run:
   ```bash
   openspec list --json
   ```
   Read, when present:
   - `openspec/project.md`
   - `openspec/constitution.md`
   - existing roadmap/planning docs
   - existing related research docs

4. **Create or update roadmap + feature docs**
   - Use `references/roadmap-template.md`.
   - Use `references/feature-template.md`.
   - Keep each feature independently proposable.

5. **Run optional research workflow**
   If `research_mode` is `scoped` or `full`:
   - Define research tasks from unresolved/high-risk decisions.
   - Use `references/research-template.md`.
   - Capture evidence quality, date, confidence, and links/paths.

6. **Generate optional prep docs**
   If `automation_mode` is `roadmap-plus-prep` or `roadmap-plus-prep-and-change-scaffolds`:
   - Create one prep doc per feature using `references/prep-template.md`.
   - Prep docs MUST stay thin (decisions, open questions, visibility contract, acceptance gates, small-model constraints).
   - Prep docs are handoff artifacts; they do not replace OpenSpec artifacts.

7. **Optionally scaffold OpenSpec changes**
   If `automation_mode` is `roadmap-plus-prep-and-change-scaffolds`:
   For each feature change slug:
   - Ensure change directory exists:
     ```bash
     openspec new change "<change-slug>"
     ```
     If it already exists, continue without recreating.
   - Do not auto-generate proposal/spec/design/tasks in this skill.

8. **Finalize**
   Summarize:
   - roadmap path,
   - feature/research/prep doc paths,
   - change slugs and whether each scaffold exists,
   - next command per feature (`openspec show`, `openspec status`, or workflow alias).

## Quality Bar

A result is acceptable only if:

1. feature docs are independently proposable,
2. dependencies and sequencing are explicit,
3. each feature has measurable acceptance criteria,
4. each feature has at least one visibility channel (`UI` or `Observability`; prefer both),
5. each feature includes proposal seed content strong enough to start OpenSpec proposal authoring,
6. OpenSpec remains the canonical requirement/design/task source,
7. research-backed claims include sources/paths when research mode is enabled.

## Guardrails

1. Do not bypass OpenSpec by putting full spec/design/task content in roadmap/prep docs.
2. Do not duplicate long-form requirements across roadmap, prep, and OpenSpec artifacts.
3. Prep docs MUST stay thin and stable; OpenSpec artifacts carry detailed normative requirements.
4. Do not present unsupported external claims as facts; mark inference vs evidence.
5. If web access is unavailable, continue with local evidence and mark gaps clearly.
6. If a change already exists, update artifacts in place; do not fork duplicate slugs.
7. This skill MUST NOT auto-write proposal/spec/design/tasks artifacts.

## Suggested User Prompts

1. "Use openspec-roadmap for <idea> in roadmap-only mode."
2. "Use openspec-roadmap with roadmap-plus-prep mode."
3. "Use openspec-roadmap with prep + change scaffolds."
4. "Use openspec-roadmap with scoped research mode and these criteria: <criteria>."
