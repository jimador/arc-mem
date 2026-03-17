# context-unit-supersession

**Status**: NEW capability
**Change**: bi-temporal-validity-and-supersession

---

## Requirements

### Requirement: Supersession tracking via SUPERSEDES relationship

When a ARC Working Memory Unit (AWMU) is archived due to conflict replacement, a `SUPERSEDES` relationship SHALL be created in Neo4j linking the successor AWMU to the predecessor AWMU. The relationship direction is `(successor)-[:SUPERSEDES]->(predecessor)`.

The `SUPERSEDES` relationship SHALL include the following metadata properties:
- `reason` (String) -- the supersession reason (see SupersessionReason)
- `occurredAt` (Instant) -- the instant the supersession occurred

#### Scenario: Conflict REPLACE creates SUPERSEDES link

- **GIVEN** an existing AWMU "A1" at UNRELIABLE authority
- **AND** an incoming proposition that conflicts with "A1"
- **WHEN** conflict resolution returns REPLACE and the incoming proposition is promoted as AWMU "A2"
- **THEN** a `SUPERSEDES` relationship SHALL be created from "A2" to "A1" with `reason = "CONFLICT_REPLACEMENT"` and `occurredAt` set to the current instant

#### Scenario: Archive for eviction records supersession

- **GIVEN** AWMU "A1" is evicted during working-memory capacity enforcement when AWMU "A2" is promoted
- **WHEN** the eviction is processed
- **THEN** a `SUPERSEDES` relationship SHALL be created from "A2" to "A1" with `reason = "EVICTION"` and `occurredAt` set to the current instant

### Requirement: Supersession fields on PropositionNode

`PropositionNode` SHALL have two nullable String fields for direct predecessor/successor lookups:
- `supersededBy` -- the AWMU ID of the successor that replaced this AWMU (set on the predecessor)
- `supersedes` -- the AWMU ID of the predecessor that this AWMU replaced (set on the successor)

These fields provide O(1) lookups without requiring relationship traversal for the immediate predecessor/successor.

#### Scenario: Supersession fields set on conflict replacement

- **GIVEN** AWMU "A1" is replaced by AWMU "A2" via conflict resolution
- **WHEN** the supersession is recorded
- **THEN** "A1" SHALL have `supersededBy = "A2"` and "A2" SHALL have `supersedes = "A1"`

### Requirement: SupersessionReason enum

The system SHALL define a `SupersessionReason` enum (or constants class) with the following values:
- `CONFLICT_REPLACEMENT` -- AWMU was replaced by a conflicting proposition with higher authority or confidence
- `DORMANCY_DECAY` -- AWMU was archived due to activation score decay below minimum threshold
- `USER_ACTION` -- AWMU was explicitly archived or replaced by user action
- `EVICTION` -- AWMU was evicted during working-memory capacity enforcement
- `USER_REVISION` -- AWMU was superseded by a user-intended revision classified via `ConflictType.REVISION`

The `fromArchiveReason()` factory method SHALL map a new `ArchiveReason.REVISION` value to `SupersessionReason.USER_REVISION`.

#### Scenario: Revision supersession creates USER_REVISION link

- **GIVEN** a REVISION conflict is accepted against AWMU "A1"
- **AND** the incoming proposition is promoted as AWMU "A2"
- **WHEN** `ArcMemEngine.supersede()` is called
- **THEN** a `SUPERSEDES` relationship SHALL be created from "A2" to "A1" with `reason = "USER_REVISION"`

#### Scenario: USER_REVISION distinguishable from CONFLICT_REPLACEMENT

- **GIVEN** AWMU "A1" was superseded by "A2" via revision
- **AND** AWMU "A3" was superseded by "A4" via contradiction resolution
- **WHEN** supersession relationships are queried
- **THEN** the "A2" → "A1" link SHALL have `reason = "USER_REVISION"`
- **AND** the "A4" → "A3" link SHALL have `reason = "CONFLICT_REPLACEMENT"`

#### Scenario: fromArchiveReason maps REVISION correctly

- **GIVEN** an `ArchiveReason.REVISION` value
- **WHEN** `SupersessionReason.fromArchiveReason()` is called
- **THEN** `SupersessionReason.USER_REVISION` SHALL be returned

### Requirement: Supersession query methods

`MemoryUnitRepository` SHALL provide the following query methods:

1. `findPredecessor(String unitId)` -- returns `Optional<PropositionNode>` of the AWMU that was superseded by the given AWMU. Uses the `supersedes` field for direct lookup or traverses the `SUPERSEDES` relationship.

2. `findSuccessor(String unitId)` -- returns `Optional<PropositionNode>` of the AWMU that superseded the given AWMU. Uses the `supersededBy` field for direct lookup or traverses the incoming `SUPERSEDES` relationship.

#### Scenario: findPredecessor returns the replaced AWMU

- **GIVEN** AWMU "A2" replaced AWMU "A1" via conflict resolution
- **WHEN** `findPredecessor("A2")` is called
- **THEN** the `PropositionNode` for "A1" SHALL be returned

#### Scenario: findSuccessor returns the replacing AWMU

- **GIVEN** AWMU "A1" was replaced by AWMU "A2" via conflict resolution
- **WHEN** `findSuccessor("A1")` is called
- **THEN** the `PropositionNode` for "A2" SHALL be returned

#### Scenario: findPredecessor for AWMU with no predecessor

- **GIVEN** AWMU "A1" has never been involved in a supersession
- **WHEN** `findPredecessor("A1")` is called
- **THEN** `Optional.empty()` SHALL be returned

#### Scenario: Supersession chain walkable for multi-hop replacements

- **GIVEN** AWMU "A1" was superseded by "A2", which was superseded by "A3"
- **WHEN** `findPredecessor("A3")` is called
- **THEN** "A2" SHALL be returned
- **WHEN** `findPredecessor("A2")` is called
- **THEN** "A1" SHALL be returned
- **WHEN** `findSuccessor("A1")` is called
- **THEN** "A2" SHALL be returned

## Invariants

- **SS1**: A `SUPERSEDES` relationship SHALL always have a non-null `reason` and `occurredAt`.
- **SS2**: A AWMU SHALL have at most one `supersededBy` value (a AWMU can only be directly replaced once).
- **SS3**: A AWMU MAY have multiple predecessors if it replaced multiple AWMUs in a single conflict resolution batch, but `supersedes` SHALL reference only the primary predecessor. Additional predecessors are tracked via `SUPERSEDES` relationships.
