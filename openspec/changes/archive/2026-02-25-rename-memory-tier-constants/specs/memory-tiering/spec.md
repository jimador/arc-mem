## MODIFIED Requirements

### Requirement: MemoryTier enum

The system SHALL provide a `MemoryTier` enum with values `COLD`, `WARM`, `HOT` ordered by ascending priority. `MemoryTier.compareTo()` SHALL reflect this ordering (`COLD < WARM < HOT`). Documentation SHALL reference these names exclusively; aliases or prefixed variants (`T0_`/`T1_`/`T2_`) SHALL NOT appear in any project documentation or specs.

#### Scenario: Enum ordering

- **GIVEN** the `MemoryTier` enum
- **WHEN** comparing tier values
- **THEN** `COLD.compareTo(WARM) < 0` AND `WARM.compareTo(HOT) < 0`

#### Scenario: Documentation consistency

- **GIVEN** all project documentation (CLAUDE.md, openspec/project.md, specs)
- **WHEN** referencing MemoryTier values
- **THEN** only `COLD`, `WARM`, `HOT` SHALL appear; no `T0_INVARIANT`, `T1_WORKING`, `T2_EPISODIC` references SHALL exist
