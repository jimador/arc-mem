# Feature: Resilience Evaluation Report

## Feature ID

`F03`

## Summary

Produce a tech report / arXiv paper that presents empirical evidence for trust/authority-governed working memory as a structural mechanism for long-horizon conversational consistency and hallucination/contradiction control. Adversarial scenarios are treated as stress tests in this evaluation setup. This is a **publication deliverable**, not a code feature. The paper positions context units against MemGPT/Letta, Zep/Graphiti, ShardMemo, MemOS, A-MemGuard, and ASB; reports ablation results with effect sizes; and scopes future research directions for serendipitous retrieval and multi-agent context unit governance.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: No existing benchmark evaluates long-horizon consistency and hallucination/contradiction control as a function of memory authority level. The literature on long-context memory systems (MemGPT, Zep, ShardMemo, MemOS) focuses on capacity management, temporal accuracy, retrieval scalability, or lifecycle governance. A-MemGuard addresses adversarial memory but uses binary trust scoring, not graduated authority with upgrade-only promotions. ASB (ICLR 2025) provides an adversarial benchmark but assumes a flat memory store.
2. **Value delivered**: A citable, peer-reviewable evaluation demonstrating that context unit subsystems (authority hierarchy, rank differentiation, budget enforcement) provide measurable, structural resistance to adversarial drift. This positions context units in the research landscape and provides the DICE team with a presentation-ready artifact.
3. **Why now**: Wave 2, parallel with F02. The experiment framework (F01) provides the ablation infrastructure. The benchmarking UI (F02) provides rapid iteration on results. The paper synthesizes both into a coherent narrative.

## Scope

### In Scope

1. **Paper authoring**: Full tech report with Abstract, Introduction, Related Work, Architecture, Experiment Design, Results, Discussion, and Conclusion.
2. **Related work positioning**: Systematic comparison against MemGPT/Letta, Zep/Graphiti, ShardMemo, MemOS, A-MemGuard, and ASB with per-system analysis of trust/authority model, adversarial consideration, and consistency-control mechanism.
3. **Ablation results**: At least 4 conditions (FULL_UNITS, NO_UNITS, FLAT_AUTHORITY, NO_RANK_DIFFERENTIATION) x 3-5 scenarios x 5 repetitions, with effect sizes and confidence intervals.
4. **Adversary taxonomy documentation**: The 19-strategy tiered adversary (BASIC through EXPERT) as a contribution.
5. **Future work scoping**: Serendipitous retrieval (spreading activation in DICE graph) and A2A extension (multi-agent trust with asymmetric budgets).

### Out of Scope

1. Code changes (this is a publication, not a code feature).
2. Cross-domain evaluation (medical, legal, customer support) -- the paper evaluates collaborative fiction only and acknowledges this limitation.
3. Cross-model evaluation (the paper uses a single LLM backend and acknowledges this limitation).
4. Full conference paper preparation (the initial target is arXiv tech report; conference upgrade is a follow-on decision).
5. Human subject studies (evaluator calibration via human agreement is included, but large-scale human evaluation is out of scope).

## Dependencies

1. **F01 (experiment-framework)**: REQUIRED. Provides the ablation infrastructure, ExperimentReport with effect sizes, and cross-condition statistical comparison.
2. **F02 (first-class-benchmarking-ui)**: RECOMMENDED. Enables rapid iteration on experiment design and result interpretation during the paper-writing process. The paper can be written without F02 by inspecting ExperimentReports directly, but F02 accelerates the process.
3. **R01 (evaluator validity and calibration)**: REQUIRED. The paper MUST include evaluator calibration methodology. Without R01, the paper's credibility is undermined by unvalidated LLM-as-judge verdicts.
4. **R02 (related work landscape analysis)**: REQUIRED. The paper's related work section depends on systematic comparison data from R02.
5. **Priority**: MUST.
6. **No OpenSpec change slug** -- this is a publication deliverable, not a code change.

## Research Requirements (Optional)

