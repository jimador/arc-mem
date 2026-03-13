## MODIFIED Requirements

### Requirement: REPLACE outcome creates supersession link

**Modifies**: "DEMOTE_EXISTING conflict resolution option" requirement -- specifically the REPLACE outcome processing.

When `AuthorityConflictResolver` produces a REPLACE outcome and the engine processes the resolution, the resolution flow SHALL, in addition to archiving the existing context unit and promoting the incoming proposition:

1. Create a `SUPERSEDES` relationship from the incoming context unit to the replaced context unit with `reason = "CONFLICT_REPLACEMENT"` and `occurredAt = Instant.now()`
2. Set `validTo = Instant.now()` and `transactionEnd = Instant.now()` on the replaced context unit
3. Set `supersededBy` on the replaced context unit to the incoming context unit's ID
4. Set `supersedes` on the incoming context unit to the replaced context unit's ID
5. Publish a `Superseded` lifecycle event with the predecessor and successor IDs

The supersession link SHALL be created within the same operation as the archive, ensuring atomicity of the conflict resolution outcome.

#### Scenario: REPLACE resolution creates supersession chain

- **GIVEN** an existing context unit "A1" at UNRELIABLE authority
- **AND** an incoming proposition with confidence above the effective replace threshold
- **WHEN** conflict resolution returns REPLACE and the engine processes the resolution at instant T5
- **THEN** "A1" SHALL be archived with `validTo = T5` and `transactionEnd = T5`
- **AND** the incoming proposition SHALL be promoted as "A2" with `validFrom = T5` and `transactionStart = T5`
- **AND** a `SUPERSEDES` relationship SHALL exist from "A2" to "A1"
- **AND** a `Superseded` event SHALL be published

#### Scenario: KEEP_EXISTING resolution does not create supersession

- **GIVEN** an existing context unit "A1" at CANON authority
- **WHEN** conflict resolution returns KEEP_EXISTING
- **THEN** no `SUPERSEDES` relationship SHALL be created
- **AND** no `Superseded` event SHALL be published
- **AND** "A1" temporal fields SHALL remain unchanged

#### Scenario: DEMOTE_EXISTING resolution does not create supersession

- **GIVEN** an existing context unit "A1" at RELIABLE authority
- **WHEN** conflict resolution returns DEMOTE_EXISTING
- **THEN** no `SUPERSEDES` relationship SHALL be created (demotion is not replacement)
- **AND** "A1" temporal fields SHALL remain unchanged (the context unit is still active, just at lower authority)
