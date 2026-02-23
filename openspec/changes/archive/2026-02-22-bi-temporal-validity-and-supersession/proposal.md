## Why

Anchors currently have no temporal model. When an anchor is archived due to conflict replacement, the supersession link is lost — there is no record of which anchor replaced which, or when an anchor was valid. The `PropositionNode` stores `created` and `revised` timestamps but has no concept of valid-time windows (`validFrom`/`validTo`) or transaction-time audit trails. This means the system cannot answer questions like "what was the active anchor set at turn 3?" or "what superseded this fact?" — critical capabilities for debugging drift, understanding conflict resolution outcomes, and building trust audit trails.

The F01 (memory tiering) and F02 (conflict calibration) changes are complete, providing the tier-aware lifecycle and calibrated conflict resolution that supersession semantics build upon. Adding bi-temporal validity now enables temporal queries, supersession chain reconstruction, and richer observability before the benchmarking wave (F06) where temporal correctness becomes measurable.

## What Changes

- Add **transaction time** fields (`transactionStart`, `transactionEnd`) to `PropositionNode` recording when anchor state changes were written. Transaction end is set when the anchor is superseded or archived.
- Add **valid time** fields (`validFrom`, `validTo`) to `PropositionNode` expressing the temporal window during which an anchor's fact is considered valid. `validFrom` defaults to promotion time; `validTo` is null (open-ended) until superseded.
- Add **supersession tracking** via a `SUPERSEDES` Neo4j relationship linking successor anchors to their predecessors, with metadata capturing the reason (conflict replacement, decay demotion, user action).
- Add **`supersededBy`** and **`supersedes`** fields to `PropositionNode` for direct predecessor/successor lookups without relationship traversal.
- Add **temporal query methods** to `AnchorRepository`: `findValidAt(contextId, instant)`, `findSupersessionChain(anchorId)`, `findPredecessor(anchorId)`, `findSuccessor(anchorId)`.
- Modify **`AnchorEngine.archive()`** and conflict resolution flows to record supersession links when one anchor replaces another.
- Add a **`Superseded`** lifecycle event with predecessor/successor IDs and supersession reason.
- Add **supersession chain visualization** to the Context Inspector panel — show predecessor/successor links for any anchor.
- Add **OTEL span attributes** for supersession events: `supersession.reason`, `supersession.predecessor_id`, `supersession.successor_id`.

## Capabilities

### New Capabilities
- `bi-temporal-validity`: Temporal validity windows and transaction-time audit trail for anchors, with temporal query support.
- `anchor-supersession`: Explicit supersession tracking via graph relationships, supersession chain queries, and lifecycle events.

### Modified Capabilities
- `anchor-lifecycle`: Archive and promotion operations MUST record supersession metadata and set temporal boundaries.
- `anchor-lifecycle-events`: Add `Superseded` event type with predecessor/successor context.
- `anchor-conflict`: Conflict resolution MUST create supersession links when REPLACE outcome is applied.
- `observability`: Add supersession-related OTEL span attributes to conflict resolution and archive spans.

## Impact

- **Persistence**: `PropositionNode` gains 4-6 new fields + `SUPERSEDES` relationship type. Neo4j indices needed for temporal query performance.
- **Domain model**: `Anchor` record gains optional temporal and supersession accessor methods.
- **Repository**: `AnchorRepository` gains temporal query methods (`findValidAt`, `findSupersessionChain`).
- **Engine**: `AnchorEngine.archive()`, `AnchorEngine.promote()`, and conflict resolver MUST set temporal fields and create supersession links.
- **Events**: New `Superseded` event type in `anchor/event/` package.
- **UI**: Context Inspector panel enhanced with supersession chain display.
- **Observability**: New OTEL span attributes on conflict resolution and archive operations.
- **Migration**: Existing SUPERSEDED-status anchors lack temporal fields — migration script or null-safe handling required.
