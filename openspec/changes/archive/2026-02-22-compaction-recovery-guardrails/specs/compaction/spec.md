## MODIFIED Requirements

### Requirement: Post-compaction fact survival validation

After `SimSummaryGenerator` produces a compaction summary, the system SHALL validate that all protected content items survived in the summary. Validation SHALL normalize both the protected content text and summary text (lowercase, strip non-alphanumeric) and check for word-level match ratio rather than substring containment alone. The match ratio SHALL be computed as the fraction of normalized protected content words found anywhere in the normalized summary text. The minimum acceptable match ratio SHALL be controlled by the `minMatchRatio` parameter in `CompactionConfig` (default 0.5, range [0.0, 1.0]) rather than any hardcoded threshold. Any protected content item whose contribution falls below the effective threshold SHALL be reported as a `CompactionLossEvent` on the turn result.

This requirement supersedes the prior version that hardcoded a 50% threshold — all references to a hardcoded threshold are removed. `CompactionConfig.minMatchRatio` is the sole source of truth.

#### Scenario: All protected facts survive above threshold

- **GIVEN** `CompactionConfig.minMatchRatio` is 0.5
- **WHEN** compaction produces a summary and all protected anchor texts achieve a word match ratio >= 0.5
- **THEN** zero CompactionLossEvents are reported

#### Scenario: Protected fact missing from summary

- **WHEN** compaction produces a summary and a protected anchor with text "Baron Krell rules the Northern Province" achieves a word match ratio of 0.0 (no words found)
- **THEN** a CompactionLossEvent is reported identifying the missing anchor

#### Scenario: Partial match at or above threshold counts as survival

- **GIVEN** `CompactionConfig.minMatchRatio` is 0.5
- **WHEN** a protected anchor states "Baron Krell rules the Northern Province" and the summary contains "baron krell" (2 of 5 normalized words — ratio 0.4)
- **THEN** the anchor is reported as a CompactionLossEvent (below the 0.5 threshold)

#### Scenario: Partial match at or above threshold with lowered threshold

- **GIVEN** `CompactionConfig.minMatchRatio` is 0.3
- **WHEN** a protected anchor states "Baron Krell rules the Northern Province" and the summary contains "baron krell" (ratio 0.4)
- **THEN** the anchor is considered to have survived (above the 0.3 threshold)

#### Scenario: Loss event displayed in UI

- **WHEN** a CompactionLossEvent occurs on turn 8
- **THEN** the Compaction tab for turn 8 shows the lost anchor's text, authority, and rank

## ADDED Requirements

### Requirement: Explicit context cleanup on simulation termination

`SimulationService` SHALL call `CompactedContextProvider.clearContext(contextId)` in a `finally` block wrapping the simulation run loop, ensuring context is cleaned up on both normal completion and abnormal termination (cancellation, exception). The cleanup SHALL execute even when individual turns throw exceptions. The `clearContext` call SHALL be idempotent — calling it on an already-cleared context SHALL be a no-op and SHALL NOT throw.

#### Scenario: Context cleared on normal completion

- **GIVEN** a simulation that runs to completion with all turns succeeding
- **WHEN** `SimulationService.runSimulation()` returns normally
- **THEN** `CompactedContextProvider.clearContext(contextId)` SHALL have been called exactly once

#### Scenario: Context cleared on exception

- **GIVEN** a simulation where turn 5 throws an unhandled exception
- **WHEN** the exception propagates out of `SimulationService.runSimulation()`
- **THEN** `CompactedContextProvider.clearContext(contextId)` SHALL still be called via the `finally` block
- **AND** the context SHALL contain no residual messages for the simulation's contextId

#### Scenario: Context cleared on cancellation

- **GIVEN** a simulation that is cancelled mid-run (e.g., interrupted)
- **WHEN** the cancellation propagates through the run loop
- **THEN** `CompactedContextProvider.clearContext(contextId)` SHALL be called before the cancellation completes

#### Scenario: clearContext is idempotent

- **GIVEN** `CompactedContextProvider.clearContext(contextId)` has already been called for a given contextId
- **WHEN** `clearContext(contextId)` is called a second time (e.g., double-finally)
- **THEN** no exception SHALL be thrown and the method SHALL return without side effects
