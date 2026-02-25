# Developer Docs

This is the canonical developer-facing documentation surface.

Normative requirements, design contracts, and implementation tasks live in `openspec/`.

## Canonical Structure

- `docs/dev/README.md`: This index and navigation entrypoint.
- `docs/dev/architecture.md`: System topology, modules, data flow, and boundaries.
- `docs/dev/design-rationale.md`: Key technical decisions and tradeoffs.
- `docs/dev/implementation-notes.md`: Important implementation details and caveats.
- `docs/dev/known-issues.md`: Confirmed defects and current mitigations.
- `docs/dev/evaluation.md`: Evaluation protocol and interpretation guidance.
- `docs/dev/reproducibility.md`: Clone/setup/run and smoke-test instructions.
- `docs/dev/related-work.md`: Positioning against relevant prior systems/work.
- `docs/dev/migration-tracker.md`: Mapping from legacy docs into canonical docs.

## Sync Contract

- Every `docs/dev` document MUST reference relevant OpenSpec capability paths.
- Any behavior change in `openspec/specs/*/spec.md` MUST be reflected in `docs/dev` within the same release.
- Duplicate technical docs outside `docs/dev` and `openspec` SHOULD be migrated or archived.

## Share-Readiness Checklist

Run through this checklist before community sharing. Record pass/fail for each item.

### Implementation Safety

- [ ] Conflict parse failures produce explicit degraded outcomes (no silent fail-open) — verify `LlmConflictDetector` ACON1 behavior
- [ ] Lifecycle hook order is deterministic: invariant → trust → mutation — verify `AnchorEngine` reinforce/demote/archive paths
- [ ] Authority upgrades are upgrade-only (PROVISIONAL → UNRELIABLE → RELIABLE → CANON) — verify no downgrade paths
- [ ] Budget enforcement evicts lowest-ranked non-pinned when over 20 — verify `AnchorEngine.enforceBudget`
- [ ] Trust re-evaluation audit records are persisted — verify `TrustAuditRecord` creation on reinforcement

### Evidence and Provenance

- [ ] Reports include model identifier when available — verify `ResilienceReport.modelId` / `BenchmarkReport.modelId`
- [ ] Reports include provisional language when ablations are missing or degraded runs detected — verify `ResilienceReportBuilder.generateNarrative`
- [ ] Degraded conflict counts surface in benchmark metadata — verify `BenchmarkAggregator` includes `degradedConflictCount`
- [ ] All tests pass (`./mvnw test`) — record test count and result

### Documentation Completeness

- [ ] All canonical docs present in `docs/dev/` per index above
- [ ] Migration tracker (`docs/dev/migration-tracker.md`) covers all legacy `docs/` files
- [ ] Transitional stubs added to migrated legacy docs
- [ ] Each `docs/dev` document includes sync metadata (OpenSpec paths + timestamp)
- [ ] Known issues ledger differentiates confirmed defects from research uncertainty
- [ ] DICE integration section documents memory layering, trust overlay, and extension points

### Checklist Record

**Date:** 2026-02-25
**Checked by:** Claude (automated verification)
**Outcome:** PASS — 696 tests pass, all canonical docs present, all legacy docs classified and stubbed, sync metadata applied, DICE integration documented. No blocking issues.

## Current Status

- Canonical docs consolidated from legacy sources.
- Migration tracker closed — all legacy docs classified and stubbed.
- Share-readiness checklist established above.
