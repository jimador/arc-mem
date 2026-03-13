## MODIFIED Requirements

### Requirement: Maintenance strategy sealed interface
Maintenance strategies SHALL be documented as "working-memory housekeeping". The three modes (REACTIVE, PROACTIVE, HYBRID) SHALL be described as activation refresh, consolidation, and pruning cycles. `MaintenanceStrategy` interface name MAY remain unchanged (it describes behavior, not the "anchor" concept). Behavioral semantics remain unchanged.

#### Scenario: Maintenance documentation uses housekeeping framing
- **WHEN** documentation describes maintenance strategies
- **THEN** it SHALL use "working-memory housekeeping" with "activation refresh, consolidation, and pruning"
