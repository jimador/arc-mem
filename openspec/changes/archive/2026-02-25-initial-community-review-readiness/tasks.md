## 1. Canonical Docs Structure And Migration Baseline

- [x] 1.1 Define and finalize canonical documentation surfaces (`docs/dev`, `openspec`) and update contributor-facing guidance. [Spec: developer-documentation-suite :: Canonical documentation surfaces]
- [x] 1.2 Build a complete inventory of all current `docs/` files and classify each as `MIGRATE_TO_DOCS_DEV`, `KEEP_IN_OPENSPEC`, or `ARCHIVE_DEPRECATE`. [Spec: developer-documentation-suite :: Documentation consolidation migration]
- [x] 1.3 Create/complete `docs/dev/migration-tracker.md` with one entry per legacy doc and target canonical path. [Spec: developer-documentation-suite :: Documentation consolidation migration]
- [x] 1.4 Add transitional stubs/pointers for legacy docs that are moved or consolidated to prevent broken references during migration. [Design: Migration risks]
- [x] 1.5 Verification: confirm 100% of current `docs/` files are classified and mapped to canonical targets. [Spec: share-ready-implementation-gate :: Documentation sync validation]

## 2. Documentation Sync Contract And Share-Readiness Checklist

- [x] 2.1 Define sync metadata format for `docs/dev` docs (linked OpenSpec paths + sync timestamp) and apply it consistently. [Spec: developer-documentation-suite :: Dev-doc and OpenSpec sync contract]
- [x] 2.2 Add a share-readiness checklist section to `docs/dev/README.md` listing each required check (implementation safety, docs sync, evidence/provenance) with pass/fail criteria. [Spec: share-ready-implementation-gate :: Gate report contract]
- [x] 2.3 Verify all legacy docs under `docs/` are present in `docs/dev/migration-tracker.md` before share-ready signoff. [Spec: share-ready-implementation-gate :: Documentation sync validation]

## 3. Conflict, Trust, And Lifecycle Safety Hardening

- [x] 3.1 Change conflict parse-failure handling to explicit degraded outcomes for shared review runs (no silent fail-open). [Spec: anchor-conflict :: DICE conflict detection strategy]
- [x] 3.2 Surface degraded conflict counts in benchmark/resilience report metadata pipelines. [Spec: anchor-conflict :: DICE conflict detection strategy]
- [x] 3.3 Persist trust re-evaluation audit records (prior/new score, trigger reason, profile) on reinforcement and profile changes. [Spec: anchor-trust :: Trust re-evaluation trigger]
- [x] 3.4 Enforce deterministic lifecycle hook order and structured mutation-block violations. [Spec: anchor-lifecycle :: Hook evaluation order]
- [x] 3.5 Verification: add/extend tests for degraded conflict behavior, trust audit records, and deterministic hook-order enforcement. [Spec: anchor-conflict, anchor-trust, anchor-lifecycle]

## 4. Run Provenance And Report Evidence Grading

- [x] 4.1 Add model identifier to `SimulationRunRecord` so reports can surface which model was used for each run. [Spec: run-history-persistence :: RunHistoryStore interface]
- [x] 4.2 Extend resilience report narrative and `MarkdownReportRenderer` with provisional language when ablations are missing, degraded-run counts are non-zero, or result stability is unmet. Fix the double-percentage bug and remove hard-coded positioning copy. [Spec: resilience-report :: Narrative generation]
- [x] 4.3 Add model identifier and degraded-run counts to `BenchmarkReport` so reviewers can assess run quality at a glance. [Spec: benchmark-report :: BenchmarkReport record structure]
- [x] 4.4 Verification: generate a report from a test run and assert model identifier, provisional language (when applicable), and degraded-run indicators are present. [Spec: benchmark-report, resilience-report]

## 5. Deterministic Regression And Observability Extensions

- [x] 5.1 Extend deterministic simulation tests to validate primary metric stability using mocked LLM responses or pre-seeded fixtures (LLM-backed runs are non-deterministic; tests should pin the LLM layer). [Spec: deterministic-simulation-tests :: Expected metrics validation]
- [x] 5.2 Add observability attributes for invariant summary and degraded-decision counters on simulation spans. [Spec: observability :: Simulation turn span includes invariant summary]
- [x] 5.3 Verification: run deterministic regression suite and inspect emitted telemetry for required attributes. [Spec: deterministic-simulation-tests, observability]

## 6. Developer Docs Consolidation And DICE Integration Detail

- [x] 6.1 Create/complete canonical docs under `docs/dev`: `architecture.md`, `design-rationale.md`, `implementation-notes.md`, `known-issues.md`, `evaluation.md`, `related-work.md`. [Spec: developer-documentation-suite :: Required developer documentation set]
- [x] 6.2 Merge material from `docs/adversarial-review/*`, root `docs/*.md`, and `docs/research/*` into canonical docs while removing duplicate technical narratives. [Spec: developer-documentation-suite :: Documentation consolidation migration]
- [x] 6.3 Ensure each `docs/dev` document references synced OpenSpec capability paths and key implementation files. [Spec: developer-documentation-suite :: Documentation traceability to code and specs]
- [x] 6.4 Add a DICE integration section that explicitly maps DICE Agent Memory retrieval to Anchors working-set injection and documents the low-trust-but-useful knowledge path. [Spec: dice-integration-review-docs :: DICE memory layering and trust-overlay clarity]
- [x] 6.5 Verification: confirm no unsynced `docs/dev` docs and no unclassified legacy docs remain. [Spec: developer-documentation-suite, share-ready-implementation-gate]

## 7. Final Share-Ready Verification

- [x] 7.1 Run manual share-readiness checklist (from `docs/dev/README.md`) and record pass/fail for each check. [Spec: share-ready-implementation-gate :: Share readiness gate evaluation]
- [x] 7.2 Run full verification suite (unit/integration where applicable, deterministic regressions) and confirm pass. [Design: Migration plan]
- [x] 7.3 Perform final documentation audit: canonical index complete, migration tracker closed, known-issues fresh, and OpenSpec sync confirmed. [Spec: developer-documentation-suite]
- [x] 7.4 Publish a concise share checklist in `docs/dev/README.md` for maintainers to reuse on future releases. [Spec: developer-documentation-suite]
