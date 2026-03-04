# Prep: Adaptive Prompt Footprint

## Feature Reference

Feature ID: `F09`. Change slug: `adaptive-prompt-footprint`. Wave 3. Priority: SHOULD.
Feature doc: `openspec/roadmaps/anchor-memory-optimization/features/09-adaptive-prompt-footprint.md`
Research: `openspec/research/llm-optimization-external-research.md` (rec D)

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Locked Decisions

These decisions are final and MUST NOT be revisited during implementation.

1. **Authority-graduated templates**: Prompt templates MUST vary by authority level. Four tiers: PROVISIONAL (full), UNRELIABLE (detailed), RELIABLE (condensed), CANON (minimal).
2. **CANON condensed**: CANON anchors SHOULD use the most condensed template -- identifier + short assertion. This is the primary token savings target.
3. **PROVISIONAL full**: PROVISIONAL anchors MUST use the most detailed template -- full text + context + source citation. Newer propositions need maximum detail for compliance.
4. **Configurable opt-in**: Adaptive footprint MUST be opt-in via configuration. Default behavior MUST be uniform templates (current behavior preserved).
5. **Graduated rollout**: CANON-only reduction MUST be implemented and validated first. RELIABLE reduction is gated on CANON compliance validation.
6. **External templates**: Template content MUST be defined in prompt template files (`src/main/resources/prompts/`), not hardcoded in Java. This enables iteration without recompilation.

## Open Questions

These questions MUST be resolved during design/implementation.

### Q1: Template Content Per Tier

**Question**: What specific template content provides the best token-savings-to-compliance ratio for each authority tier?

**Decision space**:
- **PROVISIONAL template**: Full proposition text, extraction context (source turn, surrounding dialogue), source authority signal. Estimated ~80-120 tokens per anchor.
- **UNRELIABLE template**: Full proposition text, brief context (one-line summary of origin). Estimated ~40-60 tokens per anchor.
- **RELIABLE template**: Key assertion only (main claim, no surrounding context). Estimated ~20-30 tokens per anchor.
- **CANON template candidates**:
  - (a) Identifier + assertion: `[CANON-7] The dragon's name is Aldrathar.` (~15-20 tokens)
  - (b) Tag-only: `[CANON] Aldrathar is a dragon.` (~10-15 tokens)
  - (c) Structured minimal: `FACT: Aldrathar = dragon (CANON, rank 850)` (~10-15 tokens)

**Recommendation**: Start with option (a) for CANON -- it preserves the anchor's core assertion in natural language while adding only the CANON identifier for traceability. Options (b) and (c) risk being too terse for the model to contextualize. Iterate based on compliance testing.

**Constraints**: All templates MUST include the anchor's proposition text (may be abbreviated but MUST NOT be omitted). CANON template MUST include an authority indicator.

### Q2: Compliance Threshold for CANON Reduction

**Question**: What compliance degradation, if any, is acceptable for CANON anchors using condensed templates?

**Decision space**:
- (a) Zero tolerance: any measured compliance drop from uniform baseline triggers fallback.
- (b) 5% tolerance: up to 5% compliance rate reduction is acceptable given token savings.
- (c) Statistical tolerance: no significant difference (p < 0.05) in compliance rate between adaptive and uniform on a benchmark of N runs.

**Recommendation**: Option (c) -- statistical tolerance. A single run may show variance; significance testing across multiple simulation runs prevents over-reacting to noise. Minimum N = 5 runs per configuration.

**Constraints**: Compliance measurement MUST use the same simulation scenarios and conditions for both adaptive and uniform configurations. If compliance enforcement layer (F03) is available, it provides the measurement infrastructure.

### Q3: Interaction with Retrieval Modes

**Question**: How does adaptive footprint interact with BULK, HYBRID, and TOOL retrieval modes?

