# unit-assembly

**Status**: MODIFIED capability
**Change**: retrieval-quality-gate-toolishrag

---

## MODIFIED Requirements

### Requirement: Context Unit context assembly with retrieval mode support

**Modifies**: `ArcMemLlmReference` assembly pipeline and `PromptBudgetEnforcer` interaction.

The assembly pipeline SHALL support `RetrievalMode` configuration to control which context units are injected into the system prompt baseline.

**BULK mode**: The assembly pipeline SHALL behave identically to the current implementation. All active context units (up to the configured budget) are retrieved from `ArcMemEngine`, limited by budget count, and passed through token budget enforcement. No relevance scoring or filtering is applied. This preserves full backward compatibility.

**HYBRID mode**: The assembly pipeline SHALL inject only a reduced baseline into the system prompt:
1. All CANON context units SHALL always be included in the baseline (exempt from relevance filtering).
2. Non-CANON context units SHALL be scored using the heuristic relevance function and sorted by descending score.
3. The top-N non-CANON context units (where N = `context units.retrieval.baseline-top-k`) SHALL be selected for baseline injection.
4. Context Units scoring below `context units.retrieval.min-relevance` SHALL be excluded, even if the baseline count has not been reached.
5. Remaining context units (not in the baseline) SHALL be available for retrieval via the `retrieveUnits` tool.

**TOOL mode**: The assembly pipeline SHALL produce an empty baseline. No context units SHALL be injected into the system prompt. All context units SHALL be available exclusively via the retrieval tool.

`ArcMemLlmReference` SHALL apply relevance scoring before budget enforcement. The pipeline order SHALL be:
1. Retrieve active context units from `ArcMemEngine`
2. Apply retrieval mode filtering (baseline selection with relevance scoring)
3. Apply quality threshold filtering (exclude below `min-relevance`, exempt CANON)
4. Apply token budget enforcement via `PromptBudgetEnforcer`

The relevance filter step SHALL occur AFTER retrieval and BEFORE token budget enforcement.

#### Scenario: BULK mode identical to current behavior

- **GIVEN** `context units.retrieval.mode` is `BULK`
- **AND** 15 active context units exist with budget = 20
- **WHEN** `ArcMemLlmReference.getContent()` is called
- **THEN** all 15 context units pass through to budget enforcement
- **AND** the output is identical to the pre-change assembly pipeline

#### Scenario: HYBRID mode reduces injection count

- **GIVEN** `context units.retrieval.mode` is `HYBRID`
- **AND** 15 active context units exist: 2 CANON, 5 RELIABLE, 4 UNRELIABLE, 4 PROVISIONAL
- **AND** `context units.retrieval.baseline-top-k` is 5
- **WHEN** `ArcMemLlmReference.getContent()` is called
- **THEN** the 2 CANON context units are included unconditionally
- **AND** the top 5 non-CANON context units by heuristic score are included
- **AND** 7 total context units are passed to budget enforcement
- **AND** the remaining 8 context units are available via the retrieval tool

#### Scenario: TOOL mode produces empty baseline

- **GIVEN** `context units.retrieval.mode` is `TOOL`
- **AND** 10 active context units exist
- **WHEN** `ArcMemLlmReference.getContent()` is called
- **THEN** an empty string is returned (no context units injected)
- **AND** `getUnits()` returns an empty list
- **AND** all 10 context units remain available via the retrieval tool
