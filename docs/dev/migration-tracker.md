# Migration Tracker

This file tracks migration of existing `docs/` content into canonical developer docs (`docs/dev`) or OpenSpec (`openspec`).

| Source | Classification | Target | Notes |
|---|---|---|---|
| `docs/architecture.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/architecture.md` | Canonical architecture narrative |
| `docs/simulation-harness.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/evaluation.md` + `docs/dev/reproducibility.md` | Split protocol vs run instructions |
| `docs/known-limitations.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/known-issues.md` | Preserve confirmed limitations and mitigations |
| `docs/anchors-as-working-memory.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/design-rationale.md` | Consolidate conceptual intent |
| `docs/anchor-engine-survey.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/implementation-notes.md` | Engine internals overview |
| `docs/anchors-external-technical-assessment-2026-02-21.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/related-work.md` + `docs/dev/design-rationale.md` | Keep evidence-backed critique |
| `docs/investigate.md` | ARCHIVE_DEPRECATE | `docs/dev/migration-tracker.md` references | Fold actionable items into OpenSpec/tasks |
| `docs/adversarial-review/SYSTEM_MAP.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/architecture.md` | Keep as source material during migration |
| `docs/adversarial-review/EVAL_PROTOCOL.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/evaluation.md` | Canonical protocol text |
| `docs/adversarial-review/RESULTS.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/evaluation.md` | Summarized current evidence status |
| `docs/adversarial-review/CREDIBILITY_RISKS.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/known-issues.md` | Risk ledger |
| `docs/adversarial-review/HARNESS_GAP_ANALYSIS.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/implementation-notes.md` + `docs/dev/evaluation.md` | Harness gaps and instrumentation |
| `docs/adversarial-review/ANCHORS_TECH_REPORT.md` | MIGRATE_TO_DOCS_DEV | `docs/dev/related-work.md` | Technical positioning |
| `docs/research/*` | ARCHIVE_DEPRECATE | `docs/dev/related-work.md` + OpenSpec references | Keep citations; avoid duplicate narratives |
| `docs/roadmaps/*` | KEEP_IN_OPENSPEC | `openspec/changes/*` and `openspec/specs/*` | Roadmaps should collapse into OpenSpec artifacts |

## Rules

- A file marked `MIGRATE_TO_DOCS_DEV` MUST be merged into canonical `docs/dev` targets before share-ready signoff.
- A file marked `ARCHIVE_DEPRECATE` SHOULD be archived or replaced with a pointer once migration is complete.
- OpenSpec remains canonical for normative requirements/design/tasks.