**Decision space**:
- **BULK mode**: All active anchors are included. Adaptive footprint applies to all anchors. Maximum token savings opportunity.
- **HYBRID mode**: Relevance-scored selection of top-k anchors (CANON always included). Adaptive footprint applies to selected anchors. Relevance scoring may partially compensate for reduced detail (selected anchors are already the most relevant).
- **TOOL mode**: Empty baseline; anchors retrieved on-demand. Adaptive footprint applies to tool-retrieved anchors. Impact is minimal (few anchors in prompt at any time).

**Recommendation**: Apply adaptive footprint uniformly across all retrieval modes. HYBRID mode benefits most because it already prioritizes relevance, so condensed templates for high-authority anchors compound the efficiency gain. TOOL mode impact is negligible but consistency is valuable.

**Constraints**: Template selection MUST be independent of retrieval mode. The retrieval mode selects which anchors to include; the footprint adapter selects how to format them. These are orthogonal.

## Small-Model Task Constraints

Each implementation task MUST touch at most **3 files** (excluding test files). Each task MUST be independently verifiable via `./mvnw test`.

### Suggested Task Breakdown

1. **Task 1: Authority-graduated prompt templates** (3 files)
   - Create 4 prompt template files: `anchor-provisional.st`, `anchor-unreliable.st`, `anchor-reliable.st`, `anchor-canon.st` (or equivalent naming per existing `PromptPathConstants` convention).
   - Add path constants to `PromptPathConstants`.
   - Verify templates load via `PromptTemplates.load()`.
   - Files: 4 template files (count as 1 logical unit), `PromptPathConstants.java`, test.

2. **Task 2: AnchorsLlmReference template selection** (3 files)
   - Modify `AnchorsLlmReference` to select prompt template based on anchor authority when adaptive footprint is enabled.
   - Add configuration toggle to `DiceAnchorsProperties`.
   - Default: uniform template (current behavior); opt-in: authority-graduated.
   - Files: `AnchorsLlmReference.java`, `DiceAnchorsProperties.java`, test.

3. **Task 3: PromptBudgetEnforcer authority-aware estimation** (3 files)
   - Update `PromptBudgetEnforcer` to estimate token cost per anchor using authority-dependent template sizes.
   - Ensure drop-order logic accounts for variable template sizes (dropping a PROVISIONAL anchor saves more tokens than dropping a CANON anchor).
   - Files: `PromptBudgetEnforcer.java`, `TokenCounter` integration, test.

4. **Task 4: Compliance validation + metrics** (3 files)
   - Add per-tier compliance rate tracking to observability output.
   - Add token savings metrics (tokens saved vs. uniform baseline) to `ContextTrace` or `BudgetResult`.
   - Add fallback logic: if per-tier compliance drops below threshold, revert that tier to full template.
   - Files: compliance metrics record, `AnchorsLlmReference.java` (fallback logic), test.

## Gates

Implementation is complete when ALL of the following are satisfied:

1. **Token savings measurable**: Running a simulation scenario with adaptive footprint enabled MUST produce measurably lower token utilization for the anchor context block compared to uniform templates, with equivalent or higher anchor inclusion count.
2. **Compliance rate stable**: CANON anchor compliance rate with condensed templates MUST NOT show statistically significant degradation compared to uniform templates (measured across >= 5 simulation runs).
3. **Opt-in default**: With default configuration (adaptive footprint disabled), ALL existing simulation scenarios MUST produce identical prompt output to current behavior.
4. **Template correctness**: Each authority tier MUST use the correct template. Unit tests MUST verify template selection for all 4 authority levels.

## Dependencies Map

```
(no feature dependencies)
                            ──► F09 (adaptive-prompt-footprint)

Integrates with (existing):
  - AnchorsLlmReference (template selection during prompt assembly)
  - PromptBudgetEnforcer (authority-aware token estimation)
  - PromptTemplates / PromptPathConstants (template loading)
  - TokenCounter (token estimation)
  - CompliancePolicy (compliance monitoring, if F03 available)
  - RetrievalConfig (BULK / HYBRID / TOOL mode interaction)
```
