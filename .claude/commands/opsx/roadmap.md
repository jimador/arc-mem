---
name: "OPSX: Roadmap"
description: "Create or update an OpenSpec roadmap package and optionally automate prep docs and change scaffolding"
category: Workflow
tags: [workflow, planning, roadmap, experimental]
---

Create or update a roadmap package from a high-level idea, producing sequenced feature docs that feed cleanly into OpenSpec changes.

**IMPORTANT: This command is for planning artifacts only.** Do not implement application code.

**Input**: The argument after `/opsx:roadmap` is the initiative description or roadmap slug. Could be:
- An idea: "user authentication overhaul"
- A slug: "auth-overhaul-v2"
- A mode override: "auth-overhaul-v2 --prep --scaffolds"
- Nothing (will ask what to plan)

---

## Steps

1. **If no input provided, ask what they want to plan**

   Use the **AskUserQuestion tool** (open-ended, no preset options) to ask:
   > "What initiative do you want to roadmap? Describe the high-level goal or theme."

   From their description, derive a kebab-case roadmap slug.

2. **Invoke the openspec-roadmap skill**

   Use the **Skill tool** to invoke `openspec-roadmap`. This loads the full workflow including self-check, templates, and quality bar.

3. **Determine automation mode from input flags**

   | User Input | `automation_mode` |
   |------------|-------------------|
   | No flags / default | `roadmap-only` |
   | `--prep` | `roadmap-plus-prep` |
   | `--prep --scaffolds` or `--all` | `roadmap-plus-prep-and-change-scaffolds` |

   If the user says "automate everything" or "full automation", use `roadmap-plus-prep-and-change-scaffolds`.

4. **Determine research mode from input**

   | User Input | `research_mode` |
   |------------|-----------------|
   | No mention of research | `off` |
   | `--research` or mentions research | `scoped` |
   | `--research-full` | `full` |

5. **Follow the openspec-roadmap skill workflow**

   The skill handles:
   - Self-check gate
   - Loading OpenSpec and repo context
   - Creating/updating roadmap + feature docs
   - Optional research workflow
   - Optional prep doc generation
   - Optional change scaffolding
   - Final summary

6. **On completion, summarize and suggest next steps**

   Show:
   - Roadmap path and feature count
   - Which optional outputs were generated
   - Next actions per feature:
     - `roadmap-only`: "Start any feature with `/opsx:new <slug>`"
     - `roadmap-plus-prep`: "Review prep docs, then `/opsx:new <slug>`"
     - With scaffolds: "Changes are scaffolded. Start with `/opsx:continue <slug>`"

---

## Shorthand Examples

```
/opsx:roadmap user authentication overhaul
/opsx:roadmap auth-overhaul-v2 --prep
/opsx:roadmap auth-overhaul-v2 --prep --scaffolds
/opsx:roadmap auth-overhaul-v2 --all
/opsx:roadmap auth-overhaul-v2 --research
```

---

## Guardrails

- Do NOT implement application code - planning artifacts only
- Do NOT bypass OpenSpec by putting full spec/design/task content in roadmap/prep docs
- Do NOT auto-write proposal/spec/design/tasks artifacts (that's OpenSpec's job)
- Prep docs MUST stay thin - decisions, open questions, acceptance gates, not full requirements
- If a roadmap already exists at the target path, update it in place - don't create duplicates
- Always invoke the `openspec-roadmap` skill - don't attempt the workflow without it
