## ADDED Requirements

### Requirement: Event-driven cache invalidation

The system SHALL provide an `AnchorCacheInvalidator` Spring bean (`@Component`) that listens for `AnchorLifecycleEvent` instances and tracks dirty context IDs. When any lifecycle event fires (Promoted, Reinforced, Archived, Evicted, AuthorityChanged, ConflictResolved), the invalidator marks the event's contextId as dirty.

`AnchorsLlmReference` and `PropositionsLlmReference` SHALL check with the `AnchorCacheInvalidator` before using cached data. If the contextId is marked dirty, cached content is discarded and reloaded from the repository.

This eliminates the need for manual `refresh()` calls and prevents stale prompt data when anchors are modified by background processes (decay, conflict resolution, eviction).

#### Scenario: Promotion invalidates cache

- **GIVEN** `AnchorsLlmReference` has cached anchors for context "ctx-1"
- **WHEN** a new anchor is promoted in context "ctx-1" (publishing a `Promoted` lifecycle event)
- **THEN** the `AnchorCacheInvalidator` marks "ctx-1" as dirty
- **AND** the next call to `AnchorsLlmReference.ensureAnchorsLoaded()` for "ctx-1" reloads from the repository

#### Scenario: Reinforcement invalidates cache

- **GIVEN** cached anchor data for context "ctx-1"
- **WHEN** an anchor in "ctx-1" is reinforced (rank/authority change)
- **THEN** the cache is invalidated and fresh data is loaded on next access

#### Scenario: Events in other contexts do not invalidate

- **GIVEN** cached anchor data for context "ctx-1"
- **WHEN** an anchor is promoted in context "ctx-2"
- **THEN** the cache for "ctx-1" remains valid (only "ctx-2" is marked dirty)

#### Scenario: Clean read clears dirty flag

- **GIVEN** context "ctx-1" is marked dirty in the invalidator
- **WHEN** `AnchorsLlmReference` reloads anchors for "ctx-1"
- **THEN** the dirty flag for "ctx-1" is cleared

### Requirement: AnchorCacheInvalidator thread safety

The `AnchorCacheInvalidator` SHALL use `ConcurrentHashMap.newKeySet()` for tracking dirty context IDs, ensuring thread-safe operation when lifecycle events fire from multiple threads concurrently.

#### Scenario: Concurrent events from multiple threads

- **GIVEN** lifecycle events fire simultaneously from two threads for contexts "ctx-1" and "ctx-2"
- **WHEN** both events are processed by the invalidator
- **THEN** both contexts are correctly marked dirty without data races

## MODIFIED Requirements

### Requirement: PromptBudgetEnforcer documentation

**Modifies**: `PromptBudgetEnforcer` class and method Javadoc.

`PromptBudgetEnforcer` SHALL have comprehensive Javadoc documenting:
- The budget algorithm: how total token budget is divided between mandatory overhead (RFC 2119 preamble) and anchor content
- The drop order: PROVISIONAL first, then UNRELIABLE, then RELIABLE; CANON anchors are never dropped
- The best-effort guarantee: if CANON anchors alone exceed the budget, all CANON anchors are included (safety over budget compliance)
- How `BudgetResult` reports what was included, excluded, and the token estimates

#### Scenario: Documentation describes drop order

- **GIVEN** a developer reads `PromptBudgetEnforcer` Javadoc
- **WHEN** they look for the drop order
- **THEN** the Javadoc clearly states: "Anchors are dropped in ascending authority order: PROVISIONAL first, then UNRELIABLE, then RELIABLE. CANON anchors are never dropped."

### Requirement: DICE importance in budget eviction priority

**Modifies**: `PromptBudgetEnforcer` drop order within authority tiers.

Within the same authority tier, anchors with higher `diceImportance` SHALL be retained preferentially over anchors with lower importance. The primary drop order remains authority-based (PROVISIONAL → UNRELIABLE → RELIABLE), but within each tier, low-importance anchors are dropped before high-importance ones.

If `diceImportance` is 0.0 (default), the anchor is sorted by rank only (backward-compatible).

#### Scenario: High-importance PROVISIONAL retained over low-importance PROVISIONAL

- **GIVEN** the token budget requires dropping one PROVISIONAL anchor
- **AND** PROVISIONAL anchor "A1" has rank 300 and diceImportance 0.9
- **AND** PROVISIONAL anchor "A2" has rank 400 and diceImportance 0.1
- **WHEN** the budget enforcer selects which anchor to drop
- **THEN** anchor "A2" is dropped first (lower importance), despite having higher rank

#### Scenario: Default importance falls back to rank order

- **GIVEN** the token budget requires dropping one PROVISIONAL anchor
- **AND** both PROVISIONAL anchors have diceImportance 0.0
- **WHEN** the budget enforcer selects which anchor to drop
- **THEN** the anchor with the lower rank is dropped first (existing behavior)

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
