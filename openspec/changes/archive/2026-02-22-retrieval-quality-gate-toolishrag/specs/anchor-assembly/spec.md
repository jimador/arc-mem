# anchor-assembly

**Status**: MODIFIED capability
**Change**: retrieval-quality-gate-toolishrag

---

## MODIFIED Requirements

### Requirement: Anchor context assembly with retrieval mode support

**Modifies**: `AnchorsLlmReference` assembly pipeline and `PromptBudgetEnforcer` interaction.

The assembly pipeline SHALL support `RetrievalMode` configuration to control which anchors are injected into the system prompt baseline.

**BULK mode**: The assembly pipeline SHALL behave identically to the current implementation. All active anchors (up to the configured budget) are retrieved from `AnchorEngine`, limited by budget count, and passed through token budget enforcement. No relevance scoring or filtering is applied. This preserves full backward compatibility.

**HYBRID mode**: The assembly pipeline SHALL inject only a reduced baseline into the system prompt:
1. All CANON anchors SHALL always be included in the baseline (exempt from relevance filtering).
2. Non-CANON anchors SHALL be scored using the heuristic relevance function and sorted by descending score.
3. The top-N non-CANON anchors (where N = `dice-anchors.retrieval.baseline-top-k`) SHALL be selected for baseline injection.
4. Anchors scoring below `dice-anchors.retrieval.min-relevance` SHALL be excluded, even if the baseline count has not been reached.
5. Remaining anchors (not in the baseline) SHALL be available for retrieval via the `retrieveAnchors` tool.

**TOOL mode**: The assembly pipeline SHALL produce an empty baseline. No anchors SHALL be injected into the system prompt. All anchors SHALL be available exclusively via the retrieval tool.

`AnchorsLlmReference` SHALL apply relevance scoring before budget enforcement. The pipeline order SHALL be:
1. Retrieve active anchors from `AnchorEngine`
2. Apply retrieval mode filtering (baseline selection with relevance scoring)
3. Apply quality threshold filtering (exclude below `min-relevance`, exempt CANON)
4. Apply token budget enforcement via `PromptBudgetEnforcer`

The relevance filter step SHALL occur AFTER retrieval and BEFORE token budget enforcement.

#### Scenario: BULK mode identical to current behavior

- **GIVEN** `dice-anchors.retrieval.mode` is `BULK`
- **AND** 15 active anchors exist with budget = 20
- **WHEN** `AnchorsLlmReference.getContent()` is called
- **THEN** all 15 anchors pass through to budget enforcement
- **AND** the output is identical to the pre-change assembly pipeline

#### Scenario: HYBRID mode reduces injection count

- **GIVEN** `dice-anchors.retrieval.mode` is `HYBRID`
- **AND** 15 active anchors exist: 2 CANON, 5 RELIABLE, 4 UNRELIABLE, 4 PROVISIONAL
- **AND** `dice-anchors.retrieval.baseline-top-k` is 5
- **WHEN** `AnchorsLlmReference.getContent()` is called
- **THEN** the 2 CANON anchors are included unconditionally
- **AND** the top 5 non-CANON anchors by heuristic score are included
- **AND** 7 total anchors are passed to budget enforcement
- **AND** the remaining 8 anchors are available via the retrieval tool

#### Scenario: TOOL mode produces empty baseline

- **GIVEN** `dice-anchors.retrieval.mode` is `TOOL`
- **AND** 10 active anchors exist
- **WHEN** `AnchorsLlmReference.getContent()` is called
- **THEN** an empty string is returned (no anchors injected)
- **AND** `getAnchors()` returns an empty list
- **AND** all 10 anchors remain available via the retrieval tool
