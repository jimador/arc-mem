# Experiment Runner for Whitepaper Data Gathering — Roadmap

## Intent

Complete the experiment runner infrastructure so that a single automated run produces all data needed for the ARC whitepaper. The whitepaper outlines 4 research questions, 5 hypotheses, and a multi-condition ablation matrix. The current experiment runner covers ~60% of this: it has 4 of 7+ needed ablation conditions, 23 scenarios but all in a single domain, Markdown-only export, and no cross-domain evidence. This roadmap closes those gaps so the evaluation section of the paper can be populated from automated, reproducible experiment output.

Evidence standard: "as good as it needs to be to be well done, understandable, and either validate or invalidate the hypotheses." Directional findings with honest confidence intervals, not publication-grade statistical perfection.

## RFC 2119 Compliance

Normative requirements in this roadmap use RFC 2119 keywords (`MUST`, `SHOULD`, `MAY`, and their negations).

## Research Configuration

- `Research mode`: scoped
- `Research criteria summary`: Evidence needed for whitepaper claims, ACT-R positioning, cross-domain scenario design, and statistical methodology decisions
- `Evidence freshness expectation`: 2024–2026 for ACT-R/LLM papers; current for codebase analysis
- `Minimum sources per research task`: 2

## Scope

### In Scope

1. Whitepaper outline refinement (ACT-R references, terminology alignment, methodology update)
2. Missing ablation conditions (NO_TRUST, NO_COMPLIANCE, NO_LIFECYCLE)
3. Cross-domain scenario packs (at least 2 non-D&D domains)
4. Secondary metrics the paper needs (erosion rate, reactivation success, compliance recovery, failure categorization)
5. Machine-readable export pipeline (JSON, CSV, per-turn traces)
6. Automated full-matrix experiment runner (all conditions × all packs × N reps → aggregated report)
7. Statistical hardening (hypothesis tests, multiple-comparison correction, reproducibility manifest)

### Out of Scope

1. RETRIEVAL_ONLY ablation condition (secondary; requires retrieval subsystem not yet built)
2. HTML report rendering (Markdown + JSON/CSV sufficient for paper)
3. Dokimos or other external evaluation framework adoption (evaluated and rejected — custom harness is more capable)
4. Whitepaper prose writing (this roadmap produces data, not paper text)
5. Production hardening of the experiment runner (this is research tooling)

## Constraints

1. Neo4j sole persistence store (Article II of constitution)
2. Constructor injection only (Article III)
3. Records for immutable data (Article IV)
4. AWMU invariants: budget ≤ 20, rank ∈ [100, 900], explicit promotion, no auto-CANON (Article V)
5. Simulation isolation via contextId (Article VI)
6. Core logic MUST NOT reference simulation concepts (CLAUDE.md layer boundary)
7. Existing ExperimentRunner, BenchmarkRunner, ScoringService, EffectSizeCalculator infrastructure SHOULD be extended, not replaced

## Proposal Waves

| Wave | Feature ID | Feature Slug | Priority | Depends On | Visibility | OpenSpec Change Slug |
|------|------------|--------------|----------|------------|------------|----------------------|
| 1 | F01 | whitepaper-outline-refinement | MUST | none | Observability | whitepaper-outline-refinement |
| 1 | F02 | ablation-condition-expansion | MUST | F01 | UI + Observability | ablation-condition-expansion |
| 2 | F03 | cross-domain-scenario-packs | MUST | none | UI + Observability | cross-domain-scenario-packs |
| 2 | F04 | secondary-metrics | SHOULD | none | UI + Observability | secondary-metrics |
| 3 | F05 | export-aggregation-pipeline | MUST | F04 | UI + Observability | export-aggregation-pipeline |
| 3 | F06 | automated-experiment-matrix | MUST | F02, F03, F05 | UI + Observability | automated-experiment-matrix |
| 4 | F07 | statistical-hardening | MUST | F06 | Observability | statistical-hardening |

## Research Backlog

| Task ID | Question | Target Feature(s) | Channels | Timebox | Success Criteria | Output Doc |
|---------|----------|-------------------|----------|---------|------------------|------------|
| R01 | How do recent ACT-R + LLM papers position relative to ARC's governed working memory claims? | F01 | web + repo-docs | 45m | 4 papers classified as complementary/competitive/orthogonal with citation-ready summaries | `openspec/roadmaps/experiment-runner-whitepaper/research/R01-actr-positioning.md` |
| R02 | What runtime flags and plumbing are needed to disable trust, compliance, and lifecycle subsystems independently? | F02 | codebase | 30m | Per-condition implementation sketch with affected classes and config paths | `openspec/roadmaps/experiment-runner-whitepaper/research/R02-ablation-plumbing.md` |
| R03 | What cross-domain scenarios exist in LLM memory evaluation literature and what ground-truth patterns work outside narrative? | F03 | web + repo-docs | 45m | 2+ domain scenario structures with ground-truth fact templates | `openspec/roadmaps/experiment-runner-whitepaper/research/R03-cross-domain-scenarios.md` |
| R04 | What statistical methodology is standard for LLM ablation studies — frequentist (p-values + Bonferroni) vs Bayesian vs effect-size-only? | F07 | web | 30m | Methodology recommendation with 2+ precedent papers | `openspec/roadmaps/experiment-runner-whitepaper/research/R04-statistical-methodology.md` |

## Sequencing Rationale

**Wave 1** establishes terminology and core infrastructure. F01 (outline refinement) aligns naming between paper and code, preventing terminology churn in later features. F02 (ablation expansion) adds the conditions the paper's evaluation matrix requires — NO_TRUST is explicitly referenced in hypotheses H2 and H4.

