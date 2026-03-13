## ADDED Requirements

### Requirement: Two-module repository topology

The repository SHALL define exactly two first-party Maven implementation modules named `arcmem-core` and `arcmem-simulator`. `arcmem-core` SHALL own the ARC-Mem implementation layer on top of DICE. `arcmem-simulator` SHALL own the simulator harness that drives drift-inducing scenarios, chat flows, experiment execution, and simulator-facing reporting.

#### Scenario: Build topology matches required modules
- **GIVEN** the repository root build definition
- **WHEN** module structure is inspected
- **THEN** the build SHALL declare `arcmem-core` and `arcmem-simulator` as first-party implementation modules
- **AND** no first-party `dice` module SHALL exist in the repository

#### Scenario: Simulator remains the executable harness surface
- **GIVEN** the repository module structure
- **WHEN** executable application ownership is inspected
- **THEN** the simulator harness application SHALL live in `arcmem-simulator`
- **AND** `arcmem-core` SHALL NOT be required to provide simulator UI or scenario execution surfaces

### Requirement: Module dependency direction

`arcmem-core` SHALL depend on DICE as an external library. `arcmem-simulator` SHALL depend on `arcmem-core`. `arcmem-core` SHALL NOT depend on `arcmem-simulator` or on simulator-only D&D schema, scenarios, or UI types.

#### Scenario: Core dependency direction is one-way
- **GIVEN** the module dependency graph
- **WHEN** dependencies are validated
- **THEN** `arcmem-core` SHALL NOT depend on `arcmem-simulator`
- **AND** `arcmem-simulator` SHALL be allowed to depend on `arcmem-core`

#### Scenario: DICE remains external
- **GIVEN** the module dependency graph
- **WHEN** DICE integration ownership is validated
- **THEN** `arcmem-core` SHALL consume DICE as an external dependency
- **AND** the repository SHALL NOT introduce a first-party module that duplicates DICE ownership

### Requirement: Ownership boundary for simulator-specific assets

All D&D schema types, D&D prompt templates, scenario definitions, adversary strategies, chat UI, benchmark UI, and experiment/reporting flows SHALL be owned by `arcmem-simulator`. `arcmem-core` SHALL own ARC-Mem memory, trust, conflict, maintenance, assembly, extraction, and core storage semantics.

#### Scenario: D&D assets resolve to simulator ownership
- **GIVEN** a D&D-specific schema type, prompt, or scenario file
- **WHEN** ownership is validated
- **THEN** that asset SHALL belong to `arcmem-simulator`

#### Scenario: ARC-Mem implementation logic resolves to core ownership
- **GIVEN** ARC-Mem implementation code for memory, trust, conflict, maintenance, assembly, or extraction
- **WHEN** ownership is validated
- **THEN** that code SHALL belong to `arcmem-core`

### Requirement: First-pass migration preserves behavior

The initial module split SHALL preserve the current simulator behavior before any deeper internal package redesign is required. The repository SHALL be considered compliant when the two-module build compiles and the simulator harness continues to own chat, scenario, and experiment execution behavior.

#### Scenario: Module split completes without mandatory package redesign
- **GIVEN** the first-pass implementation of the split
- **WHEN** the repository structure is validated
- **THEN** the repository SHALL be allowed to retain temporary legacy package names within the new modules
- **AND** the split SHALL NOT require full package cleanup to be considered complete

#### Scenario: Simulator behavior remains owned after split
- **GIVEN** the first-pass implementation of the split
- **WHEN** simulator responsibilities are inspected
- **THEN** chat UI, scenario execution, and experiment/reporting behavior SHALL remain available from `arcmem-simulator`

### Requirement: Canonical module package roots after cleanup

After the first-pass module split, the repository SHALL converge on canonical package roots that match the module ownership boundary. `arcmem-core` SHALL use `dev.dunnam.arcmem.core.*`. `arcmem-simulator` SHALL use `dev.dunnam.arcmem.simulator.*`.

#### Scenario: Core package root matches module ownership
- **GIVEN** a production class in `arcmem-core`
- **WHEN** its package declaration is inspected after package cleanup
- **THEN** the class SHALL live under `dev.dunnam.arcmem.core.*`

#### Scenario: Simulator package root matches module ownership
- **GIVEN** a production class in `arcmem-simulator`
- **WHEN** its package declaration is inspected after package cleanup
- **THEN** the class SHALL live under `dev.dunnam.arcmem.simulator.*`

### Requirement: Cleanup preserves module-boundary guarantees

The follow-on package cleanup SHALL preserve the ownership and dependency guarantees established by the module split.

#### Scenario: Cleanup does not collapse module ownership
- **GIVEN** the repository after the package cleanup
- **WHEN** core and simulator assets are reviewed
- **THEN** simulator-owned chat, D&D schema, scenarios, benchmark, report, and UI classes SHALL remain owned by `arcmem-simulator`
- **AND** ARC-Mem implementation logic SHALL remain owned by `arcmem-core`
