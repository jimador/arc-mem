# Prep: Whitepaper Outline Refinement

## Feature

F01 — whitepaper-outline-refinement

## Key Decisions

1. **Terminology**: Pick canonical condition names (paper terminology vs code terminology). Recommendation: use paper names (FULL_ARC, NO_ACTIVE_MEMORY) as display names; keep code enum names as internal identifiers with a mapping table.
2. **NO_RANK_DIFFERENTIATION**: Include in the paper as a secondary ablation (tests rank dynamics independently from authority). Add to Section 7.3.
3. **RYS article**: Exclude from Related Work. Tangential to working memory governance. Note in outline comments as "evaluated, excluded."
4. **ACT-R paper placement**: All 4 papers go in Section 3.2. Two are orthogonal (LLM-ACTR, Prompt-Enhanced ACT-R), two are complementary (Human-Like Remembering, Embedding Integration).

## Open Questions

1. Should the paper use "ARC" or "ARC-Mem" as the mechanism name? The outline uses "ARC" but the codebase uses "ARC-Mem."
2. Does the formalization section (5) need updating based on current engine implementation, or is it aspirational?

## Acceptance Gate

- All 4 ACT-R papers placed in outline with positioning statements
- Terminology mapping table exists
- Section 7 reflects current ExperimentRunner capabilities
- Reference inventory complete

## Research Dependencies

R01 (ACT-R positioning) — should be completed before or during this feature

## Handoff Notes

Doc-only change. No code. Reviewer should diff against current outline and verify all TODO items from `docs/related-work-and-research.md` are resolved.
