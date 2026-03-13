## ADDED Requirements

### Requirement: Module topology documentation

The developer documentation suite SHALL document the repository's two-module topology, including the ownership boundary between `arcmem-core` and `arcmem-simulator`, the dependency direction between modules, and the fact that DICE remains an external dependency rather than a first-party module.

#### Scenario: Architecture documentation describes module ownership
- **GIVEN** the canonical architecture documentation for the repository
- **WHEN** module topology is reviewed
- **THEN** the documentation SHALL identify `arcmem-core` as the ARC-Mem implementation layer
- **AND** the documentation SHALL identify `arcmem-simulator` as the D&D-driven simulator harness

#### Scenario: Documentation explains dependency direction
- **GIVEN** the canonical architecture documentation for the repository
- **WHEN** module dependencies are reviewed
- **THEN** the documentation SHALL state that `arcmem-simulator` depends on `arcmem-core`
- **AND** the documentation SHALL state that DICE is consumed as an external dependency
