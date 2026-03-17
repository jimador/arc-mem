## MODIFIED Requirements

### Requirement: ARC Working Memory Unit (AWMU) context assembly with retrieval mode support

**Modifies**: `ArcMemLlmReference` assembly pipeline and `PromptBudgetEnforcer` interaction.

The assembly pipeline SHALL support `RetrievalMode` configuration to control which AWMUs are injected into the system prompt baseline.

**BULK mode**: The assembly pipeline SHALL behave identically to the current implementation. All active AWMUs (up to the configured budget) are retrieved from `ArcMemEngine`, limited by budget count, and passed through token budget enforcement. No relevance scoring or filtering is applied. This preserves full backward compatibility.

**HYBRID mode**: The assembly pipeline SHALL inject only a reduced baseline into the system prompt:
1. All CANON AWMUs SHALL always be included in the baseline (exempt from relevance filtering).
2. Non-CANON AWMUs SHALL be scored using the heuristic relevance function and sorted by descending score.
3. The top-N non-CANON AWMUs (where N = `arc-mem.retrieval.baseline-top-k`) SHALL be selected for baseline injection.
4. AWMUs scoring below `arc-mem.retrieval.min-relevance` SHALL be excluded, even if the baseline count has not been reached.
5. Remaining AWMUs (not in the baseline) SHALL be available for retrieval via the `retrieveUnits` tool.

**TOOL mode**: The assembly pipeline SHALL produce an empty baseline. No AWMUs SHALL be injected into the system prompt. All AWMUs SHALL be available exclusively via the retrieval tool.

`ArcMemLlmReference` SHALL apply relevance scoring before budget enforcement. The pipeline order SHALL be:
1. Retrieve active AWMUs from `ArcMemEngine`
2. Apply retrieval mode filtering (baseline selection with relevance scoring)
3. Apply quality threshold filtering (exclude below `min-relevance`, exempt CANON)
4. Apply token budget enforcement via `PromptBudgetEnforcer`

The relevance filter step SHALL occur AFTER retrieval and BEFORE token budget enforcement.

#### Scenario: BULK mode identical to current behavior

- **GIVEN** `arc-mem.retrieval.mode` is `BULK`
- **AND** 15 active AWMUs exist with budget = 20
- **WHEN** `ArcMemLlmReference.getContent()` is called
- **THEN** all 15 AWMUs pass through to budget enforcement
- **AND** the output is identical to the pre-change assembly pipeline

#### Scenario: HYBRID mode reduces injection count

- **GIVEN** `arc-mem.retrieval.mode` is `HYBRID`
- **AND** 15 active AWMUs exist: 2 CANON, 5 RELIABLE, 4 UNRELIABLE, 4 PROVISIONAL
- **AND** `arc-mem.retrieval.baseline-top-k` is 5
- **WHEN** `ArcMemLlmReference.getContent()` is called
- **THEN** the 2 CANON AWMUs are included unconditionally
- **AND** the top 5 non-CANON AWMUs by heuristic score are included
- **AND** 7 total AWMUs are passed to budget enforcement
- **AND** the remaining 8 AWMUs are available via the retrieval tool

#### Scenario: TOOL mode produces empty baseline

- **GIVEN** `arc-mem.retrieval.mode` is `TOOL`
- **AND** 10 active AWMUs exist
- **WHEN** `ArcMemLlmReference.getContent()` is called
- **THEN** an empty string is returned (no AWMUs injected)
- **AND** `getUnits()` returns an empty list
- **AND** all 10 AWMUs remain available via the retrieval tool

## ADDED Requirements

### Requirement: Event-driven cache invalidation

The system SHALL provide an `ArcMemCacheInvalidator` Spring bean (`@Component`) that listens for `UnitLifecycleEvent` instances and tracks dirty context IDs. When any lifecycle event fires (Promoted, Reinforced, Archived, Evicted, AuthorityChanged, ConflictResolved), the invalidator marks the event's contextId as dirty.

`ArcMemLlmReference` and `PropositionsLlmReference` SHALL check with the `ArcMemCacheInvalidator` before using cached data. If the contextId is marked dirty, cached content is discarded and reloaded from the repository.

This eliminates the need for manual `refresh()` calls and prevents stale prompt data when AWMUs are modified by background processes (decay, conflict resolution, eviction).

#### Scenario: Promotion invalidates cache

- **GIVEN** `ArcMemLlmReference` has cached AWMUs for context "ctx-1"
- **WHEN** a new AWMU is promoted in context "ctx-1" (publishing a `Promoted` lifecycle event)
- **THEN** the `ArcMemCacheInvalidator` marks "ctx-1" as dirty
- **AND** the next call to `ArcMemLlmReference.ensureUnitsLoaded()` for "ctx-1" reloads from the repository

