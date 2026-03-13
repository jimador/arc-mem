---
name: openspec-archive-roadmap
description: Archive a completed roadmap and consolidate learnings into project documentation. Use when a roadmap's features are all implemented and archived, and you want to de-clutter openspec/roadmaps/ while preserving knowledge in living docs.
---

Archive a completed roadmap package and consolidate its learnings into project documentation.

Handles: verifying all features are implemented/archived, moving roadmap files to `openspec/changes/archive/roadmap/YYYY-MM-DD-<slug>/`, consolidating research and decisions into living project docs, and cleaning up cross-references.

**Input**: Roadmap slug (optional — prompts for selection if omitted).

## Steps

### 1. Select the roadmap

Scan `openspec/roadmaps/*-roadmap.md` for available roadmaps.

- If slug provided, use it
- If only one exists, auto-select
- If multiple exist, use **AskUserQuestion** to let user choose

### 2. Verify roadmap completion

Read `openspec/roadmaps/<slug>-roadmap.md`. Parse the feature table to get all feature slugs and their change slugs.

For each feature's change slug, check:
- Archived: exists in `openspec/changes/archive/` (any date prefix)
- Active: exists in `openspec/changes/` (not yet archived)
- Never started: neither location

Show completion table:

```
| Feature | Change Slug | Status |
|---------|-------------|--------|
| F01 | unified-maintenance-strategy | Archived (2026-02-28) |
| F10 | interference-density-budget | Archived (2026-03-03) |
| F11 | tiered-unit-storage | Active (incomplete) |
```

**If any features NOT archived**: warn and confirm with **AskUserQuestion**:
- "Archive roadmap anyway (skip incomplete features)"
- "Cancel — complete remaining features first"

### 3. Check for associated research

Scan `openspec/research/` for files referencing this roadmap slug. Also check `openspec/roadmaps/<slug>/research/` if it exists.

List associated files and ask:
- "Include research docs in archive (recommended)"
- "Leave research docs in place"

### 4. Documentation consolidation pass

Run **4 parallel subagents** to extract lasting knowledge from roadmap artifacts into living docs.

Each subagent reads: the roadmap file, all feature docs in `openspec/roadmaps/<slug>/features/`, and all archived change design docs for this roadmap's features.

#### 4a. CLAUDE.md update

- **Key Files table**: Add new files created by the roadmap's features
- **Key Design Decisions**: Add decisions that emerged (new patterns, invariants)
- **Architecture section**: Update with new subsystems or capability layers

Rules: No transient detail. No duplication. Match existing style. One line per item.

#### 4b. docs/ update

- **docs/architecture.md**: Add new layers, subsystems, component relationships
- **docs/implementation-notes.md**: Add non-obvious design choices, paper citations, known limitations

Rules: Focus on WHAT and WHY. Proper citations. Only what a new developer needs.

#### 4c. README.md update

- Add or update user-visible capability descriptions
- Skip if no user-facing changes

Rules: README is for users/evaluators. Keep additions minimal.

#### 4d. openspec/project.md update

- Update test count
- Update subsystem descriptions
- Add roadmap to "Completed Initiatives" section (create if absent)

### 5. Review consolidation

Show summary of all documentation changes. Confirm with **AskUserQuestion**:
- "Proceed with archive"
- "Review changes before archiving" (shows diffs)
- "Cancel"

### 6. Perform the archive

```bash
mkdir -p openspec/changes/archive/roadmap/YYYY-MM-DD-<slug>
```

Move:
- `openspec/roadmaps/<slug>-roadmap.md` → `openspec/changes/archive/roadmap/YYYY-MM-DD-<slug>/roadmap.md`
- `openspec/roadmaps/<slug>/` → `openspec/changes/archive/roadmap/YYYY-MM-DD-<slug>/` (features/, prep/, research/ subdirs)
- Associated research from `openspec/research/` (if user chose to include)

**Leave in place**:
- `openspec/specs/` — living record
- `openspec/changes/archive/` — individual change archives are separate
- Research files user chose to keep

### 7. Update cross-references

Grep for old roadmap path (`openspec/roadmaps/<slug>`) in:
- `CLAUDE.md`, `README.md`, `docs/`
- Update to archive path or remove stale references
- Leave references in `openspec/changes/archive/` alone (historical)

### 8. Display summary

```
## Roadmap Archive Complete

**Roadmap:** <slug>
**Features:** N/M archived
**Archived to:** openspec/changes/archive/roadmap/YYYY-MM-DD-<slug>/

### Archived
- roadmap.md, N feature docs, N prep docs, M research docs

### Documentation Consolidated
- CLAUDE.md: N updates
- docs/architecture.md: N updates
- docs/implementation-notes.md: N updates
- openspec/project.md: N updates
```

## Guardrails

- Always prompt for selection, never auto-select when ambiguous
- Warn on incomplete features but don't block
- Documentation consolidation is the primary value — never skip it
- Run consolidation subagents in parallel
- Show consolidation summary before committing
- Do NOT modify `openspec/changes/archive/` — immutable
- Do NOT delete `openspec/specs/` entries — living record
- Archive path: `openspec/changes/archive/roadmap/YYYY-MM-DD-<slug>/`
- If target exists, fail with error
- Research inclusion is user's choice
- Keep `openspec/roadmaps/` directory (other roadmaps may exist)
