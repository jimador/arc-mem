# unit-supersession

**Status**: NEW capability
**Change**: bi-temporal-validity-and-supersession

---

## Requirements

### Requirement: Supersession tracking via SUPERSEDES relationship

When an context unit is archived due to conflict replacement, a `SUPERSEDES` relationship SHALL be created in Neo4j linking the successor context unit to the predecessor context unit. The relationship direction is `(successor)-[:SUPERSEDES]->(predecessor)`.

The `SUPERSEDES` relationship SHALL include the following metadata properties:
- `reason` (String) -- the supersession reason (see SupersessionReason)
- `occurredAt` (Instant) -- the instant the supersession occurred

#### Scenario: Conflict REPLACE creates SUPERSEDES link

- **GIVEN** an existing context unit "A1" at UNRELIABLE authority
- **AND** an incoming proposition that conflicts with "A1"
- **WHEN** conflict resolution returns REPLACE and the incoming proposition is promoted as context unit "A2"
- **THEN** a `SUPERSEDES` relationship SHALL be created from "A2" to "A1" with `reason = "CONFLICT_REPLACEMENT"` and `occurredAt` set to the current instant

#### Scenario: Archive for eviction records supersession

- **GIVEN** context unit "A1" is evicted during budget enforcement when context unit "A2" is promoted
- **WHEN** the eviction is processed
- **THEN** a `SUPERSEDES` relationship SHALL be created from "A2" to "A1" with `reason = "EVICTION"` and `occurredAt` set to the current instant

### Requirement: Supersession fields on PropositionNode

`PropositionNode` SHALL have two nullable String fields for direct predecessor/successor lookups:
- `supersededBy` -- the context unit ID of the successor that replaced this context unit (set on the predecessor)
- `supersedes` -- the context unit ID of the predecessor that this context unit replaced (set on the successor)

These fields provide O(1) lookups without requiring relationship traversal for the immediate predecessor/successor.

#### Scenario: Supersession fields set on conflict replacement

- **GIVEN** context unit "A1" is replaced by context unit "A2" via conflict resolution
- **WHEN** the supersession is recorded
- **THEN** "A1" SHALL have `supersededBy = "A2"` and "A2" SHALL have `supersedes = "A1"`

### Requirement: SupersessionReason enum

The system SHALL define a `SupersessionReason` enum (or constants class) with the following values:
- `CONFLICT_REPLACEMENT` -- context unit was replaced by a conflicting proposition with higher authority or confidence
- `DORMANCY_DECAY` -- context unit was archived due to rank decay below minimum threshold
- `USER_ACTION` -- context unit was explicitly archived or replaced by user action
- `EVICTION` -- context unit was evicted during budget enforcement

### Requirement: Supersession query methods

`ContextUnitRepository` SHALL provide the following query methods:

1. `findPredecessor(String unitId)` -- returns `Optional<PropositionNode>` of the context unit that was superseded by the given context unit. Uses the `supersedes` field for direct lookup or traverses the `SUPERSEDES` relationship.

2. `findSuccessor(String unitId)` -- returns `Optional<PropositionNode>` of the context unit that superseded the given context unit. Uses the `supersededBy` field for direct lookup or traverses the incoming `SUPERSEDES` relationship.

#### Scenario: findPredecessor returns the replaced context unit

- **GIVEN** context unit "A2" replaced context unit "A1" via conflict resolution
- **WHEN** `findPredecessor("A2")` is called
- **THEN** the `PropositionNode` for "A1" SHALL be returned

#### Scenario: findSuccessor returns the replacing context unit

- **GIVEN** context unit "A1" was replaced by context unit "A2" via conflict resolution
- **WHEN** `findSuccessor("A1")` is called
- **THEN** the `PropositionNode` for "A2" SHALL be returned

#### Scenario: findPredecessor for context unit with no predecessor

- **GIVEN** context unit "A1" has never been involved in a supersession
- **WHEN** `findPredecessor("A1")` is called
- **THEN** `Optional.empty()` SHALL be returned

#### Scenario: Supersession chain walkable for multi-hop replacements

- **GIVEN** context unit "A1" was superseded by "A2", which was superseded by "A3"
- **WHEN** `findPredecessor("A3")` is called
- **THEN** "A2" SHALL be returned
- **WHEN** `findPredecessor("A2")` is called
- **THEN** "A1" SHALL be returned
- **WHEN** `findSuccessor("A1")` is called
- **THEN** "A2" SHALL be returned

## Invariants

- **SS1**: A `SUPERSEDES` relationship SHALL always have a non-null `reason` and `occurredAt`.
- **SS2**: An context unit SHALL have at most one `supersededBy` value (an context unit can only be directly replaced once).
- **SS3**: An context unit MAY have multiple predecessors if it replaced multiple context units in a single conflict resolution batch, but `supersedes` SHALL reference only the primary predecessor. Additional predecessors are tracked via `SUPERSEDES` relationships.
