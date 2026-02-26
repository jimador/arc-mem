# Feature: Anchor Provenance Metadata

## Feature ID

`F04`

## Summary

Add lightweight source provenance metadata to anchors: extraction turn number, speaker role (player/DM/system), and corroboration chain. This is NOT ownership-gating — provenance is metadata for audit, observability, and cascade heuristics, not an access-control mechanism.

## RFC 2119 Compliance

All normative statements in this document SHOULD use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Why This Feature

1. Problem addressed: Anchors today have no record of when or by whom they were introduced. The `sourceIds` field exists for corroboration counting but is not populated with actor/turn metadata. This limits audit capability and makes cascade heuristics (F03) less precise.
2. Value delivered: Provenance metadata enables temporal co-creation cascade (F03), audit trail for compliance scenarios, and observability into anchor lifecycle origins.
3. Why now: Wave 2 — independent of F01 but complementary to F03 (cascade heuristics benefit from extraction turn data).

## Scope

### In Scope

1. Add `extractionTurn` (int) and `speakerRole` (enum: PLAYER, DM, SYSTEM, UNKNOWN) fields to `PropositionNode`.
2. Populate provenance at extraction time (DICE extraction pipeline in `ChatActions` and `SimulationExtractionService`).
3. Expose provenance in the anchor panel (UI) and lifecycle events (observability).
4. Use provenance as a cascade heuristic input (anchors co-extracted in the same turn are dependency candidates).

### Out of Scope

1. Access control based on provenance (no "only the creator can revise" gating).
2. Changes to the `Anchor` record itself (provenance lives on `PropositionNode` in Neo4j).
3. Retroactive population of provenance for existing anchors.

## Dependencies

1. Feature dependencies: None (independent).
2. Priority: SHOULD.
3. OpenSpec change slug: `anchor-provenance-metadata`.

## Impacted Areas

1. Packages/components: `persistence/` (PropositionNode), `extract/` (extraction pipeline), `chat/` (ChatActions), `sim/engine/` (SimulationExtractionService).
2. Data/persistence: New fields on PropositionNode in Neo4j. Migration: new fields nullable, populated for new extractions only.
3. Domain-specific subsystem impacts: Anchor panel may show provenance details.

## Visibility Requirements

### UI Visibility

1. User-facing surface: Anchor panel MAY show extraction turn and speaker role as metadata on each anchor card.
2. What is shown: "Extracted: Turn 1, Player" or "Extracted: Turn 0, System".
3. Success signal: Provenance visible on newly-created anchors.

### Observability Visibility

1. Logs/events/metrics: Extraction events MUST include provenance fields (turn, speaker role).
2. Trace/audit payload: Provenance fields included in `TrustAuditRecord` when available.
3. How to verify: Query Neo4j for PropositionNode with populated `extractionTurn` and `speakerRole` fields.

## Acceptance Criteria

1. `PropositionNode` MUST include `extractionTurn` (int, nullable) and `speakerRole` (String, nullable) fields.
2. The extraction pipeline MUST populate provenance fields for newly-extracted propositions.
3. Existing propositions without provenance MUST NOT be broken (nullable fields, backward-compatible).
4. Provenance fields MUST be queryable in Neo4j (e.g., "find all anchors extracted in turn N").
5. Provenance SHOULD be visible in the anchor panel UI.

## Risks and Mitigations

1. Risk: Speaker role attribution is imprecise for collaborative turns (DM elaborating on player input).
2. Mitigation: Default to UNKNOWN when attribution is ambiguous; document known limitations.

## Proposal Seed

### Suggested OpenSpec Change Slug

`anchor-provenance-metadata`

### Proposal Starter Inputs

1. Problem statement: Anchors have no record of when or by whom they were introduced. This limits audit, cascade heuristics, and observability.
2. Why now: Complements F03 cascade strategy; independent of other features.
3. Constraints: Nullable fields for backward compatibility; no access-control gating.
4. Visible outcomes: Provenance in anchor panel; provenance in lifecycle events.

### Candidate Requirement Blocks

1. Requirement: The persistence layer SHALL capture extraction turn and speaker role for newly-extracted propositions.
2. Scenario: When a player sends a message that generates propositions, each proposition SHALL record `extractionTurn` and `speakerRole=PLAYER`.

## Validation Plan

1. Unit tests: Provenance fields populated at extraction; nullable for existing data.
2. Neo4j query: Verify fields queryable.
3. UI: Provenance visible on anchor cards.

## Known Limitations

1. Speaker role attribution for DM-elaborated player facts is inherently ambiguous.
2. Existing anchors will not have provenance (no backfill).

## Suggested Command

`/opsx:new anchor-provenance-metadata`
