## MODIFIED Requirements

### Requirement: SupersessionReason enum

The system SHALL define a `SupersessionReason` enum (or constants class) with the following values:
- `CONFLICT_REPLACEMENT` — context unit was replaced by a conflicting proposition with higher authority or confidence
- `DORMANCY_DECAY` — context unit was archived due to rank decay below minimum threshold
- `USER_ACTION` — context unit was explicitly archived or replaced by user action
- `EVICTION` — context unit was evicted during budget enforcement
- `USER_REVISION` — context unit was superseded by a user-intended revision classified via `ConflictType.REVISION`

The `fromArchiveReason()` factory method SHALL map a new `ArchiveReason.REVISION` value to `SupersessionReason.USER_REVISION`.

#### Scenario: Revision supersession creates USER_REVISION link

- **GIVEN** a REVISION conflict is accepted against context unit "A1"
- **AND** the incoming proposition is promoted as context unit "A2"
- **WHEN** `ArcMemEngine.supersede()` is called
- **THEN** a `SUPERSEDES` relationship SHALL be created from "A2" to "A1" with `reason = "USER_REVISION"`

#### Scenario: USER_REVISION distinguishable from CONFLICT_REPLACEMENT

- **GIVEN** context unit "A1" was superseded by "A2" via revision
- **AND** context unit "A3" was superseded by "A4" via contradiction resolution
- **WHEN** supersession relationships are queried
- **THEN** the "A2" → "A1" link SHALL have `reason = "USER_REVISION"`
- **AND** the "A4" → "A3" link SHALL have `reason = "CONFLICT_REPLACEMENT"`

#### Scenario: fromArchiveReason maps REVISION correctly

- **GIVEN** an `ArchiveReason.REVISION` value
- **WHEN** `SupersessionReason.fromArchiveReason()` is called
- **THEN** `SupersessionReason.USER_REVISION` SHALL be returned
