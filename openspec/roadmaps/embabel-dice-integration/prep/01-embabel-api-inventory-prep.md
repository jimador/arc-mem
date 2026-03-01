# Prep: Embabel API Inventory & Patterns

## Visibility Contract

- **UI Visibility**: None
- **Observability Visibility**: Living documentation reference
- **Acceptance**: Documentation is complete, reviewed, and linked from DEVELOPING.md

## Key Decisions

1. **Inventory scope**: Document ALL Embabel annotations used in dice-anchors codebase, plus available but unused capabilities
2. **File:line references required**: All code references MUST be verified against actual source at time of feature completion
3. **Pattern recommendations**: Capture Embabel framework recommendations; note which are adopted vs. deferred
4. **Tool organization**: Explicitly document CQS principle and read-only vs. full-access registration strategy

## Open Questions (Answered by Feature Specification)

1. What additional Embabel patterns are documented in 0.3.5-SNAPSHOT API docs?
2. Are there patterns used in the reference codebase (impromptu, urbot) that we should consider?
3. Should the inventory cover deprecated Embabel patterns?

## Acceptance Gates

- [ ] Inventory document created at `docs/dev/embabel-api-inventory.md`
- [ ] All Embabel annotations used in codebase documented with file:line references
- [ ] Available but unused capabilities explained with "when valuable" context
- [ ] Tool restructuring rationale (CQS principle) clearly explained
- [ ] Document reviewed and verified for accuracy
- [ ] DEVELOPING.md or README updated with reference to inventory

## Small-Model Constraints

- **Files touched**: ~5 (codebase inspection, documentation authoring)
- **Estimated runtime**: <2 hours
- **Verification command**: `grep -r "@EmbabelComponent\|@Action\|@MatryoshkaTools\|@LlmTool" src/`

## Implementation Notes

- Use Embabel 0.3.5-SNAPSHOT API docs as primary reference
- Cross-reference with CLAUDE.md for current integration details
- Search codebase for all Embabel annotation usages
- Validate file:line references before submission