| Task ID | Question | Relevance | Status |
|---------|----------|-----------|--------|
| R01 | How reliable is LLM-as-judge drift evaluation, and what calibration methodology ensures credible results? | The paper MUST report evaluator agreement and calibration methodology. Reviewers will challenge uncalibrated evaluator results. | Pending |
| R02 | How does context units position against MemGPT, Zep, ShardMemo, MemOS, and A-MemGuard? | The paper's related work section requires systematic per-system analysis with citations. | Pending |

## Impacted Areas

1. **Paper artifact**: Primary output is a LaTeX or Markdown tech report suitable for arXiv submission.
2. **Figures and tables**: Ablation result tables, effect size matrices, per-strategy breakdown charts, architecture diagram, and related work comparison table.
3. **Experiment data**: The paper references ExperimentReports produced by F01. Raw data SHOULD be archived alongside the paper for reproducibility.
4. **No code impact**: This feature produces a document, not code changes.

## Visibility Requirements

### UI Visibility

Not applicable. This is a publication deliverable.

### Observability Visibility

1. Experiment data referenced in the paper MUST be traceable to specific ExperimentReport IDs and OTEL trace IDs.
2. The paper SHOULD include an appendix or supplementary material linking each reported result to its experiment configuration and report ID, enabling independent verification.

## Acceptance Criteria

### Paper Structure

1. The paper MUST include the following sections: Abstract, Introduction, Related Work, Architecture (DICE + Context Units), Experiment Design, Results, Discussion, and Conclusion.
2. The Abstract MUST state the thesis, method (ablation study), and key finding (effect size for the primary comparison).
3. The Introduction MUST frame the gap: no existing evaluation of long-horizon consistency and hallucination/contradiction control as a function of memory authority level.

### Related Work

4. The paper MUST cite and discuss all six systems: MemGPT/Letta, Zep/Graphiti, ShardMemo, MemOS, A-MemGuard, and ASB (ICLR 2025).
5. Each system MUST be analyzed along three dimensions: trust/authority model (present/absent/binary/graduated), adversarial consideration (explicit/implicit/absent), and consistency-control mechanism (structural/incidental/absent).
6. The paper MUST NOT claim that context unit memory is the only or best approach. It MUST position it as a complementary mechanism with a specific strength (graduated-trust consistency control under long-horizon pressure, stress-tested with adversarial inputs).
7. The paper MUST NOT reference "Core Context Unit Memory" as a literature concept -- this term does not exist in the literature.

### Architecture

8. The Architecture section MUST describe: the DICE proposition model, the context unit extension (rank, authority, pinned, budget), the authority hierarchy (PROVISIONAL through CANON with upgrade-only constraint), rank clamping [100-900], and budget enforcement (max 20 active context units).
9. The Architecture section MUST include a diagram showing the context unit lifecycle from proposition extraction through authority promotion, decay, conflict detection, and eviction.

### Experiment Design

10. The Experiment Design section MUST define the four ablation conditions with precise descriptions of what each condition enables/disables.
11. The Experiment Design section MUST specify the scenario corpus (at least 3 adversarial scenarios), repetition count, evaluator model, and evaluation methodology (LLM-as-judge with per-fact verdicts).
12. The Experiment Design section MUST describe the adversary: 19 strategies across 4 tiers (BASIC, INTERMEDIATE, ADVANCED, EXPERT), tiered escalation, target switching, and multi-turn sequences. This adversary taxonomy SHOULD be presented as a contribution.
13. The Experiment Design section MUST describe the evaluation metrics: factSurvivalRate, driftAbsorptionRate, contradictionCount, majorContradictionCount, meanTurnsToFirstDrift, unitAttributionCount, and per-strategy effectiveness.

### Results

14. The Results section MUST include ablation results for at least 4 conditions x 3 scenarios.
15. The Results section MUST report Cohen's d effect sizes for key comparisons (FULL_UNITS vs. NO_UNITS, FULL_UNITS vs. FLAT_AUTHORITY, FULL_UNITS vs. NO_RANK_DIFFERENTIATION).
16. The Results section MUST include 95% confidence intervals for each metric in each condition.
17. The Results section SHOULD include per-strategy effectiveness breakdowns showing which attack strategies are most/least effective against each condition.
18. The Results section SHOULD include a per-fact analysis for at least one illustrative scenario (e.g., "Baron Krell is a four-armed sahuagin" survived in FULL_UNITS across all runs but drifted in NO_UNITS in 3/5 runs).

