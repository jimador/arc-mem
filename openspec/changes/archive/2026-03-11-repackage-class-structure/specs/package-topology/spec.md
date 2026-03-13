## ADDED Requirements

### Requirement: Canonical module package roots

The repository SHALL use canonical Java package roots that match the post-split module ownership boundary. `arcmem-core` SHALL use `dev.dunnam.arcmem.core` as its canonical package root. `arcmem-simulator` SHALL use `dev.dunnam.arcmem.simulator` as its canonical package root.

#### Scenario: Core package root matches module ownership
- **GIVEN** a production class in `arcmem-core`
- **WHEN** its package declaration is inspected
- **THEN** the class SHALL live under `dev.dunnam.arcmem.core.*`

#### Scenario: Simulator package root matches module ownership
- **GIVEN** a production class in `arcmem-simulator`
- **WHEN** its package declaration is inspected
- **THEN** the class SHALL live under `dev.dunnam.arcmem.simulator.*`

### Requirement: Core packages reflect ARC-Mem subdomains

`arcmem-core` SHALL organize code by ARC-Mem responsibilities rather than by a single legacy bucket or simulator-oriented names. The canonical bounded areas SHALL include configuration, memory, assembly, extraction, persistence, prompt infrastructure, and explicit SPI/boundary packages where needed.

#### Scenario: Legacy core package duplication is removed
- **GIVEN** the canonical `arcmem-core` package layout
- **WHEN** package declarations are reviewed
- **THEN** the repository SHALL NOT retain `dev.dunnam.arcmem.arcmem.*` as a canonical package namespace

#### Scenario: Core seam types do not use simulator package names
- **GIVEN** a core-owned seam or integration boundary type
- **WHEN** its package declaration is reviewed
- **THEN** it SHALL NOT live under `dev.dunnam.arcmem.sim.*`
- **AND** it SHALL live under an explicit core-owned boundary such as `dev.dunnam.arcmem.core.spi.*`

#### Scenario: Core memory packages are split by subdomain
- **GIVEN** the ARC-Mem classes previously inventoried in the legacy `dev.dunnam.arcmem.arcmem` bucket
- **WHEN** the canonical core package layout is reviewed
- **THEN** those classes SHALL NOT remain in one flat `dev.dunnam.arcmem.core.memory` package
- **AND** the canonical topology SHALL include explicit subdomains for at least `model`, `engine`, `trust`, `conflict`, `maintenance`, `canon`, `budget`, and `mutation`

#### Scenario: Large pipeline packages are split by responsibility
- **GIVEN** the prompt assembly classes previously inventoried in the legacy `assembly` package
- **WHEN** the canonical core package layout is reviewed
- **THEN** those classes SHALL NOT remain in one flat `dev.dunnam.arcmem.core.assembly` package
- **AND** the canonical topology SHALL include explicit bounded areas for at least retrieval, compaction, compliance, protection, and token-budget enforcement

### Requirement: Simulator packages reflect harness capabilities

`arcmem-simulator` SHALL organize code by harness capability. The canonical bounded areas SHALL include bootstrap, config, chat, D&D domain schema, engine, scenario handling, adversary logic, assertions, benchmark execution, run history, reporting, and UI.

#### Scenario: D&D schema is explicit simulator-domain content
- **GIVEN** a D&D schema type used by the simulator
- **WHEN** its package declaration is reviewed
- **THEN** the type SHALL live under `dev.dunnam.arcmem.simulator.domain.dnd.*`

#### Scenario: Simulator UI is separated from engine code
- **GIVEN** a Vaadin view, panel, dialog, or UI controller in `arcmem-simulator`
- **WHEN** its package declaration is reviewed
- **THEN** it SHALL live under `dev.dunnam.arcmem.simulator.ui.*`
- **AND** it SHALL NOT live in the same package as simulator execution services unless that package is explicitly UI-owned

### Requirement: Repackaging preserves runtime behavior

The package cleanup SHALL preserve the existing module dependency direction and SHALL NOT require a behavior redesign. Package and file moves SHALL keep the application buildable and the simulator harness runnable.

#### Scenario: Dependency direction is preserved during package cleanup
- **GIVEN** the repository after repackaging
- **WHEN** module dependencies are validated
- **THEN** `arcmem-simulator` SHALL continue to depend on `arcmem-core`
- **AND** `arcmem-core` SHALL NOT depend on `arcmem-simulator`

#### Scenario: Package cleanup keeps the build and tests runnable
- **GIVEN** the repository after repackaging
- **WHEN** build and verification commands are run
- **THEN** the repository SHALL compile successfully
- **AND** module-owned tests SHALL continue to run from their owning modules
