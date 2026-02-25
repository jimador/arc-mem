# Anchors Working Memory Evolution Roadmap

## Intent

Define a proposal-ready roadmap so each feature can cleanly start with `openspec new change <slug>` and produce a strong OpenSpec proposal.

## RFC 2119 Compliance

Normative roadmap statements SHOULD use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and negations).

## Research Configuration

- `Research mode`: `scoped`
- `Research criteria summary`: Prefer repository evidence and local research docs first.
- `Evidence freshness expectation`: external references SHOULD be within 18 months for fast-changing tooling.
- `Minimum sources per research task`: 3 (with at least 1 primary external source when web research is used).

## Scope

### In Scope

1. Feature decomposition and sequencing for proposal-ready OpenSpec changes.
2. Explicit dependencies and visibility requirements.
3. Research backlog for unresolved high-risk design choices.

### Out of Scope

1. Code implementation.
2. OpenSpec spec/design/tasks authoring at this stage.

## Constraints

1. Conform to `openspec/project.md` and `openspec/constitution.md`.
2. Preserve existing architecture constraints (Neo4j-only persistence and anchor invariants).
3. Keep OpenSpec as canonical source for proposal/spec/design/tasks artifacts.

## Proposal Waves

| Wave | Feature ID | Feature Slug                           | Priority | Depends On              | Visibility         | OpenSpec Change Slug                   | Spec Coverage |
|------|------------|----------------------------------------|----------|-------------------------|--------------------|----------------------------------------|---|
| 1    | F01        | working-memory-tiering-core            | MUST     | none                    | UI + Observability | working-memory-tiering-core            | ✓ Covered by `memory-tiering`, `tier-aware-decay`, `tier-aware-assembly` specs |
| 1    | F02        | conflict-detection-calibration-core    | MUST     | F01                     | UI + Observability | conflict-detection-calibration-core    | ✓ Covered by `conflict-detection`, `conflict-calibration`, `semantic-conflict-detection`, `anchor-conflict` specs |
| 1    | F03        | retrieval-quality-gate-toolishrag      | MUST     | F02                     | UI + Observability | retrieval-quality-gate-toolishrag      | ✓ Covered by `retrieval-quality-gate`, `anchor-trust`, `trust-scoring` specs |
| 2    | F04        | bi-temporal-validity-and-supersession  | SHOULD   | F01, F02                | UI + Observability | bi-temporal-validity-and-supersession  | ✓ Covered by `bi-temporal-validity`, `anchor-supersession`, `anchor-lifecycle` specs |
| 2    | F05        | compaction-recovery-guardrails         | MUST     | none                    | UI + Observability | compaction-recovery-guardrails         | ✓ Covered by `compaction`, `compaction-recovery` specs |
| 3    | F06        | benchmarking-and-statistical-rigor     | SHOULD   | F01, F02, F03, F04, F05 | UI + Observability | benchmarking-and-statistical-rigor     | ✓ Covered by `benchmark-runner`, `benchmark-statistics`, `benchmark-report`, `ablation-conditions`, `cross-condition-statistics` specs |
| 3    | F07        | operator-invariants-api-and-governance | MAY      | F01, F02                | UI + Observability | operator-invariants-api-and-governance | ✓ Covered by `operator-invariants`, `invariant-inspector`, `canonization-gate` specs |
| 4    | F08        | dice-framework-fit-upstream-proposal   | MUST     | F01, F02, F03           | UI + Observability | dice-framework-fit-upstream-proposal   | ◐ Partially covered by `anchor-lifecycle`, `anchor-extraction`, `anchor-llm-tools` specs |

## Research Backlog (Optional)

| Task ID | Question                                                                                                     | Target Feature(s) | Channels                                   | Timebox | Success Criteria                                                                    | Output Doc                                                                                            |
|---------|--------------------------------------------------------------------------------------------------------------|-------------------|--------------------------------------------|---------|-------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| R01     | What calibration approach should replace static conflict thresholds while preserving stable policy behavior? | F02, F03          | codebase + repo-docs + web + similar-repos | 8h      | Decision matrix and rollout gates with measurable review/false-allow targets.       | `docs/roadmaps/anchors-working-memory-evolution/research/R01-conflict-calibration-method.md`          |
| R02     | Which temporal model best fits existing graph constraints with low migration risk?                           | F04               | codebase + repo-docs + web                 | 6h      | Accepted temporal field model, migration approach, and supersession rules.          | `docs/roadmaps/anchors-working-memory-evolution/research/R02-temporal-model-and-migration.md`         |
| R03     | Which retry/fallback sequence provides no-regression compaction behavior under latency constraints?          | F05               | codebase + repo-docs + similar-repos       | 6h      | Policy recommendation with objective replacement safety criteria.                   | `docs/roadmaps/anchors-working-memory-evolution/research/R03-compaction-recovery-policy.md`           |
| R04     | How should operator-defined invariants be modeled and prioritized against extracted anchors?                 | F07, F08          | codebase + repo-docs + web                 | 8h      | Precedence matrix, lifecycle model, and audit contract ready for proposal drafting. | `docs/roadmaps/anchors-working-memory-evolution/research/R04-operator-invariants-governance-model.md` |

## Sequencing Rationale

1. Wave 1 establishes lifecycle semantics and mutation safety controls.
2. Wave 2 hardens reliability and temporal correctness.
3. Wave 3 adds evaluation rigor and optional governance capability.
4. Wave 4 packages validated behavior into framework-fit planning artifacts.

## Feature Documents

1. `docs/roadmaps/anchors-working-memory-evolution/features/01-working-memory-tiering-core.md`
2. `docs/roadmaps/anchors-working-memory-evolution/features/02-conflict-detection-calibration-core.md`
3. `docs/roadmaps/anchors-working-memory-evolution/features/03-retrieval-quality-gate-toolishrag.md`
4. `docs/roadmaps/anchors-working-memory-evolution/features/04-bi-temporal-validity-and-supersession.md`
5. `docs/roadmaps/anchors-working-memory-evolution/features/05-compaction-recovery-guardrails.md`
6. `docs/roadmaps/anchors-working-memory-evolution/features/06-benchmarking-and-statistical-rigor.md`
7. `docs/roadmaps/anchors-working-memory-evolution/features/07-operator-invariants-api-and-governance.md`
8. `docs/roadmaps/anchors-working-memory-evolution/features/08-dice-framework-fit-upstream-proposal.md`

## Research Documents (Optional)

1. `docs/roadmaps/anchors-working-memory-evolution/research/R01-conflict-calibration-method.md`
2. `docs/roadmaps/anchors-working-memory-evolution/research/R02-temporal-model-and-migration.md`
3. `docs/roadmaps/anchors-working-memory-evolution/research/R03-compaction-recovery-policy.md`
4. `docs/roadmaps/anchors-working-memory-evolution/research/R04-operator-invariants-governance-model.md`

## Exit Criteria

1. Every feature doc is proposal-ready with a stable change slug.
2. Dependencies and acceptance criteria are explicit.
3. Visibility requirements are defined per feature.
4. High-risk unknowns are captured in research tasks.

## Suggested Proposal Commands

1. `openspec new change working-memory-tiering-core`
2. `openspec new change conflict-detection-calibration-core`
3. `openspec new change retrieval-quality-gate-toolishrag`
4. `openspec new change bi-temporal-validity-and-supersession`
5. `openspec new change compaction-recovery-guardrails`
6. `openspec new change benchmarking-and-statistical-rigor`
7. `openspec new change operator-invariants-api-and-governance`
8. `openspec new change dice-framework-fit-upstream-proposal`
