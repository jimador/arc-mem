# Context Unit Working Memory Research Roadmap

## Intent

Produce a rigorous empirical evaluation of trust/authority-governed working memory (Context Units) as a mechanism for long-horizon conversational consistency and hallucination/contradiction control. Adversarial scenarios are used as stress tests to evaluate this behavior. The end-state is: (1) a reproducible experiment framework for ablation studies, (2) a first-class benchmarking UI for running and comparing experiments, (3) a tech report / arXiv paper positioned against MemGPT/Letta, Zep/Graphiti, ShardMemo, and MemOS, suitable for presentation to the DICE team and broader community review. Future tracks capture serendipitous knowledge retrieval and multi-agent (A2A) context unit governance as follow-on research directions.

## RFC 2119 Compliance

Normative roadmap statements SHOULD use RFC 2119 keywords only (`MUST`, `SHOULD`, `MAY`, and their negations).

## Research Configuration

- `Research mode`: `scoped`
- `Research criteria summary`: Prefer codebase evidence and published papers (arXiv, ACL, ICLR). Web sources for recent systems (Letta, Zep, ShardMemo) documentation.
- `Evidence freshness expectation`: Papers and system docs SHOULD be within 24 months (2024-2026).
- `Minimum sources per research task`: 4 (with at least 2 primary external sources).

## Scope

### In Scope

1. Feature decomposition and sequencing for experiment infrastructure (Track A).
2. Feature documentation for future research directions (Tracks B, C) at roadmap level.
3. Research backlog for evaluator validity and related work landscape.
4. OpenSpec change scaffolding for near-term code features (F01, F02).

### Out of Scope

1. Code implementation (deferred to OpenSpec changes).
2. Full OpenSpec spec/design/tasks authoring.
3. Actual experiment execution or paper writing (those follow from the infrastructure).
4. Changes to the core ARC-Mem engine (`context unit/` package) — this roadmap evaluates the engine, not modifies it.

## Constraints

1. Conform to `openspec/constitution.md` — Neo4j-only persistence, constructor injection, records for DTOs, context unit invariants.
2. Build on the existing `sim.benchmark` package (BenchmarkRunner, BenchmarkAggregator, BenchmarkStatistics, BenchmarkReport) delivered by the `benchmarking-and-statistical-rigor` change (F06 of the evolution roadmap).
3. Simulation isolation via `contextId` (`sim-{uuid}`) MUST be preserved — experiments run multiple conditions but each run is isolated.
4. The paper MUST position context units explicitly against MemGPT/Letta, Zep/Graphiti, ShardMemo, MemOS, and A-MemGuard. It MUST NOT claim generalization beyond what is tested.

## Tracks

### Track A: Long-Horizon Consistency Evaluation (Critical Path)

The primary deliverable. Produces the experiment framework, benchmarking UI, and the tech report. This is the near-term work.

### Track B: Creative Retrieval (Future Work)

Serendipitous knowledge retrieval via spreading activation in the DICE knowledge graph. Injecting controlled randomness (2+ hop distant nodes) to inspire creative model outputs. Captured as future work in the paper's discussion section.

### Track C: A2A Extension (Future Work)

Multi-agent context unit governance — extending the trust/authority model to agent-to-agent communication with asymmetric context budgets. Captured as future work in the paper's discussion section.

## Proposal Waves

| Wave | Feature ID | Feature Slug                      | Priority | Depends On                                | Visibility         | OpenSpec Change Slug        | Spec Coverage |
|------|------------|---------------t--------------------|----------|-------------------------------------------|--------------------|-----------------------------|---|
| 1    | F01        | experiment-framework              | MUST     | benchmarking-and-statistical-rigor (done) | UI + Observability | experiment-framework        | ✓ Covered by `ablation-conditions`, `experiment-execution`, `cross-condition-statistics`, `experiment-persistence`, `experiment-progress-monitor` specs |
| 2    | F02        | first-class-benchmarking-ui       | MUST     | F01                                       | UI + Observability | first-class-benchmarking-ui | ✓ Covered by `benchmark-ui`, `benchmark-view-routing`, `experiment-config-ux`, `experiment-history-panel`, `fact-drill-down`, `condition-comparison-view` specs |
| 2    | F03        | resilience-evaluation-report      | MUST     | F01, F02                                  | Observability      | —                           | ✓ Covered by `resilience-report`, `resilience-score`, `benchmark-report`, `benchmark-statistics` specs |
| 3    | F04        | serendipitous-knowledge-retrieval | MAY      | none (independent)                        | UI + Observability | —                           | — No spec yet (future work) |
| 3    | F05        | multi-agent-unit-governance     | MAY      | none (independent)                        | UI + Observability | —                           | — No spec yet (future work) |

## Research Backlog

