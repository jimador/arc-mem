# bi-temporal-validity

**Status**: NEW capability
**Change**: bi-temporal-validity-and-supersession

---

## Requirements

### Requirement: Temporal field model

`PropositionNode` SHALL have four temporal fields, all of type `Instant` and nullable:
- `validFrom` -- the instant from which the anchor's fact is considered valid
- `validTo` -- the instant at which the anchor's fact ceases to be valid
- `transactionStart` -- the instant at which the anchor state change was written
- `transactionEnd` -- the instant at which the anchor was superseded or archived

`validFrom` and `transactionStart` SHALL be set at promotion time (when a proposition becomes an anchor). `validTo` and `transactionEnd` SHALL remain null (open-ended) until the anchor is superseded or archived.

#### Scenario: Temporal fields set on promotion

- **GIVEN** a proposition is promoted to an anchor
- **WHEN** `AnchorEngine.promote()` completes
- **THEN** the anchor SHALL have `validFrom` and `transactionStart` set to the promotion instant
- **AND** `validTo` and `transactionEnd` SHALL be null

#### Scenario: Temporal fields set on supersession

- **GIVEN** an active anchor "A1" is superseded by incoming anchor "A2" via conflict replacement
- **WHEN** the supersession is recorded
- **THEN** anchor "A1" SHALL have `validTo` and `transactionEnd` set to the supersession instant

#### Scenario: Temporal fields set on archive

- **GIVEN** an active anchor "A1" is archived (eviction, manual, or decay)
- **WHEN** the archive operation completes
- **THEN** anchor "A1" SHALL have `validTo` and `transactionEnd` set to the archive instant

### Requirement: Temporal query support

`AnchorRepository` SHALL provide the following temporal query methods:

1. `findValidAt(String contextId, Instant instant)` -- returns all anchors where `validFrom <= instant AND (validTo IS NULL OR validTo > instant)`. This provides a point-in-time snapshot of the active anchor set.

2. `findSupersessionChain(String anchorId)` -- returns an ordered list of anchors in the supersession lineage (predecessors and successors), ordered by `validFrom` ascending. The chain is constructed by traversing `SUPERSEDES` relationships in both directions.

#### Scenario: findValidAt returns correct snapshot

- **GIVEN** anchor "A1" with `validFrom = T1` and `validTo = T3`
- **AND** anchor "A2" with `validFrom = T3` and `validTo = null`
- **AND** anchor "A3" with `validFrom = T2` and `validTo = null`
- **WHEN** `findValidAt(contextId, T2)` is called
- **THEN** anchors "A1" and "A3" SHALL be returned (A2 is not yet valid at T2)

#### Scenario: findValidAt handles null temporal fields gracefully

- **GIVEN** a legacy anchor "L1" with `validFrom = null` and `validTo = null` and `created = T0`
- **WHEN** `findValidAt(contextId, T5)` is called
- **THEN** anchor "L1" SHALL be included in the results (treated as valid from `created` to present)

#### Scenario: findSupersessionChain returns ordered lineage

- **GIVEN** anchor "A1" was superseded by "A2", which was superseded by "A3"
- **WHEN** `findSupersessionChain("A1")` is called
- **THEN** the result SHALL be `["A1", "A2", "A3"]` ordered by `validFrom` ascending
- **WHEN** `findSupersessionChain("A3")` is called
- **THEN** the result SHALL be `["A1", "A2", "A3"]` ordered by `validFrom` ascending

### Requirement: Backward compatibility for null temporal fields

When temporal fields are null (legacy data created before bi-temporal support), temporal queries SHALL treat the anchor as valid from `created` to present (open-ended). Specifically:
- If `validFrom` is null, the anchor's `created` timestamp SHALL be used as the effective `validFrom`
- If `validTo` is null, the anchor is treated as currently valid (open-ended)
- If `transactionStart` is null, the anchor's `created` timestamp SHALL be used
- If `transactionEnd` is null, the transaction is treated as current

This ensures existing anchors without temporal fields continue to function correctly without requiring a data migration.

#### Scenario: Legacy anchor included in temporal query

- **GIVEN** a legacy anchor with all temporal fields null and `created = T0`
- **WHEN** `findValidAt(contextId, T5)` is called
- **THEN** the anchor SHALL be included in results (effective validFrom = T0, open-ended)

#### Scenario: Legacy anchor excluded after archival sets temporal fields

- **GIVEN** a legacy anchor with all temporal fields null and `created = T0`
- **WHEN** the anchor is archived at instant T3, setting `validTo = T3` and `transactionEnd = T3`
- **AND** `findValidAt(contextId, T5)` is called
- **THEN** the anchor SHALL NOT be included in results (validTo = T3 < T5)

## Invariants

- **BT1**: `validFrom` and `transactionStart` SHALL always be set together (both null or both non-null).
- **BT2**: `validTo` and `transactionEnd` SHALL always be set together (both null or both non-null).
- **BT3**: When set, `validFrom <= validTo` and `transactionStart <= transactionEnd`.
