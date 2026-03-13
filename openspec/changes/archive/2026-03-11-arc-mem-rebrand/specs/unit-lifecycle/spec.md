## MODIFIED Requirements

### Requirement: Bidirectional authority lifecycle
The authority lifecycle SHALL be documented using ARC-Mem terminology. "Anchor" references in requirement text, Javadoc, and display names SHALL be replaced with "semantic unit" or "context unit" per the `arc-mem-terminology` mapping. Authority levels (PROVISIONAL, UNRELIABLE, RELIABLE, CANON) and their behavioral semantics remain unchanged.

#### Scenario: Authority lifecycle documentation uses ARC-Mem terms
- **WHEN** documentation or code comments describe authority transitions
- **THEN** they SHALL reference "semantic units" rather than "anchors"
- **AND** behavioral semantics (CANON immunity, pinned unit protection) SHALL remain identical

## RENAMED Requirements

- FROM: `Pinned anchors immune to auto-demotion` TO: `Pinned units immune to auto-demotion`
