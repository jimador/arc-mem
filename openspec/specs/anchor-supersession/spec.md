# anchor-supersession

**Status**: NEW capability
**Change**: bi-temporal-validity-and-supersession

---

## Requirements

### Requirement: Supersession tracking via SUPERSEDES relationship

When an anchor is archived due to conflict replacement, a `SUPERSEDES` relationship SHALL be created in Neo4j linking the successor anchor to the predecessor anchor. The relationship direction is `(successor)-[:SUPERSEDES]->(predecessor)`.

The `SUPERSEDES` relationship SHALL include the following metadata properties:
- `reason` (String) -- the supersession reason (see SupersessionReason)
- `occurredAt` (Instant) -- the instant the supersession occurred

#### Scenario: Conflict REPLACE creates SUPERSEDES link

- **GIVEN** an existing anchor "A1" at UNRELIABLE authority
- **AND** an incoming proposition that conflicts with "A1"
- **WHEN** conflict resolution returns REPLACE and the incoming proposition is promoted as anchor "A2"
- **THEN** a `SUPERSEDES` relationship SHALL be created from "A2" to "A1" with `reason = "CONFLICT_REPLACEMENT"` and `occurredAt` set to the current instant

#### Scenario: Archive for eviction records supersession

- **GIVEN** anchor "A1" is evicted during budget enforcement when anchor "A2" is promoted
- **WHEN** the eviction is processed
- **THEN** a `SUPERSEDES` relationship SHALL be created from "A2" to "A1" with `reason = "EVICTION"` and `occurredAt` set to the current instant

### Requirement: Supersession fields on PropositionNode

`PropositionNode` SHALL have two nullable String fields for direct predecessor/successor lookups:
- `supersededBy` -- the anchor ID of the successor that replaced this anchor (set on the predecessor)
- `supersedes` -- the anchor ID of the predecessor that this anchor replaced (set on the successor)

These fields provide O(1) lookups without requiring relationship traversal for the immediate predecessor/successor.

#### Scenario: Supersession fields set on conflict replacement

- **GIVEN** anchor "A1" is replaced by anchor "A2" via conflict resolution
- **WHEN** the supersession is recorded
- **THEN** "A1" SHALL have `supersededBy = "A2"` and "A2" SHALL have `supersedes = "A1"`

### Requirement: SupersessionReason enum

The system SHALL define a `SupersessionReason` enum (or constants class) with the following values:
- `CONFLICT_REPLACEMENT` -- anchor was replaced by a conflicting proposition with higher authority or confidence
- `DORMANCY_DECAY` -- anchor was archived due to rank decay below minimum threshold
- `USER_ACTION` -- anchor was explicitly archived or replaced by user action
- `EVICTION` -- anchor was evicted during budget enforcement

### Requirement: Supersession query methods

`AnchorRepository` SHALL provide the following query methods:

1. `findPredecessor(String anchorId)` -- returns `Optional<PropositionNode>` of the anchor that was superseded by the given anchor. Uses the `supersedes` field for direct lookup or traverses the `SUPERSEDES` relationship.

2. `findSuccessor(String anchorId)` -- returns `Optional<PropositionNode>` of the anchor that superseded the given anchor. Uses the `supersededBy` field for direct lookup or traverses the incoming `SUPERSEDES` relationship.

#### Scenario: findPredecessor returns the replaced anchor

- **GIVEN** anchor "A2" replaced anchor "A1" via conflict resolution
- **WHEN** `findPredecessor("A2")` is called
- **THEN** the `PropositionNode` for "A1" SHALL be returned

#### Scenario: findSuccessor returns the replacing anchor

- **GIVEN** anchor "A1" was replaced by anchor "A2" via conflict resolution
- **WHEN** `findSuccessor("A1")` is called
- **THEN** the `PropositionNode` for "A2" SHALL be returned

#### Scenario: findPredecessor for anchor with no predecessor

- **GIVEN** anchor "A1" has never been involved in a supersession
- **WHEN** `findPredecessor("A1")` is called
- **THEN** `Optional.empty()` SHALL be returned

#### Scenario: Supersession chain walkable for multi-hop replacements

- **GIVEN** anchor "A1" was superseded by "A2", which was superseded by "A3"
- **WHEN** `findPredecessor("A3")` is called
- **THEN** "A2" SHALL be returned
- **WHEN** `findPredecessor("A2")` is called
- **THEN** "A1" SHALL be returned
- **WHEN** `findSuccessor("A1")` is called
- **THEN** "A2" SHALL be returned

## Invariants

- **SS1**: A `SUPERSEDES` relationship SHALL always have a non-null `reason` and `occurredAt`.
- **SS2**: An anchor SHALL have at most one `supersededBy` value (an anchor can only be directly replaced once).
- **SS3**: An anchor MAY have multiple predecessors if it replaced multiple anchors in a single conflict resolution batch, but `supersedes` SHALL reference only the primary predecessor. Additional predecessors are tracked via `SUPERSEDES` relationships.
