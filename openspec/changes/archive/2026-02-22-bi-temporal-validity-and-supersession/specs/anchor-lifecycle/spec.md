## MODIFIED Requirements

### Requirement: Temporal fields set on promotion

**Modifies**: `AnchorEngine.promote()` and the "Eviction lifecycle events" requirement.

`AnchorEngine.promote()` SHALL set `validFrom = Instant.now()` and `transactionStart = Instant.now()` on the newly promoted anchor, in addition to all existing promotion behavior (rank assignment, tier computation, budget enforcement, event publishing).

The temporal fields SHALL be persisted to `PropositionNode` as part of the promotion write operation.

#### Scenario: Promotion sets temporal fields

- **GIVEN** a proposition passes all promotion gates
- **WHEN** `AnchorEngine.promote()` completes at instant T1
- **THEN** the new anchor SHALL have `validFrom = T1` and `transactionStart = T1`
- **AND** `validTo` and `transactionEnd` SHALL be null

#### Scenario: Promotion temporal fields coexist with tier assignment

- **GIVEN** a proposition promoted with `initialRank = 700`
- **WHEN** `AnchorEngine.promote()` completes at instant T1
- **THEN** the anchor SHALL have `memoryTier = HOT`, `validFrom = T1`, and `transactionStart = T1`

### Requirement: Temporal fields and supersession on archive

**Modifies**: `AnchorEngine.archive()` (implicit in "AnchorArchivedEvent" requirement from anchor-lifecycle-events spec, and "Eviction lifecycle events" requirement).

`AnchorEngine.archive()` SHALL set `validTo = Instant.now()` and `transactionEnd = Instant.now()` on the archived anchor, in addition to existing archive behavior (status change, event publishing).

When archiving due to conflict replacement (reason = `CONFLICT_REPLACEMENT`), `archive()` SHALL also:
1. Create a `SUPERSEDES` relationship from the incoming anchor to the archived anchor
2. Set `supersededBy` on the archived anchor to the incoming anchor's ID
3. Set `supersedes` on the incoming anchor to the archived anchor's ID

#### Scenario: Archive sets temporal end fields

- **GIVEN** an active anchor "A1" with `validFrom = T1`
- **WHEN** `AnchorEngine.archive("A1", ArchiveReason.MANUAL)` is called at instant T5
- **THEN** anchor "A1" SHALL have `validTo = T5` and `transactionEnd = T5`

#### Scenario: Archive for conflict replacement creates supersession

- **GIVEN** an active anchor "A1" conflicting with incoming proposition
- **AND** conflict resolution returns REPLACE
- **WHEN** the engine archives "A1" and promotes the incoming proposition as "A2" at instant T5
- **THEN** anchor "A1" SHALL have `validTo = T5`, `transactionEnd = T5`, and `supersededBy = "A2"`
- **AND** anchor "A2" SHALL have `supersedes = "A1"`
- **AND** a `SUPERSEDES` relationship SHALL exist from "A2" to "A1" with `reason = "CONFLICT_REPLACEMENT"`

#### Scenario: Archive for eviction sets temporal fields

- **GIVEN** the anchor budget is full and a new anchor is being promoted
- **WHEN** anchor "A1" is evicted (lowest-ranked non-pinned) at instant T5
- **THEN** anchor "A1" SHALL have `validTo = T5` and `transactionEnd = T5`
