## Why

dice-anchors integrates Embabel Agent 0.3.5-SNAPSHOT across 52 imports in 30 files spanning 10 packages, but there is no single reference documenting what the project uses, what it leaves on the table, or how current patterns align with Embabel's recommended idioms. Without an inventory, contributors repeat discovery work, miss available capabilities, and risk diverging from framework best practices. This feature creates a living inventory document that serves as the foundation for subsequent integration improvements (F2 tool restructuring, F3 DICE integration docs, F4 goal modeling evaluation).

## What Changes

- **New artifact**: `docs/dev/embabel-api-inventory.md` documenting the full Embabel Agent integration surface
- **DEVELOPING.md update**: Reference to the inventory document for contributor discoverability
- **No code changes**: This is a documentation-only feature

## Capabilities

### New Capabilities
- `embabel-api-inventory`: Living reference document cataloging current Embabel usage, available-but-unused capabilities, recommended patterns, and tool restructuring rationale

### Modified Capabilities
(None -- additive documentation, no behavior changes)

## Impact

- **Files**: `docs/dev/embabel-api-inventory.md` (new), `DEVELOPING.md` (updated reference)
- **APIs**: None
- **Config**: None
- **Dependencies**: None
- **Value**: Eliminates repeated discovery work; provides structured foundation for F2-F4 features

## Constitutional Alignment

- RFC 2119 keywords: Spec uses MUST/SHALL/SHOULD per Article I
- No code changes: Articles II-VII (Neo4j, constructor injection, records, anchor invariants, simulation isolation, testing) are not affected
