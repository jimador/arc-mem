---
name: "OPSX: Archive Roadmap"
description: "Archive a completed roadmap and consolidate learnings into project documentation"
category: Workflow
tags: [workflow, archive, roadmap, documentation, experimental]
---

Archive a completed roadmap package and consolidate learnings into project documentation.

**Input**: The argument after `/opsx:archive-roadmap` is the roadmap slug. Could be:
- A slug: "anchor-memory-optimization"
- Nothing (will prompt for selection if multiple roadmaps exist)

---

## Steps

1. **Invoke the openspec-archive-roadmap skill**

   Use the **Skill tool** to invoke `openspec-archive-roadmap`. This loads the full archive workflow including completion verification, documentation consolidation, and cross-reference cleanup.

2. **Follow the skill workflow**

   The skill handles:
   - Roadmap selection and completion verification
   - Research doc handling
   - Documentation consolidation (CLAUDE.md, docs/, README.md, project.md)
   - Archive move to `openspec/changes/archive/roadmap/YYYY-MM-DD-<slug>/`
   - Cross-reference cleanup

3. **On completion, suggest next steps**

   - If other roadmaps remain: "Continue with `/opsx:roadmap <slug>`"
   - If all roadmaps archived: "Start a new initiative with `/opsx:roadmap`"

---

## Shorthand Examples

```
/opsx:archive-roadmap
/opsx:archive-roadmap anchor-memory-optimization
```

---

## Guardrails

- Do NOT skip the documentation consolidation pass — it's the primary value
- Do NOT modify archived changes — they're immutable history
- Do NOT delete main specs — they're the living record
- Always invoke the `openspec-archive-roadmap` skill — don't attempt without it
