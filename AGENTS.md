# AGENTS.md instructions for C:\Users\james\Projects\dice-anchors

Follow `CLAUDE.md` for all project-level instructions.

## Codex Skill Source Of Truth

For this repository, project-local skills in `.claude/skills` are the canonical source.

- Skill root: `.claude/skills`

## Skills
A skill is a set of local instructions to follow that is stored in a `SKILL.md` file. Below is the list of skills that can be used. Each entry includes a name, description, and file path so you can open the source for full instructions when using a specific skill.

### Available skills
- openspec-apply-change: Implement tasks from an OpenSpec change. Use when the user wants to start implementing, continue implementation, or work through tasks. (file: .claude/skills/openspec-apply-change/SKILL.md)
- openspec-archive-change: Archive a completed change in the experimental workflow. Use when the user wants to finalize and archive a change after implementation is complete. (file: .claude/skills/openspec-archive-change/SKILL.md)
- openspec-bulk-archive-change: Archive multiple completed changes at once. Use when archiving several parallel changes. (file: .claude/skills/openspec-bulk-archive-change/SKILL.md)
- openspec-continue-change: Continue working on an OpenSpec change by creating the next artifact. Use when the user wants to progress their change, create the next artifact, or continue their workflow. (file: .claude/skills/openspec-continue-change/SKILL.md)
- openspec-explore: Enter explore mode - a thinking partner for exploring ideas, investigating problems, and clarifying requirements. Use when the user wants to think through something before or during a change. (file: .claude/skills/openspec-explore/SKILL.md)
- openspec-ff-change: Fast-forward through OpenSpec artifact creation. Use when the user wants to quickly create all artifacts needed for implementation without stepping through each one individually. (file: .claude/skills/openspec-ff-change/SKILL.md)
- openspec-new-change: Start a new OpenSpec change using the experimental artifact workflow. Use when the user wants to create a new feature, fix, or modification with a structured step-by-step approach. (file: .claude/skills/openspec-new-change/SKILL.md)
- openspec-onboard: Guided onboarding for OpenSpec - walk through a complete workflow cycle with narration and real codebase work. (file: .claude/skills/openspec-onboard/SKILL.md)
- openspec-roadmap: Build a consistent OpenSpec roadmap from a high-level idea, including a roadmap doc and feature docs that can become separate OpenSpec proposals. (file: .claude/skills/openspec-roadmap/SKILL.md)
- openspec-sync-specs: Sync delta specs from a change to main specs. Use when the user wants to update main specs with changes from a delta spec, without archiving the change. (file: .claude/skills/openspec-sync-specs/SKILL.md)
- openspec-verify-change: Verify implementation matches change artifacts. Use when the user wants to validate that implementation is complete, correct, and coherent before archiving. (file: .claude/skills/openspec-verify-change/SKILL.md)
