# Feature: Whitepaper Outline Refinement

## Feature ID

`F01`

## Summary

Update the whitepaper outline (`docs/drafts/whitepaper-outline.md`) to integrate recent ACT-R + LLM literature, align ablation condition and metric terminology between paper and codebase, and ensure the methodology section reflects the current experiment runner infrastructure. This is a planning/documentation change — no application code.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: The whitepaper outline's Related Work section (3.2) is missing 4 recent ACT-R + LLM papers identified in `docs/related-work-and-research.md` TODO. Condition names in the outline (FULL_ARC, NO_ACTIVE_MEMORY) don't match codebase names (FULL_AWMU, NO_AWMU). The methodology section doesn't reflect the current ExperimentRunner/BenchmarkRunner capabilities.
2. Value delivered: A single authoritative terminology mapping, complete bibliography backlog, and methodology section that accurately describes what the experiment runner can produce.
3. Why now: Every subsequent feature depends on stable terminology. Writing code before aligning names guarantees a rename churn later.

## Scope

### In Scope

1. Integrate 4 ACT-R + LLM papers from related-work TODO into Section 3.2 of the outline
2. Decide canonical condition names and document the mapping (paper name ↔ code name)
3. Add NO_RANK_DIFFERENTIATION to the outline or document why it's excluded
4. Update Section 7 (Evaluation Methodology) to reflect current ExperimentRunner, BenchmarkRunner, and ScoringService capabilities
5. Update the reference inventory (Section 14) with any new citations
6. Assess RYS (layer duplication) article for relevance — include or exclude with documented rationale

### Out of Scope

1. Writing paper prose (this updates the outline structure, not the paper itself)
2. Changing codebase naming (that's F02)
3. Adding new sections to the outline

## Dependencies

1. Feature dependencies: none
2. Technical prerequisites: Access to `docs/drafts/whitepaper-outline.md`, `docs/related-work-and-research.md`
3. Parent objectives: Whitepaper data-gathering roadmap

## Research Requirements

1. Open questions: How do the 4 ACT-R papers position relative to ARC? Is RYS relevant?
2. Required channels: web, repo-docs
3. Research completion gate: Each paper classified as complementary/competitive/orthogonal with 1-line positioning statement

## Impacted Areas

1. Packages/components: none (doc-only)
2. Data/persistence: none
3. Domain-specific subsystem impacts: none

## Visibility Requirements

### Observability Visibility

1. Logs/events/metrics: Updated outline diffable via git
2. Trace/audit payload: Commit message documents what changed and why
3. How to verify: Review the diff against the current outline; confirm all TODO papers placed, terminology aligned, methodology updated

## Acceptance Criteria

1. All 4 ACT-R papers from related-work TODO MUST appear in Section 3.2 with positioning statements
2. A terminology mapping table MUST exist documenting paper name ↔ code name for all conditions
3. Section 7.3 (Conditions and Ablations) MUST list all conditions the experiment runner will support, including NO_RANK_DIFFERENTIATION
4. Section 7.4 (Metrics) MUST reflect current ScoringService output fields
5. RYS article MUST be evaluated and either placed or excluded with documented rationale
6. Reference inventory MUST include all newly cited papers

## Risks and Mitigations

1. Risk: Terminology decision delays other features
2. Mitigation: Decide names in this feature; F02 implements the rename

## Proposal Seed

### Suggested OpenSpec Change Slug

`whitepaper-outline-refinement`

### Proposal Starter Inputs

1. Problem statement: The whitepaper outline has terminology drift from the codebase, missing recent ACT-R literature, and a methodology section that doesn't reflect the current experiment runner. This creates friction for every subsequent feature.
2. Why now: Foundation feature — every other feature references condition names and metrics defined here.
3. Constraints: Doc-only change. No code modifications.
4. Outcomes: Updated outline with complete bibliography, aligned terminology, and accurate methodology.

### Suggested Capability Areas

1. ACT-R literature integration
2. Terminology alignment
3. Methodology accuracy

### Candidate Requirement Blocks

1. Requirement: Related Work section MUST reference all known ACT-R + LLM papers
2. Scenario: Reviewer reads Section 3.2 and finds all relevant prior work cited with clear positioning

## Validation Plan

1. Diff review against current outline
2. Verify all TODO items from related-work doc are resolved
3. Terminology table cross-checked against AblationCondition enum

## Known Limitations

1. Paper prose quality depends on later writing work outside this roadmap
2. Bibliography may need further expansion as new papers are discovered during implementation

## Suggested Command

`/opsx:new whitepaper-outline-refinement`
