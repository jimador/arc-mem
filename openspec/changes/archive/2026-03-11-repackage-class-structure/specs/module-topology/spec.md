## MODIFIED Requirements

### Requirement: Two-module repository topology

The package cleanup SHALL complete the repository's transition to canonical module-aligned package roots without changing the existing two-module ownership model.

#### Scenario: Package roots align with module boundaries
- **GIVEN** the repository module structure
- **WHEN** canonical Java package roots are reviewed
- **THEN** `arcmem-core` SHALL use a `dev.dunnam.arcmem.core.*` package root
- **AND** `arcmem-simulator` SHALL use a `dev.dunnam.arcmem.simulator.*` package root

### Requirement: Ownership boundary for simulator-specific assets

Simulator-owned classes SHALL move into explicit simulator package roots so ownership remains obvious after the package cleanup.

#### Scenario: Simulator-owned classes do not remain in core-style legacy namespaces
- **GIVEN** chat UI, D&D schema, scenario execution, benchmark, report, or simulator UI classes
- **WHEN** ownership is validated
- **THEN** those classes SHALL live under simulator-owned package roots
- **AND** they SHALL NOT remain in ambiguous legacy package namespaces that obscure simulator ownership

### Requirement: First-pass migration preserves behavior

The follow-on topology cleanup SHALL preserve the behavior and ownership guarantees established by the earlier module split.

#### Scenario: Package cleanup completes after the module split
- **GIVEN** the repository after the initial two-module split
- **WHEN** the follow-on topology cleanup is applied
- **THEN** the repository SHALL replace temporary legacy package names with canonical package roots
- **AND** the cleanup SHALL preserve the ownership and behavior guarantees established by the module split