#### Scenario: Reinforcement invalidates cache

- **GIVEN** cached AWMU data for context "ctx-1"
- **WHEN** a AWMU in "ctx-1" is reinforced (rank/authority change)
- **THEN** the cache is invalidated and fresh data is loaded on next access

#### Scenario: Events in other contexts do not invalidate

- **GIVEN** cached AWMU data for context "ctx-1"
- **WHEN** a AWMU is promoted in context "ctx-2"
- **THEN** the cache for "ctx-1" remains valid (only "ctx-2" is marked dirty)

#### Scenario: Clean read clears dirty flag

- **GIVEN** context "ctx-1" is marked dirty in the invalidator
- **WHEN** `ArcMemLlmReference` reloads AWMUs for "ctx-1"
- **THEN** the dirty flag for "ctx-1" is cleared

### Requirement: ArcMemCacheInvalidator thread safety

The `ArcMemCacheInvalidator` SHALL use `ConcurrentHashMap.newKeySet()` for tracking dirty context IDs, ensuring thread-safe operation when lifecycle events fire from multiple threads concurrently.

#### Scenario: Concurrent events from multiple threads

- **GIVEN** lifecycle events fire simultaneously from two threads for contexts "ctx-1" and "ctx-2"
- **WHEN** both events are processed by the invalidator
- **THEN** both contexts are correctly marked dirty without data races

## MODIFIED Requirements

### Requirement: PromptBudgetEnforcer documentation

**Modifies**: `PromptBudgetEnforcer` class and method Javadoc.

`PromptBudgetEnforcer` SHALL have comprehensive Javadoc documenting:
- The budget algorithm: how total token budget is divided between mandatory overhead (RFC 2119 preamble) and AWMU content
- The drop order: PROVISIONAL first, then UNRELIABLE, then RELIABLE; CANON AWMUs are never dropped
- The best-effort guarantee: if CANON AWMUs alone exceed the budget, all CANON AWMUs are included (safety over budget compliance)
- How `BudgetResult` reports what was included, excluded, and the token estimates

#### Scenario: Documentation describes drop order

- **GIVEN** a developer reads `PromptBudgetEnforcer` Javadoc
- **WHEN** they look for the drop order
- **THEN** the Javadoc clearly states: "AWMUs are dropped in ascending authority order: PROVISIONAL first, then UNRELIABLE, then RELIABLE. CANON AWMUs are never dropped."

### Requirement: DICE importance in budget eviction priority

**Modifies**: `PromptBudgetEnforcer` drop order within authority tiers.

Within the same authority tier, AWMUs with higher `diceImportance` SHALL be retained preferentially over AWMUs with lower importance. The primary drop order remains authority-based (PROVISIONAL → UNRELIABLE → RELIABLE), but within each tier, low-importance AWMUs are dropped before high-importance ones.

If `diceImportance` is 0.0 (default), the AWMU is sorted by rank only (backward-compatible).

#### Scenario: High-importance PROVISIONAL retained over low-importance PROVISIONAL

- **GIVEN** the token budget requires dropping one PROVISIONAL AWMU
- **AND** PROVISIONAL AWMU "A1" has rank 300 and diceImportance 0.9
- **AND** PROVISIONAL AWMU "A2" has rank 400 and diceImportance 0.1
- **WHEN** the budget enforcer selects which AWMU to drop
- **THEN** AWMU "A2" is dropped first (lower importance), despite having higher rank

#### Scenario: Default importance falls back to rank order

- **GIVEN** the token budget requires dropping one PROVISIONAL AWMU
- **AND** both PROVISIONAL AWMUs have diceImportance 0.0
- **WHEN** the budget enforcer selects which AWMU to drop
- **THEN** the AWMU with the lower rank is dropped first (existing behavior)

### Requirement: TokenCounter SPI documentation

**Modifies**: `TokenCounter` interface and `CharHeuristicTokenCounter`.

`TokenCounter` SHALL have Javadoc documenting:
- The interface contract: estimate token count for a given text
- That `CharHeuristicTokenCounter` (4 chars/token) is a rough heuristic suitable for budget approximation but not precise token counting
- How to implement a more accurate counter (e.g., using tiktoken or a model-specific tokenizer)
- Thread-safety requirement: implementations MUST be thread-safe

#### Scenario: Documentation explains heuristic limitations

- **GIVEN** a developer reads `CharHeuristicTokenCounter` Javadoc
- **WHEN** they look for accuracy information
- **THEN** the Javadoc clearly states the 4-chars-per-token heuristic and recommends model-specific tokenizers for precise counting
