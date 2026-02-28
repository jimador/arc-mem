## Why

dice-anchors couples to DICE 0.1.0-SNAPSHOT across extraction, revision, persistence, and incremental analysis — but there is no single reference documenting the integration surface, its fragile coupling points, or the responsibility boundary between the two projects. The 15-parameter `Proposition.create()` overload (with three parameters of unclear semantics) is the highest-risk coupling point: any SNAPSHOT API evolution breaks `PropositionView.toDice()` silently at compile time or — worse — produces incorrect propositions at runtime. Without documentation, contributors rediscover these fragilities through breakage rather than preparation.

This feature creates a canonical integration surface document that catalogs current DICE usage, identifies fragile coupling points with file:line references, establishes an API stability assessment, and documents the monitoring strategy for SNAPSHOT API drift.

## What Changes

- **New artifact**: `docs/dev/dice-integration.md` — canonical DICE integration surface document
- **DEVELOPING.md update**: Reference to the integration document for contributor discoverability
- **No code changes**: Documentation-only feature

## Capabilities

### New Capabilities
- `dice-integration-surface-docs`: Living reference document cataloging DICE 0.1.0-SNAPSHOT integration surface, end-to-end extraction flows (chat + simulation), component API usage with method signatures, fragile coupling points with risk assessment, responsibility boundaries, and monitoring strategy

### Modified Capabilities
(None — additive documentation, no behavior changes)

## Impact

- **Files**: `docs/dev/dice-integration.md` (new), `DEVELOPING.md` (updated reference)
- **APIs**: None
- **Config**: None
- **Dependencies**: None
- **Value**: Eliminates repeated discovery work; provides structured reference for DICE SNAPSHOT API evolution monitoring; reduces risk of silent breakage from upstream API drift

## Constitutional Alignment

- RFC 2119 keywords: Spec uses MUST/SHALL/SHOULD per Article I
- No code changes: Articles II-VII (Neo4j, constructor injection, records, anchor invariants, simulation isolation, testing) are not affected
