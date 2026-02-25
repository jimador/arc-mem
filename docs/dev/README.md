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

## Current Status

- Structure established.
- Migration in progress (see `docs/dev/migration-tracker.md`).
