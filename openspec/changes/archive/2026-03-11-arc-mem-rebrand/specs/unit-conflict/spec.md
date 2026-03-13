## MODIFIED Requirements

### Requirement: Conflict detection and resolution
Conflict detection and resolution SHALL be documented as "semantic unit mediation". Class-level renames: `AuthorityConflictResolver` → `AuthorityConflictMediator` (or retain if too disruptive — design decision). Behavioral semantics (tier-aware thresholds, authority matrix, DEMOTE_EXISTING option) remain unchanged.

#### Scenario: Conflict documentation uses mediation framing
- **WHEN** documentation describes conflict detection and resolution
- **THEN** it SHALL use "semantic unit mediation" or "conflict mediation" terminology
