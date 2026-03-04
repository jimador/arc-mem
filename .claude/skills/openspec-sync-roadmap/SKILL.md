---
name: openspec-sync-roadmap
description: Sync OpenSpec change status and learnings back to roadmap docs. Use when the user wants to update a roadmap to reflect implementation progress, scope changes, or completed features.
---

Sync change status and implementation learnings back to the parent roadmap.

As changes progress through OpenSpec, their roadmap entries can drift out of date. This skill reads change artifacts and updates the roadmap and feature docs to reflect current reality.

## What Gets Synced

| Source (Change Artifacts) | Target (Roadmap Docs) |
|--------------------------|----------------------|
| Change exists + has tasks | Proposal Waves table: status → `in-progress` |
| All tasks complete | Proposal Waves table: status → `complete` |
| Change archived | Proposal Waves table: status → `archived` |
| Scope changes in proposal/design | Feature doc: Scope section |
| New risks discovered | Feature doc: Risks and Mitigations |
| Research findings | Feature doc: Research Findings table |
| Acceptance criteria changes | Feature doc: Acceptance Criteria |
| New limitations discovered | Feature doc: Known Limitations |
| Dependency changes | Roadmap: Sequencing Rationale (if order affected) |

## Input

Optionally specify a roadmap slug. If omitted, prompt for selection.

## Steps

1. **Select the roadmap**

   Look for roadmap files at `openspec/roadmaps/*-roadmap.md`.

   - If a slug is provided, use it directly
   - If only one roadmap exists, auto-select it
   - If multiple exist, use **AskUserQuestion** to let the user choose

2. **Read the roadmap and identify linked changes**

   Parse the Proposal Waves table to find change slugs. For each change slug:

   a. Check if `openspec/changes/<slug>/` exists
   b. Check if `openspec/changes/archive/<slug>/` exists (archived)
   c. If neither exists, mark as `pending`

3. **For each linked change, gather current state**

   Read available artifacts to determine:
   - **Status**: pending / in-progress / complete / archived
   - **Scope drift**: Compare change proposal with feature doc scope
   - **New risks**: Check design doc for risks not in feature doc
   - **Research findings**: Check for research output in change or roadmap research dirs
   - **Acceptance changes**: Compare change specs with feature doc acceptance criteria
   - **New limitations**: Check design/tasks for discovered limitations

   Status determination:
   - No change directory → `pending`
   - Change exists, no tasks artifact → `scaffolded`
   - Change exists, tasks with incomplete items → `in-progress`
   - Change exists, all tasks complete → `complete`
   - Change in archive → `archived`

4. **Update the roadmap doc**

   In the Proposal Waves table, update status for each feature. Add a `Status` column if one doesn't exist.

   If sequencing has changed (e.g., a dependency was removed or added), note it in Sequencing Rationale.

5. **Update feature docs**

   For each feature with material changes:
   - Update Scope if it drifted during implementation
   - Add new risks discovered during design/implementation
   - Update Research Findings table with completed research
   - Update Acceptance Criteria if specs refined them
   - Add Known Limitations discovered during implementation

   **Preserve existing content** — add and update, don't replace wholesale.

6. **Show summary**

   ```
   ## Roadmap Synced: <roadmap-slug>

   **Features updated:**

   | Feature | Change Slug | Previous Status | Current Status | Material Changes |
   |---------|-------------|-----------------|----------------|-----------------|
   | F01 | <slug> | pending | in-progress | scope narrowed, 1 new risk |
   | F02 | <slug> | in-progress | complete | 2 limitations added |

   Roadmap and feature docs are updated.
   ```

## Guardrails

- Read both change artifacts and roadmap docs before making changes
- Preserve existing content not contradicted by change artifacts
- Do not fabricate status — derive it from actual artifact state
- Do not modify change artifacts — this is a one-way sync to roadmap docs
- If a change's scope diverged significantly, flag it for user review rather than auto-merging
- Idempotent — running twice should produce the same result
- Do not sync implementation details — roadmap docs stay at planning level
