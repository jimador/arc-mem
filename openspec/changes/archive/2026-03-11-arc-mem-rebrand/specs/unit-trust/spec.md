## MODIFIED Requirements

### Requirement: Domain profile weight validation
The trust pipeline SHALL be documented using ARC-Mem terminology. References to "anchor trust" SHALL become "semantic unit trust" or "activation signals". Trust signal names (sourceAuthority, extractionConfidence, graphConsistency, corroboration, novelty, importance) and domain profiles (NARRATIVE, SECURE, BALANCED) remain unchanged.

#### Scenario: Trust documentation uses activation signal framing
- **WHEN** documentation describes the trust evaluation pipeline
- **THEN** it SHALL frame trust signals as "activation signals" contributing to semantic unit authority
