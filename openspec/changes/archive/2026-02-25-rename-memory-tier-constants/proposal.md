## Why

The `MemoryTier` enum values carry `T0_`/`T1_`/`T2_` ordinal prefixes that are implementation noise — they leak into log lines, Neo4j persisted strings, lifecycle events, and UI badges. Dropping the prefixes (`T0_INVARIANT → INVARIANT`, `T1_WORKING → WORKING`, `T2_EPISODIC → EPISODIC`) makes the names self-documenting everywhere they appear.

## What Changes

- **BREAKING**: Rename `MemoryTier.T0_INVARIANT` → `MemoryTier.INVARIANT`
- **BREAKING**: Rename `MemoryTier.T1_WORKING` → `MemoryTier.WORKING`
- **BREAKING**: Rename `MemoryTier.T2_EPISODIC` → `MemoryTier.EPISODIC`
- Update all reference sites in main source (AnchorEngine, Anchor, AuthorityConflictResolver, RelevanceScorer, ScoredAnchor, ContextInspectorPanel, SimulationTurnExecutor, ChatActions, AnchorLifecycleEvent, AnchorRepository migration strings)
- Update all test reference sites (MemoryTierTest, AuthorityConflictResolverTest, RelevanceScorerTest, AnchorsLlmReferenceRetrievalTest, ExponentialDecayPolicyTest, AssertionTest, AnchorEngineInvariantTest)
- Update Neo4j migration strings in AnchorRepository (`'T0_INVARIANT'`, `'T1_WORKING'`, `'T2_EPISODIC'` literals in Cypher)
- Update CLAUDE.md and openspec/project.md documentation
- Update `openspec/specs/memory-tiering/spec.md`

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `memory-tiering`: Enum constant names change (`T0_INVARIANT`, `T1_WORKING`, `T2_EPISODIC` → `INVARIANT`, `WORKING`, `EPISODIC`). Behavior, thresholds, and semantics are unchanged.

## Impact

- `MemoryTier.java` — enum definition
- `Anchor.java` — record field and factory methods
- `AnchorEngine.java` — tier computation and persistence
- `AnchorRepository.java` — Neo4j migration Cypher string literals
- `AuthorityConflictResolver.java` — switch expression
- `RelevanceScorer.java` — switch expression
- `ScoredAnchor.java` — record field type usage
- `ContextInspectorPanel.java` — switch expression for UI badges
- `SimulationTurnExecutor.java` — tier counting and decay multiplier switch
- `ChatActions.java` — tier counting
- `AnchorLifecycleEvent.java` — TierChanged inner class
- 8 test classes referencing tier constants
- CLAUDE.md, openspec/project.md, openspec/specs/memory-tiering/spec.md documentation