### Discussion

19. The Discussion section MUST interpret the effect sizes: what does it mean practically that FULL_UNITS has a large effect vs. NO_UNITS but a small effect vs. FLAT_AUTHORITY?
20. The Discussion section MUST include a "Future Work" subsection covering:
    - **Serendipitous retrieval**: Spreading activation in the DICE knowledge graph, 2+ hop random walks from active context units, quality vs. creativity tradeoff, potential for controlled creative "drift" as a feature.
    - **A2A extension**: Multi-agent context unit governance, per-agent budget partitioning, agent-level trust provenance, asymmetric context capacity across agents.
    - **Cross-domain evaluation**: Extending the evaluation to non-fiction domains (medical, legal, customer support).
    - **Cross-model evaluation**: Running the same experiments across multiple LLM backends to test generalization.
    - **Formal hypothesis testing**: Upgrading from effect sizes to statistical tests when sample sizes warrant.
21. The Discussion section MUST acknowledge limitations: single domain (collaborative fiction), single model, LLM-as-judge evaluator (with calibration data from R01), limited scenario corpus.

### Evaluator Calibration

22. The paper SHOULD include human agreement calibration for the LLM-as-judge evaluator (methodology from R01). This SHOULD report inter-rater agreement (Cohen's kappa or equivalent) on a calibration sample.
23. If human agreement calibration is not achievable before submission, the paper MUST acknowledge this as a limitation and describe the planned calibration methodology.

### Publication Metadata

24. Target venue: arXiv tech report + DICE team presentation. The paper MAY be upgraded to an ACL or EMNLP workshop submission if results and methodology are sufficiently strong.
25. The paper SHOULD include a reproducibility statement: scenario YAML files, experiment configuration, and evaluation methodology are open-source in the context units repository.

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Weak effect sizes** | Medium | High | If FULL_UNITS vs. NO_UNITS shows only small effects (d < 0.2), the paper's thesis is undermined. Mitigation: (1) increase repetitions to reduce variance, (2) focus on specific metrics/scenarios where effects are strongest, (3) reframe as "the effect is domain/scenario-dependent" with analysis of when context units help most. |
| **Evaluator unreliability** | Medium | High | If LLM-as-judge produces inconsistent verdicts, all results are questionable. Mitigation: R01 calibration study. If kappa is low, acknowledge the limitation and report calibration data transparently. |
| **Reviewer skepticism about domain** | Medium | Medium | Reviewers may argue D&D/collaborative fiction is too narrow. Mitigation: Frame as "collaborative fiction" (a recognized NLP evaluation domain). Emphasize that the mechanism is domain-agnostic; the experiment domain is specific by design. Acknowledge cross-domain evaluation as future work. |
| **Paper scope creep** | Medium | Low | The paper MAY expand into topics beyond the ablation study (e.g., detailed adversary analysis, system comparison benchmarks). Mitigation: Keep the paper focused on the thesis (authority-governed memory provides long-horizon consistency and hallucination control). Adversary taxonomy and system comparison are supporting content, not primary contributions. |
| **Timing dependency on F01 and F02** | High | Medium | The paper requires experiment results from F01 and benefits from F02's visualization for rapid iteration. Mitigation: Begin paper skeleton (related work, architecture, experiment design) before F01 is complete. Results section is filled in last. |
| **Citation currency** | Low | Medium | The related work landscape shifts rapidly. New memory systems MAY appear between writing and submission. Mitigation: R02 establishes the baseline landscape. Update citations during final review before submission. |

## Proposal Seed

### Change Slug

No OpenSpec change slug. This is a publication deliverable.

### Proposal Starter Inputs

1. **Thesis**: Trust/authority-governed working memory provides measurable, structural resistance to adversarial conversational drift that flat or unranked memory architectures do not.
2. **Method**: Controlled ablation study with 4 conditions, 3-5 scenarios, 5 repetitions per cell, using LLM-as-judge evaluation with human calibration.
3. **Key contribution claims**: (a) Architecture: rank/authority-governed working memory with upgrade-only promotions and budget enforcement. (b) Adversary: 19-strategy tiered adaptive adversary with escalation and multi-turn sequences. (c) Empirical: ablation study with effect sizes demonstrating the contribution of individual context unit subsystems. (d) Benchmark: reproducible scenarios and evaluation framework.
4. **Target audience**: NLP/AI researchers working on long-context memory, adversarial robustness, and conversational systems. Secondary: DICE team and the broader agent framework community.

### Suggested Capability Areas

1. **Related work analysis**: Systematic comparison against 6 systems with per-system trust/adversarial/drift analysis.
2. **Ablation study design and execution**: 4 conditions, 3-5 scenarios, cross-condition effect sizes.
3. **Adversary taxonomy**: 19 strategies across 4 tiers as a reusable contribution.
4. **Future work scoping**: Serendipitous retrieval and A2A as concrete follow-on directions.

### Candidate Requirement Blocks

1. **REQ-STRUCTURE**: The paper SHALL follow standard tech report structure with Abstract, Introduction, Related Work, Architecture, Experiment Design, Results, Discussion, and Conclusion.
2. **REQ-RELATED**: The paper SHALL cite and analyze MemGPT/Letta, Zep/Graphiti, ShardMemo, MemOS, A-MemGuard, and ASB along trust, adversarial, and drift dimensions.
3. **REQ-ABLATION**: The paper SHALL report ablation results for at least 4 conditions x 3 scenarios with Cohen's d effect sizes and 95% confidence intervals.
4. **REQ-ADVERSARY**: The paper SHALL document the 19-strategy tiered adversary taxonomy as a contribution.
5. **REQ-FUTURE**: The paper SHALL include a Future Work section covering serendipitous retrieval and A2A extension.
6. **REQ-CALIBRATION**: The paper SHOULD include human agreement calibration for the LLM-as-judge evaluator, or MUST acknowledge its absence as a limitation with a described mitigation plan.

## Validation Plan

1. **Internal review**: The paper draft MUST be reviewed by at least one person familiar with the DICE framework and one person familiar with the memory systems literature before submission.
2. **Result verification**: Every numeric claim in the Results section MUST be traceable to a specific ExperimentReport ID. Spot-checking SHOULD verify that reported numbers match the persisted data.
3. **Related work accuracy**: Every citation MUST be verified against the original source. Claims about other systems (e.g., "MemGPT uses OS-style paging") MUST be directly supported by the cited paper or documentation.
4. **Reproducibility check**: A second operator SHOULD be able to run the described experiment configuration and obtain results within the reported confidence intervals.
5. **Writing quality**: The paper SHOULD be checked for clarity, logical flow, and freedom from jargon that assumes reader familiarity with context units internals.
6. **arXiv formatting**: The paper MUST conform to arXiv submission requirements (LaTeX, PDF, metadata).

## Known Limitations

1. **Single domain**: The paper evaluates collaborative fiction (D&D). Generalization to other domains is claimed as plausible but not demonstrated. This MUST be stated explicitly.
2. **Single LLM backend**: Initial experiments use one model (likely GPT-4o-mini or GPT-4.1-mini). Cross-model generalization is future work.
3. **LLM-as-judge evaluation**: Even with calibration, LLM-as-judge verdicts are not ground truth. The paper MUST be transparent about this methodology choice and its limitations.
4. **Scenario corpus size**: 3-5 scenarios is small by benchmarking standards. The paper SHOULD frame this as a focused evaluation with curated adversarial scenarios, not a comprehensive benchmark.
5. **No formal hypothesis testing**: The paper reports effect sizes and confidence intervals, not p-values. This is a methodological choice (practical significance over statistical significance with small N), but reviewers MAY expect formal tests.
6. **Publication venue uncertainty**: arXiv has no peer review. If upgraded to a workshop submission, additional experiments or methodology refinements MAY be required by reviewers.
7. **Adversary is not human**: The 19-strategy adversary is LLM-generated, not a real adversarial player. This is a controlled experiment design choice, but the paper MUST acknowledge that human adversaries may behave differently.

## Suggested Command

No OpenSpec command. Paper writing proceeds as a documentation task outside the OpenSpec workflow. Experiment execution uses:

```
# Run the experiment from the benchmarking UI (F02)
# or programmatically via ExperimentRunner (F01)
```