| Task ID | Question                                                                                                  | Target Feature(s) | Channels                   | Timebox | Success Criteria                                                                                           | Output Doc                                                                                        |
|---------|-----------------------------------------------------------------------------------------------------------|-------------------|----------------------------|---------|------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| R01     | How reliable is LLM-as-judge drift evaluation, and what calibration methodology ensures credible results? | F01, F03          | codebase + web + repo-docs | 6h      | Human agreement methodology defined; Cohen's kappa target established; calibration sample size determined. | `openspec/roadmaps/unit-working-memory-research/research/R01-evaluator-validity-and-calibration.md` |
| R02     | How does context units position against MemGPT, Zep, ShardMemo, MemOS, and A-MemGuard?                     | F03               | web + repo-docs            | 8h      | Comparison table with per-system analysis; differentiation argument articulated; citation list complete.   | `openspec/roadmaps/unit-working-memory-research/research/R02-related-work-landscape-analysis.md`    |

## Sequencing Rationale

1. **Wave 1** builds the experiment engine. Without ablation configurations, paired execution, and cross-condition statistics, no credible evaluation is possible. This wave extends the existing BenchmarkRunner infrastructure with experiment-level orchestration.
2. **Wave 2** delivers the UI for running experiments and the paper itself. These are parallel-but-coupled: the UI enables rapid iteration on experiment design, and the paper requires experiment results. F02 provides the drill-down views needed to debug and understand results during paper writing.
3. **Wave 3** captures future research directions. These are independently proposable and do not block the paper. They appear in the paper's "Future Work" discussion section.

## Feature Documents

1. `openspec/roadmaps/unit-working-memory-research/features/01-experiment-framework.md`
2. `openspec/roadmaps/unit-working-memory-research/features/02-first-class-benchmarking-ui.md`
3. `openspec/roadmaps/unit-working-memory-research/features/03-resilience-evaluation-report.md`
4. `openspec/roadmaps/unit-working-memory-research/features/04-serendipitous-knowledge-retrieval.md`
5. `openspec/roadmaps/unit-working-memory-research/features/05-multi-agent-unit-governance.md`

## Research Documents

1. `openspec/roadmaps/unit-working-memory-research/research/R01-evaluator-validity-and-calibration.md`
2. `openspec/roadmaps/unit-working-memory-research/research/R02-related-work-landscape-analysis.md`

## Change Scaffolds

| Feature ID | Change Slug                       | Scaffold Status | Path                                            |
|------------|-----------------------------------|-----------------|-------------------------------------------------|
| F01        | experiment-framework              | created         | `openspec/changes/experiment-framework/`        |
| F02        | first-class-benchmarking-ui       | created         | `openspec/changes/first-class-benchmarking-ui/` |
| F03        | resilience-evaluation-report      | —               | Not a code change; paper deliverable            |
| F04        | serendipitous-knowledge-retrieval | —               | Future work; scaffold when ready                |
| F05        | multi-agent-unit-governance     | —               | Future work; scaffold when ready                |

## Global Risks

1. **LLM API cost**: Running a full experiment matrix (4 conditions x 3-5 scenarios x 5 reps = 60-100 runs, each 15-25 turns with 2-3 LLM calls/turn) SHOULD be estimated before execution. Mitigation: Start with 2 scenarios x 3 reps for calibration.
2. **Evaluator reliability**: LLM-as-judge may produce inconsistent verdicts. Mitigation: R01 defines calibration methodology; human agreement study validates the evaluator.
3. **Non-determinism may wash out effect sizes**: If LLM variance is high, even real effects may not reach statistical significance with N=5 reps. Mitigation: Increase N if initial results show high CV; report effect sizes alongside p-values.
4. **Paper positioning risk**: Reviewers may argue the D&D domain is too narrow. Mitigation: Frame as "collaborative fiction" (a recognized NLP domain); emphasize the mechanism is domain-agnostic; acknowledge limitation explicitly.
5. **Scope creep from Tracks B and C**: Future work items may pull focus from the paper. Mitigation: Tracks B and C are Wave 3 with MAY priority; they are documented but not scheduled.

## Exit Criteria

1. Every feature has a doc with acceptance criteria and visibility requirements.
2. Dependencies and sequencing are explicit.
3. Track A features (F01, F02) have OpenSpec change scaffolds.
4. Research tasks R01 and R02 have output docs.
5. High-risk unknowns (evaluator validity, related work positioning) are addressed in research docs.
6. Future work (F04, F05) is documented at sufficient depth for paper discussion section.

## Suggested Proposal Commands

1. `/opsx:ff experiment-framework`
2. `/opsx:ff first-class-benchmarking-ui`

## Known Limitations

1. **No cross-domain evaluation**: This roadmap evaluates context units in a D&D/collaborative fiction domain only. Cross-domain evaluation (medical, legal, customer support) is deferred.
2. **Single-model evaluation**: Initial experiments will use one LLM (likely GPT-4o or Claude). Cross-model evaluation (comparing context unit effectiveness across different LLM backends) is a natural follow-up but not in scope.
3. **No hypothesis testing in Wave 1**: The experiment framework computes descriptive statistics and effect sizes. Formal hypothesis testing (t-tests, ANOVA) is a candidate extension if sample sizes warrant it.
4. **Paper scope is tech report, not full conference paper**: The initial target is arXiv + DICE team presentation. Upgrading to a conference submission (e.g., EMNLP, ACL workshop) may require additional experiments and a more formal evaluation methodology.
