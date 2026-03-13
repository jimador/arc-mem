## MODIFIED Requirements

### Requirement: Memory tier classification
Memory tiers SHALL be documented in activation terms: HOT = high activation, WARM = moderate activation, COLD = low activation. The tier boundaries (hotThreshold=600, warmThreshold=350) are expressed in activation score units. `MemoryTier` enum values (COLD, WARM, HOT) MAY remain unchanged. Behavioral semantics remain unchanged.

#### Scenario: Tier documentation uses activation framing
- **WHEN** documentation describes memory tiers
- **THEN** it SHALL describe tiers as activation levels rather than abstract categories
- **AND** thresholds SHALL be expressed as "activation score thresholds"
