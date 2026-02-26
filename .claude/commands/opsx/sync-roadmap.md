---
name: "OPSX: Sync Roadmap"
description: "Sync change status and learnings back to roadmap docs"
category: Workflow
tags: [workflow, roadmap, sync, experimental]
---

Sync OpenSpec change status and implementation learnings back to the parent roadmap.

**Input**: The argument after `/opsx:sync-roadmap` is the roadmap slug. Could be:
- A slug: "auth-overhaul-v2"
- Nothing (will prompt for selection if multiple roadmaps exist)

---

## Steps

1. **Invoke the openspec-sync-roadmap skill**

   Use the **Skill tool** to invoke `openspec-sync-roadmap`. This loads the full sync workflow.

2. **Select the roadmap**

   If a slug is provided, use it. Otherwise:
   - Auto-select if only one roadmap exists at `openspec/roadmaps/*-roadmap.md`
   - If multiple exist, use **AskUserQuestion** to let the user choose

3. **Follow the skill workflow**

   The skill handles:
   - Reading the roadmap and linked change slugs
   - Gathering current state from change artifacts
   - Updating the proposal waves table with status
   - Updating feature docs with scope/risk/limitation changes
   - Producing a summary

4. **On completion, suggest next actions**

   - Features still `pending`: "Start with `/opsx:new <slug>`"
   - Features `complete`: "Archive with `/opsx:archive <slug>`"
   - Features with significant scope drift: "Review feature doc, consider updating the roadmap"

---

## Shorthand Examples

```
/opsx:sync-roadmap
/opsx:sync-roadmap auth-overhaul-v2
```

---

## Guardrails

- Do NOT modify change artifacts — sync flows one way: changes → roadmap
- Do NOT fabricate status — derive from actual artifact state
- Preserve existing roadmap content not contradicted by changes
- If scope diverged significantly, flag for user review
- Always invoke the `openspec-sync-roadmap` skill — don't attempt the workflow without it
