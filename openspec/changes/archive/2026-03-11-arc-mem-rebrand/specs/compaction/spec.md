## MODIFIED Requirements

### Requirement: Working-memory compaction
Context compaction SHALL be documented as "working-memory compaction" for capacity-exceeded scenarios. `CompactedContextProvider` name is terminology-neutral and MAY remain. Behavioral semantics (fact survival validation, minMatchRatio, CompactionLossEvent) remain unchanged.

#### Scenario: Compaction documentation uses working-memory framing
- **WHEN** documentation describes context compaction
- **THEN** it SHALL describe it as "working-memory compaction when capacity is exceeded"
