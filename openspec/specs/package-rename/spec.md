# package-rename Specification

## Purpose
TBD - created by archiving change repackage-to-dev-arcmem. Update Purpose after archive.
## Requirements
### Requirement: Package namespace MUST be dev.arcmem

All Java source files in arcmem-core and arcmem-simulator SHALL use the `dev.arcmem` package root.

#### Scenario: No dev.dunnam references remain
- **GIVEN** the complete source tree
- **WHEN** searched for `dev.dunnam`
- **THEN** zero matches SHALL be found in any `.java` file, `pom.xml`, or resource file

### Requirement: Maven groupId MUST be dev.arcmem

The parent POM `groupId` SHALL be `dev.arcmem`.

#### Scenario: Build artifacts use correct groupId
- **GIVEN** the parent `pom.xml`
- **WHEN** the `groupId` element is read
- **THEN** it SHALL be `dev.arcmem`

### Requirement: All tests MUST pass after rename

The full test suite SHALL pass with zero failures after the package rename is complete.

#### Scenario: Full test suite passes
- **GIVEN** the completed package rename
- **WHEN** `./mvnw test` is run
- **THEN** all tests SHALL pass with zero failures