**Wave 2** broadens evidence. F03 (cross-domain scenarios) is required for any cross-domain generalization claim. F04 (secondary metrics) enriches the data but the paper can proceed with primary metrics only if needed. These are parallelizable.

**Wave 3** produces output. F05 (export pipeline) makes results machine-readable for analysis and figure generation. F06 (automated matrix) ties everything together into a single-command data-gathering run. F06 depends on F02 + F03 + F05 because the full matrix needs all conditions, scenarios, and export formats in place.

**Wave 4** strengthens statistical claims. F07 (statistical hardening) adds hypothesis testing and reproducibility guarantees. Upgraded to MUST for arXiv submission — reviewers expect inferential statistics beyond effect sizes alone.

## Feature Documents

1. `openspec/roadmaps/experiment-runner-whitepaper/features/01-whitepaper-outline-refinement.md`
2. `openspec/roadmaps/experiment-runner-whitepaper/features/02-ablation-condition-expansion.md`
3. `openspec/roadmaps/experiment-runner-whitepaper/features/03-cross-domain-scenario-packs.md`
4. `openspec/roadmaps/experiment-runner-whitepaper/features/04-secondary-metrics.md`
5. `openspec/roadmaps/experiment-runner-whitepaper/features/05-export-aggregation-pipeline.md`
6. `openspec/roadmaps/experiment-runner-whitepaper/features/06-automated-experiment-matrix.md`
7. `openspec/roadmaps/experiment-runner-whitepaper/features/07-statistical-hardening.md`

## Prep Documents

1. `openspec/roadmaps/experiment-runner-whitepaper/prep/01-whitepaper-outline-refinement-prep.md`
2. `openspec/roadmaps/experiment-runner-whitepaper/prep/02-ablation-condition-expansion-prep.md`
3. `openspec/roadmaps/experiment-runner-whitepaper/prep/03-cross-domain-scenario-packs-prep.md`
4. `openspec/roadmaps/experiment-runner-whitepaper/prep/04-secondary-metrics-prep.md`
5. `openspec/roadmaps/experiment-runner-whitepaper/prep/05-export-aggregation-pipeline-prep.md`
6. `openspec/roadmaps/experiment-runner-whitepaper/prep/06-automated-experiment-matrix-prep.md`
7. `openspec/roadmaps/experiment-runner-whitepaper/prep/07-statistical-hardening-prep.md`

## Change Scaffolds

| Feature ID | Change Slug | Scaffold Status | Path |
|------------|-------------|-----------------|------|
| F01 | whitepaper-outline-refinement | created | `openspec/changes/whitepaper-outline-refinement/` |
| F02 | ablation-condition-expansion | created | `openspec/changes/ablation-condition-expansion/` |
| F03 | cross-domain-scenario-packs | created | `openspec/changes/cross-domain-scenario-packs/` |
| F04 | secondary-metrics | created | `openspec/changes/secondary-metrics/` |
| F05 | export-aggregation-pipeline | created | `openspec/changes/export-aggregation-pipeline/` |
| F06 | automated-experiment-matrix | created | `openspec/changes/automated-experiment-matrix/` |
| F07 | statistical-hardening | created | `openspec/changes/statistical-hardening/` |

## Research Documents

1. `openspec/roadmaps/experiment-runner-whitepaper/research/R01-actr-positioning.md`
2. `openspec/roadmaps/experiment-runner-whitepaper/research/R02-ablation-plumbing.md`
3. `openspec/roadmaps/experiment-runner-whitepaper/research/R03-cross-domain-scenarios.md`
4. `openspec/roadmaps/experiment-runner-whitepaper/research/R04-statistical-methodology.md`

## Global Risks

1. **LLM evaluation variance**: Adversarial and adaptive scenarios produce non-deterministic results. Mitigation: high repetition count (≥10 per cell), CI reporting, separate deterministic vs adaptive packs.
2. **Cross-domain scenario quality**: Non-D&D scenarios may not stress the same failure modes. Mitigation: R03 research task, ground-truth validation, pilot runs before full matrix.
3. **Statistical overclaim**: Effect sizes without proper hypothesis testing could mislead. Mitigation: F07 adds proper tests, paper uses conservative "directional" language per outline's evidence guardrails.
4. **Scope creep into paper writing**: This roadmap produces data infrastructure, not prose. Mitigation: out-of-scope boundary explicit.

## Exit Criteria

Roadmap is complete when:

1. every feature has a doc,
2. dependencies are explicit,
3. each feature has acceptance criteria,
4. each feature has visibility requirements,
5. each feature has a proposal seed and `/opsx:new` command,
6. each feature is ready for `openspec new change <change-slug>`.

## Suggested Proposal Commands

1. `/opsx:new whitepaper-outline-refinement`
2. `/opsx:new ablation-condition-expansion`
3. `/opsx:new cross-domain-scenario-packs`
4. `/opsx:new secondary-metrics`
5. `/opsx:new export-aggregation-pipeline`
6. `/opsx:new automated-experiment-matrix`
7. `/opsx:new statistical-hardening`

## Known Limitations

1. **RETRIEVAL_ONLY condition deferred** — requires a retrieval subsystem that doesn't exist yet. The paper can address this as future work in Section 11 (Limitations).
2. **Single LLM backend** — experiments run against one model (GPT-4.1-mini). Cross-model generalization is out of scope for this paper.
3. **Evaluator calibration** — drift evaluation uses LLM-as-judge, which has its own variance. The paper acknowledges this in Section 7.6 (Failure Analysis Protocol) and Section 11.6 (Evaluation Maturity Limits).
